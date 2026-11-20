import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";
import type { Page, Route } from "@playwright/test";
import { fillLogin } from "./login";

const proveedor = {
  id: 1,
  nombre: "Zeta Limpieza",
  cuit: "30123456781",
  telefono: "011-1234",
  rubro: "Limpieza",
  activo: true,
};
const login = { username: "cuenta.prueba", password: "clave-sintetica" };

async function abrir(
  page: Page,
  rol: "ADMINISTRADOR" | "EMPLEADO",
  proveedorApi: (route: Route, url: URL) => Promise<void>,
  path = "/proveedores",
) {
  await page.route(
    (url) => url.pathname.startsWith("/api/"),
    async (route) => {
      const url = new URL(route.request().url());
      if (url.pathname === "/api/auth/login")
        return route.fulfill({
          json: { accessToken: "prueba", tokenType: "Bearer", expiresIn: 3600 },
        });
      expect(route.request().headers().authorization).toBe("Bearer prueba");
      if (url.pathname === "/api/auth/me")
        return route.fulfill({
          json: {
            id: 1,
            nombre: "Usuario Prueba",
            email: "usuario@example.test",
            rol,
            username: login.username,
          },
        });
      if (/^\/api\/proveedores\/\d+\/insumos$/.test(url.pathname))
        return route.fulfill({
          json: {
            content: [],
            page: { size: 10, number: 0, totalElements: 0, totalPages: 0 },
          },
        });
      if (url.pathname.startsWith("/api/proveedores"))
        return proveedorApi(route, url);
      return route.abort();
    },
  );
  await page.goto(path);
  await fillLogin(page, login);
}

test("proveedores: pagina y filtra en servidor, ordena la página y permite lectura al empleado", async ({
  page,
}) => {
  const requests: URLSearchParams[] = [];
  await abrir(page, "EMPLEADO", async (route, url) => {
    expect(route.request().method()).toBe("GET");
    if (url.pathname.endsWith("/1")) return route.fulfill({ json: proveedor });
    requests.push(url.searchParams);
    const number = Number(url.searchParams.get("page"));
    const size = Number(url.searchParams.get("size"));
    await route.fulfill({
      json: {
        content:
          number === 0
            ? [proveedor, { ...proveedor, id: 2, nombre: "Alfa" }]
            : [{ ...proveedor, id: 21, nombre: "Beta" }],
        page: {
          size,
          number,
          totalPages: Math.ceil(21 / size),
          totalElements: 21,
        },
      },
    });
  });
  const table = page.getByRole("table", { name: "Proveedores activos" });
  await expect(table.getByRole("link")).toHaveText(["Zeta Limpieza", "Alfa"]);
  await page.getByLabel("Orden en esta página").selectOption("nombre");
  await expect(table.getByRole("link")).toHaveText(["Alfa", "Zeta Limpieza"]);
  expect(requests).toHaveLength(1);
  await page.getByRole("button", { name: "Siguiente" }).click();
  await expect(table.getByRole("link")).toHaveText(["Beta"]);
  expect(requests[1].get("page")).toBe("1");
  await page.getByLabel("Por página").selectOption("10");
  await expect(page.getByText("Página 1 de 3")).toBeVisible();
  expect(requests[2].get("size")).toBe("10");
  await page.getByLabel("Nombre", { exact: true }).fill(" Zeta ");
  await page.getByLabel("CUIT", { exact: true }).fill("30-12345678-9");
  await page.getByRole("button", { name: "Aplicar filtros" }).click();
  await expect(
    page.getByLabel("CUIT", { exact: true }),
  ).toHaveAccessibleDescription("El dígito verificador del CUIT es inválido.");
  await page.getByLabel("CUIT", { exact: true }).fill("30-12345678-1");
  await page.getByLabel("Teléfono", { exact: true }).fill("011");
  await page.getByLabel("Rubro", { exact: true }).fill("Limpieza");
  await page.getByRole("button", { name: "Aplicar filtros" }).click();
  await expect
    .poll(() => Object.fromEntries(requests[3] ?? []))
    .toEqual({
      page: "0",
      size: "10",
      nombre: "Zeta",
      cuit: "30-12345678-1",
      telefono: "011",
      rubro: "Limpieza",
    });
  await page.setViewportSize({ width: 375, height: 812 });
  const region = page.getByRole("region", { name: "Proveedores activos" });
  await region.focus();
  await expect(region).toBeFocused();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
  await expect(page.getByRole("link", { name: "Nuevo proveedor" })).toHaveCount(
    0,
  );
  await expect(
    page.getByRole("link", { name: "Reactivar proveedor" }),
  ).toHaveCount(0);
  await expect(
    page.getByRole("columnheader", { name: "Acciones" }),
  ).toHaveCount(0);
  await table.getByRole("link", { name: proveedor.nombre }).click();
  await expect(
    page.getByRole("heading", { name: proveedor.nombre }),
  ).toBeVisible();
  await expect(
    page.getByRole("link", { name: "Editar proveedor" }),
  ).toHaveCount(0);
  await expect(page.getByRole("button", { name: /Desactivar/ })).toHaveCount(0);
  await page.getByRole("link", { name: "Volver a proveedores" }).click();
  await expect(page.getByLabel("Nombre", { exact: true })).toHaveValue("Zeta");
  await page.getByRole("button", { name: "Limpiar filtros" }).click();
  await expect(page.getByLabel("CUIT", { exact: true })).toHaveValue("");
});

for (const path of ["/nuevo", "/1/editar", "/reactivar"]) {
  test(`proveedores: el empleado no accede por URL a ${path}`, async ({
    page,
  }) => {
    await abrir(
      page,
      "EMPLEADO",
      async () => {
        throw new Error("La ruta restringida llamó a la API.");
      },
      "/proveedores" + path,
    );
    await expect(
      page.getByRole("heading", { name: "Acceso restringido" }),
    ).toBeVisible();
  });
}

test("proveedores: carga, error recuperable y listado vacío", async ({
  page,
}) => {
  let responder!: () => void;
  const espera = new Promise<void>((resolve) => {
    responder = resolve;
  });
  let llamadas = 0;
  await abrir(page, "ADMINISTRADOR", async (route) => {
    if (++llamadas === 1) {
      await espera;
      return route.fulfill({
        status: 503,
        json: { detail: "Servicio temporalmente no disponible." },
      });
    }
    return route.fulfill({
      json: {
        content: [],
        page: { size: 20, number: 0, totalElements: 0, totalPages: 0 },
      },
    });
  });
  await expect(page.getByText("Cargando proveedores…")).toBeVisible();
  responder();
  await expect(page.getByRole("alert")).toHaveText(
    "Servicio temporalmente no disponible.",
  );
  await page.getByRole("button", { name: "Reintentar" }).click();
  await expect(
    page.getByRole("heading", { name: "Sin proveedores activos" }),
  ).toBeVisible();
});

test("proveedores: validaciones, doble envío y errores de escritura", async ({
  page,
}) => {
  let responder!: () => void;
  const espera = new Promise<void>((resolve) => {
    responder = resolve;
  });
  let llamadas = 0;
  await abrir(
    page,
    "ADMINISTRADOR",
    async (route) => {
      expect(route.request().method()).toBe("POST");
      expect(route.request().postDataJSON()).toEqual({
        nombre: "Zeta Limpieza",
        cuit: proveedor.cuit,
        telefono: proveedor.telefono,
        rubro: null,
      });
      if (++llamadas === 1) {
        await espera;
        return route.fulfill({
          status: 409,
          json: { detail: "Ya existe un proveedor con ese CUIT." },
        });
      }
      if (llamadas === 2)
        return route.fulfill({
          status: 400,
          json: {
            errores: [{ campo: "telefono", mensaje: "Teléfono rechazado." }],
          },
        });
      return route.fulfill({ status: 403, json: {} });
    },
    "/proveedores/nuevo",
  );
  const guardar = page.getByRole("button", { name: "Guardar proveedor" });
  await guardar.click();
  await expect(
    page.getByLabel("Nombre", { exact: true }),
  ).toHaveAccessibleDescription("Ingresá un nombre.");
  expect(llamadas).toBe(0);
  await page.getByLabel("Nombre", { exact: true }).fill(" Zeta Limpieza ");
  await page.getByLabel("CUIT", { exact: true }).fill(proveedor.cuit);
  await page.getByLabel("Teléfono", { exact: true }).fill(proveedor.telefono);
  await guardar.dblclick();
  await expect(page.getByRole("button", { name: "Guardando…" })).toBeDisabled();
  expect(llamadas).toBe(1);
  responder();
  await expect(
    page.getByLabel("CUIT", { exact: true }),
  ).toHaveAccessibleDescription("Ya existe un proveedor con ese CUIT.");
  await guardar.click();
  await expect(
    page.getByLabel("Teléfono", { exact: true }),
  ).toHaveAccessibleDescription("Teléfono rechazado.");
  await guardar.click();
  await expect(page.getByRole("alert")).toHaveText(
    "No tenés permiso para realizar esta operación.",
  );
});

test("proveedores: un ID ausente devuelve un detalle 404", async ({ page }) => {
  await abrir(
    page,
    "ADMINISTRADOR",
    async (route) => {
      await route.fulfill({ status: 404, json: {} });
    },
    "/proveedores/123",
  );
  await expect(
    page.getByRole("heading", { name: "Proveedor no encontrado" }),
  ).toBeVisible();
  await expect(
    page.getByRole("link", { name: "Volver a proveedores" }),
  ).toBeVisible();
});
