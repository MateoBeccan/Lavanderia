import { randomInt, randomUUID } from "node:crypto";
import { test, expect } from "@playwright/test";
import type { Page } from "@playwright/test";
import { fillLogin } from "./login";

const spring = "http://127.0.0.1:18097/api";
test.use({ trace: "off" });

function cuitSintetico() {
  const pesos = [5, 4, 3, 2, 7, 6, 5, 4, 3, 2];
  while (true) {
    const base = "30" + String(randomInt(100_000_000)).padStart(8, "0");
    const suma = pesos.reduce(
      (total, peso, i) => total + peso * Number(base[i]),
      0,
    );
    const digito = (11 - (suma % 11)) % 11;
    if (digito < 10) return base + digito;
  }
}

function esperar(page: Page, method: string, path: string) {
  return page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === "/api" + path &&
      response.request().method() === method,
  );
}

test("Insumos con Spring Boot aislado: catálogo, asociaciones y seguridad", async ({
  page,
  browser,
  request,
}) => {
  test.skip(
    process.env.E2E_INSUMOS_ISOLATED !== "1",
    "Requiere Spring Boot y PostgreSQL aislados según README.md.",
  );
  test.setTimeout(120_000);
  const username = process.env.E2E_USERNAME;
  const password = process.env.E2E_PASSWORD;
  if (!username || !password)
    throw new Error("Faltan las credenciales del administrador aislado.");
  const marca = randomUUID().slice(0, 8);
  const categoriaNombre = `Higiene ${marca}`;
  const categoriaEditada = `Lavado ${marca}`;
  const insumoNombre = `Jabón ${marca}`;
  const insumoEditado = `Jabón líquido ${marca}`;
  const proveedorNombre = `Suministros ${marca}`;

  async function autenticar(credenciales: {
    username: string;
    password: string;
  }) {
    const response = await request.post(spring + "/auth/login", {
      data: credenciales,
    });
    expect(response.status()).toBe(200);
    return { Authorization: "Bearer " + (await response.json()).accessToken };
  }
  const admin = await autenticar({ username, password });
  async function api(
    method: string,
    path: string,
    status: number,
    data?: object,
    headers = admin,
  ) {
    const response = await request.fetch(spring + path, {
      method,
      data,
      headers,
    });
    expect(response.status(), `${method} ${path}`).toBe(status);
    return response;
  }

  const proveedor = await api("POST", "/proveedores", 201, {
    nombre: proveedorNombre,
    cuit: cuitSintetico(),
    telefono: "011-1000",
    rubro: "Limpieza",
  });
  const rutaProveedor = `/proveedores/${(await proveedor.json()).id}`;
  const vinculos = rutaProveedor + "/insumos";
  await page.goto(rutaProveedor);
  await fillLogin(page, { username, password });
  // La lectura del marcador creado en Spring aislado confirma el destino del proxy.
  await expect(
    page.getByRole("heading", { name: proveedorNombre }),
  ).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "Sin insumos suministrados" }),
  ).toBeVisible();
  await page.goto("/insumos/categorias/nuevo");
  await fillLogin(page, { username, password });

  const nombre = page.getByLabel("Nombre", { exact: true });
  const guardarCategoria = page.getByRole("button", {
    name: "Guardar categoría",
  });
  const dialog = page.getByRole("alertdialog");
  await nombre.fill("  " + categoriaNombre + "  ");
  const altaCategoria = esperar(page, "POST", "/categorias-insumo");
  await guardarCategoria.click();
  const alta = await altaCategoria;
  expect(alta.status()).toBe(201);
  const { id: categoriaId } = await alta.json();
  const rutaCategoria = `/categorias-insumo/${categoriaId}`;
  await page.getByRole("link", { name: "Editar categoría" }).click();
  await expect(nombre).toHaveValue(categoriaNombre);
  await nombre.fill(categoriaEditada);
  await guardarCategoria.click();
  await expect(
    page.getByRole("heading", { name: categoriaEditada }),
  ).toBeVisible();

  const otraCategoria = await api("POST", "/categorias-insumo", 201, {
    nombre: `Otras ${marca}`,
  });
  const otroId = (await otraCategoria.json()).id;
  for (let i = 0; i < 10; i++) {
    await api("POST", "/insumos", 201, {
      nombre: `Insumo ${marca} ${i}`,
      categoriaId: otroId,
    });
  }
  await page.getByRole("button", { name: "Panel inicial" }).click();
  await page
    .getByRole("dialog")
    .getByRole("link", { name: "Insumos", exact: true })
    .click();
  await page.getByRole("link", { name: "Nuevo insumo" }).click();
  const guardarInsumo = page.getByRole("button", { name: "Guardar insumo" });
  const selectorCategoria = page.getByLabel("Categoría", { exact: true });
  await guardarInsumo.click();
  await expect(selectorCategoria).toHaveAccessibleDescription(
    "Seleccioná una categoría activa.",
  );
  await nombre.fill(insumoNombre);
  await selectorCategoria.selectOption(String(categoriaId));
  const altaInsumo = esperar(page, "POST", "/insumos");
  await guardarInsumo.click();
  const altaI = await altaInsumo;
  expect(altaI.status()).toBe(201);
  const { id: insumoId } = await altaI.json();
  const rutaInsumo = `/insumos/${insumoId}`;
  await page.getByRole("link", { name: "Editar insumo" }).click();
  await expect(selectorCategoria).toHaveValue(String(categoriaId));
  await nombre.fill(insumoEditado);
  page.once("dialog", (dialog) => dialog.dismiss());
  await page.getByRole("link", { name: "Cancelar" }).click();
  await expect(nombre).toHaveValue(insumoEditado);
  const edicion = esperar(page, "PUT", rutaInsumo);
  await guardarInsumo.click();
  expect((await edicion).status()).toBe(200);
  await expect(
    page.getByRole("heading", { name: insumoEditado }),
  ).toBeVisible();
  await api("POST", "/insumos", 409, {
    nombre: insumoEditado.replace("Jabón", "JABóN"),
    categoriaId: otroId,
  });

  await page.getByRole("link", { name: categoriaEditada }).click();
  await page.getByRole("button", { name: "Desactivar categoría" }).click();
  await dialog.getByRole("button", { name: "Desactivar categoría" }).click();
  await expect(page.getByRole("alert")).toHaveText(
    "La categoría tiene insumos activos asociados.",
  );

  await page.goto(rutaProveedor);
  await fillLogin(page, { username, password });
  await page.getByRole("button", { name: "Asociar insumo" }).click();
  const selector = page.getByLabel("Insumo para asociar");
  const paginas = page.getByRole("navigation", {
    name: "Páginas de insumos disponibles",
  });
  while (!(await selector.locator(`option[value="${insumoId}"]`).count())) {
    const siguiente = paginas.getByRole("button", { name: "Siguiente" });
    await expect(siguiente).toBeEnabled();
    const cargada = page.waitForResponse(
      (response) =>
        new URL(response.url()).pathname === "/api/insumos" && response.ok(),
    );
    await siguiente.click();
    await cargada;
    await expect(selector).toBeEnabled();
  }
  await selector.selectOption(String(insumoId));
  await paginas.getByRole("button", { name: "Anterior" }).click();
  await expect(selector).toHaveValue(String(insumoId));
  const confirmar = page.getByRole("button", { name: "Confirmar asociación" });
  const asociacion = esperar(page, "POST", vinculos);
  await confirmar.click();
  expect((await asociacion).status()).toBe(201);
  const tabla = page.getByRole("table", { name: "Insumos suministrados" });
  await expect(tabla.getByRole("link", { name: insumoEditado })).toBeVisible();
  await api("POST", vinculos, 409, { insumoId });
  await api("GET", rutaInsumo + "/proveedores", 200);

  const empleado = {
    username: `insumos.${marca}`,
    password: "Insumos-E2E-Empleado-4.2",
  };
  await api("POST", "/usuarios", 201, {
    ...empleado,
    nombre: "Empleado E2E",
    email: `${empleado.username}@example.test`,
    rol: "EMPLEADO",
  });
  const headers = await autenticar(empleado);
  for (const [method, path] of [
    ["POST", "/categorias-insumo"],
    ["PUT", rutaCategoria],
    ["DELETE", rutaCategoria],
    ["PATCH", rutaCategoria + "/activar"],
    ["POST", "/insumos"],
    ["PUT", rutaInsumo],
    ["DELETE", rutaInsumo],
    ["PATCH", rutaInsumo + "/activar"],
    ["POST", vinculos],
    ["DELETE", vinculos + "/" + insumoId],
  ])
    await api(method, path, 403, undefined, headers);
  const empleadoPage = await browser.newPage();
  try {
    await empleadoPage.goto(rutaProveedor);
    await fillLogin(empleadoPage, empleado);
    await expect(
      empleadoPage.getByRole("table", { name: "Insumos suministrados" }),
    ).toBeVisible();
    await expect(
      empleadoPage.getByRole("button", { name: /Asociar insumo|Desasociar/ }),
    ).toHaveCount(0);
  } finally {
    await empleadoPage.close();
  }

  await tabla.getByRole("link", { name: insumoEditado }).click();
  await page.getByRole("button", { name: "Desactivar insumo" }).click();
  const baja = esperar(page, "DELETE", rutaInsumo);
  await dialog.getByRole("button", { name: "Desactivar insumo" }).click();
  expect((await baja).status()).toBe(204);
  await expect(
    page.getByRole("heading", { name: "Insumos", exact: true }),
  ).toBeVisible();
  await api("GET", rutaInsumo, 404);
  await api("POST", vinculos, 409, { insumoId });
  expect((await (await api("GET", vinculos, 200)).json()).content).toEqual([]);
  await api("DELETE", rutaCategoria, 204);
  await api("GET", rutaCategoria, 404);
  await api("POST", "/categorias-insumo", 409, {
    nombre: categoriaEditada.toUpperCase(),
  });
  await api("POST", "/insumos", 404, {
    nombre: "Categoría inactiva",
    categoriaId,
  });

  async function reactivar(
    tipo: string,
    id: number,
    path: string,
    status: number,
  ) {
    await page.getByLabel("ID del registro").fill(String(id));
    await page.getByRole("button", { name: "Reactivar " + tipo }).click();
    const response = esperar(page, "PATCH", path + "/activar");
    await dialog.getByRole("button", { name: "Reactivar " + tipo }).click();
    expect((await response).status()).toBe(status);
  }
  await page.getByRole("link", { name: "Reactivar insumo" }).click();
  await reactivar("insumo", insumoId, rutaInsumo, 409);
  await expect(page.getByRole("alert")).toHaveText(
    "La categoría de insumo está inactiva.",
  );
  await page.goto("/insumos/categorias/reactivar");
  await fillLogin(page, { username, password });
  await reactivar("categoría", categoriaId, rutaCategoria, 200);
  await expect(
    page.getByRole("heading", { name: categoriaEditada }),
  ).toBeVisible();
  await page.goto("/insumos/reactivar");
  await fillLogin(page, { username, password });
  await reactivar("insumo", insumoId, rutaInsumo, 200);
  await expect(page.getByText("Activo", { exact: true })).toBeVisible();
  await page.goto(rutaProveedor);
  await fillLogin(page, { username, password });
  await expect(tabla.getByRole("link", { name: insumoEditado })).toBeVisible();
  await page
    .getByRole("button", { name: "Desasociar " + insumoEditado })
    .click();
  const desvinculacion = esperar(page, "DELETE", vinculos + "/" + insumoId);
  await dialog.getByRole("button", { name: "Desasociar insumo" }).click();
  expect((await desvinculacion).status()).toBe(204);
  await expect(
    page.getByRole("heading", { name: "Sin insumos suministrados" }),
  ).toBeVisible();
  await api("DELETE", rutaProveedor, 204);
  await api("POST", vinculos, 409, { insumoId });
  await api("DELETE", vinculos + "/" + insumoId, 204);
});
