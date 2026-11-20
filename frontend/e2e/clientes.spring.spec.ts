import { randomInt } from "node:crypto";
import { expect, test } from "@playwright/test";
import { fillLogin } from "./login";

test.use({ trace: "off" });

test("Clientes con Spring Boot aislado: alta, edición, DNI duplicado y baja", async ({
  page,
}) => {
  test.skip(
    process.env.E2E_CLIENTES_ISOLATED !== "1",
    "Requiere la instancia aislada documentada en README.md.",
  );
  const username = process.env.E2E_USERNAME;
  const password = process.env.E2E_PASSWORD;
  if (!username || !password)
    throw new Error("Faltan las credenciales del administrador de prueba.");

  const dni = String(randomInt(0, 100_000_000)).padStart(8, "0");
  const getResponse = (method: string, path: string) =>
    page.waitForResponse(
      (response) =>
        response.request().method() === method &&
        new URL(response.url()).pathname === path,
    );

  await page.goto("/clientes?sort=id%2Cdesc");
  await fillLogin(page, { username, password });
  await page.getByRole("link", { name: "Nuevo cliente" }).click();
  await page.getByLabel("Nombre", { exact: true }).fill("Cliente E2E");
  await page.getByLabel("Apellido").fill(dni);
  await page.getByLabel("DNI").fill(dni);
  await page
    .getByLabel("Correo electrónico (opcional)")
    .fill("e2e@example.test");
  const alta = getResponse("POST", "/api/clientes");
  await page.getByRole("button", { name: "Guardar cliente" }).click();
  const creado = await alta;
  expect(creado.status()).toBe(201);
  const { id } = await creado.json();
  const detalle = "/api/clientes/" + id;
  await expect(
    page.getByRole("heading", { name: "Cliente E2E " + dni }),
  ).toBeVisible();
  await expect(page.getByText("Cliente registrado.")).toBeVisible();

  await page.getByRole("link", { name: "Editar cliente" }).click();
  await expect(page.getByLabel("DNI")).toHaveValue(dni);
  await expect(page.getByLabel("Correo electrónico (opcional)")).toHaveValue(
    "e2e@example.test",
  );
  await page.getByLabel("Nombre", { exact: true }).fill("Actualizado E2E");
  page.once("dialog", (dialog) => dialog.dismiss());
  await page.getByRole("link", { name: "Cancelar" }).click();
  await expect(page).toHaveURL(/\/editar/);
  await expect(page.getByLabel("Nombre", { exact: true })).toHaveValue(
    "Actualizado E2E",
  );
  const edicion = getResponse("PUT", detalle);
  await page.getByRole("button", { name: "Guardar cliente" }).click();
  expect((await edicion).status()).toBe(200);
  await expect(
    page.getByRole("heading", { name: "Actualizado E2E " + dni }),
  ).toBeVisible();

  await page.getByRole("link", { name: "Volver a clientes" }).click();
  await expect(
    page.getByRole("link", { name: dni + ", Actualizado E2E" }),
  ).toBeVisible();
  await page.getByRole("link", { name: "Nuevo cliente" }).click();
  await page.getByLabel("Nombre", { exact: true }).fill("Duplicado E2E");
  await page.getByLabel("Apellido").fill("Prueba");
  await page.getByLabel("DNI").fill(dni);
  const conflicto = getResponse("POST", "/api/clientes");
  await page.getByRole("button", { name: "Guardar cliente" }).click();
  expect((await conflicto).status()).toBe(409);
  await expect(page.getByLabel("DNI")).toHaveAccessibleDescription(
    "Ya existe un cliente con ese DNI.",
  );
  page.once("dialog", (dialog) => dialog.accept());
  await page.getByRole("link", { name: "Cancelar" }).click();

  const desactivar = page.getByRole("button", {
    name: "Desactivar a Actualizado E2E " + dni,
  });
  await desactivar.click();
  await page
    .getByRole("alertdialog")
    .getByRole("button", { name: "Cancelar" })
    .click();
  await expect(desactivar).toBeVisible();
  await desactivar.click();
  const baja = getResponse("DELETE", detalle);
  await page
    .getByRole("alertdialog")
    .getByRole("button", { name: "Desactivar cliente" })
    .click();
  expect((await baja).status()).toBe(204);
  await expect(page.getByText("Cliente desactivado.")).toBeVisible();
  await expect(desactivar).toHaveCount(0);

  await page.goto("/clientes/" + id);
  const ausente = getResponse("GET", detalle);
  await fillLogin(page, { username, password });
  expect((await ausente).status()).toBe(404);
  await expect(
    page.getByRole("heading", { name: "Cliente no encontrado" }),
  ).toBeVisible();
});
