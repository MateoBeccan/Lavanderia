import { randomInt, randomUUID } from "node:crypto";
import { expect, test } from "@playwright/test";
import { fillLogin } from "./login";

test.use({ trace: "off", timezoneId: "America/Argentina/Buenos_Aires" });

test("consulta los tres reportes con una sesión real y conserva los importes al paginar", async ({
  page,
  request,
  browser,
  baseURL,
}) => {
  test.skip(
    process.env.E2E_REPORTES_ISOLATED !== "1",
    "Requiere un Spring aislado en 18052.",
  );
  test.setTimeout(90_000);
  const { E2E_USERNAME: username, E2E_PASSWORD: password } = process.env;
  if (!username || !password)
    throw new Error("Configurá la cuenta sintética de la instancia aislada.");
  const admin = { username, password };
  const marca = randomUUID().slice(0, 12);
  const empleado = {
    username: "reportes." + marca,
    password: randomUUID() + "!aA1",
  };
  const base = "http://127.0.0.1:18052/api";

  async function login(cuenta: typeof admin) {
    const respuesta = await request.post(base + "/auth/login", {
      data: cuenta,
    });
    expect(respuesta.status()).toBe(200);
    return { Authorization: "Bearer " + (await respuesta.json()).accessToken };
  }
  const jwt = await login(admin);
  const me = await request.get(base + "/auth/me", { headers: jwt });
  expect((await me.json()).rol).toBe("ADMINISTRADOR");

  async function consultar(
    ruta: string,
    status = 200,
    headers: Record<string, string> = jwt,
  ) {
    const respuesta = await request.get(base + ruta, { headers });
    expect(respuesta.status(), ruta).toBe(status);
    return status === 200 ? respuesta.json() : undefined;
  }
  async function crear(ruta: string, data: object) {
    const respuesta = await request.post(base + ruta, {
      data,
      headers: { ...jwt, "Idempotency-Key": randomUUID() },
    });
    expect(respuesta.status(), ruta).toBe(201);
    return respuesta.json();
  }

  // Las escrituras de preparación requieren un historial de caja vacío.
  expect(await consultar("/reportes/movimientos-caja")).toMatchObject({
    totalElementos: 0,
  });
  await consultar("/cajas/abierta", 404);
  await crear("/usuarios", {
    ...empleado,
    nombre: "Operador de reportes",
    email: marca + "@example.test",
    rol: "EMPLEADO",
  });
  const jwtEmpleado = await login(empleado);
  for (const tipo of ["pedidos", "movimientos-caja", "compras"]) {
    await consultar(`/reportes/${tipo}`, 401, {});
    await consultar(`/reportes/${tipo}`, 403, jwtEmpleado);
    await consultar(`/reportes/${tipo}/pdf`, 401, {});
    await consultar(`/reportes/${tipo}/pdf`, 403, jwtEmpleado);
  }

  const cliente = await crear("/clientes", {
    nombre: "Ana",
    apellido: "Reporte",
    dni: String(randomInt(10_000_000, 100_000_000)),
  });
  const prenda = await crear("/prendas", { nombre: "Prenda " + marca });
  const servicio = await crear("/servicios", {
    nombre: "Servicio " + marca,
    modalidadCobro: "UNIDAD",
    precio: 20.11,
  });
  const crearPedido = () =>
    crear("/pedidos", {
      clienteId: cliente.id,
      fechaEntregaEstimada: "2099-12-30",
      detalles: [{ cantidad: 1, prendaId: prenda.id, servicioId: servicio.id }],
    });
  const pedido1 = await crearPedido();
  const pedido2 = await crearPedido();

  const proveedor = await crear("/proveedores", {
    nombre: "Proveedor " + marca,
    cuit: "30712345671",
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
  const crearCompra = (numeroComprobante: string, precioCompra: number) =>
    crear("/compras", {
      proveedorId: proveedor.id,
      numeroComprobante,
      detalles: [{ insumoId: insumo.id, cantidad: 1, precioCompra }],
    });
  const compra1 = await crearCompra("A-" + marca, 22.05);
  const compra2 = await crearCompra("B-" + marca, 18.2);
  const caja = await crear("/cajas/abrir", { montoInicial: 100 });
  const metodos: { id: number; codigo: string }[] =
    await consultar("/metodos-pago");
  const efectivo = metodos.find(({ codigo }) => codigo === "EFECTIVO");
  if (!efectivo)
    throw new Error("El catálogo de pruebas requiere el método EFECTIVO.");
  const metodoPagoId = efectivo.id;
  const ingreso = await crear("/movimientos-caja/ingresos-manuales", {
    cajaId: caja.id,
    monto: 12.53,
    metodoPagoId,
    descripcion: "Ingreso de prueba",
  });
  await crear("/movimientos-caja/egresos-manuales", {
    cajaId: caja.id,
    monto: 2.41,
    metodoPagoId,
    descripcion: "Egreso de prueba",
  });
  await crear(`/pedidos/${pedido1.id}/cobros`, { monto: 20.11, metodoPagoId });
  await crear(`/compras/${compra1.id}/pagos`, { monto: 22.05, metodoPagoId });
  const cierre = await request.patch(base + `/cajas/${caja.id}/cerrar`, {
    headers: jwt,
    data: { efectivoContado: 108.18 },
  });
  expect(cierre.status()).toBe(200);

  const informeCaja = "/reportes/movimientos-caja";
  expect(await consultar(informeCaja)).toMatchObject({
    resumen: {
      cantidadMovimientos: 4,
      totalIngresos: 32.64,
      totalEgresos: 24.46,
      saldoNeto: 8.18,
    },
    datos: [
      {
        tipoMovimientoCodigo: "PAGO_PROVEEDOR",
        naturaleza: "EGRESO",
        monto: 22.05,
      },
      {
        tipoMovimientoCodigo: "COBRO_PEDIDO",
        naturaleza: "INGRESO",
        monto: 20.11,
      },
      {
        tipoMovimientoCodigo: "EGRESO_MANUAL",
        naturaleza: "EGRESO",
        monto: 2.41,
      },
      {
        tipoMovimientoCodigo: "INGRESO_MANUAL",
        naturaleza: "INGRESO",
        monto: 12.53,
      },
    ],
  });
  const [dia] = pedido1.fechaIngreso.split("T") as string[];
  const antes = await consultar(`/cajas/${caja.id}/saldo`);
  const verbos = new Set<string>();
  page.on("request", (req) => {
    if (new URL(req.url()).pathname.startsWith("/api/reportes/"))
      verbos.add(req.method());
  });
  await page.goto("/reportes");
  await fillLogin(page, admin);
  await expect(
    page.getByRole("heading", { name: "Reportes administrativos" }),
  ).toBeVisible();
  await page.getByRole("link", { name: /^Pedidos/ }).click();
  // Navegación interna para conservar el JWT en memoria y ejercitar una página de un registro.
  await page.evaluate(() => {
    history.pushState({}, "", "?size=1");
    dispatchEvent(new PopStateEvent("popstate"));
  });
  const filtros = page.getByRole("form", { name: "Filtros del reporte" });
  const aplicar = filtros.getByRole("button", { name: "Aplicar filtros" });
  const resumen = page.getByRole("region", { name: "Resumen de la consulta" });
  await filtros.getByLabel("Fecha desde").fill(dia);
  await filtros.getByLabel("Fecha hasta").fill(dia);
  await filtros.getByText("Usar ID de cliente").click();
  await filtros.getByLabel("ID del cliente").fill(String(cliente.id));
  await filtros.getByLabel("Estado del pedido").selectOption("PENDIENTE");
  await aplicar.click();
  await expect(resumen).toContainText(/\$\s40,22/);
  await expect(page.getByRole("table")).toContainText(`Pedido #${pedido2.id}`);
  await page.getByRole("button", { name: "Siguiente" }).click();
  await expect(page.getByText("Página 2 de 2")).toBeVisible();
  await expect(resumen).toContainText(/\$\s40,22/);
  const respuestaPdf = page.waitForResponse(
    (respuesta) =>
      new URL(respuesta.url()).pathname === "/api/reportes/pedidos/pdf",
  );
  const descargaPdf = page.waitForEvent("download");
  await page.getByRole("button", { name: "Generar PDF" }).click();
  const respuesta = await respuestaPdf;
  expect(respuesta.status()).toBe(200);
  expect(respuesta.headers()["content-type"]).toBe("application/pdf");
  expect((await respuesta.body()).subarray(0, 5).toString()).toBe("%PDF-");
  expect((await descargaPdf).suggestedFilename()).toMatch(
    /^mira-que-te-lavo-pedidos-.+\.pdf$/,
  );
  await page
    .getByRole("link", { name: `Pedido #${pedido1.id}`, exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: `Pedido #${pedido1.id}`, exact: true }),
  ).toBeVisible();
  await page.goBack();
  await expect(page.getByText("Página 2 de 2")).toBeVisible();
  await filtros.getByLabel("Fecha desde").fill("2099-01-01");
  await filtros.getByLabel("Fecha hasta").fill("2099-01-01");
  await aplicar.click();
  await expect(
    page.getByRole("heading", { name: "Sin resultados" }),
  ).toBeVisible();

  const nav = page.getByRole("navigation", { name: "Cambiar de reporte" });
  await nav.getByRole("link", { name: "Movimientos de Caja" }).click();
  await expect(resumen).toContainText(/\$\s8,18/);
  await expect(page.getByRole("table")).toContainText("Egreso");
  await filtros.getByLabel("Tipo de movimiento").selectOption("INGRESO_MANUAL");
  await filtros.getByLabel("Método de pago").selectOption("EFECTIVO");
  await aplicar.click();
  await expect(page.getByRole("table").getByRole("row")).toHaveCount(2);
  await expect(resumen).toContainText(/\$\s12,53/);
  await page
    .getByRole("link", { name: `Movimiento #${ingreso.id}`, exact: true })
    .click();
  await expect(
    page.getByText("Ingreso de prueba", { exact: true }),
  ).toBeVisible();
  await page.goBack();

  await nav.getByRole("link", { name: "Compras" }).click();
  await expect(resumen).toContainText(/\$\s40,25/);
  await filtros.getByText("Usar ID de proveedor").click();
  await filtros.getByLabel("ID del proveedor").fill(String(proveedor.id));
  await filtros.getByLabel("Estado de pago").selectOption("PENDIENTE");
  await aplicar.click();
  await expect(page.getByRole("table").getByRole("row")).toHaveCount(2);
  await expect(resumen).toContainText(/\$\s18,20/);
  expect(
    await consultar(
      `/reportes/compras?proveedorId=${proveedor.id}&estadoPagoCodigo=PAGADO`,
    ),
  ).toMatchObject({
    datos: [
      { compraId: compra1.id, estadoPagoCodigo: "PAGADO", totalCompra: 22.05 },
    ],
  });
  await page
    .getByRole("link", { name: `Compra #${compra2.id}`, exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: `Compra #${compra2.id}`, exact: true }),
  ).toBeVisible();
  expect([...verbos]).toEqual(["GET"]);
  expect(await consultar(`/cajas/${caja.id}/saldo`)).toEqual(antes);

  const paginaEmpleado = await browser.newPage({ baseURL });
  try {
    await paginaEmpleado.goto("/reportes/movimientos-caja");
    await fillLogin(paginaEmpleado, empleado);
    await expect(
      paginaEmpleado.getByRole("heading", { name: "Acceso restringido" }),
    ).toBeVisible();
  } finally {
    await paginaEmpleado.close();
  }
});
