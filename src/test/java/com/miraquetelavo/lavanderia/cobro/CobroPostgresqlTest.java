package com.miraquetelavo.lavanderia.cobro;

import com.miraquetelavo.lavanderia.soporte.EsquemaPostgresql;
import com.miraquetelavo.lavanderia.soporte.IntegracionPostgresql;
import com.miraquetelavo.lavanderia.caja.dto.AbrirCajaRequest;
import com.miraquetelavo.lavanderia.caja.dto.CerrarCajaRequest;
import com.miraquetelavo.lavanderia.caja.service.CajaService;
import com.miraquetelavo.lavanderia.cliente.entity.Cliente;
import com.miraquetelavo.lavanderia.cliente.repository.ClienteRepository;
import com.miraquetelavo.lavanderia.cobro.dto.CobroResponse;
import com.miraquetelavo.lavanderia.cobro.dto.RegistrarCobroRequest;
import com.miraquetelavo.lavanderia.cobro.service.CobroService;
import com.miraquetelavo.lavanderia.common.exception.ConflictoException;
import com.miraquetelavo.lavanderia.common.exception.RecursoNoEncontradoException;
import com.miraquetelavo.lavanderia.common.exception.SolicitudInvalidaException;
import com.miraquetelavo.lavanderia.detallepedido.dto.DetallePedidoRequest;
import com.miraquetelavo.lavanderia.metodopago.repository.MetodoPagoRepository;
import com.miraquetelavo.lavanderia.movimientocaja.service.MovimientoCajaService;
import com.miraquetelavo.lavanderia.pedido.dto.*;
import com.miraquetelavo.lavanderia.pedido.inicializacion.PedidoEstadoPagoInitializer;
import com.miraquetelavo.lavanderia.pedido.service.PedidoService;
import com.miraquetelavo.lavanderia.prenda.entity.Prenda;
import com.miraquetelavo.lavanderia.prenda.repository.PrendaRepository;
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
import org.springframework.http.MediaType;
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
import static org.springframework.http.HttpHeaders.LOCATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@IntegracionPostgresql("cobro")
@Sql(scripts = "/db/migration/V032__idempotencia_financiera.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "lavanderia.jwt.secret=${random.value}${random.value}",
        "spring.jpa.show-sql=false"
})
class CobroPostgresqlTest {
    @Autowired CobroService cobros;
    @Autowired CajaService cajas;
    @Autowired PedidoService pedidos;
    @Autowired MovimientoCajaService movimientos;
    @Autowired ClienteRepository clientes;
    @Autowired ServicioRepository servicios;
    @Autowired PrendaRepository prendas;
    @Autowired UsuarioRepository usuarios;
    @Autowired RolRepository roles;
    @Autowired MetodoPagoRepository metodos;
    @Autowired PedidoEstadoPagoInitializer evolucion;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired JwtService jwt;
    @Autowired MockMvc mvc;
    @Autowired Clock reloj;

    private Usuario admin;
    private PedidoResponse pedido;
    private LocalDate hoy;
    private long efectivo;
    private long transferencia;

    @BeforeEach
    void preparar() {
        admin = usuario(CodigoRol.ADMINISTRADOR);
        // Las cajas y cobros confirmados de otros escenarios permanecen en el esquema de prueba.
        cajas.listarCajas(com.miraquetelavo.lavanderia.caja.entity.EstadoCaja.ABIERTA, null, null,
                org.springframework.data.domain.Pageable.unpaged()).forEach(c -> cerrar(c.id()));
        hoy = LocalDate.now(reloj);
        var cliente = clientes.save(new Cliente("Ana", "Pérez", null, null, null));
        var prenda = prendas.save(new Prenda("Camisa " + UUID.randomUUID()));
        var servicio = servicios.save(new Servicio("Lavado " + UUID.randomUUID(), monto("10000"), ModalidadCobro.UNIDAD));
        pedido = pedidos.registrar(new CrearPedidoRequest(cliente.getId(), hoy, List.of(
                new DetallePedidoRequest(null, prenda.getId(), servicio.getId(), BigDecimal.ONE, null, null))), admin.getId());
        efectivo = metodos.findByCodigo("EFECTIVO").orElseThrow().getId();
        transferencia = metodos.findByCodigo("TRANSFERENCIA").orElseThrow().getId();
    }

    @Test
    void recuperaRespuestaOriginalTrasPerderlaYCerrarCaja() throws Exception {
        var caja = abrir();
        var clave = UUID.randomUUID();
        var anterior = mvc.perform(solicitudIdempotente(clave, "2000"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.usuarioId").value(admin.getId()))
                .andReturn().getResponse();

        mvc.perform(solicitudIdempotente(clave, "1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("IDEMPOTENCIA_CLAVE_REUTILIZADA"));
        cerrar(caja);
        var repetida = mvc.perform(solicitudIdempotente(clave, "2000.00"))
                .andExpect(status().isCreated()).andReturn().getResponse();
        verificarRespuestaOriginal(anterior, repetida);

        try (var app = EsquemaPostgresql.reinicio(CobroPostgresqlTest.class).run(
                "--server.port=0", "--lavanderia.jwt.secret=${random.value}${random.value}",
                "--spring.jpa.show-sql=false", "--lavanderia.idempotencia.financiera.requerida=true")) {
            var token = app.getBean(JwtService.class)
                    .emitir(admin.getId(), admin.getVersionCredenciales().toString()).accessToken();
            var mvcReiniciado = org.springframework.test.web.servlet.setup.MockMvcBuilders
                    .webAppContextSetup((org.springframework.web.context.WebApplicationContext) app)
                    .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                    .build();
            mvcReiniciado.perform(post("/api/pedidos/{id}/cobros", pedido.id()).header(AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(cuerpo("2000")))
                    .andExpect(status().isBadRequest());
            var recuperada = mvcReiniciado.perform(solicitudIdempotente(clave, "2000", "Bearer " + token))
                    .andExpect(status().isCreated()).andReturn().getResponse();
            verificarRespuestaOriginal(anterior, recuperada);
        }
        verificarOperacionUnica(caja, clave);
    }

    @Test
    void elMismoUuidDeOtroUsuarioNoRecuperaElCobroAjeno() throws Exception {
        abrir();
        var clave = UUID.randomUUID();
        var empleado = usuario(CodigoRol.EMPLEADO);
        var primero = mvc.perform(solicitudIdempotente(clave, "1000"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.usuarioId").value(admin.getId()))
                .andReturn().getResponse();
        var segundo = mvc.perform(solicitudIdempotente(clave, "1000", bearer(empleado)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.usuarioId").value(empleado.getId()))
                .andReturn().getResponse();
        assertThat(segundo.getHeader(LOCATION)).isNotEqualTo(primero.getHeader(LOCATION));
        assertThat(registros(clave)).isEqualTo(2);
        verificarSaldo("2000", "8000", "PARCIAL");
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
                    var respuesta = mvc.perform(solicitudIdempotente(clave, "2000"))
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
                mvc.perform(solicitudIdempotente(clave, "2000"))
                        .andExpect(status().isConflict())
                        .andExpect(header().string("Retry-After", "1"))
                        .andExpect(jsonPath("$.codigo").value("IDEMPOTENCIA_EN_CURSO"));
            } finally {
                confirmar.countDown();
            }
            var original = primera.get(10, TimeUnit.SECONDS);
            var reintento = mvc.perform(solicitudIdempotente(clave, "2000"))
                    .andExpect(status().isCreated()).andReturn().getResponse();
            verificarRespuestaOriginal(original, reintento);
        }
        verificarOperacionUnica(caja, clave);
    }

    @Test
    void permiteReusarLaClaveTrasUnErrorDeNegocioYUnRollbackAntesDelCommit() throws Exception {
        var caja = abrir();
        var clave = UUID.randomUUID();
        mvc.perform(solicitudIdempotente(clave, "11000")).andExpect(status().isConflict());
        assertThat(registros(clave)).isZero();

        tx.executeWithoutResult(t -> {
            try {
                mvc.perform(solicitudIdempotente(clave, "2000")).andExpect(status().isCreated());
                assertThat(registros(clave)).isEqualTo(1);
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
            t.setRollbackOnly();
        });

        assertThat(registros(clave)).isZero();
        assertThat(cobros.listar(pedido.id(), org.springframework.data.domain.Pageable.unpaged())).isEmpty();
        assertThat(movimientos.consultarSaldo(caja).totalIngresos()).isEqualByComparingTo("0");
        mvc.perform(solicitudIdempotente(clave, "2000")).andExpect(status().isCreated());
        verificarOperacionUnica(caja, clave);
    }

    private void verificarOperacionUnica(long caja, UUID clave) {
        verificarSaldo("2000", "8000", "PARCIAL");
        assertThat(cobros.listar(pedido.id(), org.springframework.data.domain.Pageable.unpaged())).hasSize(1);
        assertThat(movimientos.listar(caja, null, null, null, null, org.springframework.data.domain.Pageable.unpaged()))
                .hasSize(1);
        assertThat(movimientos.consultarSaldo(caja).totalIngresos()).isEqualByComparingTo("2000");
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
        return post("/api/pedidos/{id}/cobros", pedido.id()).header(AUTHORIZATION, token)
                .header("Idempotency-Key", clave.toString()).contentType("application/json").content(cuerpo(importe));
    }

    private static void verificarRespuestaOriginal(MockHttpServletResponse original, MockHttpServletResponse repetida) {
        assertThat(repetida.getContentAsByteArray()).isEqualTo(original.getContentAsByteArray());
        assertThat(repetida.getHeader("Location")).isEqualTo(original.getHeader("Location"));
    }

    @Test
    void registraParcialesLuegoCompletaYArqueaEfectivoPorSeparado() {
        var caja = abrir();
        verificarSaldo("0", "10000", "PENDIENTE");
        var primero = cobrar("2000", efectivo);
        verificarSaldo("2000", "8000", "PARCIAL");
        cobrar("3000", transferencia);
        verificarSaldo("5000", "5000", "PARCIAL");
        cobrar("5000", transferencia);
        verificarSaldo("10000", "0", "PAGADO");
        assertThatThrownBy(() -> cobrar("1", efectivo)).isInstanceOf(ConflictoException.class)
                .hasMessageContaining("totalmente");
        var movimiento = movimientos.obtener(primero.movimientoCajaId());
        assertThat(movimiento.tipoMovimientoCodigo()).isEqualTo("COBRO_PEDIDO");
        assertThat(movimiento.metodoPagoCodigo()).isEqualTo("EFECTIVO");
        assertThat(movimiento.usuarioId()).isEqualTo(admin.getId());
        assertThat(movimiento.monto()).isEqualByComparingTo(primero.montoCobrado());
        assertThat(movimiento.fechaHora()).isEqualTo(primero.fechaCobro());
        assertThat(movimiento.descripcion()).isEqualTo("Cobro del pedido #" + pedido.id());
        assertThat(pedidos.obtener(pedido.id())).isEqualTo(pedido);
        var cierre = cajas.cerrarCaja(caja, new CerrarCajaRequest(monto("2100")), admin.getId());
        assertThat(cierre.montoFinalCalculado()).isEqualByComparingTo("10100");
        assertThat(cierre.efectivoEsperado()).isEqualByComparingTo("2100");
        assertThat(cierre.diferenciaArqueo()).isEqualByComparingTo("0");
    }

    @Test
    void atiendeRolesJwtLocationHistorialPaginadoYSaldo() throws Exception {
        abrir();
        var empleado = usuario(CodigoRol.EMPLEADO);
        var respuesta = mvc.perform(post("/api/pedidos/{id}/cobros", pedido.id()).header(AUTHORIZATION, bearer(empleado))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo("4000")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.usuarioId").value(empleado.getId()))
                .andReturn().getResponse();
        mvc.perform(get(respuesta.getHeader(LOCATION)).header(AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.montoCobrado").value(4000));
        mvc.perform(post("/api/pedidos/{id}/cobros", pedido.id()).header(AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo("6000")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.usuarioId").value(admin.getId()));
        mvc.perform(get("/api/pedidos/{id}/cobros", pedido.id()).header(AUTHORIZATION, bearer(empleado))
                        .param("page", "1").param("size", "1").param("sort", "id,asc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].montoCobrado").value(4000));
        mvc.perform(get("/api/pedidos/{id}/saldo", pedido.id()).header(AUTHORIZATION, bearer(empleado)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estadoPagoCodigo").value("PAGADO"))
                .andExpect(jsonPath("$.saldoPendiente").value(0));
        mvc.perform(get("/api/pedidos/{id}/saldo", pedido.id())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/pedidos/{id}/cobros", pedido.id()).header(AUTHORIZATION, "Bearer invalido")
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo("1"))).andExpect(status().isUnauthorized());
        mvc.perform(delete(respuesta.getHeader(LOCATION)).header(AUTHORIZATION, bearer(empleado)))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1.001", "100000000"})
    void rechazaImportesInvalidosEnHttpYServicio(String importe) throws Exception {
        mvc.perform(post("/api/pedidos/{id}/cobros", pedido.id()).header(AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo(importe)))
                .andExpect(status().isBadRequest());
        assertThatThrownBy(() -> cobrar(importe, efectivo)).isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void rechazaCampoDeIdentidadEnElCuerpo() throws Exception {
        abrir();
        mvc.perform(post("/api/pedidos/{id}/cobros", pedido.id()).header(AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monto\":1,\"metodoPagoId\":" + efectivo + ",\"usuarioId\":999}"))
                .andExpect(status().isBadRequest());
        verificarSaldo("0", "10000", "PENDIENTE");
    }

    @Test
    void validaCajaPedidoMetodoYEstadoOperativo() {
        assertThatThrownBy(() -> cobrar("1", efectivo)).isInstanceOf(ConflictoException.class);
        var caja = abrir();
        assertThatThrownBy(() -> cobros.registrar(Long.MAX_VALUE, solicitud("1", efectivo), admin.getId()))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> cobrar("1", Long.MAX_VALUE)).isInstanceOf(RecursoNoEncontradoException.class);
        tx.executeWithoutResult(t -> {
            jdbc.update("update metodos_pago set activo = false where id_metodo_pago = ?", efectivo);
            assertThatThrownBy(() -> cobrar("1", efectivo)).isInstanceOf(SolicitudInvalidaException.class);
            t.setRollbackOnly();
        });
        assertThatThrownBy(() -> cobrar("10001", efectivo)).isInstanceOf(ConflictoException.class);
        cerrar(caja);
        assertThatThrownBy(() -> cobrar("1", efectivo)).isInstanceOf(ConflictoException.class);
        abrir();
        cancelar();
        assertThatThrownBy(() -> cobrar("1", efectivo)).isInstanceOf(ConflictoException.class)
                .hasMessageContaining("cancelado");
    }

    @Test
    void entregaConSaldoYPermiteCompletarElCobro() {
        abrir();
        for (var estado : List.of("EN_PROCESO", "LISTO", "ENTREGADO")) {
            pedidos.cambiarEstado(pedido.id(), new CambiarEstadoPedidoRequest(estado, null), admin.getId());
        }
        cobrar("10000", efectivo);
        verificarSaldo("10000", "0", "PAGADO");
        assertThat(pedidos.obtener(pedido.id()).estadoPedido().codigo()).isEqualTo("ENTREGADO");
    }

    @Test
    void protegePutYAmbasRutasDeCancelacion() throws Exception {
        abrir();
        cobrar("1", efectivo);
        assertThatThrownBy(this::actualizar).isInstanceOf(ConflictoException.class);
        mvc.perform(patch("/api/pedidos/{id}/cancelar", pedido.id()).header(AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"observacion\":\"Motivo\"}"))
                .andExpect(status().isConflict());
        mvc.perform(patch("/api/pedidos/{id}/estado", pedido.id()).header(AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigoEstado\":\"CANCELADO\",\"observacion\":\"Motivo\"}"))
                .andExpect(status().isConflict());
        assertThat(pedidos.obtener(pedido.id())).isEqualTo(pedido);
    }

    @ParameterizedTest
    @ValueSource(strings = {"cobros", "movimientos_caja", "pedidos"})
    void revierteTodaLaOperacionAnteFallaDeCualquieraDeLasTresEscrituras(String tabla) {
        var caja = abrir();
        assertThatThrownBy(() -> tx.executeWithoutResult(t -> {
            jdbc.execute("alter table " + tabla + " add constraint ck_rechazar_escritura_cobro_test check (false) not valid");
            cobrar("1", efectivo);
        })).isInstanceOf(DataIntegrityViolationException.class);
        verificarSaldo("0", "10000", "PENDIENTE");
        assertThat(movimientos.consultarSaldo(caja).totalIngresos()).isEqualByComparingTo("0");
    }

    @Test
    void impideDuplicarMovimientoYVerificaRestriccionesEIndice() {
        abrir();
        var cobro = cobrar("1", efectivo);
        assertThatThrownBy(() -> jdbc.update("""
                insert into cobros (id_pedido, id_mov_caja, fecha_cobro, monto_cobrado)
                values (?, ?, now(), 1)
                """, pedido.id(), cobro.movimientoCajaId())).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForList("""
                select conname from pg_constraint
                where convalidated and ((conrelid = 'cobros'::regclass and contype in ('f','c'))
                    or (conrelid = 'pedidos'::regclass and conname = 'fk_pedidos_estado_pago'))
                """, String.class)).containsExactlyInAnyOrder("fk_cobros_pedido", "fk_cobros_movimiento",
                        "ck_cobros_monto_positivo", "fk_pedidos_estado_pago");
        assertThat(jdbc.queryForObject("""
                select indexdef from pg_indexes where schemaname = current_schema()
                and indexname = 'ix_cobros_pedido_fecha_id'
                """, String.class)).contains("(id_pedido, fecha_cobro DESC, id_cobro DESC)");
        assertThat(jdbc.queryForObject("""
                select attnotnull from pg_attribute where attrelid = 'pedidos'::regclass and attname = 'id_estado_pago'
                """, Boolean.class)).isTrue();
    }

    @Test
    void evolucionaFilasAnterioresYPreservaEstadosAsignadosAlReiniciar() {
        abrir();
        var cobro = cobrar("10000", efectivo);
        tx.executeWithoutResult(t -> {
            jdbc.execute("alter table pedidos alter column id_estado_pago drop not null");
            var anterior = jdbc.queryForObject("""
                    insert into pedidos (id_cliente, id_usuario, id_estado_pedido, fecha_ingreso, fecha_entrega_est, total_pedido)
                    select id_cliente, id_usuario, id_estado_pedido, fecha_ingreso, fecha_entrega_est, total_pedido
                    from pedidos where id_pedido = ? returning id_pedido
                    """, Long.class, pedido.id());
            evolucion.run(null);
            assertThat(cobros.consultarSaldo(anterior).estadoPagoCodigo()).isEqualTo("PENDIENTE");
            verificarSaldo("10000", "0", "PAGADO");
        });
        try (var app = EsquemaPostgresql.reinicio(CobroPostgresqlTest.class).run(
                "--server.port=0", "--lavanderia.jwt.secret=${random.value}${random.value}",
                "--spring.jpa.show-sql=false")) {
            var servicio = app.getBean(CobroService.class);
            assertThat(servicio.obtener(cobro.id())).isEqualTo(cobro);
            assertThat(servicio.consultarSaldo(pedido.id()).estadoPagoCodigo()).isEqualTo("PAGADO");
        }
    }

    @Test
    void soloUnoDeDosCobrosConcurrentesPuedeSuperarLaMitadDelTotal() throws Exception {
        abrir();
        var resultados = competir(() -> cobrar("7000", efectivo), () -> cobrar("7000", efectivo));
        assertThat(resultados).filteredOn(CobroResponse.class::isInstance).hasSize(1);
        assertThat(resultados).filteredOn(ConflictoException.class::isInstance).hasSize(1);
        verificarSaldo("7000", "3000", "PARCIAL");
    }

    @Test
    void consultaElUltimoEstadoConfirmadoMientrasElCobroEstaSinConfirmar() throws Exception {
        abrir();
        var preparado = new CountDownLatch(1);
        var confirmar = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var escritura = executor.submit(() -> tx.executeWithoutResult(t -> {
                cobrar("7000", efectivo);
                preparado.countDown();
                try {
                    if (!confirmar.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("No se completó la lectura concurrente.");
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
            }));
            try {
                assertThat(preparado.await(5, TimeUnit.SECONDS)).isTrue();
                verificarSaldo("0", "10000", "PENDIENTE");
            } finally {
                confirmar.countDown();
            }
            escritura.get(10, TimeUnit.SECONDS);
        }
        verificarSaldo("7000", "3000", "PARCIAL");
    }

    @Test
    void serializaCobroYCierre() throws Exception {
        var caja = abrir();
        var resultado = competir(() -> cobrar("7000", efectivo), () -> cerrar(caja));
        boolean cobrado = resultado.getFirst() instanceof CobroResponse;
        assertThat(movimientos.consultarSaldo(caja).efectivoEsperado()).isEqualByComparingTo(cobrado ? "7100" : "100");
        verificarSaldo(cobrado ? "7000" : "0", cobrado ? "3000" : "10000", cobrado ? "PARCIAL" : "PENDIENTE");
    }

    @Test
    void serializaCobroYModificacionDelTotal() throws Exception {
        abrir();
        var resultado = competir(() -> cobrar("7000", efectivo), this::actualizar);
        assertThat(resultado.getFirst()).isInstanceOf(CobroResponse.class);
        var total = resultado.getLast() instanceof PedidoResponse editado ? editado.total() : pedido.total();
        verificarSaldo("7000", total.subtract(monto("7000")).toPlainString(), "PARCIAL");
    }

    @Test
    void cobroYCancelacionSonMutuamenteExcluyentes() throws Exception {
        abrir();
        var resultado = competir(() -> cobrar("7000", efectivo), this::cancelar);
        assertThat(resultado).filteredOn(ConflictoException.class::isInstance).hasSize(1);
        boolean cobrado = resultado.getFirst() instanceof CobroResponse;
        assertThat(pedidos.obtener(pedido.id()).estadoPedido().codigo()).isEqualTo(cobrado ? "PENDIENTE" : "CANCELADO");
        verificarSaldo(cobrado ? "7000" : "0", cobrado ? "3000" : "10000", cobrado ? "PARCIAL" : "PENDIENTE");
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

    private PedidoResponse actualizar() {
        var detalle = pedido.detalles().getFirst();
        return pedidos.actualizar(pedido.id(), new ActualizarPedidoRequest(hoy, List.of(
                new DetallePedidoRequest(detalle.id(), detalle.prendaId(), detalle.servicioId(), monto("2"), null, null))));
    }

    private PedidoResponse cancelar() {
        return pedidos.cancelar(pedido.id(), new CancelarPedidoRequest("Motivo"), admin.getId());
    }

    private void verificarSaldo(String acumulado, String pendiente, String estado) {
        var saldo = cobros.consultarSaldo(pedido.id());
        assertThat(saldo.totalCobrado()).isEqualByComparingTo(acumulado);
        assertThat(saldo.saldoPendiente()).isEqualByComparingTo(pendiente);
        assertThat(saldo.estadoPagoCodigo()).isEqualTo(estado);
    }

    private long abrir() {
        return cajas.abrirCaja(new AbrirCajaRequest(monto("100")), admin.getId()).id();
    }

    private Object cerrar(long caja) {
        var esperado = movimientos.consultarSaldo(caja).efectivoDisponible();
        return cajas.cerrarCaja(caja, new CerrarCajaRequest(esperado), admin.getId());
    }

    private CobroResponse cobrar(String importe, long metodo) {
        return cobros.registrar(pedido.id(), solicitud(importe, metodo), admin.getId());
    }

    private RegistrarCobroRequest solicitud(String importe, long metodo) {
        return new RegistrarCobroRequest(monto(importe), metodo);
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

    private String cuerpo(String importe) {
        return "{\"monto\":" + importe + ",\"metodoPagoId\":" + efectivo + "}";
    }
}
