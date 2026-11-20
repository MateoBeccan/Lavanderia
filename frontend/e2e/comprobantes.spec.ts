import AxeBuilder from "@axe-core/playwright";
import { expect, test, type Page, type Route } from "@playwright/test";
import { fillLogin } from "./login";

const cuenta = { username: "comprobantes.e2e", password: "sintetica" };
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
const comprobante = {
  id: 42,
  tipoOperacion: "COBRO",
  fechaEmision: "2024-02-29",
  observaciones: "Constancia histórica",
  montoOperacion: cobro.montoCobrado,
  cobroId: cobro.id,
  pedidoId: pedido.id,
  pagoProveedorId: null,
  compraId: null,
  metodoPagoCodigo: cobro.metodoPagoCodigo,
  metodoPagoNombre: cobro.metodoPagoNombre,
  movimientoCajaId: cobro.movimientoCajaId,
};

const pagina = (
  content: unknown[],
  number = 0,
  totalElements = content.length,
) => ({
  content,
  page: {
    size: 20,
    number,
    totalElements,
    totalPages: Math.ceil(totalElements / 20),
  },
});

async function preparar(page: Page) {
  await page.route("**/api/**", async (route) => {
    const path = new URL(route.request().url()).pathname;
    switch (path) {
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
            rol: "EMPLEADO",
          },
        });
      case "/api/pedidos/27":
        return route.fulfill({ json: pedido });
      case "/api/pedidos":
        return route.fulfill({
          json: pagina([
            { ...pedido, estadoCodigo: "PENDIENTE", estadoNombre: "Pendiente" },
          ]),
        });
      case "/api/pedidos/27/saldo":
        return route.fulfill({
          json: {
            pedidoId: 27,
            totalPedido: 50,
            totalCobrado: 12.53,
            saldoPendiente: 37.47,
            estadoPagoCodigo: "PARCIAL",
            estadoPagoNombre: "Parcial",
          },
        });
      case "/api/pedidos/27/cobros":
        return route.fulfill({ json: pagina([cobro]) });
      case "/api/pedidos/27/historial":
        return route.fulfill({ json: [] });
      case "/api/cobros/8":
        return route.fulfill({ json: cobro });
      case "/api/comprobantes/42":
        return route.fulfill({ json: comprobante });
      case "/api/movimientos-caja/9":
        return route.fulfill({ json: { id: 9, cajaId: 4 } });
      default:
        return route.fulfill({ status: 404, json: {} });
    }
  });
}

async function abrir(page: Page, path: string) {
  await page.goto(path);
  await fillLogin(page, cuenta);
  await expect(page).toHaveURL(path);
}

test("navegación autenticada, páginas de Spring, detalle, impresión y accesibilidad móvil", async ({
  page,
}) => {
  await page.setViewportSize({ width: 320, height: 820 });
  await preparar(page);
  await page.route("**/api/pedidos/27/comprobantes?**", async (route) => {
    const params = new URL(route.request().url()).searchParams;
    expect(params.get("size")).toBe("20");
    const number = Number(params.get("page"));
    const filas =
      number === 0
        ? Array.from({ length: 20 }, (_, i) => ({ ...comprobante, id: 62 - i }))
        : [comprobante];
    await route.fulfill({ json: pagina(filas, number, 21) });
  });
  await abrir(page, "/comprobantes");
  await expect(
    page.getByRole("link", { name: "Buscar pedido" }),
  ).toHaveAttribute("href", "/pedidos");
  await expect(
    page.getByRole("link", { name: "Buscar compra" }),
  ).toHaveAttribute("href", "/compras");
  await page.getByRole("link", { name: "Buscar pedido" }).click();
  await page.getByRole("link", { name: "Ver pedido 27" }).click();
  const lista = page.getByRole("region", { name: "Comprobantes asociados" });
  await expect(lista.getByText("Página 1 de 2")).toBeVisible();
  await lista.getByRole("button", { name: "Siguiente" }).click();
  await expect(lista.getByText("Página 2 de 2")).toBeVisible();
  await expect(lista.getByRole("button", { name: "Siguiente" })).toBeDisabled();
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth),
  ).toBeLessThanOrEqual(320);
  const enlace = lista.getByRole("link", { name: "Comprobante #42" });
  await enlace.focus();
  await page.keyboard.press("Enter");
  await expect(
    page.getByRole("heading", { name: "Comprobante #42" }),
  ).toBeVisible();
  await expect(page.getByText(comprobante.observaciones)).toBeVisible();
  await expect(page.getByRole("link", { name: "Cobro #8" })).toHaveAttribute(
    "href",
    "/cobros/8",
  );
  await expect(page.getByRole("link", { name: "Pedido #27" })).toHaveAttribute(
    "href",
    "/pedidos/27",
  );
  await expect(
    page.getByRole("link", { name: "Movimiento #9" }),
  ).toHaveAttribute("href", "/caja/4/movimientos/9");
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth),
  ).toBeLessThanOrEqual(320);
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
  await page.evaluate(() => {
    window.print = () => {
      document.body.dataset.printed = "true";
    };
  });
  await page.getByRole("button", { name: "Imprimir constancia" }).click();
  await expect(page.locator("body")).toHaveAttribute("data-printed", "true");
  await page.emulateMedia({ media: "print" });
  await expect(page.getByRole("banner")).toBeHidden();
  await expect(
    page.getByRole("button", { name: "Imprimir constancia" }),
  ).toBeHidden();
  await expect(
    page.getByRole("article", { name: "Constancia interna" }),
  ).toContainText("sin validez fiscal");
  await expect(
    page.getByRole("heading", { name: "Comprobante #42" }),
  ).toBeVisible();
});

for (const modo of ["alta", "conflicto", "respuesta perdida"] as const) {
  test(`revisa y confirma una vez ante ${modo}`, async ({ page }) => {
    await preparar(page);
    let alta = false;
    let post = 0;
    await page.route("**/api/cobros/8/comprobante", async (route: Route) => {
      const solicitud = route.request();
      if (solicitud.method() === "GET") {
        return alta
          ? route.fulfill({ json: comprobante })
          : route.fulfill({ status: 404, json: {} });
      }
      post++;
      expect(solicitud.headers()).not.toHaveProperty("idempotency-key");
      expect(solicitud.postDataJSON()).toEqual({
        fechaEmision: comprobante.fechaEmision,
        observaciones: comprobante.observaciones,
      });
      alta = true;
      if (modo === "respuesta perdida") return route.abort();
      if (modo === "conflicto")
        return route.fulfill({
          status: 409,
          json: { detail: "La operación ya tiene un comprobante." },
        });
      return route.fulfill({ status: 201, json: comprobante });
    });
    await abrir(page, "/cobros/8");
    const fecha = page.getByLabel("Fecha de emisión");
    const observaciones = page.getByLabel("Observaciones (opcional)");
    const revisar = page.getByRole("button", { name: "Revisar comprobante" });
    await fecha.fill("2099-01-01");
    await observaciones.fill("x".repeat(201));
    await revisar.click();
    await expect(
      page.getByText("La fecha de emisión no puede ser futura."),
    ).toBeVisible();
    await expect(page.getByText("Usá hasta 200 caracteres.")).toBeVisible();
    await fecha.fill(comprobante.fechaEmision);
    await observaciones.fill("  " + comprobante.observaciones + "  ");
    await revisar.click();
    await expect(page.getByRole("alertdialog")).toContainText(
      comprobante.observaciones,
    );
    await page.keyboard.press("Escape");
    await expect(revisar).toBeFocused();
    expect(post).toBe(0);
    await revisar.click();
    await page
      .getByRole("button", { name: "Confirmar registro" })
      .click({ clickCount: 2 });
    if (modo !== "alta") {
      await expect(page.getByRole("alert")).toContainText(
        modo === "conflicto"
          ? "ya tiene un comprobante"
          : "No pudimos confirmar",
      );
      await expect(revisar).toHaveCount(0);
      await page.getByRole("button", { name: "Consultar comprobante" }).click();
    }
    await expect(
      page.getByRole("link", { name: "Ver comprobante #42" }),
    ).toBeVisible();
    expect(post).toBe(1);
  });
}

test("muestra carga, 403 y recuperación a un listado vacío", async ({
  page,
}) => {
  await preparar(page);
  let resolver = () => {};
  const pausa = new Promise<void>((resolve) => {
    resolver = resolve;
  });
  let intento = 0;
  await page.route("**/api/pedidos/27/comprobantes?**", async (route) => {
    if (++intento === 1) {
      await pausa;
      return route.fulfill({ status: 403, json: {} });
    }
    return route.fulfill({ json: pagina([]) });
  });
  await abrir(page, "/pedidos/27");
  const lista = page.getByRole("region", { name: "Comprobantes asociados" });
  try {
    await expect(lista.getByRole("status")).toHaveText(
      "Cargando comprobantes…",
    );
  } finally {
    resolver();
  }
  await expect(lista.getByRole("alert")).toContainText("No tenés permiso");
  await lista.getByRole("button", { name: "Reintentar" }).click();
  await expect(
    lista.getByText("Todavía no hay comprobantes registrados."),
  ).toBeVisible();
});
