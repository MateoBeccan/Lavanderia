import { randomInt, randomUUID } from "node:crypto";
import { expect, test } from "@playwright/test";
import { fillLogin } from "./login";

const spring = "http://127.0.0.1:18098/api";
test.use({ trace: "off" });

function cuitSintetico() {
  const pesos = [5, 4, 3, 2, 7, 6, 5, 4, 3, 2];
  let base: string;
  let digito: number;
  do {
    base = "30" + String(randomInt(100_000_000)).padStart(8, "0");
    digito =
      (11 -
        (pesos.reduce((total, peso, i) => total + peso * Number(base[i]), 0) %
          11)) %
      11;
  } while (digito >= 10);
  return base + digito;
}

test("Compras con Spring Boot aislado: alta, edición, saldo y permisos", async ({
  request,
  page,
  browser,
  baseURL,
}) => {
  test.skip(
    process.env.E2E_COMPRAS_ISOLATED !== "1",
    "Requiere PostgreSQL 17.6 y Spring aislados según README.md.",
  );
  test.setTimeout(90_000);
  const username = process.env.E2E_USERNAME;
  const password = process.env.E2E_PASSWORD;
  if (!username || !password)
    throw new Error("Faltan las credenciales del administrador aislado.");
  const marca = randomUUID().slice(0, 8);
  const credenciales = { username, password };
  const sesion = await request.post(spring + "/auth/login", {
    data: credenciales,
  });
  expect(sesion.status()).toBe(200);
  const admin = {
    Authorization: "Bearer " + (await sesion.json()).accessToken,
  };
  async function api(
    method: string,
    path: string,
    status: number,
    data?: object,
    headers: Record<string, string> = admin,
  ) {
    const response = await request.fetch(spring + path, {
      method,
      data,
      headers,
    });
    expect(response.status(), `${method} ${path}`).toBe(status);
    return response;
  }
  const nombreProveedor = `Compras Sur ${marca}`;
  const proveedor = await (
    await api("POST", "/proveedores", 201, {
      nombre: nombreProveedor,
      cuit: cuitSintetico(),
      telefono: "011-1000",
      rubro: "Limpieza",
    })
  ).json();
  const otroProveedor = await (
    await api("POST", "/proveedores", 201, {
      nombre: `Compras Norte ${marca}`,
      cuit: cuitSintetico(),
      telefono: "011-1001",
    })
  ).json();
  const categoria = await (
    await api("POST", "/categorias-insumo", 201, { nombre: `Higiene ${marca}` })
  ).json();
  const [jabon, bolsa] = await Promise.all(
    ["Jabón", "Bolsa"].map(async (nombre) =>
      (
        await api("POST", "/insumos", 201, {
          nombre: `${nombre} ${marca}`,
          categoriaId: categoria.id,
        })
      ).json(),
    ),
  );
  const rutaProveedor = `/proveedores/${proveedor.id}`;
  const insumos = rutaProveedor + "/insumos";
  await api("POST", insumos, 201, { insumoId: jabon.id });
  await api("POST", insumos, 201, { insumoId: bolsa.id });

  await page.goto(rutaProveedor);
  await fillLogin(page, credenciales);
  // Verifica que el proxy apunta al mismo Spring aislado antes de escribir desde la UI.
  await expect(
    page.getByRole("heading", { name: nombreProveedor }),
  ).toBeVisible();
  await page.goto("/compras/nueva");
  await fillLogin(page, credenciales);
  const selector = page.getByLabel("Proveedor", { exact: true });
  await expect(selector).toBeEnabled();
  while (
    (await selector.getByRole("option", { name: nombreProveedor }).count()) ===
    0
  ) {
    const siguiente = page
      .getByRole("navigation", { name: "Páginas de proveedores" })
      .getByRole("button", { name: "Siguiente" });
    await expect(siguiente).toBeEnabled();
    await siguiente.click();
    await expect(selector).toBeEnabled();
  }
  await selector.selectOption(String(proveedor.id));
  await page.getByRole("button", { name: "Agregar renglón" }).click();
  const renglones = [
    { insumo: jabon, cantidad: "1,25", precio: "12,34" },
    { insumo: bolsa, cantidad: "3", precio: "1" },
  ];
  for (const [index, fila] of renglones.entries()) {
    const grupo = page.getByRole("group", { name: `Renglón ${index + 1}` });
    await grupo
      .getByLabel("Insumo", { exact: true })
      .selectOption(String(fila.insumo.id));
    await grupo.getByLabel("Cantidad").fill(fila.cantidad);
    await grupo.getByLabel("Precio de compra").fill(fila.precio);
  }
  const comprobante = `E2E-${marca}`;
  await page.getByLabel("Número de comprobante (opcional)").fill(comprobante);
  await page.getByRole("button", { name: "Revisar compra" }).click();
  const alta = page.waitForResponse(
    (res) =>
      new URL(res.url()).pathname === "/api/compras" &&
      res.request().method() === "POST",
  );
  await page.getByRole("button", { name: "Confirmar registro" }).click();
  const response = await alta;
  expect(response.status()).toBe(201);
  const compra = await response.json();
  expect(compra).toMatchObject({
    totalCompra: 18.43,
    estadoPagoCodigo: "PENDIENTE",
  });
  expect(compra.detalles).toEqual(
    expect.arrayContaining([
      expect.objectContaining({
        insumoId: jabon.id,
        cantidad: 1.25,
        precioCompra: 12.34,
        subtotal: 15.43,
      }),
      expect.objectContaining({
        insumoId: bolsa.id,
        cantidad: 3,
        precioCompra: 1,
        subtotal: 3,
      }),
    ]),
  );
  const rutaCompra = `/compras/${compra.id}`;
  await expect(
    page.getByRole("heading", { name: `Compra #${compra.id}` }),
  ).toBeVisible();
  await expect(
    page.getByText("Saldo pendiente", { exact: true }),
  ).toBeVisible();
  await expect(page.getByText("Todavía no hay pagos asociados.")).toBeVisible();
  expect(
    await (await api("GET", rutaCompra + "/saldo", 200)).json(),
  ).toMatchObject({
    totalCompra: 18.43,
    saldoPendiente: 18.43,
    totalPagado: 0,
  });
  const resultados = await (
    await api(
      "GET",
      `/compras?proveedorId=${proveedor.id}&numeroComprobante=${comprobante}&estadoPagoCodigo=PENDIENTE&page=0&size=1`,
      200,
    )
  ).json();
  expect(resultados.content.map((fila: { id: number }) => fila.id)).toEqual([
    compra.id,
  ]);
  expect(resultados.content[0]).not.toHaveProperty("saldoPendiente");

  const empleado = {
    username: `empleado_${marca}`,
    password: "EmpleadoE2Eabc-37",
  };
  await api("POST", "/usuarios", 201, {
    ...empleado,
    nombre: "Empleado Compras",
    email: `empleado_${marca}@example.test`,
    rol: "EMPLEADO",
  });
  const token = await api("POST", "/auth/login", 200, empleado, {});
  const headersEmpleado = {
    Authorization: "Bearer " + (await token.json()).accessToken,
  };
  await api("GET", rutaCompra, 401, undefined, {});
  await api("POST", insumos, 403, { insumoId: jabon.id }, headersEmpleado);

  const solicitud = {
    proveedorId: otroProveedor.id,
    detalles: [{ insumoId: jabon.id, cantidad: 1, precioCompra: 1 }],
  };
  await api("POST", "/compras", 409, solicitud, headersEmpleado);
  await api("DELETE", `/proveedores/${otroProveedor.id}`, 204);
  await api("POST", "/compras", 409, solicitud, headersEmpleado);
  const { id: compraEmpleado } = await (
    await api(
      "POST",
      "/compras",
      201,
      { ...solicitud, proveedorId: proveedor.id },
      headersEmpleado,
    )
  ).json();
  expect(compraEmpleado).not.toBe(compra.id);

  const paginaEmpleado = await browser.newPage({ baseURL });
  try {
    await paginaEmpleado.goto(rutaCompra);
    await fillLogin(paginaEmpleado, empleado);
    await paginaEmpleado.getByRole("link", { name: "Editar compra" }).click();
    await expect(
      paginaEmpleado.getByLabel("Precio de compra").first(),
    ).toHaveValue("12.34");
    await paginaEmpleado
      .getByLabel("Número de comprobante (opcional)")
      .fill(comprobante + "-EDIT");
    await paginaEmpleado
      .getByRole("button", { name: "Revisar compra" })
      .click();
    await paginaEmpleado
      .getByRole("button", { name: "Confirmar cambios" })
      .click();
    await expect(
      paginaEmpleado.getByText("Cambios guardados.", { exact: true }),
    ).toBeVisible();
    const editada = await (await api("GET", rutaCompra, 200)).json();
    expect(editada).toMatchObject({
      numeroComprobante: comprobante + "-EDIT",
      totalCompra: 18.43,
      detalles: compra.detalles,
    });
  } finally {
    await paginaEmpleado.close();
  }
});
