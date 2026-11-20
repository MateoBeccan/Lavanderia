import { randomUUID } from "node:crypto";
import { expect, test } from "@playwright/test";
import { fillLogin } from "./login";

test.use({ trace: "off" });

test("Caja registra apertura, movimientos idempotentes y cierre con Spring y PostgreSQL aislados", async ({
  page,
  request,
}) => {
  test.setTimeout(90_000);
  test.skip(
    process.env.E2E_CAJA_ISOLATED !== "1",
    "Requiere PostgreSQL desechable y Spring Boot aislado en 18094.",
  );
  const { E2E_USERNAME: username, E2E_PASSWORD: password } = process.env;
  if (!username || !password)
    throw new Error("Configurá las credenciales del administrador aislado.");
  const api = "http://127.0.0.1:18094/api";
  const admin = { username, password };
  const sufijo = randomUUID().slice(0, 12);
  const empleado = {
    username: "caja." + sufijo,
    password: randomUUID() + "A!a0",
  };

  async function autenticar(cuenta: typeof admin) {
    const login = await request.post(api + "/auth/login", { data: cuenta });
    expect(login.status()).toBe(200);
    return { Authorization: "Bearer " + (await login.json()).accessToken };
  }

  const administrador = await autenticar(admin);
  expect((await request.get(api + "/cajas/abierta")).status()).toBe(401);
  expect(
    (
      await request.get(api + "/cajas/abierta", { headers: administrador })
    ).status(),
  ).toBe(404);
  expect(
    (
      await request.post(api + "/usuarios", {
        headers: administrador,
        data: {
          ...empleado,
          nombre: "Empleado caja",
          email: sufijo + "@example.test",
          rol: "EMPLEADO",
        },
      })
    ).status(),
  ).toBe(201);
  const operador = await autenticar(empleado);
  const conClave = () => ({ ...operador, "Idempotency-Key": randomUUID() });
  const metodo = await request.get(api + "/metodos-pago", {
    headers: operador,
  });
  expect(metodo.ok()).toBe(true);
  const efectivo = (
    (await metodo.json()) as { id: number; codigo: string }[]
  ).find(({ codigo }) => codigo === "EFECTIVO");
  if (!efectivo)
    throw new Error("El entorno aislado requiere el método EFECTIVO.");

  const cajaNav = page.getByRole("link", { name: "Caja", exact: true }).first();
  const dashboard = page
    .getByRole("navigation", { name: "Ruta de navegación" })
    .getByRole("link", { name: "Inicio", exact: true });
  const recibo = page.getByRole("region", {
    name: "Resultado de la operación",
  });
  await page.goto("/");
  await fillLogin(page, empleado);
  await expect(
    page.getByText("Sin caja abierta", { exact: true }),
  ).toBeVisible();
  await cajaNav.click();
  await page.getByLabel("Importe inicial").fill("100");
  await page.getByRole("button", { name: "Revisar apertura" }).click();
  await page.getByRole("button", { name: "Abrir caja", exact: true }).click();
  await page.getByRole("button", { name: "Confirmar apertura" }).dblclick();
  await expect(recibo.getByRole("status")).toContainText(/Caja #\d+ abierta/);
  const abierta = await request.get(api + "/cajas/abierta", {
    headers: operador,
  });
  expect(abierta.status()).toBe(200);
  const { id } = (await abierta.json()) as { id: number };
  const leerSaldo = () =>
    request.get(api + `/cajas/${id}/saldo`, { headers: operador });
  expect(await (await leerSaldo()).json()).toMatchObject({
    estadoCaja: "ABIERTA",
    montoInicial: 100,
    saldoGeneral: 100,
    efectivoDisponible: 100,
    totalIngresos: 0,
    totalEgresos: 0,
  });
  const duplicada = await request.post(api + "/cajas/abrir", {
    headers: conClave(),
    data: { montoInicial: 10 },
  });
  expect(duplicada.status()).toBe(409);
  expect(await duplicada.json()).toMatchObject({
    detail: "Ya existe una caja abierta.",
  });
  await dashboard.click();
  await expect(page.getByText("Caja #" + id, { exact: true })).toBeVisible();
  await cajaNav.click();
  await recibo.getByRole("button", { name: "Continuar" }).click();
  await expect(page.getByLabel("Efectivo contado")).toHaveCount(0);
  await expect(
    page.getByRole("link", { name: "Historial de cajas" }),
  ).toHaveCount(0);
  expect(
    (
      await request.patch(api + `/cajas/${id}/cerrar`, {
        headers: operador,
        data: { efectivoContado: 100 },
      })
    ).status(),
  ).toBe(403);

  async function revisarMovimiento(
    tipo: "Ingreso" | "Egreso",
    monto: string,
    concepto: string,
  ) {
    await page
      .getByRole("button", { name: tipo + " manual", exact: true })
      .click();
    await page.getByLabel("Importe", { exact: true }).fill(monto);
    await page.getByLabel("Concepto", { exact: true }).fill(concepto);
    await page
      .getByLabel("Método del movimiento")
      .selectOption(String(efectivo!.id));
    await page.getByRole("button", { name: "Revisar movimiento" }).click();
  }

  const endpointIngreso = "/movimientos-caja/ingresos-manuales";
  const envios: {
    clave: string;
    body: string | null;
    location: string;
    id: number;
  }[] = [];
  await page.route("**/api" + endpointIngreso, async (route) => {
    const respuesta = await route.fetch({ maxRetries: 0 });
    expect(respuesta.status()).toBe(201);
    envios.push({
      clave: route.request().headers()["idempotency-key"],
      body: route.request().postData(),
      location: respuesta.headers().location,
      id: (await respuesta.json()).id,
    });
    if (envios.length === 1) return route.abort("connectionreset");
    await route.fulfill({ response: respuesta });
  });
  await revisarMovimiento("Ingreso", "25,50", "Fondo adicional E2E");
  await page.getByRole("button", { name: "Registrar ingreso manual" }).click();
  await page.getByRole("button", { name: "Confirmar movimiento" }).dblclick();
  await expect(recibo.getByRole("alert")).toContainText("Resultado pendiente");
  expect(envios).toHaveLength(1);
  await recibo
    .getByRole("button", { name: "Reintentar el mismo envío" })
    .click();
  await expect(recibo.getByRole("status")).toContainText(
    `Movimiento #${envios[0].id} registrado`,
  );
  expect(envios).toEqual([envios[0], envios[0]]);
  expect(envios[0].clave).toMatch(/^[0-9a-f-]{36}$/);
  expect(JSON.parse(envios[0].body!)).toMatchObject({ cajaId: id });
  await expect(
    page.getByRole("row").filter({ hasText: "Fondo adicional E2E" }),
  ).toHaveCount(1);
  expect(await (await leerSaldo()).json()).toMatchObject({
    saldoGeneral: 125.5,
    efectivoDisponible: 125.5,
    totalIngresos: 25.5,
    totalEgresos: 0,
  });
  await recibo.getByRole("button", { name: "Continuar" }).click();

  const endpointEgreso = "/movimientos-caja/egresos-manuales";
  await revisarMovimiento("Egreso", "125,51", "Insumos E2E");
  await expect(page.getByRole("alert")).toContainText(
    "supera el efectivo disponible",
  );
  await expect(
    page.getByRole("button", { name: "Registrar egreso manual" }),
  ).toHaveCount(0);
  expect(
    (
      await request.post(api + endpointEgreso, {
        headers: conClave(),
        data: {
          cajaId: id,
          monto: 125.51,
          metodoPagoId: efectivo.id,
          descripcion: "Egreso rechazado",
        },
      })
    ).status(),
  ).toBe(409);
  await revisarMovimiento("Egreso", "5,25", "Insumos E2E");
  await page.getByRole("button", { name: "Registrar egreso manual" }).click();
  await page.getByRole("button", { name: "Confirmar movimiento" }).click();
  await expect(recibo.getByRole("status")).toContainText("Insumos E2E");
  await expect(
    page.getByRole("row").filter({ hasText: "Insumos E2E" }),
  ).toHaveCount(1);
  expect(await (await leerSaldo()).json()).toMatchObject({
    saldoGeneral: 120.25,
    efectivoDisponible: 120.25,
    totalIngresos: 25.5,
    totalEgresos: 5.25,
  });
  await expect(page.getByText(/120,25/)).toHaveCount(2);

  await page.reload();
  await fillLogin(page, admin);
  await page.getByLabel("Efectivo contado").fill("118,20");
  await page.getByRole("button", { name: "Revisar cierre" }).click();
  await page.getByRole("button", { name: "Cerrar caja", exact: true }).click();
  await page.getByRole("button", { name: "Confirmar cierre" }).click();
  await expect(
    page.getByRole("heading", { name: "Sin caja abierta" }),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Ingreso manual" }),
  ).toHaveCount(0);
  await dashboard.click();
  await expect(
    page.getByText("Sin caja abierta", { exact: true }),
  ).toBeVisible();
  await cajaNav.click();
  await page.getByRole("link", { name: "Historial de cajas" }).click();
  await page.getByRole("link", { name: "Caja #" + id, exact: true }).click();
  await expect(page.getByText(/Diferencia de arqueo.*2,05/)).toBeVisible();
  await expect(
    page.getByRole("row").filter({ hasText: "Fondo adicional E2E" }),
  ).toHaveCount(1);
  await expect(
    page.getByRole("row").filter({ hasText: "Insumos E2E" }),
  ).toHaveCount(1);
  expect(await (await leerSaldo()).json()).toMatchObject({
    estadoCaja: "CERRADA",
    saldoGeneral: 120.25,
    efectivoEsperado: 120.25,
    efectivoContado: 118.2,
    diferenciaArqueo: -2.05,
    totalIngresos: 25.5,
    totalEgresos: 5.25,
  });
  expect(
    (
      await request.post(api + endpointIngreso, {
        headers: conClave(),
        data: {
          cajaId: id,
          monto: 1,
          metodoPagoId: efectivo.id,
          descripcion: "Caja cerrada",
        },
      })
    ).status(),
  ).toBe(409);
  expect(
    (
      await request.patch(api + `/cajas/${id}/cerrar`, {
        headers: administrador,
        data: { efectivoContado: 0 },
      })
    ).status(),
  ).toBe(409);
  const historial = await request.get(api + `/cajas/${id}/movimientos`, {
    headers: operador,
  });
  expect(await historial.json()).toMatchObject({
    content: [
      { tipoMovimientoCodigo: "EGRESO_MANUAL", monto: 5.25, cajaId: id },
      {
        tipoMovimientoCodigo: "INGRESO_MANUAL",
        monto: 25.5,
        cajaId: id,
        id: envios[0].id,
      },
    ],
    page: { totalElements: 2 },
  });

  const nueva = await request.post(api + "/cajas/abrir", {
    headers: conClave(),
    data: { montoInicial: 70 },
  });
  expect(nueva.status()).toBe(201);
  const { id: cajaB } = (await nueva.json()) as { id: number };
  const obsoleta = await request.post(api + endpointEgreso, {
    headers: conClave(),
    data: {
      cajaId: id,
      monto: 1,
      metodoPagoId: efectivo.id,
      descripcion: "Intento de la jornada anterior",
    },
  });
  expect(obsoleta.status()).toBe(409);
  expect(await obsoleta.json()).toMatchObject({
    codigo: "CAJA_JORNADA_CAMBIADA",
  });
  const recuperada = await request.post(api + endpointIngreso, {
    headers: { ...operador, "Idempotency-Key": envios[0].clave },
    data: JSON.parse(envios[0].body!),
  });
  expect(recuperada.status()).toBe(201);
  expect(recuperada.headers().location).toBe(envios[0].location);
  expect(await recuperada.json()).toMatchObject({
    id: envios[0].id,
    cajaId: id,
  });
  const saldoB = await request.get(api + `/cajas/${cajaB}/saldo`, {
    headers: operador,
  });
  expect(await saldoB.json()).toMatchObject({
    saldoGeneral: 70,
    totalIngresos: 0,
    totalEgresos: 0,
  });
});
