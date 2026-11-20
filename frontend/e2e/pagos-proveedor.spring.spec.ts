import { randomInt, randomUUID } from "node:crypto";
import { expect, test } from "@playwright/test";
import { fillLogin } from "./login";

const spring = "http://127.0.0.1:18099/api";
test.use({ trace: "off" });

function cuitSintetico() {
  const pesos = [5, 4, 3, 2, 7, 6, 5, 4, 3, 2];
  for (;;) {
    const base = "30" + String(randomInt(100_000_000)).padStart(8, "0");
    const suma = pesos.reduce(
      (total, peso, index) => total + peso * Number(base[index]),
      0,
    );
    const digito = (11 - (suma % 11)) % 11;
    if (digito < 10) return base + digito;
  }
}

test("Spring aislado confirma pagos parciales y totales sin duplicar el egreso tras perder la respuesta", async ({
  page,
  browser,
  request,
  baseURL,
}) => {
  test.skip(
    process.env.E2E_PAGOS_PROVEEDOR_ISOLATED !== "1",
    "Requiere Spring aislado en 18099 y PostgreSQL con V032.",
  );
  test.setTimeout(90_000);
  const { E2E_USERNAME: username, E2E_PASSWORD: password } = process.env;
  if (!username || !password)
    throw new Error("Configurá las credenciales del administrador aislado.");
  const credenciales = { username, password };
  const marca = randomUUID().slice(0, 12);
  const operador = {
    username: "pagos." + marca,
    password: randomUUID() + "a!A1",
  };

  async function autenticar(cuenta: typeof credenciales) {
    const response = await request.post(spring + "/auth/login", {
      data: cuenta,
    });
    expect(response.status()).toBe(200);
    return { Authorization: "Bearer " + (await response.json()).accessToken };
  }
  const admin = await autenticar(credenciales);
  async function api(
    method: string,
    path: string,
    status: number,
    data?: object,
    headers: Record<string, string> = admin,
  ) {
    const response = await request.fetch(spring + path, {
      method,
      headers,
      data,
    });
    expect(response.status(), `${method} ${path}`).toBe(status);
    return response;
  }

  await api("GET", "/cajas/abierta", 404);
  await api("POST", "/usuarios", 201, {
    ...operador,
    nombre: "Operador E2E",
    email: marca + "@example.test",
    rol: "EMPLEADO",
  });
  const empleado = await autenticar(operador);
  const nombre = "Proveedor E2E " + marca;
  const proveedor = await (
    await api("POST", "/proveedores", 201, {
      nombre,
      cuit: cuitSintetico(),
      telefono: "011-4000-0000",
    })
  ).json();
  const categoria = await (
    await api("POST", "/categorias-insumo", 201, {
      nombre: "Categoría " + marca,
    })
  ).json();
  const insumo = await (
    await api("POST", "/insumos", 201, {
      nombre: "Insumo " + marca,
      categoriaId: categoria.id,
    })
  ).json();
  await api("POST", `/proveedores/${proveedor.id}/insumos`, 201, {
    insumoId: insumo.id,
  });
  const compra = await (
    await api(
      "POST",
      "/compras",
      201,
      {
        proveedorId: proveedor.id,
        numeroComprobante: "E2E-" + marca,
        detalles: [{ insumoId: insumo.id, cantidad: 1, precioCompra: 30 }],
      },
      empleado,
    )
  ).json();
  const rutaCompra = `/compras/${compra.id}`;
  const rutaPagos = rutaCompra + "/pagos";
  const rutaRegistro = `/pagos-proveedor/compras/${compra.id}/registrar`;
  const leerSaldo = async () =>
    (await api("GET", rutaCompra + "/saldo", 200, undefined, empleado)).json();
  expect(await leerSaldo()).toMatchObject({
    saldoPendiente: 30,
    estadoPagoCodigo: "PENDIENTE",
  });
  const caja = await (
    await api(
      "POST",
      "/cajas/abrir",
      201,
      { montoInicial: 100 },
      { ...admin, "Idempotency-Key": randomUUID() },
    )
  ).json();
  const rutaCaja = `/cajas/${caja.id}`;
  const metodos: { id: number; codigo: string }[] = await (
    await api("GET", "/metodos-pago", 200, undefined, empleado)
  ).json();
  const efectivo = metodos.find(({ codigo }) => codigo === "EFECTIVO")!;
  const transferencia = metodos.find(
    ({ codigo }) => codigo === "TRANSFERENCIA",
  )!;
  await api(
    "POST",
    rutaPagos,
    401,
    { monto: 8.25, metodoPagoId: efectivo.id },
    {},
  );
  await api(
    "POST",
    rutaPagos,
    400,
    { monto: 8.25, metodoPagoId: efectivo.id },
    empleado,
  );

  const envios: {
    clave: string;
    body: string;
    status: number;
    location: string;
    pago: unknown;
  }[] = [];
  await page.route(`**/api${rutaPagos}`, async (route) => {
    const solicitud = route.request();
    if (solicitud.method() !== "POST") return route.continue();
    const respuesta = await route.fetch();
    envios.push({
      clave: solicitud.headers()["idempotency-key"],
      body: solicitud.postData() ?? "",
      status: respuesta.status(),
      location: respuesta.headers().location,
      pago: await respuesta.json(),
    });
    if (envios.length === 1) await route.abort();
    else await route.fulfill({ response: respuesta });
  });

  await page.goto(rutaCompra);
  await fillLogin(page, operador);
  await expect(page.getByRole("link", { name: nombre })).toBeVisible();
  await page.getByRole("link", { name: "Registrar pago" }).click();
  await page.getByLabel("Importe pagado").fill("8,25");
  await page.getByLabel("Método de pago").selectOption(String(efectivo.id));
  await page.getByRole("button", { name: "Revisar pago" }).click();
  await page.getByRole("button", { name: "Confirmar pago" }).dblclick();
  await expect(
    page.getByText(/Resultado pendiente de confirmar/),
  ).toBeVisible();
  expect(envios).toHaveLength(1);
  expect(envios[0].status).toBe(201);
  expect(await leerSaldo()).toMatchObject({
    totalPagado: 8.25,
    saldoPendiente: 21.75,
    estadoPagoCodigo: "PARCIAL",
  });

  async function verificarAsientos(cantidad: number) {
    const pagos = await (
      await api("GET", rutaPagos, 200, undefined, empleado)
    ).json();
    const movimientos = await (
      await api("GET", rutaCaja + "/movimientos", 200, undefined, empleado)
    ).json();
    expect(pagos.page.totalElements).toBe(cantidad);
    expect(movimientos.page.totalElements).toBe(cantidad);
    for (const pago of pagos.content) {
      expect(movimientos.content).toContainEqual(
        expect.objectContaining({
          id: pago.movimientoCajaId,
          tipoMovimientoCodigo: "PAGO_PROVEEDOR",
          monto: pago.montoPagado,
        }),
      );
    }
    return pagos.content;
  }
  const [parcial] = await verificarAsientos(1);
  await page.getByRole("button", { name: "Reintentar el mismo envío" }).click();
  await expect(
    page.getByRole("heading", { name: `Pago #${parcial.id} registrado` }),
  ).toBeVisible();
  expect(envios).toEqual([envios[0], envios[0]]);
  await verificarAsientos(1);
  await page.getByRole("link", { name: "Ver detalle del pago" }).click();
  await page
    .getByRole("link", { name: `Movimiento #${parcial.movimientoCajaId}` })
    .click();
  await expect(page.getByRole("heading", { name: /Movimiento/ })).toBeVisible();
  await api("GET", `/pagos-proveedor/${parcial.id}`, 200);
  await api("GET", `/pagos-proveedor/${parcial.id}`, 200, undefined, empleado);
  await api("GET", `/pagos-proveedor/${parcial.id}`, 401, undefined, {});
  const conflicto = await api(
    "POST",
    rutaPagos,
    409,
    { monto: 8.26, metodoPagoId: efectivo.id },
    { ...empleado, "Idempotency-Key": envios[0].clave },
  );
  expect(await conflicto.json()).toMatchObject({
    codigo: "IDEMPOTENCIA_CLAVE_REUTILIZADA",
  });

  const paginaAdmin = await browser.newPage({ baseURL });
  try {
    await paginaAdmin.goto(rutaRegistro);
    await fillLogin(paginaAdmin, credenciales);
    await expect(paginaAdmin.getByRole("link", { name: nombre })).toBeVisible();
    await paginaAdmin.getByLabel("Importe pagado").fill("21,75");
    await paginaAdmin
      .getByLabel("Método de pago")
      .selectOption(String(transferencia.id));
    await paginaAdmin.getByRole("button", { name: "Revisar pago" }).click();
    const segundo = paginaAdmin.waitForRequest(
      (r) =>
        r.method() === "POST" &&
        new URL(r.url()).pathname === "/api" + rutaPagos,
    );
    await paginaAdmin.getByRole("button", { name: "Confirmar pago" }).click();
    const clave = (await segundo).headers()["idempotency-key"];
    expect(clave).toMatch(/^[0-9a-f-]{36}$/i);
    expect(clave).not.toBe(envios[0].clave);
    await expect(
      paginaAdmin.getByText("Pagado", { exact: true }),
    ).toBeVisible();
  } finally {
    await paginaAdmin.close();
  }
  await verificarAsientos(2);
  expect(await leerSaldo()).toMatchObject({
    totalPagado: 30,
    saldoPendiente: 0,
    estadoPagoCodigo: "PAGADO",
  });
  expect(
    await (
      await api("GET", rutaCaja + "/saldo", 200, undefined, empleado)
    ).json(),
  ).toMatchObject({
    saldoGeneral: 70,
    efectivoDisponible: 91.75,
    totalEgresos: 30,
  });
  await api(
    "PATCH",
    rutaCaja + "/cerrar",
    403,
    { efectivoContado: 91.75 },
    empleado,
  );
  await api("PATCH", rutaCaja + "/cerrar", 200, { efectivoContado: 91.75 });
});
