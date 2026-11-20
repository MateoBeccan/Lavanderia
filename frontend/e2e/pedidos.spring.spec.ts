import { randomInt, randomUUID } from "node:crypto";
import { expect, test } from "@playwright/test";
import type { Locator, Page } from "@playwright/test";
import { fillLogin } from "./login";

test.use({ trace: "off" });

async function seleccionar(campo: Locator, id: number) {
  const grupo = campo.locator("..");
  for (;;) {
    const siguiente = grupo.getByRole("button", { name: "Siguiente" });
    await expect(campo).toBeEnabled();
    if (await campo.locator(`option[value="${id}"]`).count()) {
      await campo.selectOption(String(id));
      return;
    }
    await expect(siguiente).toBeEnabled();
    await siguiente.click();
  }
}

async function guardar(page: Page, metodo: string, path: string) {
  const respuesta = page.waitForResponse(
    (r) =>
      r.request().method() === metodo && new URL(r.url()).pathname === path,
  );
  await page.getByRole("button", { name: "Revisar pedido" }).click();
  await page
    .getByRole("alertdialog")
    .getByRole("button", { name: "Guardar pedido" })
    .click();
  return respuesta;
}

for (const rol of ["ADMINISTRADOR", "EMPLEADO"] as const) {
  test(`Pedidos con Spring Boot aislado: ${rol}`, async ({ page, request }) => {
    test.skip(
      process.env.E2E_PEDIDOS_ISOLATED !== "1",
      "Requiere Spring y PostgreSQL aislados.",
    );
    const { E2E_USERNAME: username, E2E_PASSWORD: password } = process.env;
    if (!username || !password)
      throw new Error("Configurá el administrador de la base aislada.");
    const sufijo = randomUUID().slice(0, 12);
    const admin = { username, password };
    async function token(cuenta: typeof admin) {
      const login = await request.post("/api/auth/login", { data: cuenta });
      expect(login.status()).toBe(200);
      return "Bearer " + (await login.json()).accessToken;
    }
    const adminHeaders = { Authorization: await token(admin) };
    async function crear(path: string, data: object) {
      const respuesta = await request.post("/api" + path, {
        headers: adminHeaders,
        data,
      });
      expect(respuesta.status()).toBe(201);
      return (await respuesta.json()).id as number;
    }

    const cliente = await crear("/clientes", {
      nombre: "Cliente",
      apellido: sufijo,
      dni: String(randomInt(10000000, 99999999)),
    });
    const prenda = await crear("/prendas", { nombre: "Prenda " + sufijo });
    const servicio = await crear("/servicios", {
      nombre: "Servicio " + sufijo,
      modalidadCobro: "KILO",
      precio: 20.8,
    });
    const operador =
      rol === "EMPLEADO"
        ? { username: "empleado." + sufijo, password: randomUUID() + "A!a0" }
        : admin;
    if (rol === "EMPLEADO")
      await crear("/usuarios", {
        ...operador,
        nombre: "Empleado " + sufijo,
        email: sufijo + "@example.test",
        rol,
      });
    const operadorHeaders = { Authorization: await token(operador) };
    const accesoAjeno = await request.get("/api/pedidos", {
      headers: { Authorization: "Bearer invalido" },
    });
    expect(accesoAjeno.status()).toBe(401);
    const permisoAdmin = await request.get("/api/usuarios", {
      headers: operadorHeaders,
    });
    expect(permisoAdmin.status()).toBe(rol === "EMPLEADO" ? 403 : 200);

    await page.goto("/pedidos/nuevo");
    await fillLogin(page, operador);
    await seleccionar(page.getByLabel("Cliente", { exact: true }), cliente);
    await page.getByLabel("Entrega estimada").fill("2099-12-30");
    await seleccionar(page.getByLabel("Prenda", { exact: true }), prenda);
    await seleccionar(page.getByLabel("Servicio", { exact: true }), servicio);
    await page.getByLabel("Cantidad (kg)").fill("1,25");
    await page.getByLabel("Observaciones (opcional)").fill("Primer ingreso");
    const alta = await guardar(page, "POST", "/api/pedidos");
    expect(alta.status()).toBe(201);
    const creado = await alta.json();
    expect(creado).toMatchObject({
      total: 26,
      clienteId: cliente,
      estadoPedido: { codigo: "PENDIENTE" },
      detalles: [
        {
          prendaId: prenda,
          servicioId: servicio,
          cantidad: 1.25,
          precioUnitario: 20.8,
          subtotal: 26,
        },
      ],
    });
    await expect(
      page.getByRole("heading", { name: `Pedido #${creado.id}` }),
    ).toBeVisible();
    await expect(
      page.getByRole("region", { name: "Historial de estados" }),
    ).toContainText("Pedido registrado");
    await expect(
      page.getByRole("region", { name: "Información de pago" }),
    ).toContainText("26,00");

    const cambioPrecio = await request.put(`/api/servicios/${servicio}`, {
      headers: adminHeaders,
      data: {
        nombre: "Servicio " + sufijo,
        modalidadCobro: "KILO",
        precio: 50,
      },
    });
    expect(cambioPrecio.status()).toBe(200);
    await page.getByRole("link", { name: "Editar pedido" }).click();
    await expect(page.getByLabel("Cantidad (kg)")).toHaveValue("1.25");
    await page.getByLabel("Cantidad (kg)").fill("2");
    const path = `/api/pedidos/${creado.id}`;
    const edicion = await guardar(page, "PUT", path);
    expect(edicion.status()).toBe(200);
    expect(await edicion.json()).toMatchObject({
      total: 41.6,
      detalles: [
        {
          id: creado.detalles[0].id,
          precioUnitario: 20.8,
          subtotal: 41.6,
          cantidad: 2,
        },
      ],
    });

    const conflicto = await request.patch(path + "/estado", {
      headers: operadorHeaders,
      data: { codigoEstado: "ENTREGADO" },
    });
    expect(conflicto.status()).toBe(409);
    const cambios =
      rol === "ADMINISTRADOR"
        ? ["EN_PROCESO", "LISTO", "ENTREGADO"]
        : ["CANCELADO"];
    for (const codigo of cambios) {
      await page.getByRole("button", { name: "Cambiar estado" }).click();
      const dialog = page.getByRole("dialog");
      await dialog.getByLabel("Nuevo estado").selectOption(codigo);
      await dialog
        .getByLabel(/Observación|Motivo de cancelación/)
        .fill("Seguimiento " + codigo);
      await dialog
        .getByRole("button", { name: /Confirmar (cambio|cancelación)/ })
        .click();
      await expect(dialog).toBeHidden();
      await expect(
        page.getByRole("region", { name: "Historial de estados" }),
      ).toContainText("Seguimiento " + codigo);
    }
    const historial = await request.get(path + "/historial", {
      headers: operadorHeaders,
    });
    expect(historial.status()).toBe(200);
    expect(
      (await historial.json()).map(
        (e: { estadoNuevoCodigo: string }) => e.estadoNuevoCodigo,
      ),
    ).toEqual(["PENDIENTE", ...cambios]);
    await expect(page.getByRole("link", { name: "Editar pedido" })).toHaveCount(
      0,
    );
    await expect(
      page.getByRole("button", { name: "Cambiar estado" }),
    ).toHaveCount(0);

    await page.getByRole("link", { name: "Volver a pedidos" }).click();
    await page.getByLabel("ID del cliente").fill(String(cliente));
    await page
      .getByLabel("Estado", { exact: true })
      .selectOption(cambios.at(-1)!);
    await page.getByRole("button", { name: "Aplicar filtros" }).click();
    await expect(
      page.getByRole("link", { name: `Ver pedido ${creado.id}` }),
    ).toBeVisible();
  });
}
