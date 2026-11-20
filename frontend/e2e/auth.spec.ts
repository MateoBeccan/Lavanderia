import { test, expect } from "@playwright/test";
import type { Page } from "@playwright/test";
import { fillLogin } from "./login";
import AxeBuilder from "@axe-core/playwright";

const fakeToken = "token-sintetico";
const credentials = { username: "cuenta.prueba", password: "clave-sintetica" };

async function wireApi(
  page: Page,
  role: "ADMINISTRADOR" | "EMPLEADO",
  metricStatus = 200,
  expiresIn = 3600,
) {
  await page.route(
    (url) => url.pathname.startsWith("/api/"),
    async (route) => {
      const request = route.request();
      const { pathname } = new URL(request.url());
      if (pathname === "/api/auth/login") {
        expect(request.method()).toBe("POST");
        expect(request.postDataJSON()).toEqual(credentials);
        return route.fulfill({
          json: { accessToken: fakeToken, tokenType: "Bearer", expiresIn },
        });
      }
      expect(request.headers().authorization).toBe("Bearer " + fakeToken);
      if (pathname === "/api/auth/me") {
        return route.fulfill({
          json: {
            id: 1,
            nombre: "Ana Prueba",
            email: "ana@example.test",
            rol: role,
            username: credentials.username,
          },
        });
      }
      if (metricStatus !== 200)
        return route.fulfill({
          status: metricStatus,
          json: { status: metricStatus },
        });
      if (pathname === "/api/cajas/abierta")
        return route.fulfill({
          status: 404,
          json: { detail: "No hay una caja abierta." },
        });
      if (
        pathname === "/api/estados-pedido" ||
        pathname === "/api/estados-pago" ||
        pathname === "/api/categorias-insumo"
      )
        return route.fulfill({ json: [] });
      return route.fulfill({
        json: {
          content: [],
          page: { size: 1, number: 0, totalElements: 0, totalPages: 0 },
        },
      });
    },
  );
}

test("ruta protegida, retorno al destino, navegación y cierre de sesión", async ({
  page,
}) => {
  await wireApi(page, "ADMINISTRADOR");
  await page.goto("/pedidos");
  await expect(page).toHaveURL(/\/login$/);
  await fillLogin(page, credentials);
  await expect(page).toHaveURL(/\/pedidos$/);
  await expect(
    page.getByRole("heading", { name: "Pedidos", exact: true }),
  ).toBeVisible();
  const breadcrumb = page.getByRole("navigation", {
    name: "Ruta de navegación",
  });
  await expect(breadcrumb.locator('[aria-current="page"]')).toHaveText(
    "Pedidos",
  );
  await page.getByRole("button", { name: "Panel inicial" }).click();
  const drawer = page.getByRole("dialog", { name: "Panel inicial" });
  const nav = drawer.getByRole("navigation", { name: "Navegación principal" });
  await expect(nav.getByRole("link", { name: "Reportes" })).toBeVisible();
  await nav.getByRole("link", { name: "Inicio" }).click();
  await expect(drawer).toBeHidden();
  await expect(page.getByText("Sin caja abierta")).toBeVisible();
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
  await page.getByRole("button", { name: "Cerrar sesión" }).click();
  await expect(page).toHaveURL(/\/login$/);
});

test("identidad, métricas y destinos del panel de inicio", async ({ page }) => {
  await wireApi(page, "ADMINISTRADOR");
  await page.goto("/");
  await fillLogin(page, credentials);

  const header = page.getByRole("banner");
  await expect(header).toContainText("MIRÁ QUE TE LAVO");
  await expect(header).toContainText("Sistema de Gestión de Lavandería");
  await expect(header).toContainText("Ana Prueba");
  await expect(header).toContainText("Administrador");
  const logo = header.locator("img");
  await expect(logo).toHaveAttribute("src", /\/images\/logo-lavanderia\.png$/);
  await expect
    .poll(() => logo.evaluate((image: HTMLImageElement) => image.naturalWidth))
    .toBeGreaterThan(0);

  const quick = page.getByRole("region", { name: "Acceso rápido" });
  await expect(quick.getByRole("link")).toHaveCount(4);
  const destinations = [
    ["Nuevo pedido", "/pedidos/nuevo"],
    ["Ver pedidos", "/pedidos"],
    ["Caja", "/caja"],
    ["Registrar cobro", "/cobros"],
  ] as const;

  const metrics = page.getByRole("region", { name: "Estado de la operación" });
  await expect(
    metrics.getByRole("heading", { name: "Clientes activos" }),
  ).toBeVisible();
  await expect(
    metrics.getByRole("heading", { name: "Pedidos registrados" }),
  ).toBeVisible();
  await expect(
    metrics.getByRole("heading", { name: "Estado de caja" }),
  ).toBeVisible();
  await expect(metrics.getByText("Sin caja abierta")).toBeVisible();
  for (const width of [768, 1024, 1280]) {
    await page.setViewportSize({ width, height: 800 });
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBe(true);
  }

  for (const [name, path] of destinations) {
    const shortcut = quick.getByRole("link", { name, exact: true });
    if (path === "/pedidos/nuevo") {
      await shortcut.focus();
      await expect(shortcut).toBeFocused();
      await page.keyboard.press("Enter");
    } else {
      await shortcut.click();
    }
    await expect(page).toHaveURL(path);
    if (path === "/cobros") {
      await expect(
        page.getByRole("heading", {
          name: "Cobros",
          exact: true,
        }),
      ).toBeVisible();
    }
    await page
      .getByRole("navigation", { name: "Ruta de navegación" })
      .getByRole("link", { name: "Inicio" })
      .click();
  }
});

test("empleado, 403 y navegación móvil con teclado", async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 812 });
  await wireApi(page, "EMPLEADO", 403);
  await page.goto("/");
  await fillLogin(page, credentials);
  await expect(
    page.getByRole("heading", { name: "Bienvenido, Ana Prueba" }),
  ).toBeVisible();
  await expect(
    page.getByText("Acceso restringido.", { exact: true }),
  ).toHaveCount(3);
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  const header = page.getByRole("banner");
  await expect(header).toContainText("Ana Prueba");
  await expect(header).toContainText("Empleado");
  await expect(
    header.getByRole("button", { name: "Cerrar sesión" }),
  ).toBeVisible();
  const main = page.getByRole("main");
  const skip = page.getByRole("link", { name: "Ir al contenido" });
  await skip.focus();
  await expect(skip).toBeFocused();
  await page.keyboard.press("Enter");
  await expect(main).toBeFocused();
  await page.keyboard.press("Tab");
  await expect(
    main.getByRole("button", { name: "Buscar módulos" }),
  ).toBeFocused();
  await page.keyboard.press("Enter");
  const buscador = page.getByRole("dialog", { name: "Buscar módulos" });
  await expect(buscador.getByRole("combobox")).toBeFocused();
  const dialogo = await buscador.boundingBox();
  expect(dialogo?.width).toBeLessThan(320);
  await expect(
    buscador.getByRole("option", { name: /Reportes|Historial|administración/ }),
  ).toHaveCount(0);
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
  await page.keyboard.press("Escape");
  await expect(
    main.getByRole("button", { name: "Buscar módulos" }),
  ).toBeFocused();
  await page.keyboard.press("Tab");
  await expect(main.getByRole("button", { name: "Atajos" })).toBeFocused();
  await page.keyboard.press("Tab");
  await expect(main.getByRole("link", { name: "Nuevo pedido" })).toBeFocused();
  await page.keyboard.press("Tab");
  await expect(main.getByRole("link", { name: "Ver pedidos" })).toBeFocused();
  await expect(
    main.getByRole("link", { name: "Historial de caja" }),
  ).toHaveCount(0);
  await expect(
    main.getByRole("link", { name: /Reportes|Usuarios y administración/ }),
  ).toHaveCount(0);
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
  const toggle = page.getByRole("button", { name: "Panel inicial" });
  await toggle.focus();
  await page.keyboard.press("Enter");
  const drawer = page.getByRole("dialog");
  await expect(drawer).toBeVisible();
  await expect(
    drawer.getByRole("link", { name: /Reportes|Usuarios y administración/ }),
  ).toHaveCount(0);
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
  await page.keyboard.press("Escape");
  await expect(drawer).toBeHidden();
  await expect(toggle).toBeFocused();
  for (const path of ["/reportes", "/administracion", "/caja/historial"]) {
    await page.evaluate((path) => {
      history.pushState({}, "", path);
      dispatchEvent(new PopStateEvent("popstate"));
    }, path);
    await expect(
      page.getByRole("heading", { name: "Acceso restringido" }),
    ).toBeVisible();
  }
});

test("busca localmente, navega con atajos y protege diálogos y formularios", async ({
  page,
}) => {
  await wireApi(page, "ADMINISTRADOR");
  await page.goto("/");
  await fillLogin(page, credentials);
  await expect(page.getByRole("heading", { name: /Bienvenido/ })).toBeVisible();
  await page.keyboard.press("?");
  await expect(
    page.getByRole("dialog", { name: "Atajos del inicio" }),
  ).toBeVisible();
  await page.keyboard.press("p");
  await expect(page).toHaveURL("/");
  await page.keyboard.press("Escape");
  await page.keyboard.press("/");
  const buscador = page.getByRole("dialog", { name: "Buscar módulos" });
  const campo = buscador.getByRole("combobox");
  await campo.fill("categorias");
  await expect(buscador.getByRole("option")).toHaveText(
    "Categorías de insumos",
  );
  await campo.fill("administración");
  await expect(buscador.getByRole("status")).toHaveText(
    "No encontramos módulos.",
  );
  await campo.fill("caja");
  await campo.press("ArrowDown");
  await expect(buscador.getByRole("option", { selected: true })).toHaveText(
    "Historial de caja",
  );
  await campo.press("Enter");
  await expect(page).toHaveURL("/caja/historial");

  for (const [tecla, ruta] of [
    ["n", "/pedidos/nuevo"],
    ["p", "/pedidos"],
    ["c", "/cobros"],
    ["j", "/caja"],
  ]) {
    await page.getByRole("link", { name: /MIRÁ QUE TE LAVO/ }).click();
    await expect(
      page.getByRole("heading", { name: /Bienvenido/ }),
    ).toBeVisible();
    await page.keyboard.press(tecla);
    await expect(page).toHaveURL(ruta);
    if (tecla === "n") {
      await expect(
        page.getByRole("heading", { name: "Nuevo pedido" }),
      ).toBeVisible({ timeout: 15_000 });
      await page.getByLabel("Entrega estimada").focus();
      await page.keyboard.press("p");
      await expect(page).toHaveURL(ruta);
    }
  }
  await page.getByRole("link", { name: /MIRÁ QUE TE LAVO/ }).click();
  await page.getByRole("button", { name: "Atajos" }).click();
  await page
    .getByRole("checkbox", { name: "Activar atajos de una tecla" })
    .uncheck();
  await page.keyboard.press("Escape");
  await expect(
    page.getByRole("dialog", { name: "Atajos del inicio" }),
  ).toBeHidden();
  await page.keyboard.press("p");
  await expect(page).toHaveURL("/");
});

test("401 del backend invalida la sesión activa", async ({ page }) => {
  await wireApi(page, "EMPLEADO", 401);
  await page.goto("/");
  await fillLogin(page, credentials);
  await expect(
    page.getByText("Tu sesión venció. Volvé a ingresar para continuar."),
  ).toBeVisible();
});

test("historial de Caja restringe al empleado antes de cargar el módulo", async ({
  page,
}) => {
  await wireApi(page, "EMPLEADO");
  const solicitudes: string[] = [];
  page.on("request", (request) => {
    const path = new URL(request.url()).pathname;
    if (/\/CajaRoutes[^/]*\.(js|tsx)$/.test(path) || path === "/api/cajas") {
      solicitudes.push(path);
    }
  });
  await page.route(/\/CajaRoutes[^/]*\.(js|tsx)(\?.*)?$/, (route) =>
    route.abort(),
  );
  await page.goto("/caja/historial");
  await fillLogin(page, credentials);
  await expect(
    page.getByRole("heading", { name: "Acceso restringido" }),
  ).toBeVisible();
  expect(solicitudes).toEqual([]);
});

test("una recarga descarta la sesión", async ({ page }) => {
  await wireApi(page, "ADMINISTRADOR");
  await page.goto("/");
  await fillLogin(page, credentials);
  await expect(
    page.getByRole("heading", { name: "Bienvenido, Ana Prueba" }),
  ).toBeVisible();
  await page.reload();
  await expect(page).toHaveURL(/\/login$/);
});

test("backend desconectado", async ({ page }) => {
  await page.route("**/api/auth/login", (route) =>
    route.abort("connectionrefused"),
  );
  await page.goto("/login");
  await fillLogin(page, credentials);
  await expect(page.getByRole("alert")).toContainText(
    "No pudimos conectar con el servidor.",
  );
});
