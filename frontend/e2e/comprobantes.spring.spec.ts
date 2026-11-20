import { randomInt, randomUUID } from "node:crypto";
import { expect, test } from "@playwright/test";
import { fillLogin } from "./login";

test.use({ trace: "off" });

function cuitDePrueba(): string {
  const base = "30" + String(randomInt(100_000_000)).padStart(8, "0");
  const pesos = [5, 4, 3, 2, 7, 6, 5, 4, 3, 2];
  const suma = pesos.reduce(
    (total, peso, i) => total + Number(base[i]) * peso,
    0,
  );
  const digito = (11 - (suma % 11)) % 11;
  return digito === 10 ? cuitDePrueba() : base + digito;
}

test("ambos roles registran y consultan comprobantes en Spring aislado conservando saldos y movimientos", async ({
  request,
  page,
  browser,
  baseURL,
}) => {
  test.skip(
    process.env.E2E_COMPROBANTES_ISOLATED !== "1",
    "Requiere un Spring aislado en 18051.",
  );
  test.setTimeout(90_000);
  const { E2E_USERNAME: username, E2E_PASSWORD: password } = process.env;
  if (!username || !password)
    throw new Error(
      "Configurá las credenciales sintéticas del entorno aislado.",
    );
  const base = "http://127.0.0.1:18051/api";
  const admin = { username, password };
  const marca = randomUUID().slice(0, 12);
  const empleado = {
    username: "comprobantes." + marca,
    password: randomUUID() + "a!A1",
  };

  async function autenticar(cuenta: typeof admin) {
    const respuesta = await request.post(base + "/auth/login", {
      data: cuenta,
    });
    expect(respuesta.status()).toBe(200);
    return { Authorization: "Bearer " + (await respuesta.json()).accessToken };
  }
  const administrador = await autenticar(admin);
  async function api(
    method: "GET" | "POST",
    path: string,
    status: number,
    data?: object,
    headers: Record<string, string> = administrador,
  ) {
    const respuesta = await request.fetch(base + path, {
      method,
      headers,
      data,
    });
    expect(respuesta.status(), `${method} ${path}`).toBe(status);
    return respuesta.json();
  }
  const crear = (
    path: string,
    data: object,
    headers?: Record<string, string>,
  ) => api("POST", path, 201, data, headers);
  const conClave = (headers: Record<string, string>) => ({
    ...headers,
    "Idempotency-Key": randomUUID(),
  });
  await api("GET", "/cajas/abierta", 404);
  await crear("/usuarios", {
    ...empleado,
    nombre: "Operador E2E",
    email: marca + "@example.test",
    rol: "EMPLEADO",
  });
  const operador = await autenticar(empleado);
  const cliente = await crear("/clientes", {
    nombre: "Ada",
    apellido: marca,
    dni: String(randomInt(10_000_000, 99_999_999)),
  });
  const prenda = await crear("/prendas", { nombre: "Prenda " + marca });
  const servicio = await crear("/servicios", {
    nombre: "Servicio " + marca,
    precio: 100,
    modalidadCobro: "UNIDAD",
  });
  const pedido = await crear("/pedidos", {
    clienteId: cliente.id,
    fechaEntregaEstimada: "2099-12-30",
    detalles: [{ prendaId: prenda.id, servicioId: servicio.id, cantidad: 1 }],
  });
  const proveedor = await crear("/proveedores", {
    nombre: "Proveedor " + marca,
    cuit: cuitDePrueba(),
    telefono: "011-4000-0000",
  });
  const categoria = await crear("/categorias-insumo", {
    nombre: "Categoría " + marca,
  });
  const insumo = await crear("/insumos", {
    nombre: "Insumo " + marca,
    categoriaId: categoria.id,
  });
  await crear(`/proveedores/${proveedor.id}/insumos`, { insumoId: insumo.id });
  const compra = await crear("/compras", {
    proveedorId: proveedor.id,
    numeroComprobante: "REFERENCIA-" + marca,
    detalles: [{ insumoId: insumo.id, cantidad: 1, precioCompra: 80 }],
  });
  const caja = await crear(
    "/cajas/abrir",
    { montoInicial: 100 },
    conClave(administrador),
  );
  const metodos: { id: number; codigo: string }[] = await api(
    "GET",
    "/metodos-pago",
    200,
  );
  const efectivo = metodos.find(({ codigo }) => codigo === "EFECTIVO");
  if (!efectivo)
    throw new Error("El entorno de pruebas requiere el método EFECTIVO.");
  const cobro = await crear(
    `/pedidos/${pedido.id}/cobros`,
    { monto: 30, metodoPagoId: efectivo.id },
    conClave(operador),
  );
  const pago = await crear(
    `/compras/${compra.id}/pagos`,
    { monto: 25, metodoPagoId: efectivo.id },
    conClave(operador),
  );

  const rutasFinancieras = [
    `/pedidos/${pedido.id}/saldo`,
    `/compras/${compra.id}/saldo`,
    `/cajas/${caja.id}/saldo`,
    `/cajas/${caja.id}/movimientos`,
  ];
  const leerFinanzas = () =>
    Promise.all(rutasFinancieras.map((ruta) => api("GET", ruta, 200)));
  const antes = await leerFinanzas();
  const rutaCobro = `/cobros/${cobro.id}/comprobante`;
  const rutaPago = `/pagos-proveedor/${pago.id}/comprobante`;
  await api("GET", rutaCobro, 401, undefined, {});
  await api("POST", rutaCobro, 401, { fechaEmision: "2024-02-29" }, {});
  await api("POST", rutaPago, 400, { fechaEmision: "2099-01-01" }, operador);

  let envios = 0;
  let comprobanteCobro: number | undefined;
  await page.route("**/api" + rutaCobro, async (route) => {
    if (route.request().method() !== "POST") return route.continue();
    envios++;
    const respuesta = await route.fetch({ maxRetries: 0 });
    expect(respuesta.status()).toBe(201);
    comprobanteCobro = (await respuesta.json()).id;
    await route.abort();
  });
  async function registrar(
    contexto: typeof page,
    ruta: string,
    usuario: typeof admin,
    nota: string,
  ) {
    await contexto.goto(ruta);
    await fillLogin(contexto, usuario);
    await contexto.getByLabel("Fecha de emisión").fill("2024-02-29");
    await contexto.getByLabel("Observaciones (opcional)").fill(nota);
    await contexto.getByRole("button", { name: "Revisar comprobante" }).click();
    await contexto.getByRole("button", { name: "Confirmar registro" }).click();
  }
  await registrar(page, `/cobros/${cobro.id}`, empleado, "Constancia de cobro");
  await expect(page.getByRole("alert")).toContainText(
    "No pudimos confirmar el registro",
  );
  await page.getByRole("button", { name: "Consultar comprobante" }).click();
  await page
    .getByRole("link", { name: "Ver comprobante #" + comprobanteCobro })
    .click();
  const constancia = page.getByRole("article", { name: "Constancia interna" });
  await expect(constancia).toContainText("Constancia de cobro");
  await constancia.getByRole("link", { name: "Pedido #" + pedido.id }).click();
  await expect(
    page
      .getByRole("region", { name: "Comprobantes asociados" })
      .getByRole("link", { name: "Comprobante #" + comprobanteCobro }),
  ).toBeVisible();
  expect(envios).toBe(1);
  expect(await api("GET", rutaCobro, 200)).toMatchObject({
    id: comprobanteCobro,
    montoOperacion: 30,
    movimientoCajaId: cobro.movimientoCajaId,
  });
  await api("POST", rutaCobro, 409, { fechaEmision: "2024-02-29" });

  const paginaAdmin = await browser.newPage({ baseURL });
  try {
    await registrar(
      paginaAdmin,
      `/pagos-proveedor/${pago.id}`,
      admin,
      "Constancia de pago",
    );
    await expect(
      paginaAdmin.getByRole("link", { name: /^Ver comprobante #/ }),
    ).toBeVisible();
    const comprobantePago = await api(
      "GET",
      rutaPago,
      200,
      undefined,
      operador,
    );
    expect(comprobantePago).toMatchObject({
      tipoOperacion: "PAGO_PROVEEDOR",
      montoOperacion: 25,
      movimientoCajaId: pago.movimientoCajaId,
    });
    await paginaAdmin
      .getByRole("link", { name: "Ver comprobante #" + comprobantePago.id })
      .click();
    const ficha = paginaAdmin.getByRole("article", {
      name: "Constancia interna",
    });
    await expect(
      ficha.getByRole("link", { name: "Movimiento #" + pago.movimientoCajaId }),
    ).toHaveAttribute(
      "href",
      `/caja/${caja.id}/movimientos/${pago.movimientoCajaId}`,
    );
    await ficha.getByRole("link", { name: "Compra #" + compra.id }).click();
    await expect(
      paginaAdmin
        .getByRole("region", { name: "Comprobantes asociados" })
        .getByRole("link", { name: "Comprobante #" + comprobantePago.id }),
    ).toBeVisible();
  } finally {
    await paginaAdmin.close();
  }
  expect(await leerFinanzas()).toEqual(antes);
});
