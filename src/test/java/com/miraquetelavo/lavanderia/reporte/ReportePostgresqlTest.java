package com.miraquetelavo.lavanderia.reporte;

import com.miraquetelavo.lavanderia.soporte.EsquemaPostgresql;
import com.miraquetelavo.lavanderia.soporte.IntegracionPostgresql;
import com.miraquetelavo.lavanderia.caja.dto.AbrirCajaRequest;
import com.miraquetelavo.lavanderia.caja.dto.CerrarCajaRequest;
import com.miraquetelavo.lavanderia.caja.service.CajaService;
import com.miraquetelavo.lavanderia.categoriainsumo.entity.CategoriaInsumo;
import com.miraquetelavo.lavanderia.categoriainsumo.repository.CategoriaInsumoRepository;
import com.miraquetelavo.lavanderia.cliente.entity.Cliente;
import com.miraquetelavo.lavanderia.cliente.repository.ClienteRepository;
import com.miraquetelavo.lavanderia.cobro.dto.RegistrarCobroRequest;
import com.miraquetelavo.lavanderia.cobro.service.CobroService;
import com.miraquetelavo.lavanderia.compra.dto.CrearCompraRequest;
import com.miraquetelavo.lavanderia.compra.service.CompraService;
import com.miraquetelavo.lavanderia.detallecompra.dto.DetalleCompraRequest;
import com.miraquetelavo.lavanderia.detallepedido.dto.DetallePedidoRequest;
import com.miraquetelavo.lavanderia.insumo.entity.Insumo;
import com.miraquetelavo.lavanderia.insumo.repository.InsumoRepository;
import com.miraquetelavo.lavanderia.metodopago.repository.MetodoPagoRepository;
import com.miraquetelavo.lavanderia.movimientocaja.dto.RegistrarMovimientoManualRequest;
import com.miraquetelavo.lavanderia.movimientocaja.service.MovimientoCajaService;
import com.miraquetelavo.lavanderia.pagoproveedor.dto.RegistrarPagoProveedorRequest;
import com.miraquetelavo.lavanderia.pagoproveedor.service.PagoProveedorService;
import com.miraquetelavo.lavanderia.pedido.dto.CrearPedidoRequest;
import com.miraquetelavo.lavanderia.pedido.service.PedidoService;
import com.miraquetelavo.lavanderia.prenda.entity.Prenda;
import com.miraquetelavo.lavanderia.prenda.repository.PrendaRepository;
import com.miraquetelavo.lavanderia.proveedor.entity.Proveedor;
import com.miraquetelavo.lavanderia.proveedor.repository.ProveedorRepository;
import com.miraquetelavo.lavanderia.proveedorinsumo.dto.AsociarInsumoRequest;
import com.miraquetelavo.lavanderia.proveedorinsumo.service.ProveedorInsumoService;
import com.miraquetelavo.lavanderia.reporte.dto.ReporteCompraItemResponse;
import com.miraquetelavo.lavanderia.reporte.dto.ReporteMovimientoItemResponse;
import com.miraquetelavo.lavanderia.reporte.dto.ReportePedidoItemResponse;
import com.miraquetelavo.lavanderia.reporte.service.ReporteService;
import com.miraquetelavo.lavanderia.rol.entity.CodigoRol;
import com.miraquetelavo.lavanderia.rol.repository.RolRepository;
import com.miraquetelavo.lavanderia.security.JwtService;
import com.miraquetelavo.lavanderia.servicio.entity.ModalidadCobro;
import com.miraquetelavo.lavanderia.servicio.entity.Servicio;
import com.miraquetelavo.lavanderia.servicio.repository.ServicioRepository;
import com.miraquetelavo.lavanderia.usuario.entity.Usuario;
import com.miraquetelavo.lavanderia.usuario.repository.UsuarioRepository;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.miraquetelavo.lavanderia.movimientocaja.entity.NaturalezaMovimiento.EGRESO;
import static com.miraquetelavo.lavanderia.movimientocaja.entity.NaturalezaMovimiento.INGRESO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@IntegracionPostgresql("reportes")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "lavanderia.jwt.secret=${random.value}${random.value}",
        "spring.jpa.show-sql=false"
})
class ReportePostgresqlTest {
    private static final LocalDate DIA = LocalDate.of(2026, 11, 30);
    private static final LocalDateTime ULTIMO_MICRO = DIA.atTime(LocalTime.MAX).minusNanos(999);
    private static final ThreadLocal<Consumer<String>> INSPECCION_SQL = new ThreadLocal<>();

    @TestConfiguration(proxyBeanMethods = false)
    static class Inspeccion {
        @Bean
        HibernatePropertiesCustomizer inspectorDeReportes() {
            return propiedades -> propiedades.put("hibernate.session_factory.statement_inspector",
                    (StatementInspector) sql -> {
                        var inspector = INSPECCION_SQL.get();
                        if (inspector != null) {
                            inspector.accept(sql);
                        }
                        return sql;
                    });
        }
    }

    @Autowired ReporteService reportes;
    @Autowired PedidoService pedidos;
    @Autowired CompraService compras;
    @Autowired CobroService cobros;
    @Autowired PagoProveedorService pagos;
    @Autowired CajaService cajas;
    @Autowired MovimientoCajaService movimientos;
    @Autowired ClienteRepository clientes;
    @Autowired ServicioRepository servicios;
    @Autowired PrendaRepository prendas;
    @Autowired UsuarioRepository usuarios;
    @Autowired RolRepository roles;
    @Autowired ProveedorRepository proveedores;
    @Autowired CategoriaInsumoRepository categorias;
    @Autowired InsumoRepository insumos;
    @Autowired ProveedorInsumoService asociaciones;
    @Autowired MetodoPagoRepository metodos;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @Autowired Clock reloj;

    private Usuario admin;
    private Usuario empleado;
    private Long cliente;
    private Long proveedor;
    private Long pedidoPagado;
    private Long pedidoCancelado;
    private Long pedidoPosterior;
    private Long compraPendiente;
    private Long compraPagada;
    private Long caja;
    private Long ingresoManual;
    private Long egresoManual;
    private Long cobro;
    private Long pago;

    @BeforeAll
    void prepararHistorial() {
        String sufijo = UUID.randomUUID().toString();
        admin = usuario(CodigoRol.ADMINISTRADOR);
        empleado = usuario(CodigoRol.EMPLEADO);
        cliente = clientes.save(new Cliente("Ana", "Pérez", null, null, null)).getId();
        var otroCliente = clientes.save(new Cliente("Juan", "Arias", null, null, null)).getId();
        var prenda = prendas.save(new Prenda("Camisa " + sufijo)).getId();
        var servicio = servicios.save(new Servicio("Lavado " + sufijo, importe("30000000.05"),
                ModalidadCobro.UNIDAD)).getId();
        var detalle = new DetallePedidoRequest(null, prenda, servicio, BigDecimal.ONE, null, null);
        pedidoPagado = registrarPedido(cliente, List.of(detalle, detalle));
        pedidoCancelado = registrarPedido(cliente, List.of(detalle, detalle));
        pedidoPosterior = registrarPedido(otroCliente, List.of(detalle));

        var proveedorEntidad = proveedores.save(new Proveedor("Proveedor " + sufijo,
                sufijo.substring(0, 20), "123", null));
        proveedor = proveedorEntidad.getId();
        var categoria = categorias.save(new CategoriaInsumo("Categoría " + sufijo));
        var insumo1 = insumos.save(new Insumo("Insumo A " + sufijo, categoria)).getId();
        var insumo2 = insumos.save(new Insumo("Insumo B " + sufijo, categoria)).getId();
        asociaciones.asociar(proveedor, new AsociarInsumoRequest(insumo1));
        asociaciones.asociar(proveedor, new AsociarInsumoRequest(insumo2));
        var lineas = List.of(new DetalleCompraRequest(insumo1, BigDecimal.ONE, importe("20.10")),
                new DetalleCompraRequest(insumo2, BigDecimal.ONE, importe("20.20")));
        compraPendiente = compras.crearCompra(new CrearCompraRequest(proveedor, "A-1", lineas), admin.getId()).id();
        compraPagada = compras.crearCompra(new CrearCompraRequest(proveedor, "A-2", lineas), admin.getId()).id();

        long efectivo = metodos.findByCodigo("EFECTIVO").orElseThrow().getId();
        long transferencia = metodos.findByCodigo("TRANSFERENCIA").orElseThrow().getId();
        caja = cajas.abrirCaja(new AbrirCajaRequest(importe("100.00")), admin.getId()).id();
        ingresoManual = movimientos.registrarIngreso(new RegistrarMovimientoManualRequest(
                caja, importe("5.10"), efectivo, "Ingreso"), admin.getId()).id();
        egresoManual = movimientos.registrarEgreso(new RegistrarMovimientoManualRequest(
                caja, importe("10.20"), efectivo, "Egreso"), admin.getId()).id();
        cobro = cobros.registrar(pedidoPagado, new RegistrarCobroRequest(importe("60000000.10"), transferencia),
                admin.getId()).movimientoCajaId();
        pago = pagos.registrarPago(compraPagada, new RegistrarPagoProveedorRequest(importe("40.30"), efectivo),
                admin.getId()).movimientoCajaId();
        cajas.cerrarCaja(caja, new CerrarCajaRequest(movimientos.consultarSaldo(caja).efectivoDisponible()), admin.getId());

        jdbc.update("update proveedores set activo = false where id_proveedor = ?", proveedor);
        jdbc.update("""
                update pedidos set id_estado_pedido = (select id_estado_pedido from estados_pedido where codigo = 'CANCELADO')
                where id_pedido = ?
                """, pedidoCancelado);
        jdbc.update("update pedidos set fecha_ingreso = ? where id_pedido in (?, ?)",
                ULTIMO_MICRO, pedidoPagado, pedidoCancelado);
        jdbc.update("update pedidos set fecha_ingreso = ? where id_pedido = ?",
                DIA.plusDays(1).atStartOfDay(), pedidoPosterior);
        jdbc.update("update compras set fecha_compra = ? where id_compra in (?, ?)", ULTIMO_MICRO, compraPendiente, compraPagada);
        jdbc.update("update movimientos_caja set fecha_hora = ? where id_caja = ?", ULTIMO_MICRO, caja);
    }

    @Test
    void descargaPdfCompletoConLosMismosFiltrosSinEscrituras() throws Exception {
        String ruta = "/api/reportes/pedidos/pdf";
        mvc.perform(get(ruta)).andExpect(status().isUnauthorized());
        mvc.perform(get(ruta).header(AUTHORIZATION, token(empleado))).andExpect(status().isForbidden());
        mvc.perform(get(ruta).header(AUTHORIZATION, token(admin)).param("fechaDesde", "2026-11-31"))
                .andExpect(status().isBadRequest());

        var sql = new ArrayList<String>();
        INSPECCION_SQL.set(sql::add);
        try {
            var respuesta = mvc.perform(get(ruta).header(AUTHORIZATION, token(admin))
                            .param("fechaDesde", DIA.toString()).param("fechaHasta", DIA.toString())
                            .param("clienteId", cliente.toString()).param("page", "3").param("size", "1"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType("application/pdf"))
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(header().exists("Content-Disposition")).andReturn().getResponse();
            try (var documento = Loader.loadPDF(respuesta.getContentAsByteArray())) {
                var texto = new PDFTextStripper().getText(documento);
                assertThat(texto).contains("2 pedidos", "$ 120.000.000,20", "Ana Pérez",
                        "#" + pedidoPagado, "#" + pedidoCancelado)
                        .doesNotContain("Juan Arias");
            }
        } finally {
            INSPECCION_SQL.remove();
        }
        var consultas = sql.stream().filter(s -> s.contains("pedidos")).toList();
        assertThat(consultas).hasSize(2);
        assertThat(sql).allSatisfy(s -> assertThat(s.trim().toLowerCase()).startsWith("select"));
    }

    @Test
    void pedidosCombinaFiltrosYUsaResumenCompletoEnCadaPagina() {
        var todos = reportes.pedidos(null, null, null, null, null, 0, 20);
        assertThat(todos.datos()).extracting(ReportePedidoItemResponse::pedidoId)
                .containsExactly(pedidoPosterior, pedidoCancelado, pedidoPagado);
        assertThat(todos.datos()).extracting(ReportePedidoItemResponse::estadoPagoCodigo)
                .containsExactly("PENDIENTE", "PENDIENTE", "PAGADO");

        var primera = reportes.pedidos(DIA, DIA, cliente, null, null, 0, 1);
        var segunda = reportes.pedidos(DIA, DIA, cliente, null, null, 1, 1);
        assertThat(primera.datos()).extracting(ReportePedidoItemResponse::pedidoId).containsExactly(pedidoCancelado);
        assertThat(segunda.datos()).extracting(ReportePedidoItemResponse::pedidoId).containsExactly(pedidoPagado);
        assertThat(segunda.datos().getFirst().fechaIngreso()).isEqualTo(ULTIMO_MICRO);
        assertThat(segunda.datos().getFirst().clienteNombreCompleto()).isEqualTo("Ana Pérez");
        assertThat(primera.totalPaginas()).isEqualTo(2);
        assertThat(primera.totalElementos()).isEqualTo(2);
        assertThat(primera.resumen().cantidadPedidos()).isEqualTo(2);
        assertThat(primera.resumen().totalGeneral()).isEqualTo(importe("120000000.20"));
        assertThat(segunda.resumen()).isEqualTo(primera.resumen());

        assertThat(reportes.pedidos(null, null, cliente, null, null, 0, 20).totalElementos()).isEqualTo(2);
        assertThat(reportes.pedidos(null, null, null, "CANCELADO", null, 0, 20).datos())
                .extracting(ReportePedidoItemResponse::pedidoId).containsExactly(pedidoCancelado);
        assertThat(reportes.pedidos(DIA, DIA, cliente, "PENDIENTE", "PAGADO", 0, 20).datos())
                .extracting(ReportePedidoItemResponse::pedidoId).containsExactly(pedidoPagado);
        var fueraDeRango = reportes.pedidos(null, DIA, null, null, null, Integer.MAX_VALUE, 100);
        assertThat(fueraDeRango.datos()).isEmpty();
        assertThat(fueraDeRango.resumen()).isEqualTo(primera.resumen());
    }

    @Test
    void movimientosIncluyeCajaCerradaYCuentaCobrosYPagosUnaVez() {
        var todos = reportes.movimientos(null, null, null, null, 0, 20);
        assertThat(todos.datos()).extracting(ReporteMovimientoItemResponse::movimientoId)
                .containsExactly(pago, cobro, egresoManual, ingresoManual);
        assertThat(todos.datos()).extracting(ReporteMovimientoItemResponse::naturaleza)
                .containsExactly(EGRESO, INGRESO, EGRESO, INGRESO);
        assertThat(todos.datos()).allSatisfy(item -> {
            assertThat(item.cajaId()).isEqualTo(caja);
            assertThat(item.monto()).isPositive();
        });
        assertThat(todos.resumen().cantidadMovimientos()).isEqualTo(4);
        assertThat(todos.resumen().totalIngresos()).isEqualTo(importe("60000005.20"));
        assertThat(todos.resumen().totalEgresos()).isEqualTo(importe("50.50"));
        assertThat(todos.resumen().saldoNeto()).isEqualTo(importe("59999954.70"));

        var efectivo = reportes.movimientos(DIA, DIA, null, "EFECTIVO", 0, 1);
        assertThat(efectivo.datos()).hasSize(1);
        assertThat(efectivo.totalElementos()).isEqualTo(3);
        assertThat(efectivo.resumen().saldoNeto()).isEqualTo(importe("-45.40"));
        assertThat(reportes.movimientos(DIA, DIA, "PAGO_PROVEEDOR", "EFECTIVO", 0, 20).datos())
                .extracting(ReporteMovimientoItemResponse::movimientoId).containsExactly(pago);
        assertThat(reportes.movimientos(DIA, DIA, "COBRO_PEDIDO", "TRANSFERENCIA", 0, 20).datos())
                .extracting(ReporteMovimientoItemResponse::movimientoId).containsExactly(cobro);
    }

    @Test
    void comprasIncluyeProveedorInactivoYDetallesNoDuplicanTotales() {
        var todos = reportes.compras(null, null, null, null, 0, 20);
        assertThat(todos.datos()).extracting(ReporteCompraItemResponse::compraId)
                .containsExactly(compraPagada, compraPendiente);
        var segunda = reportes.compras(DIA, DIA, proveedor, null, 1, 1);
        assertThat(segunda.totalElementos()).isEqualTo(2);
        assertThat(segunda.totalPaginas()).isEqualTo(2);
        assertThat(segunda.resumen().cantidadCompras()).isEqualTo(2);
        assertThat(segunda.resumen().totalComprado()).isEqualTo(importe("80.60"));
        assertThat(segunda.datos()).extracting(ReporteCompraItemResponse::numeroComprobante).containsExactly("A-1");
        assertThat(reportes.compras(DIA, DIA, proveedor, "PENDIENTE", 0, 20).resumen().totalComprado())
                .isEqualTo(importe("40.30"));
        assertThat(reportes.compras(DIA, DIA, proveedor + 1000, null, 0, 20).datos()).isEmpty();
    }

    @Test
    void devuelveVaciosConImportesCero() {
        var diaSinRegistros = DIA.minusDays(1);
        var pedidosVacios = reportes.pedidos(null, diaSinRegistros, null, null, null, 0, 20);
        var movimientosVacios = reportes.movimientos(DIA.plusDays(1), null, null, null, 0, 20);
        var comprasVacias = reportes.compras(DIA.plusDays(1), null, null, null, 0, 20);
        assertThat(pedidosVacios.datos()).isEmpty();
        assertThat(movimientosVacios.datos()).isEmpty();
        assertThat(comprasVacias.datos()).isEmpty();
        assertThat(pedidosVacios.totalPaginas()).isZero();
        assertThat(movimientosVacios.totalElementos()).isZero();
        assertThat(comprasVacias.totalPaginas()).isZero();
        assertThat(pedidosVacios.resumen().totalGeneral()).isEqualTo(importe("0.00"));
        assertThat(movimientosVacios.resumen().totalIngresos()).isEqualTo(importe("0.00"));
        assertThat(movimientosVacios.resumen().totalEgresos()).isEqualTo(importe("0.00"));
        assertThat(movimientosVacios.resumen().saldoNeto()).isEqualTo(importe("0.00"));
        assertThat(comprasVacias.resumen().totalComprado()).isEqualTo(importe("0.00"));
    }

    @Test
    void consultaHttpConJwtRealYRespuestasProyectadas() throws Exception {
        var cabecera = token(admin);
        mvc.perform(get("/api/reportes/pedidos").header(AUTHORIZATION, cabecera)
                        .param("fechaDesde", DIA.toString()).param("fechaHasta", DIA.toString())
                        .param("sort", "usuario.password,asc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.datos[0].pedidoId").value(pedidoCancelado))
                .andExpect(jsonPath("$.datos[0].dni").doesNotExist()).andExpect(jsonPath("$.tamanio").value(20))
                .andExpect(jsonPath("$.pagina").value(0)).andExpect(jsonPath("$.totalElementos").value(2));
        mvc.perform(get("/api/reportes/movimientos-caja").header(AUTHORIZATION, cabecera)
                        .param("tipoMovimientoCodigo", "COBRO_PEDIDO").param("metodoPagoCodigo", "TRANSFERENCIA"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.datos[0].naturaleza").value("INGRESO"))
                .andExpect(jsonPath("$.datos[0].password").doesNotExist());
        mvc.perform(get("/api/reportes/compras").header(AUTHORIZATION, cabecera)
                        .param("proveedorId", proveedor.toString()).param("estadoPagoCodigo", "PENDIENTE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.datos[0].numeroComprobante").value("A-1"));
        mvc.perform(get("/api/reportes/compras").header(AUTHORIZATION, token(empleado)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/reportes/movimientos-caja").header(AUTHORIZATION, cabecera)
                        .param("metodoPagoCodigo", "INEXISTENTE"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/reportes/compras").header(AUTHORIZATION, cabecera)
                        .param("fechaDesde", DIA.toString()).param("fechaHasta", DIA.minusDays(1).toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void usaDosSelectsProyectadosPorReporteSinNMasUnoNiEscrituras() {
        var consultas = new ArrayList<String>();
        INSPECCION_SQL.set(consultas::add);
        try {
            reportes.pedidos(null, null, null, null, null, 0, 20);
            reportes.movimientos(null, null, null, null, 0, 20);
            reportes.compras(null, null, null, null, 0, 20);
        } finally {
            INSPECCION_SQL.remove();
        }
        assertThat(consultas).hasSize(6).allSatisfy(sql -> assertThat(sql).startsWith("select ")
                .doesNotContain("detalles_pedido", "detalles_compra", "password", "monto_inicial", " cobros ", " pagos "));
    }

    @Test
    void conservaSnapshotMientrasOtraTransaccionConfirmaUnCambio() throws Exception {
        try (var escritor = Executors.newSingleThreadExecutor()) {
            INSPECCION_SQL.set(sql -> {
                if (!sql.contains("order by")) {
                    return;
                }
                try {
                    escritor.submit(() -> jdbc.update("update pedidos set total_pedido = ? where id_pedido = ?",
                            importe("60000000.11"), pedidoCancelado)).get(10, TimeUnit.SECONDS);
                } catch (Exception ex) {
                    throw new AssertionError("La escritura concurrente debe confirmarse antes del detalle", ex);
                }
            });
            try {
                var duranteElCambio = reportes.pedidos(DIA, DIA, null, null, null, 0, 20);
                assertThat(duranteElCambio.resumen().totalGeneral()).isEqualTo(importe("120000000.20"));
                assertThat(duranteElCambio.datos().getFirst().totalPedido()).isEqualTo(importe("60000000.10"));
                INSPECCION_SQL.remove();
                var despues = reportes.pedidos(DIA, DIA, null, null, null, 0, 20);
                assertThat(despues.resumen().totalGeneral()).isEqualTo(importe("120000000.21"));
                assertThat(despues.datos().getFirst().totalPedido()).isEqualTo(importe("60000000.11"));
            } finally {
                INSPECCION_SQL.remove();
                jdbc.update("update pedidos set total_pedido = ? where id_pedido = ?", importe("60000000.10"), pedidoCancelado);
            }
        }
    }

    @Test
    void unContextoNuevoConsultaElMismoHistorial() {
        var previo = reportes.movimientos(DIA, DIA, null, null, 0, 20);
        try (var app = EsquemaPostgresql.reinicio(ReportePostgresqlTest.class).run(
                "--server.port=0", "--lavanderia.jwt.secret=${random.value}${random.value}",
                "--spring.jpa.show-sql=false")) {
            var nuevo = app.getBean(ReporteService.class);
            assertThat(nuevo.movimientos(DIA, DIA, null, null, 0, 20)).isEqualTo(previo);
            assertThat(nuevo.pedidos(DIA, DIA, null, null, null, 0, 20))
                    .isEqualTo(reportes.pedidos(DIA, DIA, null, null, null, 0, 20));
            assertThat(nuevo.compras(DIA, DIA, null, null, 0, 20))
                    .isEqualTo(reportes.compras(DIA, DIA, null, null, 0, 20));
        }
    }

    private Long registrarPedido(Long clienteId, List<DetallePedidoRequest> detalles) {
        return pedidos.registrar(new CrearPedidoRequest(clienteId, LocalDate.now(reloj), detalles), admin.getId()).id();
    }

    private Usuario usuario(CodigoRol codigo) {
        var nombre = UUID.randomUUID().toString();
        var rol = roles.findByNombre(codigo).orElseThrow();
        return usuarios.save(new Usuario(nombre, "hash-de-prueba", "Nombre de prueba", nombre + "@example.com", rol));
    }

    private String token(Usuario usuario) {
        return "Bearer " + jwt.emitir(usuario.getId(), usuario.getVersionCredenciales().toString()).accessToken();
    }

    private static BigDecimal importe(String valor) {
        return new BigDecimal(valor);
    }
}
