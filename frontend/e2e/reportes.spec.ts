import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";
import type { Page } from "@playwright/test";
import { fillLogin } from "./login";

const cuenta = { username: "reportes.prueba", password: "clave-sintetica" };
const pendiente = {
  id: 1,
  codigo: "PENDIENTE",
  nombre: "Pendiente",
  activo: true,
};
const ingreso = {
  id: 2,
  codigo: "INGRESO_MANUAL",
  nombre: "Ingreso manual",
  activo: true,
};
const efectivo = {
  id: 3,
  codigo: "EFECTIVO",
  nombre: "Efectivo",
  activo: true,
};

async function prepararSesion(page: Page, rol = "ADMINISTRADOR") {
  await page.route("**/api/auth/login", (route) =>
    route.fulfill({
      json: {
        accessToken: "jwt-reportes-sintetico",
        tokenType: "Bearer",
        expiresIn: 3600,
      },
    }),
  );
  await page.route("**/api/auth/me", (route) =>
    route.fulfill({
      json: {
        id: 1,
        username: cuenta.username,
        nombre: "Ana Prueba",
        email: "ana@example.test",
        rol,
      },
    }),
  );
  for (const [path, opcion] of [
    ["estados-pedido", pendiente],
    ["estados-pago", pendiente],
    ["tipos-movimiento-caja", ingreso],
    ["metodos-pago", efectivo],
  ] as const) {
    await page.route(`**/api/${path}`, (route) =>
      route.fulfill({ json: [opcion] }),
    );
  }
  for (const path of ["proveedores", "clientes"]) {
    await page.route(`**/api/${path}?*`, (route) =>
      route.fulfill({
        json: {
          content: [],
          page: { size: 10, number: 0, totalElements: 0, totalPages: 0 },
        },
      }),
    );
  }
}

test("descarga con JWT, filtros aplicados y sin paginación, con errores recuperables", async ({
  page,
}) => {
  await prepararSesion(page);
  await page.route("**/api/reportes/compras?*", (route) =>
    route.fulfill({
      json: {
        datos: [],
        resumen: { cantidadCompras: 0, totalComprado: 0 },
        pagina: 3,
        tamanio: 20,
        totalPaginas: 0,
        totalElementos: 0,
      },
    }),
  );
  let intentos = 0;
  let liberar!: () => void;
  const espera = new Promise<void>((resolve) => {
    liberar = resolve;
  });
  await page.route("**/api/reportes/compras/pdf?*", async (route) => {
    const request = route.request();
    expect(request.headers().authorization).toBe(
      "Bearer jwt-reportes-sintetico",
    );
    expect(Object.fromEntries(new URL(request.url()).searchParams)).toEqual({
      fechaHasta: "2026-11-19",
      proveedorId: "8",
    });
    intentos++;
    if (intentos === 1) {
      await espera;
      return route.fulfill({
        status: 429,
        json: { detail: "Reintentá en unos segundos." },
      });
    }
    if (intentos === 2) {
      return route.fulfill({ contentType: "application/pdf", body: "" });
    }
    return route.fulfill({
      contentType: "application/pdf",
      headers: {
        "Content-Disposition":
          'attachment; filename="mira-que-te-lavo-compras-20261119-150000.pdf"',
      },
      body: "%PDF-1.7\n",
    });
  });

  await page.goto(
    "/reportes/compras?fechaHasta=2026-11-19&proveedorId=8&page=3",
  );
  await fillLogin(page, cuenta);
  const generar = page.getByRole("button", { name: "Generar PDF" });
  await expect(generar).toBeEnabled();
  await page.getByLabel("Fecha hasta").fill("2026-11-20");
  expect(intentos).toBe(0);
  await generar.click();
  await expect(
    page.getByRole("button", { name: "Generando PDF…" }),
  ).toBeDisabled();
  try {
    await expect.poll(() => intentos).toBe(1);
  } finally {
    liberar();
  }
  await expect(page.getByRole("alert")).toContainText(
    "Reintentá en unos segundos.",
  );
  await generar.click();
  await expect(page.getByRole("alert")).toContainText(
    "El PDF recibido está vacío o es inválido.",
  );
  const descarga = page.waitForEvent("download");
  await generar.click();
  expect((await descarga).suggestedFilename()).toBe(
    "mira-que-te-lavo-compras-20261119-150000.pdf",
  );
  await expect(
    page.getByText("PDF generado. Se inició la descarga."),
  ).toBeVisible();
  expect(intentos).toBe(3);
});

test("el centro navega entre los tres reportes y consulta solo la vista elegida", async ({
  page,
}) => {
  await prepararSesion(page);
  const consultas: string[] = [];
  await page.route("**/api/reportes/**", (route) => {
    const { pathname } = new URL(route.request().url());
    expect(route.request().method()).toBe("GET");
    expect(route.request().headers().authorization).toBe(
      "Bearer jwt-reportes-sintetico",
    );
    const reportes = {
      pedidos: { cantidadPedidos: 0, totalGeneral: 0 },
      compras: { cantidadCompras: 0, totalComprado: 0 },
      "movimientos-caja": {
        cantidadMovimientos: 0,
        totalIngresos: 0,
        totalEgresos: 0,
        saldoNeto: 0,
      },
    };
    const tipo = pathname.split("/").pop() as keyof typeof reportes;
    consultas.push(tipo);
    return route.fulfill({
      json: {
        datos: [],
        resumen: reportes[tipo],
        pagina: 0,
        tamanio: 20,
        totalElementos: 0,
        totalPaginas: 0,
      },
    });
  });

  await page.goto("/reportes");
  await expect(page).toHaveURL(/\/login$/);
  await fillLogin(page, cuenta);
  await expect(
    page.getByRole("heading", { name: "Reportes administrativos" }),
  ).toBeVisible();
  const tarjetas = page.getByRole("region", { name: "Reportes disponibles" });
  expect(consultas).toEqual([]);
  const pedidos = tarjetas.getByRole("link", { name: /^Pedidos/ });
  await pedidos.focus();
  await page.keyboard.press("Enter");
  const nav = page.getByRole("navigation", { name: "Cambiar de reporte" });
  await expect(nav.getByRole("link", { name: "Pedidos" })).toHaveAttribute(
    "aria-current",
    "page",
  );
  await expect(page.getByText("Sin resultados", { exact: true })).toBeVisible();
  await nav.getByRole("link", { name: "Movimientos de Caja" }).click();
  await expect(
    page.getByRole("heading", { name: "Neto de movimientos" }),
  ).toBeVisible();
  await nav.getByRole("link", { name: "Compras" }).click();
  await expect(
    page.getByRole("heading", { name: "Importe total de compras" }),
  ).toBeVisible();
  expect(consultas).toEqual(["pedidos", "movimientos-caja", "compras"]);
  await page.getByRole("link", { name: "Todos los reportes" }).click();
  await expect(tarjetas).toBeVisible();
});

test("valida, aplica, pagina y limpia filtros sin recalcular el resumen", async ({
  page,
}) => {
  await prepararSesion(page);
  const consultas: Record<string, string>[] = [];
  await page.route("**/api/reportes/pedidos?*", (route) => {
    const params = Object.fromEntries(
      new URL(route.request().url()).searchParams,
    );
    consultas.push(params);
    const pagina = Number(params.page);
    return route.fulfill({
      json: {
        datos: [
          {
            pedidoId: 45 - pagina,
            fechaIngreso: "2024-02-29T23:55:00",
            fechaEntregaEstimada: null,
            clienteId: 9,
            clienteNombreCompleto: "Ana López",
            estadoPedidoCodigo: "PENDIENTE",
            estadoPedidoNombre: "Pendiente",
            estadoPagoCodigo: null,
            estadoPagoNombre: null,
            totalPedido: 7.13,
          },
        ],
        resumen: { cantidadPedidos: 21, totalGeneral: 157.12 },
        pagina,
        tamanio: 20,
        totalElementos: 21,
        totalPaginas: 2,
      },
    });
  });
  await page.goto("/reportes/pedidos");
  await fillLogin(page, cuenta);
  const tabla = page.getByRole("table", { name: "Pedidos del reporte" });
  await expect(tabla).toContainText("Ana López");
  await expect(tabla).toContainText(/29\/2\/24.*(?:23:55|11:55\s*p)/i);
  const filtros = page.getByRole("form", { name: "Filtros del reporte" });
  const aplicar = filtros.getByRole("button", { name: "Aplicar filtros" });
  await filtros.getByLabel("Fecha desde").fill("2024-03-01");
  await filtros.getByLabel("Fecha hasta").fill("2024-02-29");
  await aplicar.click();
  await expect(filtros.getByRole("alert")).toContainText("anterior o igual");
  expect(consultas).toHaveLength(1);
  await filtros.getByLabel("Fecha desde").fill("2024-02-29");
  await filtros.getByText("Usar ID de cliente").click();
  await filtros.getByLabel("ID del cliente").fill("9");
  await filtros.getByLabel("Estado del pedido").selectOption("PENDIENTE");
  expect(consultas).toHaveLength(1);
  await aplicar.click();
  await expect
    .poll(() => consultas.at(-1))
    .toEqual({
      fechaDesde: "2024-02-29",
      fechaHasta: "2024-02-29",
      clienteId: "9",
      estadoPedidoCodigo: "PENDIENTE",
      page: "0",
      size: "20",
    });
  const resumen = page.getByRole("region", { name: "Resumen de la consulta" });
  await expect(resumen).toContainText(/\$\s157,12/);
  await aplicar.click();
  expect(consultas).toHaveLength(2);
  await page.getByRole("button", { name: "Siguiente" }).click();
  await expect(page.getByText("Página 2 de 2")).toBeVisible();
  await expect(tabla.getByRole("link", { name: "Pedido #44" })).toHaveAttribute(
    "href",
    "/pedidos/44",
  );
  await expect(resumen).toContainText(/\$\s157,12/);
  expect(consultas.at(-1)).toMatchObject({
    page: "1",
    clienteId: "9",
    estadoPedidoCodigo: "PENDIENTE",
  });
  await filtros.getByLabel("Estado del pedido").selectOption("");
  await aplicar.click();
  await expect(page.getByText("Página 1 de 2")).toBeVisible();
  await filtros.getByRole("button", { name: "Limpiar filtros" }).click();
  await expect(page).toHaveURL(/\/reportes\/pedidos$/);
  await expect(filtros.getByLabel("ID del cliente")).toHaveValue("");
  await expect(filtros.getByLabel("Fecha desde")).toHaveValue("");
});

test("carga, recupera un error y permite consultar una tabla en móvil", async ({
  page,
}) => {
  await page.setViewportSize({ width: 320, height: 812 });
  await prepararSesion(page);
  let resolver!: () => void;
  const espera = new Promise<void>((resolve) => {
    resolver = resolve;
  });
  let intentos = 0;
  await page.route("**/api/reportes/movimientos-caja?*", async (route) => {
    if (++intentos === 1) {
      await espera;
      return route.fulfill({
        status: 503,
        json: { detail: "Reporte temporalmente fuera de servicio." },
      });
    }
    return route.fulfill({
      json: {
        datos: [
          {
            movimientoId: 11,
            cajaId: 3,
            fechaHora: "2024-02-29T23:55:00",
            tipoMovimientoNombre: "Ingreso manual",
            metodoPagoNombre: "Efectivo",
            naturaleza: "INGRESO",
            monto: 12.53,
          },
        ],
        resumen: {
          cantidadMovimientos: 1,
          totalIngresos: 12.53,
          totalEgresos: 0,
          saldoNeto: 12.53,
        },
        pagina: 0,
        tamanio: 20,
        totalElementos: 1,
        totalPaginas: 1,
      },
    });
  });
  await page.goto("/reportes/movimientos-caja");
  await fillLogin(page, cuenta);
  try {
    await expect(
      page.getByRole("status").filter({ hasText: "Cargando reporte" }),
    ).toBeVisible();
  } finally {
    resolver();
  }
  await expect(page.getByRole("alert")).toContainText(
    "Reporte temporalmente fuera de servicio.",
  );
  await page.getByRole("button", { name: "Reintentar" }).click();
  const contenedor = page.getByRole("region", {
    name: "Movimientos de Caja del reporte",
  });
  const tabla = contenedor.getByRole("table");
  await expect(tabla).toContainText("Ingreso manual");
  await expect(
    tabla.getByRole("link", { name: "Movimiento #11" }),
  ).toHaveAttribute("href", "/caja/3/movimientos/11");
  await expect(tabla.getByRole("link", { name: "Caja #3" })).toHaveAttribute(
    "href",
    "/caja/3",
  );
  await expect(contenedor).toHaveAttribute("tabindex", "0");
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
});

test("EMPLEADO no ve accesos ni dispara consultas al entrar a una URL de reportes", async ({
  page,
}) => {
  await prepararSesion(page, "EMPLEADO");
  const llamadas: string[] = [];
  await page.route("**/api/reportes/**", (route) => {
    llamadas.push(route.request().url());
    return route.fulfill({ status: 403 });
  });
  await page.goto("/reportes/compras");
  await fillLogin(page, cuenta);
  await expect(
    page.getByRole("heading", { name: "Acceso restringido" }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Panel inicial" }).click();
  await expect(
    page.getByRole("dialog").getByRole("link", { name: "Reportes" }),
  ).toHaveCount(0);
  expect(llamadas).toEqual([]);
});

for (const status of [401, 403]) {
  test(`respeta el HTTP ${status} de Spring en una consulta de reportes`, async ({
    page,
  }) => {
    await prepararSesion(page);
    await page.route("**/api/reportes/compras?*", (route) =>
      route.fulfill({ status }),
    );
    await page.goto("/reportes/compras");
    await fillLogin(page, cuenta);
    if (status === 401) {
      await expect(page).toHaveURL(/\/login$/);
      await expect(
        page.getByText("Tu sesión venció. Volvé a ingresar para continuar."),
      ).toBeVisible();
    } else {
      await expect(page.getByRole("alert")).toContainText("No tenés permiso");
      await expect(page.getByRole("table")).toHaveCount(0);
    }
  });
}
