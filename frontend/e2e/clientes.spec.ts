import { expect, test } from "@playwright/test";
import type { Page, Route } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { fillLogin } from "./login";

const cliente = {
  id: 1,
  nombre: "Ana",
  apellido: "Núñez",
  dni: "12345678",
  telefono: null,
  email: null,
  activo: true,
  fechaCreacion: "2026-11-18T11:15:00",
};
const login = { username: "cuenta.prueba", password: "clave-sintetica" };

async function openClientes(
  page: Page,
  role: "ADMINISTRADOR" | "EMPLEADO",
  clientes: (route: Route, url: URL) => Promise<void>,
  path = "/clientes",
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
            rol: role,
            username: login.username,
          },
        });
      if (/^\/api\/clientes(?:\/\d+)?$/.test(url.pathname))
        return clientes(route, url);
      return route.abort();
    },
  );
  await page.goto(path);
  await fillLogin(page, login);
}

test("lista, cambia página y orden en el servidor y busca en la página visible", async ({
  page,
}) => {
  const requests: URLSearchParams[] = [];
  await openClientes(page, "EMPLEADO", async (route, url) => {
    expect(route.request().method()).toBe("GET");
    requests.push(url.searchParams);
    const number = Number(url.searchParams.get("page"));
    await route.fulfill({
      json: {
        content: [
          number === 0
            ? cliente
            : { ...cliente, id: 21, nombre: "Luz", apellido: "Pérez" },
        ],
        page: {
          size: Number(url.searchParams.get("size")),
          number,
          totalElements: 51,
          totalPages: Number(url.searchParams.get("size")) === 50 ? 2 : 3,
        },
      },
    });
  });
  await expect(
    page.getByRole("table", { name: "Clientes activos" }),
  ).toBeVisible();
  await expect(page.getByText("51 clientes activos")).toBeVisible();
  await expect(page.getByRole("link", { name: "Nuevo cliente" })).toBeVisible();
  await expect(
    page.getByRole("link", { name: "Editar a Ana Núñez" }),
  ).toBeVisible();
  await expect(page.getByRole("button", { name: /Desactivar/ })).toHaveCount(0);
  await page.getByLabel("Buscar en esta página").fill("nunez ana");
  await expect(page.getByText(/1 coincidencia/)).toBeVisible();
  await page.getByLabel("Buscar en esta página").fill("Sin coincidencias");
  await expect(
    page.getByRole("heading", { name: "Sin coincidencias en esta página" }),
  ).toBeVisible();
  expect(requests).toHaveLength(1);
  await page.getByRole("button", { name: "Siguiente" }).click();
  await expect(page.getByText("Página 2 de 3")).toBeVisible();
  expect(requests[1].get("page")).toBe("1");
  await page.getByRole("button", { name: "Limpiar búsqueda" }).click();
  await expect(page.getByRole("link", { name: "Pérez, Luz" })).toBeVisible();
  await page.getByLabel("Ordenar por").selectOption("apellido,desc");
  await expect(page.getByText("Página 1 de 3")).toBeVisible();
  expect(requests[2].getAll("sort")).toEqual(["apellido,desc", "id,asc"]);
  await page.getByLabel("Por página").selectOption("50");
  await expect(page.getByText("Página 1 de 2")).toBeVisible();
  expect(requests[3].get("size")).toBe("50");

  await page.setViewportSize({ width: 375, height: 812 });
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
});

test("muestra carga, un error de servidor recuperable y el estado vacío", async ({
  page,
}) => {
  let respond!: () => void;
  const firstResponse = new Promise<void>((resolve) => {
    respond = resolve;
  });
  let attempts = 0;
  await openClientes(page, "EMPLEADO", async (route) => {
    if (attempts++ === 0) {
      await firstResponse;
      await route.fulfill({
        status: 500,
        json: { detail: "Detalle técnico de prueba" },
      });
    } else {
      await route.fulfill({
        json: {
          content: [],
          page: { size: 20, number: 0, totalElements: 0, totalPages: 0 },
        },
      });
    }
  });
  await expect(page.getByText("Cargando clientes…")).toBeVisible();
  respond();
  await expect(page.getByRole("alert")).toContainText(
    "El servidor no está disponible temporalmente.",
  );
  await page.getByRole("button", { name: "Reintentar" }).click();
  await expect(
    page.getByRole("heading", { name: "Todavía no hay clientes activos" }),
  ).toBeVisible();
});

test("el formulario muestra errores de campo del servidor y bloquea el envío pendiente", async ({
  page,
}) => {
  let respond!: () => void;
  const validation = new Promise<void>((resolve) => {
    respond = resolve;
  });
  await openClientes(
    page,
    "EMPLEADO",
    async (route) => {
      expect(route.request().method()).toBe("POST");
      expect(route.request().postDataJSON()).toEqual({
        nombre: "Ana",
        apellido: "Núñez",
        dni: "12345678",
        telefono: null,
        email: null,
      });
      await validation;
      await route.fulfill({
        status: 400,
        json: {
          detail: "Hay campos inválidos en la solicitud.",
          errores: [{ campo: "nombre", mensaje: "Revisá el nombre." }],
        },
      });
    },
    "/clientes/nuevo",
  );
  await page.getByRole("button", { name: "Guardar cliente" }).click();
  await expect(page.getByLabel("Nombre", { exact: true })).toHaveAttribute(
    "aria-invalid",
    "true",
  );
  await page.getByLabel("Nombre", { exact: true }).fill("Ana");
  await page.getByLabel("Apellido").fill("Núñez");
  await page.getByLabel("DNI").fill("12345678");
  await page.getByRole("button", { name: "Guardar cliente" }).click();
  await expect(page.getByRole("button", { name: "Guardando…" })).toBeDisabled();
  respond();
  await expect(
    page.getByRole("alert").filter({ hasText: "Revisá el nombre." }),
  ).toBeVisible();
  await expect(
    page.getByLabel("Nombre", { exact: true }),
  ).toHaveAccessibleDescription("Revisá el nombre.");
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
});

test("una baja administrativa rechazada conserva el cliente visible", async ({
  page,
}) => {
  await openClientes(page, "ADMINISTRADOR", async (route) => {
    if (route.request().method() === "DELETE")
      return route.fulfill({ status: 403 });
    await route.fulfill({
      json: {
        content: [cliente],
        page: { size: 20, number: 0, totalElements: 1, totalPages: 1 },
      },
    });
  });
  await page.getByRole("button", { name: "Desactivar a Ana Núñez" }).click();
  const dialog = page.getByRole("alertdialog");
  await expect(dialog.getByRole("button", { name: "Cancelar" })).toBeFocused();
  await dialog.getByRole("button", { name: "Desactivar cliente" }).click();
  await expect(page.getByRole("alert")).toContainText("No tenés permiso");
  await expect(page.getByRole("link", { name: "Núñez, Ana" })).toBeVisible();
});
