import { expect, test } from "@playwright/test";
import type { Page, Route } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { fillLogin } from "./login";

const cuenta = { username: "pedidos.prueba", password: "clave-sintetica" };
const estados = [
  "Pendiente",
  "En proceso",
  "Listo para entregar",
  "Entregado",
  "Cancelado",
].map((nombre, id) => ({
  id: id + 1,
  nombre,
  codigo: ["PENDIENTE", "EN_PROCESO", "LISTO", "ENTREGADO", "CANCELADO"][id],
  activo: true,
  orden: id,
  descripcion: null,
}));
const resumen = {
  id: 27,
  clienteId: 15,
  clienteNombre: "Ada",
  clienteApellido: "Prueba",
  estadoCodigo: "PENDIENTE",
  fechaIngreso: "2026-11-18T13:15:00",
  fechaEntregaEstimada: "2099-12-30",
  total: 41.5,
};
const detalles = [
  {
    id: 28,
    prendaId: 4,
    prendaNombre: "Camisa",
    servicioId: 8,
    servicioNombre: "Lavado",
    cantidad: 2,
    modalidadCobro: "PRENDA",
    precioUnitario: 20.75,
    subtotal: 41.5,
    estadoPrenda: null,
    observaciones: null,
  },
];
const pedido = {
  ...resumen,
  estadoPedido: estados[0],
  usuarioId: 1,
  usuarioNombre: "Personal",
  detalles,
};
const paginado = (
  content: unknown[],
  number = 0,
  totalElements = content.length,
  totalPages = content.length ? 1 : 0,
) => ({ content, page: { number, size: 20, totalElements, totalPages } });

async function abrir(
  page: Page,
  path: string,
  rol: "ADMINISTRADOR" | "EMPLEADO",
  responder: (route: Route, url: URL) => Promise<void>,
  opciones: { tieneCobros?: boolean } = {},
) {
  await page.route(
    (url) => url.pathname.startsWith("/api/"),
    async (route) => {
      const url = new URL(route.request().url());
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
              username: cuenta.username,
              nombre: "Personal",
              email: "personal@example.test",
              rol,
            },
          });
        case "/api/estados-pedido":
          return route.fulfill({ json: estados });
        case "/api/clientes": {
          const p = Number(url.searchParams.get("page"));
          return route.fulfill({
            json: paginado(
              [
                {
                  id: p ? 15 : 1,
                  nombre: p ? "Ada" : "Otro",
                  apellido: "Prueba",
                  dni: "22111222",
                  activo: true,
                },
              ],
              p,
              11,
              2,
            ),
          });
        }
        case "/api/prendas":
          return route.fulfill({
            json: paginado([{ id: 4, nombre: "Camisa", activo: true }]),
          });
        case "/api/servicios":
          return route.fulfill({
            json: paginado([
              {
                id: 8,
                nombre: "Lavado",
                activo: true,
                precio: 20.75,
                modalidadCobro: "PRENDA",
              },
            ]),
          });
        case "/api/pedidos/27/saldo":
          return route.fulfill({
            json: {
              pedidoId: 27,
              totalPedido: 41.5,
              estadoPagoCodigo: opciones.tieneCobros ? "PARCIAL" : "PENDIENTE",
              estadoPagoNombre: opciones.tieneCobros
                ? "Parcialmente pagado"
                : "Pendiente",
              saldoPendiente: opciones.tieneCobros ? 31.5 : 41.5,
              totalCobrado: opciones.tieneCobros ? 10 : 0,
            },
          });
        case "/api/cajas/abierta":
          return route.fulfill({ status: 404, json: {} });
        case "/api/pedidos/27/cobros":
          return route.fulfill({
            json: paginado(
              opciones.tieneCobros
                ? [
                    {
                      id: 31,
                      pedidoId: 27,
                      movimientoCajaId: 11,
                      cajaId: 2,
                      metodoPagoCodigo: "EFECTIVO",
                      metodoPagoNombre: "Efectivo",
                      usuarioId: 1,
                      usuarioNombre: "Personal",
                      fechaCobro: "2026-11-18T13:30:00",
                      montoCobrado: 10,
                    },
                  ]
                : [],
            ),
          });
        default:
          return responder(route, url);
      }
    },
  );
  await page.goto(path);
  await fillLogin(page, cuenta);
}

const nuevo = async (page: Page) => {
  const grupo = page.getByRole("group", { name: "Renglón 1" });
  await page.getByRole("button", { name: "Siguiente", exact: true }).click();
  await page.getByLabel("Cliente", { exact: true }).selectOption("15");
  await page.getByRole("button", { name: "Anterior", exact: true }).click();
  await expect(page.getByLabel("Cliente", { exact: true })).toHaveValue("15");
  await page.getByLabel("Entrega estimada").fill("2099-12-30");
  await grupo.getByLabel("Prenda", { exact: true }).selectOption("4");
  await grupo.getByLabel("Servicio", { exact: true }).selectOption("8");
  await grupo.getByLabel("Cantidad").fill("2");
};
const revisar = async (page: Page) => {
  await page.getByRole("button", { name: "Revisar pedido" }).click();
  return page.getByRole("alertdialog");
};

test("listado móvil, filtros, paginación y orden enviados a Spring", async ({
  page,
}) => {
  const llamadas: URLSearchParams[] = [];
  await page.setViewportSize({ width: 375, height: 820 });
  await abrir(page, "/pedidos", "EMPLEADO", async (route, url) => {
    llamadas.push(url.searchParams);
    const p = Number(url.searchParams.get("page"));
    await route.fulfill({
      json: paginado([{ ...resumen, id: resumen.id + p }], p, 22, 2),
    });
  });
  await expect(page.getByRole("link", { name: "Ver pedido 27" })).toBeVisible();
  await page.getByRole("button", { name: "Siguiente" }).focus();
  await page.keyboard.press("Enter");
  await expect(page.getByRole("link", { name: "Ver pedido 28" })).toBeVisible();
  expect(llamadas.at(-1)?.get("page")).toBe("1");
  await page.getByLabel("Ordenar por").selectOption("total,desc");
  await expect(page.getByRole("link", { name: "Ver pedido 27" })).toBeVisible();
  expect(llamadas.at(-1)?.get("sort")).toBe("total,desc");
  await page.getByLabel("ID del cliente").fill("15");
  await page.getByLabel("Estado", { exact: true }).selectOption("PENDIENTE");
  await page.getByLabel("Ingreso desde").fill("2026-11-01");
  await page.getByLabel("Ingreso hasta").fill("2026-11-30");
  await page.getByRole("button", { name: "Aplicar filtros" }).click();
  await expect
    .poll(() => Object.fromEntries(llamadas.at(-1) ?? []))
    .toMatchObject({
      page: "0",
      sort: "total,desc",
      clienteId: "15",
      estado: "PENDIENTE",
      desde: "2026-11-01",
      hasta: "2026-11-30",
    });
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
});

for (const rol of ["ADMINISTRADOR", "EMPLEADO"] as const) {
  test(`alta y detalle con ${rol}, validación y protección contra doble envío`, async ({
    page,
  }) => {
    let posts = 0;
    let publicar!: () => void;
    const espera = new Promise<void>((resolve) => {
      publicar = resolve;
    });
    await abrir(page, "/pedidos/nuevo", rol, async (route, url) => {
      if (route.request().method() === "POST") {
        posts++;
        expect(route.request().postDataJSON()).toEqual({
          clienteId: 15,
          fechaEntregaEstimada: "2099-12-30",
          detalles: [
            {
              prendaId: 4,
              servicioId: 8,
              cantidad: 2,
              estadoPrenda: null,
              observaciones: null,
            },
          ],
        });
        await espera;
        return route.fulfill({ status: 201, json: pedido });
      }
      if (url.pathname.endsWith("/historial"))
        return route.fulfill({ json: [] });
      return route.fulfill({ json: pedido });
    });
    await revisar(page);
    await expect(
      page.getByLabel("Cliente", { exact: true }),
    ).toHaveAccessibleDescription("Seleccioná una opción.");
    expect(posts).toBe(0);
    await nuevo(page);
    const dialog = await revisar(page);
    await expect(dialog).toContainText("Camisa · Lavado · 2");
    await dialog
      .getByRole("button", { name: "Guardar pedido" })
      .evaluate((el: HTMLButtonElement) => {
        el.click();
        el.click();
      });
    await expect(page.getByText("Guardando pedido…")).toBeVisible();
    await expect(
      page.getByRole("button", { name: "Revisar pedido" }),
    ).toBeDisabled();
    expect(posts).toBe(1);
    publicar();
    await expect(
      page.getByRole("heading", { name: "Pedido #27" }),
    ).toBeVisible();
    await expect(page.getByText("41,50").first()).toBeVisible();
    await expect(
      page.getByRole("table", { name: "Renglones del pedido" }),
    ).toBeVisible();
    await expect(
      page.getByRole("heading", { name: "Información de pago" }),
    ).toBeVisible();
    await expect(
      page.getByText("Todavía no hay cambios registrados."),
    ).toBeVisible();
  });
}

test("edición precargada, protección de borrador, conflicto y actualización del historial", async ({
  page,
}) => {
  let estado = estados[0];
  let observacion = "";
  let cambio = 0;
  let cantidad = 2;
  await abrir(page, "/pedidos/27", "EMPLEADO", async (route, url) => {
    switch (route.request().method()) {
      case "PUT": {
        const body = route.request().postDataJSON();
        expect(body).not.toHaveProperty("clienteId");
        expect(body.detalles[0]).toMatchObject({ detalleId: 28, cantidad: 3 });
        cantidad = 3;
        break;
      }
      case "PATCH":
        expect(route.request().postDataJSON().codigoEstado).toBe("EN_PROCESO");
        if (++cambio === 1)
          return route.fulfill({
            status: 409,
            json: { detail: "Transición no permitida en este momento." },
          });
        estado = estados[1];
        observacion = route.request().postDataJSON().observacion;
        break;
    }
    if (url.pathname.endsWith("/historial"))
      return route.fulfill({
        json: observacion
          ? [
              {
                id: 7,
                estadoAnteriorCodigo: "PENDIENTE",
                estadoAnteriorNombre: "Pendiente",
                estadoNuevoCodigo: estado.codigo,
                estadoNuevoNombre: estado.nombre,
                usuarioNombre: "Personal",
                fechaCambio: "2026-11-18T13:15:00",
                observacion,
              },
            ]
          : [],
      });
    return route.fulfill({
      json: {
        ...pedido,
        total: cantidad === 3 ? 62.25 : 41.5,
        estadoPedido: estado,
        detalles: [
          { ...detalles[0], cantidad, subtotal: cantidad === 3 ? 62.25 : 41.5 },
        ],
      },
    });
  });
  await page.getByRole("link", { name: "Editar pedido" }).click();
  await expect(page.getByLabel("Cantidad")).toHaveValue("2");
  await page.getByLabel("Cantidad").fill("3");
  page.once("dialog", (dialog) => dialog.dismiss());
  await page.getByRole("link", { name: "Salir del formulario" }).click();
  await expect(page).toHaveURL(/\/editar$/);
  await (
    await revisar(page)
  )
    .getByRole("button", { name: "Guardar pedido" })
    .click();
  await expect(page.getByText("62,25").first()).toBeVisible();
  await page.getByRole("button", { name: "Cambiar estado" }).click();
  const dialog = page.getByRole("dialog");
  await expect(dialog.getByRole("option", { name: "Entregado" })).toHaveCount(
    0,
  );
  await dialog.getByLabel("Nuevo estado").selectOption("EN_PROCESO");
  await dialog.getByLabel(/Observación/).fill("Pasó al sector de lavado");
  await dialog.getByRole("button", { name: "Confirmar cambio" }).click();
  await expect(dialog.getByRole("alert")).toContainText(
    "Transición no permitida",
  );
  await dialog.getByRole("button", { name: "Confirmar cambio" }).click();
  await expect(dialog).toBeHidden();
  await expect(
    page.getByRole("region", { name: "Historial de estados" }),
  ).toContainText("Pasó al sector de lavado");
});

test("con cobros se ocultan edición y cancelación, incluido el acceso directo", async ({
  page,
}) => {
  await abrir(
    page,
    "/pedidos/27",
    "ADMINISTRADOR",
    (route) => route.fulfill({ json: pedido }),
    { tieneCobros: true },
  );
  await expect(
    page.getByText(/El pedido tiene cobros registrados/),
  ).toBeVisible();
  await expect(page.getByRole("link", { name: "Editar pedido" })).toHaveCount(
    0,
  );
  await page.getByRole("button", { name: "Cambiar estado" }).click();
  await expect(
    page.getByRole("dialog").getByRole("option", { name: "Cancelado" }),
  ).toHaveCount(0);
  await page.keyboard.press("Escape");
  await page.evaluate(() => {
    history.pushState({}, "", "/pedidos/27/editar");
    dispatchEvent(new PopStateEvent("popstate"));
  });
  await expect(
    page.getByRole("heading", { name: "Edición no disponible" }),
  ).toBeVisible();
});

test("errores 403 y 404 al consultar, y 401 al reintentar", async ({
  page,
}) => {
  let status = 403;
  await abrir(page, "/pedidos/27", "EMPLEADO", (route) =>
    route.fulfill({
      status,
      json: { detail: "Acceso restringido a este pedido." },
    }),
  );
  await expect(page.getByRole("alert")).toContainText("Acceso restringido");
  status = 404;
  await page.getByRole("button", { name: "Reintentar" }).click();
  await expect(
    page.getByRole("heading", { name: "Pedido no encontrado" }),
  ).toBeVisible();
  status = 401;
  await page.getByRole("link", { name: "Volver a pedidos" }).click();
  await expect(page).toHaveURL(/\/login$/);
});

test("referencia obsoleta y fallo de conexión sin reenvío automático", async ({
  page,
}) => {
  let intentos = 0;
  await abrir(page, "/pedidos/nuevo", "EMPLEADO", async (route) => {
    intentos++;
    if (intentos === 1)
      return route.fulfill({
        status: 404,
        json: { detail: "Prenda 4 inexistente o inactiva." },
      });
    return route.abort("timedout");
  });
  await nuevo(page);
  await (
    await revisar(page)
  )
    .getByRole("button", { name: "Guardar pedido" })
    .click();
  await expect(page.getByRole("alert")).toContainText(
    "Prenda 4 inexistente o inactiva",
  );
  await (
    await revisar(page)
  )
    .getByRole("button", { name: "Guardar pedido" })
    .click();
  await expect(page.getByRole("alert")).toContainText(
    "No pudimos confirmar el guardado",
  );
  await expect(
    page.getByRole("link", { name: "Verificar en otra pestaña" }),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Revisar pedido" }),
  ).toBeDisabled();
  expect(intentos).toBe(2);
});
