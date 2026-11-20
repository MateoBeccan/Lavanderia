import { expect, test } from "@playwright/test";
import type { Page, Route } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { fillLogin } from "./login";

const cuenta = { username: "catalogo.prueba", password: "clave-sintetica" };

async function abrir(
  page: Page,
  path: string,
  rol: "ADMINISTRADOR" | "EMPLEADO",
  catalogo: (route: Route, url: URL) => Promise<void>,
) {
  await page.route(
    (url) => url.pathname.startsWith("/api/"),
    async (route) => {
      const url = new URL(route.request().url());
      if (url.pathname === "/api/auth/login")
        return route.fulfill({
          json: { accessToken: "prueba", tokenType: "Bearer", expiresIn: 3600 },
        });
      if (url.pathname === "/api/auth/me")
        return route.fulfill({
          json: {
            id: 1,
            nombre: "Usuario Prueba",
            email: "prueba@example.test",
            rol,
            username: cuenta.username,
          },
        });
      return catalogo(route, url);
    },
  );
  await page.goto(path);
  await fillLogin(page, cuenta);
}

test("Servicios pagina y ordena en Spring, filtra la página y permite navegar con teclado en móvil", async ({
  page,
}) => {
  const consultas: URLSearchParams[] = [];
  await page.setViewportSize({ width: 375, height: 812 });
  await abrir(page, "/servicios", "EMPLEADO", async (route, url) => {
    consultas.push(url.searchParams);
    const number = Number(url.searchParams.get("page"));
    await route.fulfill({
      json: {
        content: [
          {
            id: number + 1,
            nombre: number ? "Planchado" : "Lavado",
            activo: true,
            modalidadCobro: "PRENDA",
            precio: 23.75,
          },
        ],
        page: { number, size: 20, totalPages: 2, totalElements: 21 },
      },
    });
  });
  await expect(
    page.getByRole("table", { name: "Servicios activos" }),
  ).toBeVisible();
  await expect(page.getByRole("link", { name: "Nuevo servicio" })).toHaveCount(
    0,
  );
  await expect(page.getByRole("button", { name: /Desactivar/ })).toHaveCount(0);
  await page.getByLabel("Filtrar página actual").fill("Planchado");
  await expect(
    page.getByText("Sin coincidencias en esta página"),
  ).toBeVisible();
  expect(consultas).toHaveLength(1);
  const siguiente = page.getByRole("button", { name: "Siguiente" });
  await siguiente.focus();
  await page.keyboard.press("Enter");
  await expect(page.getByRole("link", { name: "Planchado" })).toBeVisible();
  expect(consultas[1].get("page")).toBe("1");
  await page.getByLabel("Ordenar por").selectOption("nombre,asc");
  await expect(page.getByText("Página 1 de 2")).toBeVisible();
  expect(consultas[2].getAll("sort")).toEqual(["nombre,asc", "id,asc"]);
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
});

test("Prendas presenta carga, 403 recuperable, lista vacía y 401 de sesión", async ({
  page,
}) => {
  let liberar!: () => void;
  const espera = new Promise<void>((resolve) => {
    liberar = resolve;
  });
  let intento = 0;
  await abrir(page, "/prendas", "EMPLEADO", async (route) => {
    if (intento++ === 0) {
      await espera;
      return route.fulfill({ status: 403 });
    }
    if (intento === 2)
      return route.fulfill({
        json: {
          content: [],
          page: { number: 0, size: 20, totalPages: 0, totalElements: 0 },
        },
      });
    await route.fulfill({ status: 401 });
  });
  await expect(page.getByText("Cargando prendas…")).toBeVisible();
  liberar();
  await expect(page.getByRole("alert")).toContainText("No tenés permiso");
  await page.getByRole("button", { name: "Reintentar" }).click();
  await expect(page.getByText("Todavía no hay prendas activas")).toBeVisible();
  await page.getByLabel("Por página").selectOption("10");
  await expect(page).toHaveURL(/\/login/);
});

test("Servicios valida los campos y evita perder cambios", async ({ page }) => {
  await abrir(page, "/servicios/nuevo", "ADMINISTRADOR", async (route) => {
    expect(route.request().method()).toBe("POST");
    expect(route.request().postDataJSON()).toEqual({
      nombre: "Lavado",
      precio: 12.5,
      modalidadCobro: "KILO",
    });
    await route.fulfill({
      status: 400,
      json: {
        detail: "Solicitud inválida",
        errores: [{ campo: "precio", mensaje: "Precio inválido según Spring" }],
      },
    });
  });
  await page.getByRole("button", { name: "Guardar servicio" }).click();
  await expect(page.getByLabel("Nombre")).toHaveAccessibleDescription(
    "Ingresá un nombre.",
  );
  await expect(page.getByLabel("Precio")).toHaveAccessibleDescription(
    /8 enteros/,
  );
  await expect(
    page.getByLabel("Modalidad de cobro"),
  ).toHaveAccessibleDescription("Seleccioná una modalidad de cobro.");
  await page.getByLabel("Nombre").fill("Lavado");
  await page.getByLabel("Precio").fill("12,50");
  await page.getByLabel("Modalidad de cobro").selectOption("KILO");
  page.once("dialog", (dialog) => dialog.dismiss());
  await page.getByRole("link", { name: "Cancelar" }).click();
  await expect(page).toHaveURL(/\/servicios\/nuevo/);
  await page.getByRole("button", { name: "Guardar servicio" }).click();
  await expect(page.getByLabel("Precio")).toHaveAccessibleDescription(
    "Precio inválido según Spring",
  );
});

test("Prendas muestra el estado 404 en el detalle de un registro ausente", async ({
  page,
}) => {
  await abrir(page, "/prendas/42", "EMPLEADO", async (route, url) => {
    expect(url.pathname).toBe("/api/prendas/42");
    await route.fulfill({ status: 404 });
  });
  await expect(
    page.getByRole("heading", { name: "Registro no encontrado" }),
  ).toBeVisible();
  await expect(
    page.getByRole("link", { name: "Volver al listado" }),
  ).toHaveAttribute("href", "/prendas");
});

for (const path of [
  "/prendas/nuevo",
  "/prendas/1/editar",
  "/servicios/reactivar",
]) {
  test(`Empleado tiene bloqueado el acceso directo a ${path}`, async ({
    page,
  }) => {
    await abrir(page, path, "EMPLEADO", async (route) => {
      throw new Error("La vista protegida consultó " + route.request().url());
    });
    await expect(
      page.getByRole("heading", { name: "Acceso restringido" }),
    ).toBeVisible();
  });
}
