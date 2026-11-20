import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";
import type { Page } from "@playwright/test";
import { fillLogin } from "./login";

const creds = { username: "caja.e2e", password: "clave-sintetica" };
const fecha = "2026-11-18T11:00:00";
const caja = {
  id: 4,
  estado: "ABIERTA",
  fechaApertura: fecha,
  fechaCierre: null,
  montoInicial: 100,
  montoFinalCalculado: null,
  efectivoContado: null,
  efectivoEsperado: null,
  diferenciaArqueo: null,
  usuarioAperturaId: 1,
  usuarioAperturaNombre: "Ana",
  usuarioCierreId: null,
  usuarioCierreNombre: null,
};
const saldo = {
  ...caja,
  cajaId: 4,
  estadoCaja: "ABIERTA",
  saldoGeneral: 231.55,
  efectivoDisponible: 150,
  totalIngresos: 141.55,
  totalEgresos: 10,
  ingresosEfectivo: 60,
  egresosEfectivo: 10,
};
const movimiento = {
  id: 52,
  cajaId: 4,
  monto: 10,
  tipoMovimientoCodigo: "EGRESO_MANUAL",
  tipoMovimientoNombre: "Egreso manual",
  metodoPagoCodigo: "EFECTIVO",
  metodoPagoNombre: "Efectivo",
  usuarioId: 2,
  usuarioNombre: "Leo",
  descripcion: "Insumos",
  fechaHora: fecha,
};

async function entorno(
  page: Page,
  rol: "ADMINISTRADOR" | "EMPLEADO" = "ADMINISTRADOR",
) {
  await page.route("**/api/**", async (route) => {
    const { pathname, searchParams } = new URL(route.request().url());
    switch (pathname) {
      case "/api/auth/login":
        return route.fulfill({
          json: {
            accessToken: "token-sintetico",
            tokenType: "Bearer",
            expiresIn: 3600,
          },
        });
      case "/api/auth/me":
        return route.fulfill({
          json: {
            id: 1,
            username: creds.username,
            nombre: "Ana",
            email: "ana@example.test",
            rol,
          },
        });
      case "/api/cajas/abierta":
        return route.fulfill({ json: caja });
      case "/api/cajas/4/saldo":
        return route.fulfill({ json: saldo });
      case "/api/tipos-movimiento-caja":
        return route.fulfill({
          json: [{ id: 1, codigo: "EGRESO_MANUAL", nombre: "Egreso manual" }],
        });
      case "/api/metodos-pago":
        return route.fulfill({
          json: [{ id: 2, codigo: "EFECTIVO", nombre: "Efectivo" }],
        });
      case "/api/cajas/4/movimientos": {
        const number = Number(searchParams.get("page"));
        const filtrado = searchParams.has("tipo");
        return route.fulfill({
          json: {
            content: filtrado ? [] : [{ ...movimiento, id: 52 - number }],
            page: {
              number,
              size: 20,
              totalPages: filtrado ? 0 : 2,
              totalElements: filtrado ? 0 : 21,
            },
          },
        });
      }
      case "/api/movimientos-caja/52":
        return route.fulfill({ json: movimiento });
      default:
        return route.fulfill({
          status: 404,
          json: { detail: "Recurso inexistente." },
        });
    }
  });
}

async function ingresar(page: Page, path = "/caja") {
  await page.goto(path);
  await fillLogin(page, creds);
}

async function confirmar(page: Page) {
  await page.getByLabel("Efectivo contado").fill("149,25");
  await page.getByRole("button", { name: "Revisar cierre" }).click();
  await page.getByRole("button", { name: "Cerrar caja", exact: true }).click();
  const dialog = page.getByRole("alertdialog");
  await expect(dialog).toContainText("149,25");
  return dialog.getByRole("button", { name: "Confirmar cierre" });
}

test("Caja lee saldo oficial, detalle, filtros y páginas en móvil", async ({
  page,
}) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await entorno(page, "EMPLEADO");
  await ingresar(page);
  await expect(
    page.getByRole("heading", { name: "Caja #4 · Abierta" }),
  ).toBeVisible();
  await expect(page.getByText(/231,55/)).toBeVisible();
  await expect(
    page.getByRole("link", { name: "Historial de cajas" }),
  ).toHaveCount(0);
  await expect(page.getByLabel("Efectivo contado")).toHaveCount(0);
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  await page.getByRole("button", { name: "Siguiente" }).click();
  await expect(page.getByText("Página 2 de 2")).toBeVisible();
  await page.getByRole("button", { name: "Anterior" }).click();
  await page.getByRole("link", { name: "Ver movimiento #52" }).click();
  await expect(page.getByText("Leo (#2)")).toBeVisible();
  await page.getByRole("link", { name: "Volver a caja #4" }).click();
  await page.getByLabel("Tipo", { exact: true }).selectOption("EGRESO_MANUAL");
  await page.getByLabel("Método de pago").selectOption("2");
  await page.getByLabel("Desde", { exact: true }).fill("2026-11-18");
  await page.getByLabel("Hasta", { exact: true }).fill("2026-11-18");
  const consulta = page.waitForRequest((r) => {
    const u = new URL(r.url());
    return (
      u.pathname === "/api/cajas/4/movimientos" && u.searchParams.has("tipo")
    );
  });
  await page.getByRole("button", { name: "Aplicar filtros" }).click();
  expect(
    Object.fromEntries(new URL((await consulta).url()).searchParams),
  ).toEqual({
    page: "0",
    size: "20",
    tipo: "EGRESO_MANUAL",
    metodoPagoId: "2",
    desde: "2026-11-18",
    hasta: "2026-11-18",
  });
  await expect(
    page.getByRole("heading", { name: "Sin movimientos" }),
  ).toBeVisible();
});

test("Caja restringe historial a administradores", async ({ page }) => {
  await entorno(page, "EMPLEADO");
  await ingresar(page, "/caja/historial");
  await expect(
    page.getByRole("heading", { name: "Acceso restringido" }),
  ).toBeVisible();
});

for (const status of [401, 403, 404, 0]) {
  test(`Caja maneja consulta con resultado ${status}`, async ({ page }) => {
    await entorno(page);
    await page.route("**/api/cajas/abierta", (route) =>
      status === 0
        ? route.abort("connectionrefused")
        : route.fulfill({
            status,
            json: { detail: "Acceso de prueba rechazado." },
          }),
    );
    await ingresar(page);
    if (status === 401) await expect(page).toHaveURL(/\/login$/);
    if (status === 403)
      await expect(page.getByRole("alert")).toHaveText(
        "Acceso de prueba rechazado.",
      );
    if (status === 404)
      await expect(
        page.getByRole("heading", { name: "Sin caja abierta" }),
      ).toBeVisible();
    if (status === 0)
      await expect(
        page.getByRole("button", { name: "Reintentar" }),
      ).toBeVisible();
  });
}

test("Caja valida el arqueo y muestra conflicto de cierre", async ({
  page,
}) => {
  await entorno(page);
  await page.route("**/api/cajas/4/cerrar", (route) => {
    expect(route.request().method()).toBe("PATCH");
    expect(route.request().postDataJSON()).toEqual({ efectivoContado: 149.25 });
    return route.fulfill({
      status: 409,
      json: { detail: "La caja ya está cerrada." },
    });
  });
  await ingresar(page);
  await page.getByLabel("Efectivo contado").fill("-1");
  await page.getByRole("button", { name: "Revisar cierre" }).click();
  await expect(page.getByRole("alert")).toContainText("Ingresá un importe");
  await (await confirmar(page)).click();
  await expect(page.getByRole("alert")).toContainText(
    "La caja ya está cerrada.",
  );
});

test("Caja bloquea otro envío cuando el cierre pierde la respuesta", async ({
  page,
}) => {
  await entorno(page);
  let envios = 0;
  await page.route("**/api/cajas/4/cerrar", async (route) => {
    envios++;
    await route.fulfill({
      status: 408,
      json: { detail: "Solicitud vencida." },
    });
  });
  await ingresar(page);
  await (await confirmar(page)).dblclick();
  await expect(page.getByRole("alert")).toContainText("Resultado incierto");
  await page
    .getByRole("button", { name: "Consultar estado actualizado" })
    .click();
  await expect(
    page.getByRole("button", { name: "Revisar cierre" }),
  ).toHaveCount(0);
  expect(envios).toBe(1);
});

async function revisarApertura(page: Page, importe: string) {
  await page.getByLabel("Importe inicial").fill(importe);
  await page.getByRole("button", { name: "Revisar apertura" }).click();
}

async function confirmarApertura(page: Page) {
  await page.getByRole("button", { name: "Abrir caja", exact: true }).click();
  return page
    .getByRole("alertdialog")
    .getByRole("button", { name: "Confirmar apertura" });
}

test("Caja valida apertura y refresca el estado ante una apertura concurrente", async ({
  page,
}) => {
  await entorno(page, "EMPLEADO");
  let abierta = false;
  let envios = 0;
  await page.route("**/api/cajas/abierta", (route) =>
    abierta ? route.fulfill({ json: caja }) : route.fulfill({ status: 404 }),
  );
  await page.route("**/api/cajas/abrir", (route) => {
    abierta = true;
    envios++;
    return route.fulfill({
      status: 409,
      json: { detail: "Ya existe una caja abierta." },
    });
  });
  await ingresar(page);
  await expect(page.getByRole("button", { name: "Egreso manual" })).toHaveCount(
    0,
  );
  await revisarApertura(page, "-1");
  await expect(page.getByRole("alert")).toContainText("Ingresá un importe");
  expect(envios).toBe(0);
  await revisarApertura(page, "0");
  await (await confirmarApertura(page)).click();
  await expect(page.getByRole("alert")).toContainText(
    "Ya existe una caja abierta.",
  );
  await expect(
    page.getByRole("heading", { name: "Caja #4 · Abierta" }),
  ).toBeVisible();
  await expect(page.getByLabel("Importe inicial")).toHaveCount(0);
  await expect(page.getByLabel("Efectivo contado")).toHaveCount(0);
  expect(envios).toBe(1);
});

test("Caja conserva el mismo envío tras timeout y respeta Retry-After", async ({
  page,
}) => {
  await entorno(page);
  let abierta = false;
  const envios: { clave: string; body: string | null }[] = [];
  await page.route("**/api/cajas/abierta", (route) =>
    abierta ? route.fulfill({ json: caja }) : route.fulfill({ status: 404 }),
  );
  await page.route("**/api/cajas/abrir", (route) => {
    envios.push({
      clave: route.request().headers()["idempotency-key"],
      body: route.request().postData(),
    });
    if (envios.length === 1) return route.fulfill({ status: 408 });
    if (envios.length === 2) {
      return route.fulfill({
        status: 409,
        headers: { "Retry-After": "2" },
        json: { codigo: "IDEMPOTENCIA_EN_CURSO" },
      });
    }
    abierta = true;
    return route.fulfill({ status: 201, json: caja });
  });
  await ingresar(page);
  await revisarApertura(page, "100,00");
  await (await confirmarApertura(page)).dblclick();
  await expect(page.getByRole("alert")).toContainText("Resultado pendiente");
  expect(envios).toHaveLength(1);
  const reintento = page.getByRole("button", {
    name: "Reintentar el mismo envío",
  });
  await reintento.click();
  await expect(page.getByRole("alert")).toContainText(
    "todavía está procesando",
  );
  await expect(reintento).toBeDisabled();
  expect(envios).toHaveLength(2);
  await expect(reintento).toBeEnabled();
  await reintento.click();
  await expect(
    page
      .getByRole("region", { name: "Resultado de la operación" })
      .getByRole("status"),
  ).toContainText("Caja #4 abierta");
  expect(envios).toEqual(Array(3).fill(envios[0]));
  expect(envios[0].clave).toMatch(/^[0-9a-f-]{36}$/);
  expect(envios[0].body).toBe('{"montoInicial":100}');
});

test("Caja distingue una clave reutilizada y conserva el aviso al recargar", async ({
  page,
}) => {
  await entorno(page);
  await page.route("**/api/cajas/abierta", (route) =>
    route.fulfill({ status: 404 }),
  );
  await page.route("**/api/cajas/abrir", (route) =>
    route.fulfill({
      status: 409,
      json: { codigo: "IDEMPOTENCIA_CLAVE_REUTILIZADA" },
    }),
  );
  await ingresar(page);
  await revisarApertura(page, "0");
  await (await confirmarApertura(page)).click();
  await expect(page.getByRole("alert")).toContainText(
    "ya corresponde a otra operación",
  );
  await expect(
    page.getByRole("button", { name: "Reintentar el mismo envío" }),
  ).toHaveCount(0);
  await page.reload();
  await fillLogin(page, creds);
  await expect(page.getByRole("alert")).toContainText("pendiente de conciliar");
  await expect(page.getByLabel("Importe inicial")).toHaveCount(0);
  await page.getByRole("button", { name: "Ya concilié el envío" }).click();
  await page.getByRole("button", { name: "Confirmar conciliación" }).click();
  await expect(page.getByLabel("Importe inicial")).toBeVisible();
});
