import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";
import type { Page, Route } from "@playwright/test";
import { fillLogin } from "./login";

const credenciales = { username: "cuenta.prueba", password: "clave-sintetica" };
const categoria = { id: 3, nombre: "Higiene", activo: true };
const insumo = {
  id: 21,
  nombre: "Jabón",
  categoriaId: categoria.id,
  categoriaNombre: categoria.nombre,
  activo: true,
};
const proveedor = {
  id: 8,
  nombre: "Proveedor de prueba",
  telefono: "011-12",
  cuit: "30123456781",
  activo: true,
};

function pagina<T>(
  content: T[],
  number = 0,
  size = 10,
  totalElements = content.length,
) {
  return {
    content,
    page: {
      number,
      size,
      totalElements,
      totalPages: Math.ceil(totalElements / size),
    },
  };
}

async function abrir(
  page: Page,
  path: string,
  rol: "ADMINISTRADOR" | "EMPLEADO",
  handler: (route: Route, url: URL) => Promise<void>,
) {
  await page.route("**/api/**", (route) => {
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
          nombre: "Cuenta de prueba",
          email: "prueba@example.test",
          username: credenciales.username,
          rol,
        },
      });
    return handler(route, url);
  });
  await page.goto(path);
  await fillLogin(page, credenciales);
}

test("insumos: filtro, páginas del servidor, orden local, teclado y móvil", async ({
  page,
}) => {
  await page.setViewportSize({ width: 320, height: 812 });
  const consultas: Record<string, string>[] = [];
  await abrir(page, "/insumos?size=10", "EMPLEADO", async (route, url) => {
    if (url.pathname === "/api/categorias-insumo")
      return route.fulfill({ json: [categoria] });
    expect(url.pathname).toBe("/api/insumos");
    consultas.push(Object.fromEntries(url.searchParams));
    const index = Number(url.searchParams.get("page"));
    const filtrado = url.searchParams.has("categoriaId");
    if (filtrado) return route.fulfill({ json: pagina([insumo]) });
    const filas =
      index === 0
        ? [
            { ...insumo, nombre: "Zeta" },
            { ...insumo, id: 22, nombre: "Alfa" },
          ]
        : [{ ...insumo, id: 35 }];
    await route.fulfill({ json: pagina(filas, index, 10, 11) });
  });
  const tabla = page.getByRole("table", { name: "Insumos", exact: true });
  await expect(tabla.getByRole("link")).toHaveText(["Zeta", "Alfa"]);
  await page.getByLabel("Orden en esta página").selectOption("nombre");
  await expect(tabla.getByRole("link")).toHaveText(["Alfa", "Zeta"]);
  expect(consultas).toHaveLength(1);
  const region = page.getByRole("region", { name: "Insumos", exact: true });
  await region.focus();
  await expect(region).toBeFocused();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
  await page.getByRole("button", { name: "Siguiente" }).click();
  await expect(tabla.getByRole("link")).toHaveText(["Jabón"]);
  expect(consultas[1]).toEqual({ page: "1", size: "10" });
  await page.getByLabel("Filtrar por categoría").selectOption("3");
  await expect(
    page.getByText("1 insumo activo", { exact: true }),
  ).toBeVisible();
  expect(consultas[2]).toEqual({ page: "0", size: "10", categoriaId: "3" });
  await expect(page.getByRole("link", { name: "Nuevo insumo" })).toHaveCount(0);
});

test("insumos: una categoría desactivada entre la selección y el alta se valida al guardar", async ({
  page,
}) => {
  let disponible = true;
  let intentos = 0;
  await abrir(page, "/insumos/nuevo", "ADMINISTRADOR", async (route, url) => {
    if (url.pathname === "/api/categorias-insumo")
      return route.fulfill({
        json: disponible
          ? [categoria, { ...categoria, id: 4, nombre: "Otros" }]
          : [{ ...categoria, id: 4, nombre: "Otros" }],
      });
    expect(url.pathname).toBe("/api/insumos");
    expect(route.request().method()).toBe("POST");
    expect(route.request().postDataJSON()).toEqual({
      nombre: "Jabón",
      categoriaId: 3,
    });
    intentos++;
    disponible = false;
    return route.fulfill({
      status: 404,
      json: {
        detail: "No se encontró una categoría de insumo activa con ID 3.",
      },
    });
  });
  const nombre = page.getByLabel("Nombre", { exact: true });
  const selector = page.getByLabel("Categoría", { exact: true });
  await nombre.fill(" Jabón ");
  await selector.selectOption("3");
  const guardar = page.getByRole("button", { name: "Guardar insumo" });
  await guardar.click();
  await expect(selector).toHaveAccessibleDescription(
    "No se encontró una categoría de insumo activa con ID 3.",
  );
  await expect(selector.locator('option[value="3"]')).toBeDisabled();
  await guardar.click();
  await expect(selector).toHaveAccessibleDescription(
    "Seleccioná una categoría activa.",
  );
  expect(intentos).toBe(1);
  await expect(nombre).toHaveValue(" Jabón ");
});

test("categorías: carga, reintento y nombre duplicado asociado al campo", async ({
  page,
}) => {
  let consultas = 0;
  await abrir(
    page,
    "/insumos/categorias",
    "ADMINISTRADOR",
    async (route, url) => {
      expect(url.pathname).toBe("/api/categorias-insumo");
      if (route.request().method() === "GET")
        return ++consultas === 1
          ? route.fulfill({ status: 503, json: {} })
          : route.fulfill({ json: [categoria] });
      expect(route.request().postDataJSON()).toEqual({ nombre: "HIGIENE" });
      return route.fulfill({
        status: 409,
        json: { detail: "Ya existe una categoría de insumo con ese nombre." },
      });
    },
  );
  await expect(page.getByRole("alert")).toHaveText(
    "El servidor no está disponible temporalmente.",
  );
  await page.getByRole("button", { name: "Reintentar" }).click();
  await expect(
    page.getByRole("table", { name: "Categorías de insumos" }),
  ).toBeVisible();
  await page.getByRole("link", { name: "Nueva categoría" }).click();
  const nombre = page.getByLabel("Nombre", { exact: true });
  await nombre.fill(" HIGIENE ");
  await page.getByRole("button", { name: "Guardar categoría" }).click();
  await expect(nombre).toHaveAccessibleDescription(
    "Ya existe una categoría de insumo con ese nombre.",
  );
});

for (const path of [
  "/nuevo",
  "/21/editar",
  "/reactivar",
  "/categorias/nuevo",
  "/categorias/3/editar",
  "/categorias/reactivar",
]) {
  test(`insumos: el empleado tiene restringida la ruta ${path}`, async ({
    page,
  }) => {
    await abrir(page, "/insumos" + path, "EMPLEADO", async () => {
      throw new Error("La ruta restringida consultó la API del catálogo.");
    });
    await expect(
      page.getByRole("heading", { name: "Acceso restringido" }),
    ).toBeVisible();
  });
}

test("asociación: recorre todas las páginas de vínculos y actualiza el selector tras un 409 concurrente", async ({
  page,
}) => {
  let duplicado = false;
  let escritos = 0;
  const yaVinculado = { ...insumo, id: 24, nombre: "Guantes" };
  const relaciones = Array.from({ length: 101 }, (_, i) => ({
    proveedorId: 8,
    proveedorNombre: proveedor.nombre,
    insumoId: i < 100 ? i + 50 : yaVinculado.id,
    insumoNombre: i < 100 ? `Otro insumo ${i}` : yaVinculado.nombre,
    categoriaId: categoria.id,
    categoriaNombre: categoria.nombre,
  }));
  await abrir(page, "/proveedores/8", "ADMINISTRADOR", async (route, url) => {
    if (url.pathname === "/api/proveedores/8")
      return route.fulfill({ json: proveedor });
    if (url.pathname === "/api/categorias-insumo")
      return route.fulfill({ json: [categoria] });
    if (url.pathname === "/api/insumos")
      return route.fulfill({
        json: pagina([
          insumo,
          yaVinculado,
          { ...insumo, id: 25, nombre: "Archivado", activo: false },
          { ...insumo, id: 26, nombre: "Categoría archivada", categoriaId: 6 },
        ]),
      });
    expect(url.pathname).toBe("/api/proveedores/8/insumos");
    if (route.request().method() === "POST") {
      expect(route.request().postDataJSON()).toEqual({ insumoId: insumo.id });
      escritos++;
      duplicado = true;
      return route.fulfill({
        status: 409,
        json: { detail: "El proveedor ya está asociado con ese insumo." },
      });
    }
    const size = Number(url.searchParams.get("size"));
    const index = Number(url.searchParams.get("page"));
    const datos = duplicado
      ? [
          {
            ...relaciones[0],
            insumoId: insumo.id,
            insumoNombre: insumo.nombre,
          },
          ...relaciones,
        ]
      : relaciones;
    return route.fulfill({
      json: pagina(
        datos.slice(index * size, (index + 1) * size),
        index,
        size,
        datos.length,
      ),
    });
  });
  const tabla = page.getByRole("table", { name: "Insumos suministrados" });
  await expect(
    tabla.getByRole("link", { name: "Otro insumo 0", exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Siguiente" }).click();
  await expect(
    tabla.getByRole("link", { name: "Otro insumo 10", exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Anterior" }).click();
  await page.getByRole("button", { name: "Asociar insumo" }).click();
  const selector = page.getByLabel("Insumo para asociar");
  await expect(selector).toBeEnabled();
  await expect(selector.getByRole("option")).toHaveText([
    "Seleccioná una opción",
    "Jabón · Higiene · ID 21",
  ]);
  await selector.selectOption("21");
  const confirmar = page.getByRole("button", { name: "Confirmar asociación" });
  await confirmar.click();
  await expect(selector).toHaveAccessibleDescription(
    "El proveedor ya está asociado con ese insumo.",
  );
  await expect(confirmar).toBeDisabled();
  await expect(tabla.getByRole("link", { name: "Jabón" })).toBeVisible();
  expect(escritos).toBe(1);
});
