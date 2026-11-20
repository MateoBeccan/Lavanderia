import { randomInt, randomUUID } from "node:crypto";
import { expect, test } from "@playwright/test";
import { fillLogin } from "./login";

test.use({ trace: "off" });

test("Cobros con Spring Boot aislado recupera un 201 perdido y registra un solo movimiento", async ({
  page,
  request,
}) => {
  test.setTimeout(90_000);
  test.skip(
    process.env.E2E_COBROS_ISOLATED !== "1",
    "Requiere la base desechable de Cobros y Spring en 18095.",
  );
  const { E2E_USERNAME: username, E2E_PASSWORD: password } = process.env;
  if (!username || !password)
    throw new Error("Configurá la cuenta sintética del entorno aislado.");
  const api = "http://127.0.0.1:18095/api";
  const admin = { username, password };
  const sufijo = randomUUID().slice(0, 12);
  const empleado = {
    username: "cobros." + sufijo,
    password: randomUUID() + "A!a0",
  };
  const conClave = (
    headers: Record<string, string>,
    clave: string = randomUUID(),
  ) => ({ ...headers, "Idempotency-Key": clave });

  async function autenticar(cuenta: typeof admin) {
    const respuesta = await request.post(api + "/auth/login", { data: cuenta });
    expect(respuesta.status()).toBe(200);
    return { Authorization: "Bearer " + (await respuesta.json()).accessToken };
  }

  const administrador = await autenticar(admin);
  async function crear(
    path: string,
    data: object,
    headers: Record<string, string> = administrador,
  ) {
    const respuesta = await request.post(api + path, { headers, data });
    expect(respuesta.status()).toBe(201);
    return (await respuesta.json()).id as number;
  }

  expect(
    (
      await request.get(api + "/cajas/abierta", { headers: administrador })
    ).status(),
  ).toBe(404);
  await crear("/usuarios", {
    ...empleado,
    nombre: "Empleado cobros",
    email: sufijo + "@example.test",
    rol: "EMPLEADO",
  });
  const operador = await autenticar(empleado);
  const clienteId = await crear("/clientes", {
    nombre: "Ada",
    apellido: sufijo,
    dni: String(randomInt(10000000, 99999999)),
  });
  const prendaId = await crear("/prendas", { nombre: "Prenda " + sufijo });
  const servicioId = await crear("/servicios", {
    nombre: "Servicio " + sufijo,
    precio: 50,
    modalidadCobro: "KILO",
  });
  const nuevoPedido = () =>
    crear(
      "/pedidos",
      {
        clienteId,
        fechaEntregaEstimada: "2099-12-30",
        detalles: [{ prendaId, servicioId, cantidad: 1 }],
      },
      operador,
    );
  const pedido = await nuevoPedido();
  const pedidoCancelado = await nuevoPedido();
  const path = `/pedidos/${pedido}/cobros`;
  expect(
    (
      await request.patch(api + `/pedidos/${pedidoCancelado}/cancelar`, {
        headers: administrador,
        data: { observacion: "Preparación sintética" },
      })
    ).status(),
  ).toBe(200);
  const metodos = await request.get(api + "/metodos-pago", {
    headers: operador,
  });
  expect(metodos.ok()).toBe(true);
  const efectivo = (
    (await metodos.json()) as { id: number; codigo: string }[]
  ).find(({ codigo }) => codigo === "EFECTIVO");
  if (!efectivo) throw new Error("El catálogo aislado no contiene EFECTIVO.");
  const body = { monto: 12.53, metodoPagoId: efectivo.id };
  const post = (headers: Record<string, string>, data = body) =>
    request.post(api + path, { headers, data });
  expect((await post(conClave(operador))).status()).toBe(409);
  expect((await post({})).status()).toBe(401);

  await page.goto(`/pedidos/${pedido}`);
  await fillLogin(page, empleado);
  await expect(page).toHaveURL(new RegExp(`/pedidos/${pedido}$`), {
    timeout: 15_000,
  });
  await expect(
    page.getByText("Se necesita una caja abierta para registrar un cobro."),
  ).toBeVisible();
  await expect(page.getByRole("link", { name: "Registrar cobro" })).toHaveCount(
    0,
  );
  const caja = await crear(
    "/cajas/abrir",
    { montoInicial: 100 },
    conClave(administrador),
  );
  expect((await post(operador)).status()).toBe(400);
  expect((await post(conClave(operador), { ...body, monto: 0 })).status()).toBe(
    400,
  );
  expect(
    (await post(conClave(operador), { ...body, monto: 50.01 })).status(),
  ).toBe(409);
  expect(
    (
      await post(conClave(operador), { ...body, metodoPagoId: 2147483647 })
    ).status(),
  ).toBe(404);
  expect(
    (
      await request.post(api + `/pedidos/${pedidoCancelado}/cobros`, {
        headers: conClave(operador),
        data: body,
      })
    ).status(),
  ).toBe(409);

  const envios: {
    clave: string;
    body: string | null;
    id: number;
    location: string;
  }[] = [];
  await page.route("**/api" + path, async (route) => {
    if (route.request().method() !== "POST") return route.continue();
    const respuesta = await route.fetch({ maxRetries: 0 });
    expect(respuesta.status()).toBe(201);
    envios.push({
      clave: route.request().headers()["idempotency-key"],
      body: route.request().postData(),
      id: (await respuesta.json()).id,
      location: respuesta.headers().location,
    });
    if (envios.length === 1) return route.abort("connectionreset");
    await route.fulfill({ response: respuesta });
  });
  await page.reload();
  await fillLogin(page, empleado);
  await page.getByRole("link", { name: "Registrar cobro" }).click();
  await page.getByLabel("Importe recibido").fill("12,53");
  await page.getByLabel("Método de pago").selectOption(String(efectivo.id));
  await page.getByRole("button", { name: "Revisar cobro" }).click();
  await page
    .getByRole("button", { name: "Confirmar cobro", exact: true })
    .dblclick();
  await expect(
    page.getByText(/Resultado pendiente de confirmar/),
  ).toBeVisible();
  await page.getByRole("button", { name: "Reintentar el mismo envío" }).click();
  await expect(page.getByText(/Cobro #\d+ registrado/)).toBeVisible();
  expect(envios).toEqual([envios[0], envios[0]]);
  expect(envios[0].clave).toMatch(/^[0-9a-f-]{36}$/);
  expect(JSON.parse(envios[0].body!)).toEqual(body);
  expect(envios[0].location).toMatch(
    new RegExp("/api/cobros/" + envios[0].id + "$"),
  );
  await expect(page.getByText(/37,47/)).toBeVisible();
  await expect(
    page.getByText("Parcialmente pagado", { exact: true }),
  ).toBeVisible();

  const colision = await post(conClave(operador, envios[0].clave), {
    ...body,
    monto: 1,
  });
  expect(colision.status()).toBe(409);
  expect(await colision.json()).toMatchObject({
    codigo: "IDEMPOTENCIA_CLAVE_REUTILIZADA",
  });
  const listado = await request.get(api + path, { headers: administrador });
  expect(await listado.json()).toMatchObject({
    page: { totalElements: 1 },
    content: [{ id: envios[0].id, montoCobrado: 12.53, cajaId: caja }],
  });
  const saldo = await request.get(api + `/cajas/${caja}/saldo`, {
    headers: operador,
  });
  expect(await saldo.json()).toMatchObject({
    saldoGeneral: 112.53,
    efectivoDisponible: 112.53,
    totalIngresos: 12.53,
  });
  const movimientos = await request.get(api + `/cajas/${caja}/movimientos`, {
    headers: operador,
  });
  const historial = await movimientos.json();
  expect(historial).toMatchObject({
    page: { totalElements: 1 },
    content: [{ monto: 12.53, tipoMovimientoCodigo: "COBRO_PEDIDO" }],
  });
  await page.getByRole("link", { name: "Ver detalle del cobro" }).click();
  await page
    .getByRole("link", { name: "Movimiento #" + historial.content[0].id })
    .click();
  await expect(
    page.getByRole("heading", { name: /Movimiento #/ }),
  ).toBeVisible();

  const otro = await nuevoPedido();
  const mismaClave = conClave(administrador);
  const concurrencia = async () =>
    request.post(api + `/pedidos/${otro}/cobros`, {
      headers: mismaClave,
      data: body,
    });
  const simultaneas = await Promise.all([concurrencia(), concurrencia()]);
  const exitos = simultaneas.filter((respuesta) => respuesta.status() === 201);
  expect(exitos.length).toBeGreaterThanOrEqual(1);
  for (const respuesta of simultaneas.filter(
    (resultado) => resultado.status() !== 201,
  )) {
    expect(respuesta.status()).toBe(409);
    expect(await respuesta.json()).toMatchObject({
      codigo: "IDEMPOTENCIA_EN_CURSO",
    });
    expect(respuesta.headers()["retry-after"]).toBe("1");
  }
  const recuperada = await concurrencia();
  expect(recuperada.status()).toBe(201);
  expect(await recuperada.json()).toEqual(await exitos[0].json());
  expect(recuperada.headers().location).toBe(exitos[0].headers().location);
  const segundoHistorial = await request.get(api + `/pedidos/${otro}/cobros`, {
    headers: administrador,
  });
  expect(await segundoHistorial.json()).toMatchObject({
    page: { totalElements: 1 },
  });
  const movimientosFinales = await request.get(
    api + `/cajas/${caja}/movimientos`,
    { headers: administrador },
  );
  expect(await movimientosFinales.json()).toMatchObject({
    page: { totalElements: 2 },
  });
  const cerrar = await request.patch(api + `/cajas/${caja}/cerrar`, {
    headers: administrador,
    data: { efectivoContado: 125.06 },
  });
  expect(cerrar.status()).toBe(200);
  expect((await post(conClave(operador))).status()).toBe(409);
});
