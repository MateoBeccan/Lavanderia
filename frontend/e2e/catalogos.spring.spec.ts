import { randomUUID } from "node:crypto";
import { expect, test } from "@playwright/test";
import { fillLogin } from "./login";

test.use({ trace: "off" });

for (const singular of ["servicio", "prenda"] as const) {
  test(`Catálogos con Spring Boot aislado: ${singular}`, async ({
    page,
    request,
  }) => {
    test.skip(
      process.env.E2E_CATALOGOS_ISOLATED !== "1",
      "Requiere PostgreSQL y Spring Boot aislados.",
    );
    const username = process.env.E2E_USERNAME;
    const password = process.env.E2E_PASSWORD;
    if (!username || !password)
      throw new Error("Faltan las credenciales del administrador de prueba.");
    const path = "/" + singular + "s";
    const api = "/api" + path;
    const sufijo = randomUUID().slice(0, 12);
    const nombre = "Catálogo E2E " + sufijo;
    const editado = "Actualizado E2E " + sufijo;
    const campos =
      singular === "servicio"
        ? { precio: 25.75, modalidadCobro: "PRENDA" }
        : {};
    const respuesta = (method: string, pathname: string) =>
      page.waitForResponse(
        (r) =>
          r.request().method() === method &&
          new URL(r.url()).pathname === pathname,
      );
    await page.goto(path + "?sort=id%2Cdesc");
    await fillLogin(page, { username, password });
    const altaLink = page.getByRole("link", {
      name: singular === "prenda" ? "Nueva prenda" : "Nuevo servicio",
    });
    await altaLink.click();
    const guardar = page.getByRole("button", { name: `Guardar ${singular}` });
    await guardar.click();
    await expect(page.getByLabel("Nombre")).toHaveAccessibleDescription(
      "Ingresá un nombre.",
    );
    await page.getByLabel("Nombre").fill(nombre);
    if (singular === "servicio") {
      await page.getByLabel("Precio").fill("25,75");
      await page.getByLabel("Modalidad de cobro").selectOption("PRENDA");
    }
    const alta = respuesta("POST", api);
    await guardar.click();
    const creado = await alta;
    expect(creado.status()).toBe(201);
    const registro = await creado.json();
    expect(registro).toMatchObject({ nombre, activo: true, ...campos });
    const detalle = api + "/" + registro.id;
    await expect(page.getByRole("heading", { name: nombre })).toBeVisible();
    await page.getByRole("link", { name: `Editar ${singular}` }).click();
    await expect(page.getByLabel("Nombre")).toHaveValue(nombre);
    await page.getByLabel("Nombre").fill(editado);
    const edicion = respuesta("PUT", detalle);
    await guardar.click();
    expect((await edicion).status()).toBe(200);
    await page.getByRole("link", { name: "Volver al listado" }).click();
    const desactivar = page.getByRole("button", {
      name: `Desactivar ${editado}`,
    });
    await desactivar.click();
    const dialogo = page.getByRole("alertdialog");
    await dialogo.getByRole("button", { name: "Cancelar" }).click();
    await expect(desactivar).toBeVisible();
    await desactivar.click();
    const baja = respuesta("DELETE", detalle);
    await dialogo
      .getByRole("button", { name: `Desactivar ${singular}` })
      .click();
    expect((await baja).status()).toBe(204);
    await expect(desactivar).toHaveCount(0);

    await altaLink.click();
    await page.getByLabel("Nombre").fill(editado.toUpperCase());
    if (singular === "servicio") {
      await page.getByLabel("Precio").fill("25,75");
      await page.getByLabel("Modalidad de cobro").selectOption("PRENDA");
    }
    const conflicto = respuesta("POST", api);
    await guardar.click();
    expect((await conflicto).status()).toBe(409);
    await expect(page.getByLabel("Nombre")).toHaveAccessibleDescription(
      /Ya existe .* con ese nombre/,
    );
    page.once("dialog", (dialog) => dialog.accept());
    await page.getByRole("link", { name: "Cancelar" }).click();
    await page.getByRole("link", { name: `Reactivar ${singular}` }).click();
    await page.getByLabel("ID del registro").fill(String(registro.id));
    await page.getByRole("button", { name: `Reactivar ${singular}` }).click();
    const activacion = respuesta("PATCH", detalle + "/activar");
    await dialogo
      .getByRole("button", { name: `Reactivar ${singular}` })
      .click();
    expect((await activacion).status()).toBe(200);
    await expect(page.getByRole("heading", { name: editado })).toBeVisible();

    const admin = await request.post("/api/auth/login", {
      data: { username, password },
    });
    expect(admin.status()).toBe(200);
    const adminHeaders = {
      Authorization: "Bearer " + (await admin.json()).accessToken,
    };
    const empleado = {
      username: "emp." + sufijo,
      password: "Empleado-local-" + sufijo,
    };
    const altaEmpleado = await request.post("/api/usuarios", {
      headers: adminHeaders,
      data: {
        ...empleado,
        nombre: "Empleado E2E",
        email: empleado.username + "@example.test",
        rol: "EMPLEADO",
      },
    });
    expect(altaEmpleado.status()).toBe(201);
    const login = await request.post("/api/auth/login", { data: empleado });
    expect(login.status()).toBe(200);
    const headers = {
      Authorization: "Bearer " + (await login.json()).accessToken,
    };
    expect((await request.get(api, { headers })).status()).toBe(200);
    expect((await request.get(detalle, { headers })).status()).toBe(200);
    expect((await request.get(api)).status()).toBe(401);
    const data = { nombre: "Sin permiso " + sufijo, ...campos };
    expect((await request.post(api, { headers, data })).status()).toBe(403);
    expect((await request.put(detalle, { headers, data })).status()).toBe(403);
    expect((await request.delete(detalle, { headers })).status()).toBe(403);
    expect(
      (await request.patch(detalle + "/activar", { headers })).status(),
    ).toBe(403);
    expect(
      (await request.delete(detalle, { headers: adminHeaders })).status(),
    ).toBe(204);
    expect((await request.get(detalle, { headers })).status()).toBe(404);
    expect(
      (await request.put(detalle, { headers: adminHeaders, data })).status(),
    ).toBe(404);
    expect(
      (
        await request.patch(api + "/9223372036854775807/activar", {
          headers: adminHeaders,
        })
      ).status(),
    ).toBe(404);
  });
}
