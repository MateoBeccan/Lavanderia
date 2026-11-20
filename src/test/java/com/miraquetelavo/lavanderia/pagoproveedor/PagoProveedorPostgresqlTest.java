package com.miraquetelavo.lavanderia.pagoproveedor;

import com.miraquetelavo.lavanderia.soporte.EsquemaPostgresql;
import com.miraquetelavo.lavanderia.soporte.IntegracionPostgresql;
import com.miraquetelavo.lavanderia.caja.dto.AbrirCajaRequest;
import com.miraquetelavo.lavanderia.caja.dto.CerrarCajaRequest;
import com.miraquetelavo.lavanderia.caja.entity.EstadoCaja;
import com.miraquetelavo.lavanderia.caja.service.CajaService;
import com.miraquetelavo.lavanderia.categoriainsumo.entity.CategoriaInsumo;
import com.miraquetelavo.lavanderia.categoriainsumo.repository.CategoriaInsumoRepository;
import com.miraquetelavo.lavanderia.cliente.entity.Cliente;
import com.miraquetelavo.lavanderia.cliente.repository.ClienteRepository;
import com.miraquetelavo.lavanderia.cobro.dto.RegistrarCobroRequest;
import com.miraquetelavo.lavanderia.cobro.service.CobroService;
import com.miraquetelavo.lavanderia.common.exception.ConflictoException;
import com.miraquetelavo.lavanderia.common.exception.RecursoNoEncontradoException;
import com.miraquetelavo.lavanderia.common.exception.SolicitudInvalidaException;
import com.miraquetelavo.lavanderia.compra.dto.ActualizarCompraRequest;
import com.miraquetelavo.lavanderia.compra.dto.CompraResponse;
import com.miraquetelavo.lavanderia.compra.dto.CrearCompraRequest;
import com.miraquetelavo.lavanderia.compra.service.CompraService;
import com.miraquetelavo.lavanderia.detallecompra.dto.ActualizarDetalleCompraRequest;
import com.miraquetelavo.lavanderia.detallecompra.dto.DetalleCompraRequest;
import com.miraquetelavo.lavanderia.insumo.entity.Insumo;
import com.miraquetelavo.lavanderia.insumo.repository.InsumoRepository;
import com.miraquetelavo.lavanderia.metodopago.repository.MetodoPagoRepository;
import com.miraquetelavo.lavanderia.movimientocaja.dto.RegistrarMovimientoManualRequest;
import com.miraquetelavo.lavanderia.movimientocaja.service.MovimientoCajaService;
import com.miraquetelavo.lavanderia.pagoproveedor.dto.PagoProveedorResponse;
import com.miraquetelavo.lavanderia.pagoproveedor.dto.RegistrarPagoProveedorRequest;
import com.miraquetelavo.lavanderia.pagoproveedor.service.PagoProveedorService;
import com.miraquetelavo.lavanderia.pedido.dto.CrearPedidoRequest;
import com.miraquetelavo.lavanderia.pedido.service.PedidoService;
import com.miraquetelavo.lavanderia.detallepedido.dto.DetallePedidoRequest;
import com.miraquetelavo.lavanderia.prenda.entity.Prenda;
import com.miraquetelavo.lavanderia.prenda.repository.PrendaRepository;
import com.miraquetelavo.lavanderia.proveedor.entity.Proveedor;
import com.miraquetelavo.lavanderia.proveedor.repository.ProveedorRepository;
import com.miraquetelavo.lavanderia.proveedorinsumo.dto.AsociarInsumoRequest;
import com.miraquetelavo.lavanderia.proveedorinsumo.service.ProveedorInsumoService;
import com.miraquetelavo.lavanderia.rol.entity.CodigoRol;
import com.miraquetelavo.lavanderia.rol.repository.RolRepository;
import com.miraquetelavo.lavanderia.security.JwtService;
import com.miraquetelavo.lavanderia.servicio.entity.ModalidadCobro;
import com.miraquetelavo.lavanderia.servicio.entity.Servicio;
import com.miraquetelavo.lavanderia.servicio.repository.ServicioRepository;
import com.miraquetelavo.lavanderia.usuario.entity.Usuario;
import com.miraquetelavo.lavanderia.usuario.repository.UsuarioRepository;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@IntegracionPostgresql("pagos_proveedor")
@Sql(scripts = "/db/migration/V032__idempotencia_financiera.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "lavanderia.jwt.secret=${random.value}${random.value}",
        "spring.jpa.show-sql=false"
})
class PagoProveedorPostgresqlTest {
    @Autowired PagoProveedorService pagos;
    @Autowired CompraService compras;
    @Autowired CajaService cajas;
    @Autowired MovimientoCajaService movimientos;
    @Autowired CobroService cobros;
    @Autowired PedidoService pedidos;
    @Autowired ClienteRepository clientes;
    @Autowired PrendaRepository prendas;
    @Autowired ServicioRepository servicios;
    @Autowired ProveedorRepository proveedores;
    @Autowired CategoriaInsumoRepository categorias;
    @Autowired InsumoRepository insumos;
    @Autowired ProveedorInsumoService asociaciones;
    @Autowired UsuarioRepository usuarios;
    @Autowired RolRepository roles;
    @Autowired MetodoPagoRepository metodos;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @Autowired Clock reloj;

    private Usuario admin;
    private Proveedor proveedor;
    private Long insumoId;
    private CompraResponse compra;
    private long efectivo;
    private long transferencia;
    private long tarjeta;
    private long otros;

    @BeforeEach
    void preparar() {
        admin = usuario(CodigoRol.ADMINISTRADOR);
        cajas.listarCajas(EstadoCaja.ABIERTA, null, null, Pageable.unpaged()).forEach(c -> cerrar(c.id()));
        var sufijo = UUID.randomUUID().toString();
        proveedor = proveedores.save(new Proveedor("Proveedor " + sufijo, sufijo.substring(0, 20), "123", null));
        var categoria = categorias.save(new CategoriaInsumo("Categoría " + sufijo));
        insumoId = insumos.save(new Insumo("Insumo " + sufijo, categoria)).getId();
        asociaciones.asociar(proveedor.getId(), new AsociarInsumoRequest(insumoId));
        compra = nuevaCompra();
        efectivo = metodo("EFECTIVO");
        transferencia = metodo("TRANSFERENCIA");
        tarjeta = metodo("TARJETA");
        otros = metodo("OTROS");
    }

    @Test
    void recuperaRespuestaOriginalTrasPerderlaYCerrarCaja() throws Exception {
        var caja = abrir();
        var clave = UUID.randomUUID();
        var anterior = mvc.perform(solicitudIdempotente(clave, "20"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.usuarioId").value(admin.getId()))
                .andReturn().getResponse();

        mvc.perform(solicitudIdempotente(clave, "1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("IDEMPOTENCIA_CLAVE_REUTILIZADA"));
        cerrar(caja);
        var repetida = mvc.perform(solicitudIdempotente(clave, "20.00"))
                .andExpect(status().isCreated()).andReturn().getResponse();
        verificarRespuestaOriginal(anterior, repetida);

        try (var app = EsquemaPostgresql.reinicio(PagoProveedorPostgresqlTest.class).run(
                "--server.port=0", "--lavanderia.jwt.secret=${random.value}${random.value}",
                "--spring.jpa.show-sql=false")) {
            var token = app.getBean(JwtService.class)
                    .emitir(admin.getId(), admin.getVersionCredenciales().toString()).accessToken();
            var mvcReiniciado = org.springframework.test.web.servlet.setup.MockMvcBuilders
                    .webAppContextSetup((org.springframework.web.context.WebApplicationContext) app)
                    .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                    .build();
            var recuperada = mvcReiniciado.perform(solicitudIdempotente(clave, "20", "Bearer " + token))
                    .andExpect(status().isCreated()).andReturn().getResponse();
            verificarRespuestaOriginal(anterior, recuperada);
        }
        verificarOperacionUnica(caja, clave);
    }

    @Test
    void informaClaveEnCursoYElReintentoDevuelveLaUnicaOperacionConfirmada() throws Exception {
        var caja = abrir();
        var clave = UUID.randomUUID();
        var ejecutada = new CountDownLatch(1);
        var confirmar = new CountDownLatch(1);
        try (var hilos = Executors.newSingleThreadExecutor()) {
            var primera = hilos.submit(() -> tx.execute(t -> {
                try {
                    var respuesta = mvc.perform(solicitudIdempotente(clave, "20"))
                            .andExpect(status().isCreated()).andReturn().getResponse();
                    ejecutada.countDown();
                    assertThat(confirmar.await(10, TimeUnit.SECONDS)).isTrue();
                    return respuesta;
                } catch (Exception ex) {
                    throw new IllegalStateException(ex);
                }
            }));
            try {
                assertThat(ejecutada.await(10, TimeUnit.SECONDS)).isTrue();
                mvc.perform(solicitudIdempotente(clave, "20"))
                        .andExpect(status().isConflict())
                        .andExpect(header().string("Retry-After", "1"))
                        .andExpect(jsonPath("$.codigo").value("IDEMPOTENCIA_EN_CURSO"));
            } finally {
                confirmar.countDown();
            }
            var original = primera.get(10, TimeUnit.SECONDS);
            var reintento = mvc.perform(solicitudIdempotente(clave, "20"))
                    .andExpect(status().isCreated()).andReturn().getResponse();
            verificarRespuestaOriginal(original, reintento);
        }
        verificarOperacionUnica(caja, clave);
    }

    @Test
    void permiteReusarLaClaveTrasUnErrorDeNegocioYUnRollbackAntesDelCommit() throws Exception {
        var caja = abrir();
        var clave = UUID.randomUUID();
        mvc.perform(solicitudIdempotente(clave, "110")).andExpect(status().isConflict());
        assertThat(registros(clave)).isZero();

        tx.executeWithoutResult(t -> {
            try {
                mvc.perform(solicitudIdempotente(clave, "20")).andExpect(status().isCreated());
                assertThat(registros(clave)).isEqualTo(1);
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
            t.setRollbackOnly();
        });

        assertThat(registros(clave)).isZero();
        assertThat(pagos.listarPagosCompra(compra.id(), Pageable.unpaged())).isEmpty();
        assertThat(movimientos.consultarSaldo(caja).totalEgresos()).isEqualByComparingTo("0");
        mvc.perform(solicitudIdempotente(clave, "20")).andExpect(status().isCreated());
        verificarOperacionUnica(caja, clave);
    }

    private void verificarOperacionUnica(long caja, UUID clave) {
        verificarSaldo("20", "80", "PARCIAL");
        assertThat(pagos.listarPagosCompra(compra.id(), Pageable.unpaged())).hasSize(1);
        assertThat(movimientos.listar(caja, null, null, null, null, org.springframework.data.domain.Pageable.unpaged()))
                .hasSize(1);
        assertThat(movimientos.consultarSaldo(caja).totalEgresos()).isEqualByComparingTo("20");
        assertThat(registros(clave)).isEqualTo(1);
    }

    private int registros(UUID clave) {
        return jdbc.queryForObject("select count(*) from solicitudes_financieras_idempotentes where clave = ?",
                Integer.class, clave);
    }

    private MockHttpServletRequestBuilder solicitudIdempotente(UUID clave, String importe) {
        return solicitudIdempotente(clave, importe, bearer(admin));
    }

    private MockHttpServletRequestBuilder solicitudIdempotente(UUID clave, String importe, String token) {
        return post("/api/compras/{id}/pagos", compra.id()).header(AUTHORIZATION, token)
                .header("Idempotency-Key", clave.toString()).contentType("application/json").content(cuerpo(importe, efectivo));
    }

    private static void verificarRespuestaOriginal(MockHttpServletResponse original, MockHttpServletResponse repetida) {
        assertThat(repetida.getContentAsByteArray()).isEqualTo(original.getContentAsByteArray());
        assertThat(repetida.getHeader("Location")).isEqualTo(original.getHeader("Location"));
    }

    @Test
    void registraParcialesCompletaYArqueaCaja() {
        var caja = abrir();
        verificarSaldo("0", "100", "PENDIENTE");

        var primero = pagar("20", efectivo);
        verificarSaldo("20", "80", "PARCIAL");
        pagar("30", transferencia);
        verificarSaldo("50", "50", "PARCIAL");
        pagar("30", tarjeta);
        verificarSaldo("80", "20", "PARCIAL");
        pagar("20", otros);
        verificarSaldo("100", "0", "PAGADO");
        assertThatThrownBy(() -> pagar("1", efectivo)).isInstanceOf(ConflictoException.class);

        var movimiento = movimientos.obtener(primero.movimientoCajaId());
        assertThat(movimiento.tipoMovimientoCodigo()).isEqualTo("PAGO_PROVEEDOR");
        assertThat(movimiento.usuarioId()).isEqualTo(admin.getId());
        assertThat(movimiento.monto()).isEqualByComparingTo(primero.montoPagado());
        assertThat(movimiento.fechaHora()).isEqualTo(primero.fechaPago());
        assertThat(movimiento.descripcion()).isEqualTo("Pago de compra #" + compra.id());
        var saldo = movimientos.consultarSaldo(caja);
        assertThat(saldo.totalEgresos()).isEqualByComparingTo("100");
        assertThat(saldo.egresosEfectivo()).isEqualByComparingTo("20");
        var cierre = cajas.cerrarCaja(caja, new CerrarCajaRequest(monto("30")), admin.getId());
        assertThat(cierre.montoFinalCalculado()).isEqualByComparingTo("-50");
        assertThat(cierre.efectivoEsperado()).isEqualByComparingTo("30");
        assertThat(cierre.diferenciaArqueo()).isEqualByComparingTo("0");
    }

    @Test
    void aceptaPagoTotalConProveedorInactivoYConservaHistorialAlReiniciar() {
        abrir();
        proveedor.desactivar();
        proveedores.save(proveedor);
        var pago = pagar("100", transferencia);
        assertThat(pago.proveedorId()).isEqualTo(proveedor.getId());
        verificarSaldo("100", "0", "PAGADO");

        try (var app = EsquemaPostgresql.reinicio(PagoProveedorPostgresqlTest.class).run(
                "--server.port=0", "--lavanderia.jwt.secret=${random.value}${random.value}",
                "--spring.jpa.show-sql=false")) {
            var servicio = app.getBean(PagoProveedorService.class);
            assertThat(servicio.obtenerPorId(pago.id())).isEqualTo(pago);
            assertThat(servicio.consultarSaldoCompra(compra.id()).estadoPagoCodigo()).isEqualTo("PAGADO");
        }
    }

    @Test
    void validaCajaCompraMetodoSaldoYEfectivo() {
        assertThatThrownBy(() -> pagar("1", efectivo)).isInstanceOf(ConflictoException.class);
        var caja = abrir();
        assertThatThrownBy(() -> pagar("101", efectivo)).isInstanceOf(ConflictoException.class)
                .hasMessageContaining("saldo pendiente");
        assertThatThrownBy(() -> pagar("51", efectivo)).isInstanceOf(ConflictoException.class)
                .hasMessageContaining("efectivo");
        assertThatThrownBy(() -> pagar("1", Long.MAX_VALUE)).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> pagos.registrarPago(Long.MAX_VALUE, solicitud("1", efectivo), admin.getId()))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            jdbc.update("update metodos_pago set activo = false where codigo = 'EFECTIVO'");
            pagar("1", efectivo);
        })).isInstanceOf(SolicitudInvalidaException.class);
        cerrar(caja);
        assertThatThrownBy(() -> pagar("1", efectivo)).isInstanceOf(ConflictoException.class);
        verificarSaldo("0", "100", "PENDIENTE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1.001", "100000000"})
    void validaImporteTambienEnElServicio(String importe) {
        assertThatThrownBy(() -> pagar(importe, efectivo)).isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void autenticaUsuarioRealResuelveLocationYPaginaHistorial() throws Exception {
        abrir();
        var empleado = usuario(CodigoRol.EMPLEADO);
        var ruta = "/api/compras/" + compra.id();
        var alta = mvc.perform(post(ruta + "/pagos").header(AUTHORIZATION, bearer(empleado))
                        .contentType(APPLICATION_JSON).content(cuerpo("25", efectivo)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.usuarioId").value(empleado.getId()))
                .andReturn().getResponse();
        var ubicacion = alta.getHeader("Location");
        mvc.perform(get(ubicacion).header(AUTHORIZATION, bearer(admin))).andExpect(status().isOk())
                .andExpect(jsonPath("$.montoPagado").value(25));

        var segundo = pagar("25", transferencia);
        mvc.perform(get(ruta + "/pagos").header(AUTHORIZATION, bearer(empleado)).param("size", "1")
                        .param("sort", "id,asc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(segundo.id()));
        mvc.perform(get(ruta + "/saldo").header(AUTHORIZATION, bearer(admin))).andExpect(status().isOk())
                .andExpect(jsonPath("$.saldoPendiente").value(50))
                .andExpect(jsonPath("$.estadoPagoCodigo").value("PARCIAL"));

        var actualizacion = """
                {"proveedorId":%d,"detalles":[{"insumoId":%d,"cantidad":1,"precioCompra":100}]}
                """.formatted(proveedor.getId(), insumoId);
        mvc.perform(put(ruta).header(AUTHORIZATION, bearer(empleado)).contentType(APPLICATION_JSON)
                .content(actualizacion)).andExpect(status().isConflict());
        var token = bearer(empleado);
        empleado.desactivar();
        usuarios.save(empleado);
        mvc.perform(get(ruta + "/saldo").header(AUTHORIZATION, token)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"pagos", "movimientos_caja", "compras"})
    void revierteLasTresEscriturasAnteErrorSql(String tabla) {
        var caja = abrir();
        assertThatThrownBy(() -> tx.executeWithoutResult(t -> {
            jdbc.execute("alter table " + tabla + " add constraint ck_falla_pago_test check (false) not valid");
            pagar("1", efectivo);
        })).isInstanceOf(DataIntegrityViolationException.class);
        verificarSaldo("0", "100", "PENDIENTE");
        assertThat(movimientos.listar(caja, null, null, null, null, Pageable.unpaged())).isEmpty();
        assertThat(movimientos.consultarSaldo(caja).efectivoDisponible()).isEqualByComparingTo("50");
    }

    @Test
    void detectaEstadoInconsistenteYRevierteSiElCatalogoEstaInactivo() {
        var caja = abrir();
        assertThatThrownBy(() -> tx.executeWithoutResult(t -> {
            jdbc.update("update estados_pago set activo = false where codigo = 'PARCIAL'");
            pagar("1", efectivo);
        })).isInstanceOf(ConflictoException.class).hasMessageContaining("inactivo");
        assertThat(movimientos.listar(caja, null, null, null, null, Pageable.unpaged())).isEmpty();
        verificarSaldo("0", "100", "PENDIENTE");

        tx.executeWithoutResult(t -> {
            jdbc.update("""
                    update compras set id_estado_pago = (select id_estado_pago from estados_pago where codigo='PARCIAL')
                    where id_compra=?
                    """, compra.id());
            assertThatThrownBy(() -> pagos.consultarSaldoCompra(compra.id()))
                    .isInstanceOf(ConflictoException.class).hasMessageContaining("inconsistente");
            t.setRollbackOnly();
        });
    }

    @Test
    void protegeIntegridadPostgresql() {
        var caja = abrir();
        var pago = pagar("1", efectivo);
        assertThatThrownBy(() -> insertarPago(compra.id(), pago.movimientoCajaId(), 1))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertarPago(compra.id(), Long.MAX_VALUE, 1))
                .isInstanceOf(DataIntegrityViolationException.class);
        var otroMovimiento = movimientos.registrarEgreso(
                new RegistrarMovimientoManualRequest(caja, BigDecimal.ONE, transferencia, "Prueba de restricciones"),
                admin.getId());
        assertThatThrownBy(() -> insertarPago(Long.MAX_VALUE, otroMovimiento.id(), 1))
                .isInstanceOf(DataIntegrityViolationException.class).hasStackTraceContaining("fk_pagos_compra");
        assertThatThrownBy(() -> insertarPago(compra.id(), otroMovimiento.id(), 0))
                .isInstanceOf(DataIntegrityViolationException.class).hasStackTraceContaining("ck_pagos_monto_positivo");
        assertThatThrownBy(() -> tx.executeWithoutResult(t -> {
            jdbc.update("delete from detalles_compra where id_compra = ?", compra.id());
            jdbc.update("delete from compras where id_compra = ?", compra.id());
        })).isInstanceOf(DataIntegrityViolationException.class).hasStackTraceContaining("fk_pagos_compra");
        assertThatThrownBy(() -> jdbc.update("delete from movimientos_caja where id_mov_caja = ?", pago.movimientoCajaId()))
                .isInstanceOf(DataIntegrityViolationException.class).hasStackTraceContaining("fk_pagos_movimiento");
        assertThat(jdbc.queryForList("""
                select conname from pg_constraint where conrelid = 'pagos'::regclass and convalidated
                """, String.class)).contains("fk_pagos_compra", "fk_pagos_movimiento", "ck_pagos_monto_positivo");
        assertThat(jdbc.queryForObject("""
                select indexdef from pg_indexes where schemaname = current_schema()
                and indexname = 'ix_pagos_compra_fecha_id'
                """, String.class)).contains("(id_compra, fecha_pago DESC, id_pago DESC)");
    }

    @Test
    void leeUnaVersionConfirmadaMientrasElPagoEstaEnCurso() throws Exception {
        abrir();
        var escrito = new CountDownLatch(1);
        var confirmar = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var pendiente = executor.submit(() -> tx.executeWithoutResult(t -> {
                pagar("40", transferencia);
                escrito.countDown();
                try {
                    assertThat(confirmar.await(10, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
            }));
            try {
                assertThat(escrito.await(10, TimeUnit.SECONDS)).isTrue();
                verificarSaldo("0", "100", "PENDIENTE");
            } finally {
                confirmar.countDown();
            }
            pendiente.get(10, TimeUnit.SECONDS);
        }
        verificarSaldo("40", "60", "PARCIAL");
    }

    @Test
    void serializaPagosSobreLaMismaCompra() throws Exception {
        abrir();
        assertThat(competir(() -> pagar("70", transferencia), () -> pagar("70", transferencia)))
                .extracting(Object::getClass).containsExactlyInAnyOrder(PagoProveedorResponse.class, ConflictoException.class);
        verificarSaldo("70", "30", "PARCIAL");
    }

    @Test
    void serializaDosComprasQueCompitenPorEfectivo() throws Exception {
        var caja = abrir();
        var otra = nuevaCompra();
        assertThat(competir(() -> pagar("40", efectivo),
                () -> pagos.registrarPago(otra.id(), solicitud("40", efectivo), admin.getId())))
                .extracting(Object::getClass).containsExactlyInAnyOrder(PagoProveedorResponse.class, ConflictoException.class);
        assertThat(movimientos.consultarSaldo(caja).efectivoDisponible()).isEqualByComparingTo("10");
    }

    @Test
    void serializaPagoYCierre() throws Exception {
        var caja = abrir();
        var resultados = competir(() -> pagar("40", efectivo),
                () -> cajas.cerrarCaja(caja, new CerrarCajaRequest(monto("50")), admin.getId()));
        boolean pagoConfirmado = resultados.getFirst() instanceof PagoProveedorResponse;
        assertThat(movimientos.consultarSaldo(caja).efectivoEsperado())
                .isEqualByComparingTo(pagoConfirmado ? "10" : "50");
        if (!pagoConfirmado) {
            assertThat(resultados.getFirst()).isInstanceOf(ConflictoException.class);
        }
    }

    @Test
    void serializaPagoYEdicionDeCompra() throws Exception {
        abrir();
        var detalle = compra.detalles().getFirst();
        var solicitud = new ActualizarCompraRequest(proveedor.getId(), "Nuevo",
                List.of(new ActualizarDetalleCompraRequest(detalle.id(), insumoId, BigDecimal.ONE, monto("60"))));
        var resultados = competir(() -> pagar("80", transferencia),
                () -> compras.actualizarCompra(compra.id(), solicitud));
        assertThat(resultados).filteredOn(ConflictoException.class::isInstance).hasSize(1);
        if (resultados.getFirst() instanceof PagoProveedorResponse) {
            verificarSaldo("80", "20", "PARCIAL");
            assertThat(compras.obtenerPorId(compra.id()).numeroComprobante()).isEqualTo("Original");
        } else {
            verificarSaldo("0", "60", "PENDIENTE");
            assertThat(compras.obtenerPorId(compra.id()).numeroComprobante()).isEqualTo("Nuevo");
        }
    }

    @Test
    void serializaPagoYCobro() throws Exception {
        var caja = abrir();
        var cliente = clientes.save(new Cliente("Ana", "Pérez", null, null, null));
        var prenda = prendas.save(new Prenda("Camisa " + UUID.randomUUID()));
        var servicio = servicios.save(new Servicio("Lavado " + UUID.randomUUID(), monto("50"), ModalidadCobro.UNIDAD));
        var pedido = pedidos.registrar(new CrearPedidoRequest(cliente.getId(), LocalDate.now(reloj), List.of(
                new DetallePedidoRequest(null, prenda.getId(), servicio.getId(), BigDecimal.ONE, null, null))), admin.getId());
        var resultados = competir(() -> pagar("40", efectivo),
                () -> cobros.registrar(pedido.id(), new RegistrarCobroRequest(monto("50"), efectivo), admin.getId()));
        assertThat(resultados).noneMatch(ConflictoException.class::isInstance);
        assertThat(movimientos.consultarSaldo(caja).efectivoDisponible()).isEqualByComparingTo("60");
    }

    @Test
    void serializaPagoYEgresoManual() throws Exception {
        var caja = abrir();
        var resultados = competir(() -> pagar("40", efectivo),
                () -> movimientos.registrarEgreso(new RegistrarMovimientoManualRequest(caja, monto("40"), efectivo,
                        "Egreso manual"), admin.getId()));
        assertThat(resultados).filteredOn(ConflictoException.class::isInstance).hasSize(1);
        assertThat(movimientos.consultarSaldo(caja).efectivoDisponible()).isEqualByComparingTo("10");
    }

    private void insertarPago(long compraId, long movimientoId, int importe) {
        jdbc.update("insert into pagos (id_compra,id_mov_caja,fecha_pago,monto_pagado) values (?,?,now(),?)",
                compraId, movimientoId, importe);
    }

    private List<Object> competir(Supplier<?> primera, Supplier<?> segunda) throws Exception {
        var barrera = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var uno = executor.submit(() -> ejecutar(barrera, primera));
            var dos = executor.submit(() -> ejecutar(barrera, segunda));
            return List.of(uno.get(15, TimeUnit.SECONDS), dos.get(15, TimeUnit.SECONDS));
        }
    }

    private Object ejecutar(CyclicBarrier barrera, Supplier<?> operacion) throws Exception {
        barrera.await(5, TimeUnit.SECONDS);
        try {
            return operacion.get();
        } catch (ConflictoException ex) {
            return ex;
        }
    }

    private CompraResponse nuevaCompra() {
        return compras.crearCompra(new CrearCompraRequest(proveedor.getId(), "Original",
                List.of(new DetalleCompraRequest(insumoId, BigDecimal.ONE, monto("100")))), admin.getId());
    }

    private void verificarSaldo(String pagado, String pendiente, String estado) {
        var saldo = pagos.consultarSaldoCompra(compra.id());
        assertThat(saldo.totalPagado()).isEqualByComparingTo(pagado);
        assertThat(saldo.saldoPendiente()).isEqualByComparingTo(pendiente);
        assertThat(saldo.estadoPagoCodigo()).isEqualTo(estado);
    }

    private long abrir() {
        return cajas.abrirCaja(new AbrirCajaRequest(monto("50")), admin.getId()).id();
    }

    private void cerrar(long caja) {
        var esperado = movimientos.consultarSaldo(caja).efectivoDisponible();
        cajas.cerrarCaja(caja, new CerrarCajaRequest(esperado), admin.getId());
    }

    private PagoProveedorResponse pagar(String importe, long metodo) {
        return pagos.registrarPago(compra.id(), solicitud(importe, metodo), admin.getId());
    }

    private RegistrarPagoProveedorRequest solicitud(String importe, long metodo) {
        return new RegistrarPagoProveedorRequest(monto(importe), metodo);
    }

    private long metodo(String codigo) {
        return metodos.findByCodigo(codigo).orElseThrow().getId();
    }

    private static BigDecimal monto(String importe) {
        return new BigDecimal(importe);
    }

    private Usuario usuario(CodigoRol codigo) {
        var rol = roles.findByNombre(codigo).orElseThrow();
        var nombre = UUID.randomUUID().toString();
        return usuarios.save(new Usuario(nombre, "hash-de-prueba", "Ana", nombre + "@example.com", rol));
    }

    private String bearer(Usuario usuario) {
        return "Bearer " + jwt.emitir(usuario.getId(), usuario.getVersionCredenciales().toString()).accessToken();
    }

    private String cuerpo(String importe, long metodo) {
        return "{\"monto\":" + importe + ",\"metodoPagoId\":" + metodo + "}";
    }
}
