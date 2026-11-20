import { randomInt, randomUUID } from "node:crypto";
import { expect, test } from "@playwright/test";
import { fillLogin } from "./login";

test.use({ trace: "off" });

const spring = "http://127.0.0.1:18096/api";

function cuitValido() {
  const pesos = [5, 4, 3, 2, 7, 6, 5, 4, 3, 2];
  let base: string;
  let verificador: number;
  do {
    base = "30" + String(randomInt(100_000_000)).padStart(8, "0");
    const suma = pesos.reduce(
      (total, peso, i) => total + peso * Number(base[i]),
      0,
    );
    verificador = (11 - (suma % 11)) % 11;
  } while (verificador === 10);
  return base + verificador;
}

test("Proveedores con Spring Boot aislado: ciclo de vida, filtros y permisos reales", async ({
  page,
  browser,
  request,
}) => {
  test.skip(
    process.env.E2E_PROVEEDORES_ISOLATED !== "1",
    "Requiere Spring Boot y PostgreSQL aislados según README.md.",
  );
  const username = process.env.E2E_USERNAME;
  const password = process.env.E2E_PASSWORD;
  if (!username || !password)
    throw new Error("Faltan las credenciales del administrador aislado.");
  const sufijo = randomUUID().slice(0, 8);
  const grupo = "E2E Proveedores " + sufijo;
  const altaNombre = grupo + " Alta";
  const editadoNombre = grupo + " Editado";
  const cuit = cuitValido();
  const telefono = "011-4000";

  async function token(credenciales: { username: string; password: string }) {
    const response = await request.post(spring + "/auth/login", {
      data: credenciales,
    });
    expect(response.status()).toBe(200);
    return { Authorization: "Bearer " + (await response.json()).accessToken };
  }

  const admin = await token({ username, password });
  for (let i = 0; i < 10; i++) {
    const response = await request.post(spring + "/proveedores", {
      headers: admin,
      data: {
        nombre: `${grupo} referencia ${i}`,
        cuit: cuitValido(),
        telefono,
        rubro: "Limpieza",
      },
    });
    expect(response.status()).toBe(201);
  }
  const empleado = {
    username: "proveedores." + sufijo,
    password: "ProveedorE2E-Empleado-4.1",
  };
  const usuario = await request.post(spring + "/usuarios", {
    headers: admin,
    data: {
      ...empleado,
      nombre: "Empleado E2E",
      email: `proveedores.${sufijo}@example.test`,
      rol: "EMPLEADO",
    },
  });
  expect(usuario.status()).toBe(201);

  const respuesta = (method: string, path: string) =>
    page.waitForResponse(
      (response) =>
        response.request().method() === method &&
        new URL(response.url()).pathname === "/api" + path,
    );
  const lista = page.getByRole("table", { name: "Proveedores activos" });
  const nuevoProveedor = page.getByRole("heading", { name: "Nuevo proveedor" });
  const nombre = page.getByLabel("Nombre", { exact: true });
  const guardar = page.getByRole("button", { name: "Guardar proveedor" });
  await page.goto("/proveedores?size=10&nombre=" + encodeURIComponent(grupo));
  await fillLogin(page, { username, password });
  // La vista previa debe leer un registro que acaba de crearse en Spring aislado.
  await expect(
    lista.getByRole("link", { name: `${grupo} referencia 0`, exact: true }),
  ).toBeVisible();
  await page.getByRole("link", { name: "Nuevo proveedor" }).click();
  await expect(nuevoProveedor).toBeVisible();
  await nombre.fill(altaNombre);
  await page
    .getByLabel("CUIT", { exact: true })
    .fill(`${cuit.slice(0, 2)}-${cuit.slice(2, 10)}-${cuit[10]}`);
  await page.getByLabel("Teléfono", { exact: true }).fill(telefono);
  await page.getByLabel("Rubro (opcional)").fill("Limpieza");
  const alta = respuesta("POST", "/proveedores");
  await guardar.click();
  const creado = await alta;
  expect(creado.status()).toBe(201);
  const { id } = await creado.json();
  const recurso = `/proveedores/${id}`;
  await expect(page.getByRole("heading", { name: altaNombre })).toBeVisible();
  await expect(page.getByText(cuit, { exact: true })).toBeVisible();

  await page.getByRole("link", { name: "Editar proveedor" }).click();
  await expect(
    page.getByRole("heading", { name: "Editar proveedor" }),
  ).toBeVisible();
  await expect(nombre).toHaveValue(altaNombre);
  await expect(page.getByLabel("CUIT", { exact: true })).toHaveValue(cuit);
  await nombre.fill(editadoNombre);
  page.once("dialog", (dialog) => dialog.dismiss());
  await page.getByRole("link", { name: "Cancelar" }).click();
  await expect(nombre).toHaveValue(editadoNombre);
  const edicion = respuesta("PUT", recurso);
  await guardar.click();
  expect((await edicion).status()).toBe(200);
  await expect(
    page.getByRole("heading", { name: editadoNombre }),
  ).toBeVisible();

  const empleadoHeaders = await token(empleado);
  for (const [method, path] of [
    ["POST", "/proveedores"],
    ["PUT", recurso],
    ["DELETE", recurso],
    ["PATCH", recurso + "/activar"],
  ]) {
    const response = await request.fetch(spring + path, {
      method,
      headers: empleadoHeaders,
    });
    expect(response.status()).toBe(403);
  }
  const empleadoContext = await browser.newContext();
  try {
    const empleadoPage = await empleadoContext.newPage();
    await empleadoPage.goto(recurso);
    await fillLogin(empleadoPage, empleado);
    await expect(
      empleadoPage.getByRole("heading", { name: editadoNombre }),
    ).toBeVisible();
    await expect(
      empleadoPage.getByRole("link", { name: "Editar proveedor" }),
    ).toHaveCount(0);
    await empleadoPage
      .getByRole("link", { name: "Volver a proveedores" })
      .click();
    await expect(
      empleadoPage.getByRole("table", { name: "Proveedores activos" }),
    ).toBeVisible();
    await expect(
      empleadoPage.getByRole("link", { name: "Nuevo proveedor" }),
    ).toHaveCount(0);
  } finally {
    await empleadoContext.close();
  }

  await page.getByRole("link", { name: "Volver a proveedores" }).click();
  await expect(page.getByText("11 proveedores activos")).toBeVisible();
  await page.getByRole("button", { name: "Siguiente" }).click();
  await expect(page.getByText("Página 2 de 2")).toBeVisible();
  const ficha = lista.getByRole("link", { name: editadoNombre, exact: true });
  await expect(ficha).toBeVisible();
  await page.getByLabel("CUIT", { exact: true }).fill(cuit);
  await page.getByLabel("Teléfono", { exact: true }).fill("4000");
  await page.getByLabel("Rubro", { exact: true }).fill("limpieza");
  await page.getByRole("button", { name: "Aplicar filtros" }).click();
  await expect(
    page.getByText("1 proveedor activo", { exact: true }),
  ).toBeVisible();
  await expect(ficha).toBeVisible();

  const desactivar = page.getByRole("button", {
    name: `Desactivar a ${editadoNombre}`,
  });
  await desactivar.click();
  const dialog = page.getByRole("alertdialog");
  await dialog.getByRole("button", { name: "Cancelar" }).click();
  await expect(desactivar).toBeFocused();
  await desactivar.click();
  const baja = respuesta("DELETE", recurso);
  await dialog.getByRole("button", { name: "Desactivar proveedor" }).click();
  expect((await baja).status()).toBe(204);
  await expect(
    page.getByRole("heading", { name: "Sin proveedores activos" }),
  ).toBeVisible();
  expect(
    (await request.get(spring + recurso, { headers: admin })).status(),
  ).toBe(404);

  await page.getByRole("link", { name: "Nuevo proveedor" }).click();
  await expect(nuevoProveedor).toBeVisible();
  await nombre.fill("Alta duplicada");
  await page.getByLabel("CUIT", { exact: true }).fill(cuit);
  await page.getByLabel("Teléfono", { exact: true }).fill(telefono);
  const duplicado = respuesta("POST", "/proveedores");
  await guardar.click();
  expect((await duplicado).status()).toBe(409);
  await expect(
    page.getByLabel("CUIT", { exact: true }),
  ).toHaveAccessibleDescription("Ya existe un proveedor con ese CUIT.");
  page.once("dialog", (dialog) => dialog.accept());
  await page.getByRole("link", { name: "Cancelar" }).click();

  await page.getByRole("link", { name: "Reactivar proveedor" }).click();
  const inputId = page.getByLabel("ID del proveedor");
  await page.getByRole("button", { name: "Revisar reactivación" }).click();
  await expect(inputId).toHaveAccessibleDescription(
    "Ingresá un ID de proveedor válido.",
  );
  await inputId.fill(String(id));
  await inputId.press("Enter");
  const reactivacion = respuesta("PATCH", recurso + "/activar");
  await dialog.getByRole("button", { name: "Reactivar proveedor" }).click();
  expect((await reactivacion).status()).toBe(200);
  await expect(
    page.getByRole("heading", { name: editadoNombre }),
  ).toBeVisible();
  await expect(page.getByText("Activo", { exact: true })).toBeVisible();
  await page.getByRole("link", { name: "Volver a proveedores" }).click();
  await expect(ficha).toBeVisible();
});
