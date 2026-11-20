import { expect, test } from "@playwright/test";
import type { Page, Route } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { fillLogin } from "./login";

const cuenta = { username: "cobros.e2e", password: "sintetica" };
const registro = "/cobros/pedidos/27/registrar";
const pedido = {
  id: 27,
  clienteId: 10,
  clienteNombre: "Ada",
  clienteApellido: "Prueba",
  usuarioId: 1,
  usuarioNombre: "Personal",
  fechaIngreso: "2026-11-18T13:15:00",
  fechaEntregaEstimada: "2099-12-30",
  total: 50,
  detalles: [],
  estadoCodigo: "PENDIENTE",
  estadoPedido: { id: 1, codigo: "PENDIENTE", nombre: "Pendiente" },
};
const cobro = {
  id: 8,
  pedidoId: 27,
  cajaId: 4,
  movimientoCajaId: 9,
  montoCobrado: 12.53,
  metodoPagoCodigo: "EFECTIVO",
  metodoPagoNombre: "Efectivo",
  usuarioId: 1,
  usuarioNombre: "Personal",
  fechaCobro: "2026-11-18T14:30:04.123456",
};
const caja = {
  id: 4,
  estado: "ABIERTA",
  montoInicial: 100,
  fechaApertura: cobro.fechaCobro,
  usuarioAperturaNombre: "Personal",
};

function paginado(content: unknown[], number = 0, totalPages = 1) {
  return {
    content,
    page: { size: 20, number, totalElements: content.length, totalPages },
  };
}

async function abrir(
  page: Page,
  path: string,
  config: {
    rol?: string;
    cancelar?: boolean;
    cerrarCaja?: boolean;
    paginar?: boolean;
    post?: (route: Route, n: number) => Promise<void>;
  } = {},
) {
  const llamadas: { clave: string; body: string | null }[] = [];
  let confirmado = false;
  await page.route(
    (url) => url.pathname.startsWith("/api/"),
    async (route) => {
      const url = new URL(route.request().url());
      const p = Number(url.searchParams.get("page"));
      switch (url.pathname) {
        case "/api/auth/login":
          return route.fulfill({
            json: {
              accessToken: "sintetico",
              tokenType: "Bearer",
              expiresIn: 3600,
            },
          });
        case "/api/auth/me":
          return route.fulfill({
            json: {
              id: 1,
              username: cuenta.username,
              nombre: "Personal",
              email: "personal@example.test",
              rol: config.rol ?? "EMPLEADO",
            },
          });
        case "/api/pedidos":
          return route.fulfill({ json: paginado([pedido]) });
        case "/api/pedidos/27":
          return route.fulfill({
            json: config.cancelar
              ? {
                  ...pedido,
                  estadoPedido: {
                    id: 5,
                    codigo: "CANCELADO",
                    nombre: "Cancelado",
                  },
                }
              : pedido,
          });
        case "/api/pedidos/27/saldo":
          return route.fulfill({
            json: {
              pedidoId: 27,
              totalPedido: 50,
              totalCobrado: confirmado ? 12.53 : 0,
              saldoPendiente: confirmado ? 37.47 : 50,
              estadoPagoCodigo: confirmado ? "PARCIAL" : "PENDIENTE",
              estadoPagoNombre: confirmado
                ? "Parcialmente pagado"
                : "Pendiente",
            },
          });
        case "/api/pedidos/27/cobros":
          if (route.request().method() === "POST") {
            llamadas.push({
              clave: route.request().headers()["idempotency-key"],
              body: route.request().postData(),
            });
            if (config.post) return config.post(route, llamadas.length);
            confirmado = true;
            return route.fulfill({ status: 201, json: cobro });
          }
          return route.fulfill({
            json: config.paginar
              ? paginado([{ ...cobro, id: cobro.id + p }], p, 2)
              : paginado(confirmado ? [cobro] : []),
          });
        case "/api/cobros/8":
        case "/api/cobros/9":
          return route.fulfill({
            json: { ...cobro, id: Number(url.pathname.split("/").at(-1)) },
          });
        case "/api/estados-pedido":
        case "/api/pedidos/27/historial":
        case "/api/tipos-movimiento-caja":
          return route.fulfill({ json: [] });
        case "/api/cajas/abierta":
          return config.cerrarCaja
            ? route.fulfill({ status: 404, json: {} })
            : route.fulfill({ json: caja });
        case "/api/cajas/4/saldo":
          return route.fulfill({
            json: {
              ...caja,
              cajaId: 4,
              estadoCaja: "ABIERTA",
              saldoGeneral: confirmado ? 112.53 : 100,
              efectivoDisponible: confirmado ? 112.53 : 100,
              totalIngresos: confirmado ? 12.53 : 0,
              totalEgresos: 0,
              ingresosEfectivo: confirmado ? 12.53 : 0,
              egresosEfectivo: 0,
            },
          });
        case "/api/cajas/4/movimientos":
          return route.fulfill({ json: paginado([]) });
        case "/api/metodos-pago":
          return route.fulfill({
            json: [{ id: 1, codigo: "EFECTIVO", nombre: "Efectivo" }],
          });
        default:
          return route.fulfill({ status: 404, json: {} });
      }
    },
  );
  await page.goto(path);
  await fillLogin(page, cuenta);
  return llamadas;
}

async function revisar(page: Page, monto = "12,53") {
  await page.getByLabel("Importe recibido").fill(monto);
  await page.getByLabel("Método de pago").selectOption("1");
  await page.getByRole("button", { name: "Revisar cobro" }).click();
  return page.getByRole("button", { name: "Confirmar cobro", exact: true });
}

test("consulta el historial paginado, el detalle y el movimiento desde un móvil", async ({
  page,
}) => {
  await page.setViewportSize({ width: 375, height: 820 });
  await abrir(page, "/cobros", { rol: "ADMINISTRADOR", paginar: true });
  await page.getByLabel("Pedido", { exact: true }).selectOption("27");
  await expect(page.getByRole("link", { name: "Cobro #8" })).toBeVisible();
  await page.getByRole("button", { name: "Siguiente" }).click();
  await page.getByRole("link", { name: "Cobro #9" }).click();
  await expect(page.getByRole("heading", { name: "Cobro #9" })).toBeVisible();
  await expect(
    page.getByRole("link", { name: "Movimiento #9" }),
  ).toHaveAttribute("href", "/caja/4/movimientos/9");
  await expect(page.getByRole("link", { name: "Pedido #27" })).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
});

test("empleado registra un cobro parcial desde el pedido con un solo POST", async ({
  page,
}) => {
  const llamadas = await abrir(page, "/pedidos/27");
  await page.getByRole("link", { name: "Registrar cobro" }).click();
  await (await revisar(page)).dblclick();
  await expect(page.getByText("Cobro #8 registrado")).toBeVisible();
  await expect(
    page.getByText("Parcialmente pagado", { exact: true }),
  ).toBeVisible();
  expect(llamadas).toEqual([
    {
      clave: expect.stringMatching(/^[0-9a-f-]{36}$/),
      body: '{"monto":12.53,"metodoPagoId":1}',
    },
  ]);
  await page.getByRole("link", { name: "Pedido #27", exact: true }).click();
  await expect(page.getByRole("link", { name: "Cobro #8" })).toBeVisible();
  await expect(page.getByText(/37,47/)).toBeVisible();
  await page.getByRole("button", { name: "Panel inicial" }).click();
  await page
    .getByRole("dialog")
    .getByRole("link", { name: "Caja", exact: true })
    .click();
  await expect(page.getByText(/112,53/)).toHaveCount(2);
});

test("valida selección, cero y saldo antes de enviar", async ({ page }) => {
  const llamadas = await abrir(page, registro);
  await page.getByLabel("Importe recibido").fill("0");
  await page.getByRole("button", { name: "Revisar cobro" }).click();
  await expect(
    page.getByText("El importe debe ser mayor a cero."),
  ).toBeVisible();
  await expect(
    page.getByText("Seleccioná un método de pago activo."),
  ).toBeVisible();
  await revisar(page, "50,01");
  await expect(
    page.getByText("El importe supera el saldo pendiente."),
  ).toBeVisible();
  expect(llamadas).toHaveLength(0);
});

for (const [config, mensaje] of [
  [
    { cerrarCaja: true },
    "Se necesita una caja abierta para registrar un cobro.",
  ],
  [{ cancelar: true }, "El pedido está cancelado."],
] as const) {
  test(mensaje, async ({ page }) => {
    const llamadas = await abrir(page, registro, config);
    await expect(page.getByText(mensaje)).toBeVisible();
    await expect(page.getByLabel("Importe recibido")).toHaveCount(0);
    expect(llamadas).toHaveLength(0);
  });
}

for (const [status, detail] of [
  [400, "El método ya no está disponible."],
  [403, "No tenés permiso para acceder."],
  [409, "El cobro supera el saldo pendiente del pedido."],
] as const) {
  test(`muestra el rechazo definitivo ${status}`, async ({ page }) => {
    const llamadas = await abrir(page, registro, {
      post: (route) => route.fulfill({ status, json: { detail } }),
    });
    await (await revisar(page)).click();
    await expect(page.getByText(detail)).toBeVisible();
    await expect(
      page.getByRole("button", { name: "Reintentar el mismo envío" }),
    ).toHaveCount(0);
    expect(llamadas).toHaveLength(1);
  });
}

test("un 401 devuelve al login y conserva la ruta", async ({ page }) => {
  await abrir(page, registro, {
    post: (route) => route.fulfill({ status: 401, json: {} }),
  });
  await (await revisar(page)).click();
  await expect(page).toHaveURL(/\/login$/);
  await fillLogin(page, cuenta);
  await expect(page).toHaveURL(new RegExp(registro + "$"));
  await expect(page.getByLabel("Importe recibido")).toBeVisible();
});

test("espera Retry-After y recupera el resultado original con el mismo cuerpo", async ({
  page,
}) => {
  const llamadas = await abrir(page, registro, {
    post: (route, n) =>
      n === 1
        ? route.fulfill({
            status: 409,
            headers: { "Retry-After": "2" },
            json: { codigo: "IDEMPOTENCIA_EN_CURSO" },
          })
        : route.fulfill({ status: 201, json: cobro }),
  });
  await (await revisar(page)).click();
  const reintento = page.getByRole("button", {
    name: "Reintentar el mismo envío",
  });
  await expect(reintento).toBeDisabled();
  await expect(reintento).toBeEnabled();
  expect(llamadas).toHaveLength(1);
  await reintento.click();
  await expect(page.getByText("Cobro #8 registrado")).toBeVisible();
  expect(llamadas).toEqual([llamadas[0], llamadas[0]]);
});

test("la clave reutilizada queda bloqueada y exige verificación", async ({
  page,
}) => {
  const llamadas = await abrir(page, registro, {
    post: (route) =>
      route.fulfill({
        status: 409,
        json: { codigo: "IDEMPOTENCIA_CLAVE_REUTILIZADA" },
      }),
  });
  await (await revisar(page)).click();
  await expect(
    page.getByText(/ya está asociada a otra operación/),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Reintentar el mismo envío" }),
  ).toHaveCount(0);
  await expect(
    page.getByRole("button", { name: "Ya verifiqué el historial" }),
  ).toHaveCount(0);
  await page
    .getByRole("button", { name: "Actualizar saldo e historial" })
    .click();
  await expect(
    page.getByRole("button", { name: "Ya verifiqué el historial" }),
  ).toBeVisible();
  expect(llamadas).toHaveLength(1);
});

test("un timeout conserva el envío al navegar y al reintentar", async ({
  page,
}) => {
  const llamadas = await abrir(page, registro, {
    post: (route, n) =>
      n === 1
        ? route.fulfill({ status: 408, json: {} })
        : route.fulfill({ status: 201, json: cobro }),
  });
  await (await revisar(page)).click();
  await expect(
    page.getByText(/Resultado pendiente de confirmar/),
  ).toBeVisible();
  await page.getByRole("link", { name: "Consultar pedido #27" }).click();
  await page.getByRole("link", { name: "Revisar envío pendiente" }).click();
  await page.getByRole("button", { name: "Reintentar el mismo envío" }).click();
  await expect(page.getByText("Cobro #8 registrado")).toBeVisible();
  expect(llamadas).toEqual([llamadas[0], llamadas[0]]);
});

test("tras recargar exige verificar una respuesta perdida antes de registrar", async ({
  page,
}) => {
  const llamadas = await abrir(page, registro, {
    post: (route) => route.abort("connectionreset"),
  });
  await (await revisar(page)).click();
  await expect(
    page.getByText(/Resultado pendiente de confirmar/),
  ).toBeVisible();
  expect(await page.evaluate(() => Object.values(sessionStorage))).toEqual([
    "27",
  ]);
  await page.reload();
  await fillLogin(page, cuenta);
  await expect(
    page.getByText(/Hay un envío previo para verificar/),
  ).toBeVisible();
  await expect(page.getByLabel("Importe recibido")).toHaveCount(0);
  await page
    .getByRole("button", { name: "Actualizar saldo e historial" })
    .click();
  await page.getByRole("button", { name: "Ya verifiqué el historial" }).click();
  await expect(page.getByLabel("Importe recibido")).toBeVisible();
  expect(llamadas).toHaveLength(1);
});
