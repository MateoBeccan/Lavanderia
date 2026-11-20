import { expect, test } from "@playwright/test";
import { fillLogin } from "./login";
test.use({ trace: "off" });

test("login con Spring Boot", async ({ page }) => {
  const username = process.env.E2E_USERNAME;
  const password = process.env.E2E_PASSWORD;
  test.skip(
    process.env.E2E_LOGIN_ISOLATED !== "1",
    "Requiere Spring Boot aislado en 18053.",
  );
  if (!username || !password)
    throw new Error("Faltan las credenciales de prueba.");
  await page.goto("/pedidos");
  await fillLogin(page, {
    username: username,
    password: "intento-invalido-de-prueba",
  });
  await expect(page.getByRole("alert")).toContainText(
    "El usuario o la contraseña son incorrectos.",
  );
  await fillLogin(page, { username: username, password: password });
  await expect(page).toHaveURL(/\/pedidos$/);
  await expect(
    page.getByRole("heading", { name: "Pedidos", exact: true }),
  ).toBeVisible();
  const metrics = ["/api/clientes", "/api/pedidos", "/api/cajas/abierta"].map(
    (path) =>
      page.waitForResponse(
        (response) => new URL(response.url()).pathname === path,
      ),
  );
  await page
    .getByRole("navigation", { name: "Ruta de navegación" })
    .getByRole("link", { name: "Inicio" })
    .click();
  await expect(
    page.getByRole("heading", { name: /^Bienvenido,/ }),
  ).toBeVisible();
  const [clientes, pedidos, caja] = await Promise.all(metrics);
  expect(clientes.status()).toBe(200);
  expect(pedidos.status()).toBe(200);
  expect([200, 404]).toContain(caja.status());
  await expect(
    page.getByText(caja.status() === 200 ? "Abierta" : "Sin caja abierta", {
      exact: true,
    }),
  ).toBeVisible();
});
