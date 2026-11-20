import AxeBuilder from "@axe-core/playwright";
import { expect, test, type Page, type Route } from "@playwright/test";
import { fillLogin } from "./login";

const login = { username: "cuenta.prueba", password: "clave-sintetica" };
const listaProveedores = [
  { id: 2, nombre: "Distribuidora Sur", activo: true },
  { id: 3, nombre: "Distribuidora Norte", activo: true },
  { id: 4, nombre: "Baja", activo: false },
];
const detalle = [
  {
    id: 7,
    insumoId: 10,
    insumoNombre: "Jabón",
    cantidad: 1.25,
    precioCompra: 12.34,
    subtotal: 15.43,
  },
];
const compra = {
  id: 42,
  proveedorId: 2,
  proveedorNombre: listaProveedores[0].nombre,
  usuarioId: 1,
  usuarioNombre: "Operador",
  fechaCompra: "2026-11-18T16:10:00.123456",
  numeroComprobante: "FC-42",
  totalCompra: 15.43,
  estadoPagoCodigo: "PENDIENTE",
  estadoPagoNombre: "Pendiente",
  detalles: detalle,
};
const saldo = {
  compraId: 42,
  totalCompra: 15.43,
  saldoPendiente: 15.43,
  totalPagado: 0,
  estadoPagoCodigo: "PENDIENTE",
  estadoPagoNombre: "Pendiente",
};

function paginar(
  content: unknown[],
  number = 0,
  size = 10,
  totalElements = content.length,
) {
  return {
    content,
    page: {
      size,
      number,
      totalElements,
      totalPages: Math.ceil(totalElements / size),
    },
  };
}

async function preparar(
  page: Page,
  path: string,
  rol: string,
  interceptar?: (route: Route, url: URL) => Promise<boolean>,
) {
  await page.route(
    (url) => url.pathname.startsWith("/api/"),
    async (route) => {
      const url = new URL(route.request().url());
      if (await interceptar?.(route, url)) return;
      const number = Number(url.searchParams.get("page"));
      const size = Number(url.searchParams.get("size")) || 10;
      switch (url.pathname) {
        case "/api/auth/login":
          return route.fulfill({
            json: {
              accessToken: "prueba",
              tokenType: "Bearer",
              expiresIn: 3600,
            },
          });
        case "/api/auth/me":
          return route.fulfill({
            json: {
              id: 1,
              nombre: "Operador",
              email: "operador@example.test",
              rol,
              username: login.username,
            },
          });
        case "/api/estados-pago":
          return route.fulfill({
            json: [
              { id: 1, codigo: "PENDIENTE", nombre: "Pendiente", activo: true },
            ],
          });
        case "/api/proveedores":
          return route.fulfill({
            json: paginar(
              number ? listaProveedores.slice(1) : listaProveedores.slice(0, 1),
              number,
              size,
              11,
            ),
          });
        case "/api/proveedores/2":
          return route.fulfill({ json: listaProveedores[0] });
        case "/api/proveedores/2/insumos": {
          const insumos = [
            { insumoId: 10, insumoNombre: "Jabón" },
            { insumoId: 11, insumoNombre: "Bolsa" },
          ];
          const content = size === 100 ? insumos : [insumos[number]];
          return route.fulfill({
            json: paginar(content, number, size, size === 100 ? 2 : 11),
          });
        }
        case "/api/proveedores/3/insumos":
          return route.fulfill({
            json: paginar([{ insumoId: 11, insumoNombre: "Bolsa" }]),
          });
        case "/api/compras":
          return route.fulfill({
            json: paginar(
              [{ ...compra, id: number ? 21 : 42 }],
              number,
              size,
              21,
            ),
          });
        case "/api/compras/42":
          return route.fulfill({ json: compra });
        case "/api/compras/42/saldo":
          return route.fulfill({ json: saldo });
        case "/api/compras/42/pagos":
          return route.fulfill({ json: paginar([]) });
        default:
          return route.abort();
      }
    },
  );
  await page.goto(path);
  await fillLogin(page, login);
}

const renglon = (page: Page, numero = 1) =>
  page.getByRole("group", { name: `Renglón ${numero}` });

async function llenar(
  page: Page,
  numero = 1,
  insumo = "10",
  cantidad = "1,25",
  precio = "12,34",
) {
  const fila = renglon(page, numero);
  await fila.getByLabel("Insumo", { exact: true }).selectOption(insumo);
  await fila.getByLabel("Cantidad").fill(cantidad);
  await fila.getByLabel("Precio de compra").fill(precio);
}

test("listado paginado, filtros reales, carga, ficha y navegación móvil para empleado", async ({
  page,
}) => {
  const consultas: URLSearchParams[] = [];
  let liberar!: () => void;
  const bloqueada = new Promise<void>((resolve) => {
    liberar = resolve;
  });
  await preparar(page, "/compras", "EMPLEADO", async (_route, url) => {
    if (url.pathname !== "/api/compras") return false;
    consultas.push(url.searchParams);
    await bloqueada;
    return false;
  });
  await expect(page.getByText("Cargando compras…")).toBeVisible();
  liberar();
  await expect(page.getByRole("link", { name: "Nueva compra" })).toBeVisible();
  await page.getByRole("button", { name: "Siguiente" }).click();
  await expect(page.getByRole("link", { name: "Ver compra 21" })).toBeVisible();
  expect(consultas[1].get("page")).toBe("1");
  await page.getByLabel("ID del proveedor").fill("2");
  await page.getByLabel("Fecha desde").fill("2026-11-19");
  await page.getByLabel("Fecha hasta").fill("2026-11-18");
  await page.getByRole("button", { name: "Aplicar filtros" }).click();
  await expect(page.getByRole("alert")).toContainText("fecha desde");
  await page.getByLabel("Fecha desde").fill("2026-11-01");
  await page.getByLabel("Estado de pago").selectOption("PENDIENTE");
  await page.getByLabel("Número de comprobante").fill(" FC-42 ");
  await page.getByRole("button", { name: "Aplicar filtros" }).click();
  await expect(page.getByRole("link", { name: "Ver compra 42" })).toBeVisible();
  expect(Object.fromEntries(consultas[2])).toEqual({
    page: "0",
    size: "20",
    proveedorId: "2",
    fechaDesde: "2026-11-01",
    fechaHasta: "2026-11-18",
    estadoPagoCodigo: "PENDIENTE",
    numeroComprobante: "FC-42",
  });
  await page.setViewportSize({ width: 375, height: 812 });
  const region = page.getByRole("region", { name: /Compras por fecha/ });
  await region.focus();
  await expect(region).toBeFocused();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
  await page.getByRole("link", { name: "Ver compra 42" }).click();
  await expect(
    page.getByText("Saldo pendiente", { exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole("link", { name: "Distribuidora Sur" }),
  ).toHaveAttribute("href", "/proveedores/2");
  await expect(page.getByRole("link", { name: "Editar compra" })).toBeVisible();
  await expect(page.getByText("Todavía no hay pagos asociados.")).toBeVisible();
});

test("alta con varias páginas, errores por renglón y un solo POST", async ({
  page,
}) => {
  const solicitudes: unknown[] = [];
  let liberar!: () => void;
  const bloqueada = new Promise<void>((resolve) => {
    liberar = resolve;
  });
  await preparar(
    page,
    "/compras/nueva",
    "ADMINISTRADOR",
    async (route, url) => {
      if (
        url.pathname !== "/api/compras" ||
        route.request().method() !== "POST"
      )
        return false;
      expect(route.request().headers()["idempotency-key"]).toBeUndefined();
      solicitudes.push(route.request().postDataJSON());
      await bloqueada;
      await route.fulfill({
        status: 201,
        json: {
          ...compra,
          totalCompra: 18.43,
          detalles: [
            ...detalle,
            {
              id: 8,
              insumoId: 11,
              insumoNombre: "Bolsa",
              cantidad: 3,
              precioCompra: 1,
              subtotal: 3,
            },
          ],
        },
      });
      return true;
    },
  );
  const proveedor = page.getByLabel("Proveedor", { exact: true });
  await proveedor.selectOption("2");
  const paginacion = page.getByRole("navigation", {
    name: "Páginas de proveedores",
  });
  await paginacion.getByRole("button", { name: "Siguiente" }).click();
  await expect(proveedor).toHaveValue("2");
  await expect(proveedor.getByRole("option", { name: "Baja" })).toHaveCount(0);
  await llenar(page, 1, "10", "0");
  await page.getByRole("button", { name: "Revisar compra" }).click();
  await expect(
    renglon(page).getByLabel("Cantidad"),
  ).toHaveAccessibleDescription(/valor positivo/);
  await renglon(page).getByLabel("Cantidad").fill("1,25");
  await page
    .getByRole("navigation", { name: "Páginas de insumos" })
    .getByRole("button", { name: "Siguiente" })
    .click();
  await expect(renglon(page).getByLabel("Insumo")).toHaveValue("10");
  await page.getByRole("button", { name: "Agregar renglón" }).click();
  await llenar(page, 2, "11", "3", "1");
  const revisar = page.getByRole("button", { name: "Revisar compra" });
  await revisar.click();
  const dialogo = page.getByRole("alertdialog");
  await expect(dialogo).toContainText("Total estimado 18,43");
  await page.keyboard.press("Escape");
  await expect(revisar).toBeFocused();
  await page.keyboard.press("Enter");
  await dialogo
    .getByRole("button", { name: "Confirmar registro" })
    .click({ clickCount: 2 });
  await expect(revisar).toBeDisabled();
  await expect(page.getByText("Guardando compra…")).toBeVisible();
  await expect
    .poll(() => solicitudes)
    .toEqual([
      {
        proveedorId: 2,
        numeroComprobante: null,
        detalles: [
          { insumoId: 10, cantidad: "1.25", precioCompra: "12.34" },
          { insumoId: 11, cantidad: "3", precioCompra: "1" },
        ],
      },
    ]);
  liberar();
  await expect(page.getByRole("heading", { name: "Compra #42" })).toBeVisible();
  await expect(
    page.getByText("Compra registrada.", { exact: true }),
  ).toBeVisible();
  await expect(
    page
      .getByRole("table", { name: "Renglones de la compra" })
      .getByRole("row"),
  ).toHaveCount(3);
  expect(solicitudes).toHaveLength(1);
});

for (const caso of [
  "proveedor inactivo",
  "insumo desasociado",
  "timeout real del cliente",
] as const) {
  test(`alta: ${caso}`, async ({ page }) => {
    let posts = 0;
    if (caso.startsWith("timeout")) {
      await page.addInitScript(() => {
        const timeout = AbortSignal.timeout.bind(AbortSignal);
        let llamadas = 0;
        AbortSignal.timeout = (ms) =>
          timeout(ms === 20_000 && ++llamadas === 2 ? 1_000 : ms);
      });
    }
    await preparar(page, "/compras/nueva", "EMPLEADO", async (route, url) => {
      if (
        caso === "proveedor inactivo" &&
        url.pathname === "/api/proveedores/2"
      ) {
        await route.fulfill({
          json: { ...listaProveedores[0], activo: false },
        });
        return true;
      }
      if (
        caso === "insumo desasociado" &&
        url.pathname === "/api/proveedores/2/insumos" &&
        url.searchParams.get("size") === "100"
      ) {
        await route.fulfill({ json: paginar([]) });
        return true;
      }
      if (
        url.pathname === "/api/compras" &&
        route.request().method() === "POST"
      ) {
        posts++;
        if (caso.startsWith("timeout")) {
          await new Promise((resolve) => setTimeout(resolve, 1_500));
        }
        await route.fulfill({ status: 504 }).catch(() => {});
        return true;
      }
      return false;
    });
    await page.getByLabel("Proveedor", { exact: true }).selectOption("2");
    await llenar(page);
    await page.getByRole("button", { name: "Revisar compra" }).click();
    await page.getByRole("button", { name: "Confirmar registro" }).click();
    if (caso.startsWith("timeout")) {
      await expect(page.getByRole("alert")).toContainText(
        "podría haberse guardado",
      );
      await expect(
        page.getByRole("button", { name: "Revisar compra" }),
      ).toBeDisabled();
      page.once("dialog", (dialog) => dialog.accept());
      await page
        .getByRole("link", { name: "Revisar listado de compras" })
        .click();
      await expect(
        page.getByRole("heading", { name: "Compras" }),
      ).toBeVisible();
    } else {
      await expect(page.getByRole("alert")).toContainText(
        caso === "proveedor inactivo"
          ? "El proveedor está inactivo"
          : "Este insumo ya no está disponible",
      );
    }
    expect(posts).toBe(caso.startsWith("timeout") ? 1 : 0);
  });
}

test("edición conserva precio histórico, confirma y envía el ID de detalle", async ({
  page,
}) => {
  let edicion: unknown;
  await preparar(page, "/compras/42/editar", "EMPLEADO", async (route, url) => {
    if (
      url.pathname !== "/api/compras/42" ||
      route.request().method() !== "PUT"
    )
      return false;
    edicion = route.request().postDataJSON();
    await route.fulfill({ json: { ...compra, numeroComprobante: "FC-43" } });
    return true;
  });
  await expect(page.getByLabel("Precio de compra")).toHaveValue("12.34");
  await page.getByLabel("Número de comprobante (opcional)").fill("FC-43");
  page.once("dialog", (dialog) => dialog.dismiss());
  await page.getByRole("link", { name: "Volver a la compra" }).click();
  await expect(
    page.getByRole("heading", { name: "Editar compra #42" }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Revisar compra" }).click();
  await page.getByRole("button", { name: "Confirmar cambios" }).click();
  await expect(page.getByText("Cambios guardados.")).toBeVisible();
  expect(edicion).toEqual({
    proveedorId: 2,
    numeroComprobante: "FC-43",
    detalles: [
      { detalleId: 7, insumoId: 10, cantidad: "1.25", precioCompra: "12.34" },
    ],
  });
});

test("bloquea el acceso directo a la edición con pagos y muestra el 403 de la API", async ({
  page,
}) => {
  await preparar(
    page,
    "/compras/42/editar",
    "ADMINISTRADOR",
    async (route, url) => {
      if (url.pathname === "/api/compras/42/saldo") {
        await route.fulfill({
          json: {
            ...saldo,
            totalPagado: 5,
            saldoPendiente: 10.43,
            estadoPagoCodigo: "PARCIAL",
          },
        });
        return true;
      }
      if (url.pathname === "/api/compras") {
        await route.fulfill({
          status: 403,
          json: { detail: "No tiene permiso para esta operación." },
        });
        return true;
      }
      return false;
    },
  );
  await expect(
    page.getByRole("heading", { name: "La compra no se puede editar" }),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Revisar compra" }),
  ).toHaveCount(0);
  await page.getByRole("link", { name: "Volver a la compra" }).click();
  await expect(page.getByRole("link", { name: "Editar compra" })).toHaveCount(
    0,
  );
  await page.getByRole("link", { name: "Volver a compras" }).click();
  await expect(page.getByRole("alert")).toContainText(
    "No tiene permiso para esta operación.",
  );
});
