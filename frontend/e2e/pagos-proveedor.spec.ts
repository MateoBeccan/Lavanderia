import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";
import type { Page, Route } from "@playwright/test";
import { fillLogin } from "./login";

const login = { username: "operador", password: "clave-sintetica" };
const registro = "/pagos-proveedor/compras/42/registrar";
const rutaPagos = "/api/compras/42/pagos";
const fecha = "2026-11-18T16:10:00";
const compra = {
  id: 42,
  proveedorId: 2,
  proveedorNombre: "Distribuidora Sur",
  usuarioId: 1,
  usuarioNombre: "Operador",
  fechaCompra: fecha,
  numeroComprobante: null,
  totalCompra: 30,
  detalles: [],
};
const efectivo = { id: 1, codigo: "EFECTIVO", nombre: "Efectivo" };
const transferencia = {
  id: 2,
  codigo: "TRANSFERENCIA",
  nombre: "Transferencia",
};

function pago(id: number, montoPagado: number, metodo = efectivo) {
  return {
    id,
    compraId: 42,
    proveedorId: 2,
    proveedorNombre: compra.proveedorNombre,
    movimientoCajaId: id + 10,
    cajaId: 8,
    metodoPagoCodigo: metodo.codigo,
    metodoPagoNombre: metodo.nombre,
    usuarioId: 1,
    usuarioNombre: "Operador",
    fechaPago: fecha,
    montoPagado,
  };
}

async function preparar(
  page: Page,
  path: string,
  opciones: {
    rol?: string;
    sinCaja?: boolean;
    efectivoDisponible?: number;
    historial?: ReturnType<typeof pago>[];
    post?: (route: Route, numero: number) => Promise<void>;
    denegar?: boolean;
  } = {},
) {
  const pagos = [...(opciones.historial ?? [])];
  const envios: { clave: string; cuerpo: string }[] = [];
  const lecturas: URL[] = [];
  await page.route("**/api/**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const paginar = (content: unknown[], total = content.length) => {
      const number = Number(url.searchParams.get("page"));
      const size = Number(url.searchParams.get("size")) || 20;
      return {
        content,
        page: {
          number,
          size,
          totalElements: total,
          totalPages: Math.ceil(total / size),
        },
      };
    };
    if (url.pathname === rutaPagos && request.method() === "POST") {
      envios.push({
        clave: request.headers()["idempotency-key"],
        cuerpo: request.postData() ?? "",
      });
      if (opciones.post) return opciones.post(route, envios.length);
      const { monto, metodoPagoId } = request.postDataJSON() as {
        monto: number;
        metodoPagoId: number;
      };
      const nuevo = pago(
        10 + pagos.length,
        monto,
        metodoPagoId === 1 ? efectivo : transferencia,
      );
      pagos.unshift(nuevo);
      return route.fulfill({ status: 201, json: nuevo });
    }
    lecturas.push(url);
    const totalPagado =
      pagos.reduce(
        (total, item) => total + Math.round(item.montoPagado * 100),
        0,
      ) / 100;
    const totalEfectivo =
      pagos
        .filter(({ metodoPagoCodigo }) => metodoPagoCodigo === "EFECTIVO")
        .reduce(
          (total, item) => total + Math.round(item.montoPagado * 100),
          0,
        ) / 100;
    const saldoPendiente = (3000 - Math.round(totalPagado * 100)) / 100;
    const estadoPagoCodigo =
      saldoPendiente === 0
        ? "PAGADO"
        : totalPagado > 0
          ? "PARCIAL"
          : "PENDIENTE";
    const estadoPagoNombre = {
      PENDIENTE: "Pendiente",
      PARCIAL: "Parcial",
      PAGADO: "Pagado",
    }[estadoPagoCodigo];
    switch (url.pathname) {
      case "/api/auth/login":
        return route.fulfill({
          json: { accessToken: "prueba", tokenType: "Bearer", expiresIn: 3600 },
        });
      case "/api/auth/me":
        return route.fulfill({
          json: {
            id: 1,
            username: login.username,
            nombre: "Operador",
            email: "operador@example.test",
            rol: opciones.rol ?? "EMPLEADO",
          },
        });
      case "/api/estados-pago":
        return route.fulfill({
          json: [
            {
              id: 1,
              codigo: estadoPagoCodigo,
              nombre: estadoPagoNombre,
              activo: true,
            },
          ],
        });
      case "/api/compras":
        return route.fulfill({
          json: paginar([{ ...compra, estadoPagoCodigo }]),
        });
      case "/api/compras/42":
        return opciones.denegar
          ? route.fulfill({
              status: 403,
              json: { detail: "Acceso denegado por Spring." },
            })
          : route.fulfill({
              json: { ...compra, estadoPagoCodigo, estadoPagoNombre },
            });
      case "/api/compras/42/saldo":
        return route.fulfill({
          json: {
            compraId: 42,
            totalCompra: 30,
            totalPagado,
            saldoPendiente,
            estadoPagoCodigo,
            estadoPagoNombre,
          },
        });
      case rutaPagos: {
        const number = Number(url.searchParams.get("page"));
        return route.fulfill({
          json: paginar(
            pagos.slice(number * 10, (number + 1) * 10),
            pagos.length,
          ),
        });
      }
      case "/api/pagos-proveedor/21":
        return route.fulfill({ json: pago(21, 1) });
      case "/api/pagos-proveedor/21/comprobante":
        return route.fulfill({
          json: {
            id: 4,
            tipoOperacion: "PAGO_PROVEEDOR",
            compraId: 42,
            pagoProveedorId: 21,
            cobroId: null,
            pedidoId: null,
            fechaEmision: "2026-11-18",
            observaciones: "Recibo original",
            montoOperacion: pago(21, 1).montoPagado,
            metodoPagoCodigo: "EFECTIVO",
            metodoPagoNombre: "Efectivo",
            movimientoCajaId: 31,
          },
        });
      case "/api/cajas/abierta":
      case "/api/cajas/8":
        return opciones.sinCaja
          ? route.fulfill({
              status: 404,
              json: { detail: "No hay caja abierta." },
            })
          : route.fulfill({
              json: {
                id: 8,
                estado: "ABIERTA",
                fechaApertura: fecha,
                montoInicial: 100,
              },
            });
      case "/api/cajas/8/saldo":
        return route.fulfill({
          json: {
            cajaId: 8,
            estadoCaja: "ABIERTA",
            saldoGeneral: 100 - totalPagado,
            efectivoDisponible:
              (opciones.efectivoDisponible ?? 100) - totalEfectivo,
            montoInicial: 100,
            totalIngresos: 0,
            totalEgresos: totalPagado,
            efectivoContado: null,
            diferenciaArqueo: null,
            usuarioAperturaNombre: "Operador",
            fechaApertura: fecha,
          },
        });
      case "/api/cajas/8/movimientos":
      case "/api/clientes":
      case "/api/pedidos":
        return route.fulfill({ json: paginar([]) });
      case "/api/metodos-pago":
        return route.fulfill({ json: [efectivo, transferencia] });
      case "/api/tipos-movimiento-caja":
        return route.fulfill({ json: [] });
      default:
        return route.fulfill({
          status: 404,
          json: { detail: "Recurso ausente." },
        });
    }
  });
  await page.goto(path);
  await fillLogin(page, login);
  return { envios, lecturas };
}

async function revisar(page: Page, monto = "8,25", metodo = "1") {
  await page.getByLabel("Importe pagado").fill(monto);
  await page.getByLabel("Método de pago").selectOption(metodo);
  await page.getByRole("button", { name: "Revisar pago" }).click();
}

test("historial paginado y detalle con comprobante, navegación móvil y accesibilidad", async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 });
  const { lecturas } = await preparar(page, "/pagos-proveedor?proveedorId=2", {
    historial: Array.from({ length: 11 }, (_, n) => pago(31 - n, 1)),
  });
  await page
    .getByRole("link", { name: "Consultar pagos de compra #42" })
    .click();
  await expect(page.getByRole("link", { name: "Pago #31" })).toBeVisible();
  expect(
    lecturas.some(
      ({ pathname, searchParams }) =>
        pathname === "/api/compras" && searchParams.get("proveedorId") === "2",
    ),
  ).toBe(true);
  await page.getByRole("button", { name: "Siguiente" }).click();
  await page.getByRole("link", { name: "Pago #21" }).focus();
  await page.keyboard.press("Enter");
  await expect(
    page.getByRole("heading", { name: "Pago a proveedor #21" }),
  ).toBeVisible();
  await expect(page.getByText("Recibo original")).toBeVisible();
  await expect(
    page.getByRole("link", { name: "Movimiento #31" }),
  ).toHaveAttribute("href", "/caja/8/movimientos/31");
  await expect(
    page.getByRole("link", { name: "Distribuidora Sur" }),
  ).toHaveAttribute("href", "/proveedores/2");
  await expect(page.getByRole("link", { name: "Compra #42" })).toHaveAttribute(
    "href",
    "/compras/42",
  );
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth),
  ).toBeLessThanOrEqual(390);
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
});

test("pago parcial y total, doble clic, Compra, listado, Caja y permisos de lectura de empleado", async ({
  page,
}) => {
  const { envios, lecturas } = await preparar(page, "/compras/42");
  await page.getByRole("link", { name: "Registrar pago" }).click();
  await revisar(page);
  await page.getByRole("button", { name: "Confirmar pago" }).dblclick();
  await expect(
    page.getByRole("heading", { name: "Pago #10 registrado" }),
  ).toBeVisible();
  await expect(page.getByText("Parcial", { exact: true })).toBeVisible();
  expect(envios).toHaveLength(1);
  await page.getByRole("link", { name: "Volver a la compra" }).click();
  await expect(page.getByRole("link", { name: "Pago #10" })).toBeVisible();
  await expect(page.getByRole("link", { name: "Editar compra" })).toHaveCount(
    0,
  );
  await page.getByRole("link", { name: "Registrar pago" }).click();
  await page
    .getByRole("button", { name: "Volver al saldo de la compra" })
    .click();
  await revisar(page, "21,75", "2");
  await page.getByRole("button", { name: "Confirmar pago" }).click();
  await expect(page.getByText("Pagado", { exact: true })).toBeVisible();
  expect(envios).toHaveLength(2);
  expect(envios[0].clave).toMatch(/^[0-9a-f-]{36}$/i);
  expect(envios[0].clave).not.toBe(envios[1].clave);
  await page.getByRole("link", { name: "Volver a la compra" }).click();
  await expect(page.getByRole("link", { name: "Registrar pago" })).toHaveCount(
    0,
  );
  await page.getByRole("link", { name: "Volver a compras" }).click();
  await expect(
    page.getByRole("cell", { name: "Pagado", exact: true }),
  ).toBeVisible();
  await page.getByRole("link", { name: /MIRÁ QUE TE LAVO/ }).click();
  await expect(
    page
      .getByRole("region", { name: "Estado de la operación" })
      .getByText("Abierta", { exact: true }),
  ).toBeVisible();
  await page
    .getByRole("region", { name: "Acceso rápido" })
    .getByRole("link", { name: /^Caja\b/ })
    .click();
  await expect(page.getByText(/70,00/)).toBeVisible();
  await expect(page.getByText(/91,75/)).toBeVisible();
  expect(
    lecturas.filter(({ pathname }) => pathname === "/api/cajas/8/saldo").length,
  ).toBeGreaterThan(1);
});

test("valida saldo, efectivo y método activo antes de enviar", async ({
  page,
}) => {
  const { envios } = await preparar(page, registro, {
    efectivoDisponible: 4,
    rol: "ADMINISTRADOR",
  });
  await revisar(page, "30,01", "2");
  await expect(
    page.getByText("El importe supera el saldo pendiente."),
  ).toBeVisible();
  await revisar(page, "4,01");
  await expect(
    page.getByText("El pago supera el efectivo disponible de la caja."),
  ).toBeVisible();
  await revisar(page, "3", "");
  await expect(
    page.getByText("Seleccioná un método de pago activo."),
  ).toBeVisible();
  expect(envios).toHaveLength(0);
});

test("bloquea el pago si no hay caja abierta o Spring deniega el acceso", async ({
  page,
  browser,
  baseURL,
}) => {
  const { envios } = await preparar(page, registro, { sinCaja: true });
  await expect(
    page.getByText("Se necesita una caja abierta para registrar un pago."),
  ).toBeVisible();
  await expect(page.getByRole("button", { name: "Revisar pago" })).toHaveCount(
    0,
  );
  expect(envios).toHaveLength(0);
  const otra = await browser.newPage({ baseURL });
  try {
    await preparar(otra, registro, { denegar: true });
    await expect(otra.getByText("Acceso denegado por Spring.")).toBeVisible();
    await expect(
      otra.getByRole("button", { name: "Revisar pago" }),
    ).toHaveCount(0);
  } finally {
    await otra.close();
  }
});

test("timeout y conflicto en curso conservan UUID y cuerpo y respetan Retry-After", async ({
  page,
}) => {
  await page.addInitScript(() => {
    const timeout = AbortSignal.timeout.bind(AbortSignal);
    AbortSignal.timeout = (ms) => timeout(ms === 15000 ? 350 : ms);
  });
  const { envios } = await preparar(page, registro, {
    post: async (route, numero) => {
      if (numero === 1) {
        await new Promise((resolve) => setTimeout(resolve, 700));
        return route.abort().catch(() => undefined);
      }
      if (numero === 2)
        return route.fulfill({
          status: 409,
          headers: { "Retry-After": "2" },
          json: { codigo: "IDEMPOTENCIA_EN_CURSO" },
        });
      return route.fulfill({ status: 201, json: pago(10, 8.25) });
    },
  });
  await revisar(page);
  await page.getByRole("button", { name: "Confirmar pago" }).click();
  await expect(
    page.getByText(/Resultado pendiente de confirmar/),
  ).toBeVisible();
  expect(envios).toHaveLength(1);
  const reintentar = page.getByRole("button", {
    name: "Reintentar el mismo envío",
  });
  await reintentar.click();
  await expect(page.getByText(/todavía está procesando/)).toBeVisible();
  await expect(reintentar).toBeDisabled();
  await reintentar.click();
  await expect(
    page.getByRole("heading", { name: "Pago #10 registrado" }),
  ).toBeVisible();
  expect(envios).toEqual([envios[0], envios[0], envios[0]]);
});

test("UUID reutilizado requiere conciliar con saldo e historial, incluso tras recargar", async ({
  page,
}) => {
  const { envios, lecturas } = await preparar(page, registro, {
    post: (route) =>
      route.fulfill({
        status: 409,
        json: { codigo: "IDEMPOTENCIA_CLAVE_REUTILIZADA" },
      }),
  });
  await revisar(page);
  await page.getByRole("button", { name: "Confirmar pago" }).click();
  await expect(page.getByText(/ya corresponde a otra operación/)).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Reintentar el mismo envío" }),
  ).toHaveCount(0);
  await page.reload();
  await fillLogin(page, login);
  await expect(
    page.getByText(/Hay un envío pendiente de conciliar/),
  ).toBeVisible();
  await expect(page.getByRole("button", { name: "Revisar pago" })).toHaveCount(
    0,
  );
  const desde = lecturas.length;
  await page
    .getByRole("button", { name: "Actualizar saldo e historial" })
    .click();
  await page.getByRole("button", { name: "Ya concilié el envío" }).click();
  await expect(
    page.getByRole("button", { name: "Revisar pago" }),
  ).toBeVisible();
  expect(lecturas.slice(desde).map(({ pathname }) => pathname)).toEqual(
    expect.arrayContaining([rutaPagos, "/api/compras/42/saldo"]),
  );
  expect(envios).toHaveLength(1);
});
