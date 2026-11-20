import type { Page } from "@playwright/test";
export async function fillLogin(
  page: Page,
  login: { username: string; password: string },
) {
  await page.getByLabel("Usuario", { exact: true }).fill(login.username);
  await page.getByLabel("Contraseña", { exact: true }).fill(login.password);
  await page
    .getByRole("button", { name: "Iniciar sesión", exact: true })
    .click();
}
