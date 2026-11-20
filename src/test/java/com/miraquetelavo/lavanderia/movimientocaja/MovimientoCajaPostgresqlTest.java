package com.miraquetelavo.lavanderia.movimientocaja;

import com.miraquetelavo.lavanderia.soporte.EsquemaPostgresql;
import com.miraquetelavo.lavanderia.soporte.IntegracionPostgresql;
import com.miraquetelavo.lavanderia.caja.dto.AbrirCajaRequest;
import com.miraquetelavo.lavanderia.caja.dto.CerrarCajaRequest;
import com.miraquetelavo.lavanderia.caja.entity.EstadoCaja;
import com.miraquetelavo.lavanderia.caja.repository.CajaRepository;
import com.miraquetelavo.lavanderia.caja.service.CajaService;
import com.miraquetelavo.lavanderia.common.exception.ConflictoException;
import com.miraquetelavo.lavanderia.common.idempotencia.IdempotenciaFinanciera;
import com.miraquetelavo.lavanderia.metodopago.repository.MetodoPagoRepository;
import com.miraquetelavo.lavanderia.movimientocaja.dto.MovimientoCajaResponse;
import com.miraquetelavo.lavanderia.movimientocaja.dto.RegistrarMovimientoManualRequest;
import com.miraquetelavo.lavanderia.movimientocaja.service.MovimientoCajaService;
import com.miraquetelavo.lavanderia.rol.entity.CodigoRol;
import com.miraquetelavo.lavanderia.rol.repository.RolRepository;
import com.miraquetelavo.lavanderia.security.JwtService;
import com.miraquetelavo.lavanderia.tipomovimientocaja.repository.TipoMovimientoCajaRepository;
import com.miraquetelavo.lavanderia.usuario.entity.Usuario;
import com.miraquetelavo.lavanderia.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@IntegracionPostgresql("movimientos_caja")
@Sql(scripts = "/db/migration/V032__idempotencia_financiera.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "lavanderia.jwt.secret=${random.value}${random.value}"
})
class MovimientoCajaPostgresqlTest {

    @Autowired CajaService cajas;
    @Autowired CajaRepository repositorioCajas;
    @Autowired MovimientoCajaService movimientos;
    @Autowired MetodoPagoRepository metodos;
    @Autowired TipoMovimientoCajaRepository tipos;
    @Autowired RolRepository roles;
    @Autowired UsuarioRepository usuarios;
    @Autowired JwtService jwt;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transacciones;
    @Autowired IdempotenciaFinanciera idempotencia;

    @BeforeEach
    void limpiar() {
        jdbc.update("delete from movimientos_caja");
        jdbc.update("delete from cajas");
    }

    @Test
    void conservaUnSoloIngresoYEgresoYRechazaReutilizarLaClaveEntreTipos() throws Exception {
        var admin = usuario(CodigoRol.ADMINISTRADOR);
        var caja = abrir(admin, "100");
        var token = bearer(admin);
        var efectivo = metodo("EFECTIVO");
        var claveIngreso = UUID.randomUUID().toString();
        var ingreso = post("/api/movimientos-caja/ingresos-manuales").header(HttpHeaders.AUTHORIZATION, token)
                .header("Idempotency-Key", claveIngreso).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(caja, "20", efectivo));
        var primero = mvc.perform(ingreso).andExpect(status().isCreated()).andReturn().getResponse();
        mvc.perform(ingreso).andExpect(status().isCreated())
                .andExpect(content().bytes(primero.getContentAsByteArray()));

        var rutaEgreso = "/api/movimientos-caja/egresos-manuales";
        mvc.perform(post(rutaEgreso).header(HttpHeaders.AUTHORIZATION, token)
                        .header("Idempotency-Key", claveIngreso).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(caja, "20", efectivo)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("IDEMPOTENCIA_CLAVE_REUTILIZADA"));
        var egreso = post(rutaEgreso).header(HttpHeaders.AUTHORIZATION, token)
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(caja, "5", efectivo));
        var segundo = mvc.perform(egreso).andExpect(status().isCreated()).andReturn().getResponse();
        mvc.perform(egreso).andExpect(status().isCreated())
                .andExpect(content().bytes(segundo.getContentAsByteArray()));

        assertThat(jdbc.queryForObject("select count(*) from movimientos_caja where id_caja = ?", Integer.class, caja))
                .isEqualTo(2);
        assertThat(movimientos.consultarSaldo(caja).efectivoDisponible()).isEqualByComparingTo("115");
    }

    @Test
    void unaSegundaSolicitudConLaMismaClaveVeElIntentoEnCurso() throws Exception {
        var admin = usuario(CodigoRol.ADMINISTRADOR);
        long caja = abrir(admin, "100");
        long efectivo = metodo("EFECTIVO");
        var clave = UUID.randomUUID().toString();
        try (var ejecutor = Executors.newSingleThreadExecutor()) {
            transacciones.executeWithoutResult(tx -> {
                try {
                    mvc.perform(postIngreso(admin, caja, efectivo, clave)).andExpect(status().isCreated());
                    ejecutor.submit(() -> {
                        mvc.perform(postIngreso(admin, caja, efectivo, clave)).andExpect(status().isConflict())
                                .andExpect(jsonPath("$.codigo").value("IDEMPOTENCIA_EN_CURSO"))
                                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "1"));
                        return null;
                    }).get(10, TimeUnit.SECONDS);
                } catch (Exception ex) {
                    throw new IllegalStateException(ex);
                }
            });
        }
        mvc.perform(postIngreso(admin, caja, efectivo, clave)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.cajaId").value(caja));
        assertThat(contar()).isEqualTo(1);
        assertThat(movimientos.consultarSaldo(caja).saldoGeneral()).isEqualByComparingTo("120");
    }

    @Test
    void conservaLaCajaOriginalTrasRollbackCambioDeJornadaYReinicio() throws Exception {
        var admin = usuario(CodigoRol.ADMINISTRADOR);
        long cajaA = abrir(admin, "100");
        long efectivo = metodo("EFECTIVO");
        var claveConfirmada = UUID.randomUUID().toString();
        var claveRevertida = UUID.randomUUID().toString();
        var confirmada = mvc.perform(postIngreso(admin, cajaA, efectivo, claveConfirmada))
                .andExpect(status().isCreated()).andReturn().getResponse();

        transacciones.executeWithoutResult(tx -> {
            try {
                mvc.perform(postIngreso(admin, cajaA, efectivo, claveRevertida)).andExpect(status().isCreated());
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
            tx.setRollbackOnly();
        });
        assertThat(contar()).isEqualTo(1);
        cerrar(cajaA, "120", admin);
        long cajaB = abrir(admin, "70");
        mvc.perform(postIngreso(admin, cajaA, efectivo, claveRevertida)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CAJA_JORNADA_CAMBIADA"));
        mvc.perform(postIngreso(admin, cajaB, efectivo, claveConfirmada)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("IDEMPOTENCIA_CLAVE_REUTILIZADA"));

        var cabeceras = new HttpHeaders();
        cabeceras.set("Idempotency-Key", claveConfirmada);
        try (var reinicio = EsquemaPostgresql.reinicio(MovimientoCajaPostgresqlTest.class).run(
                "--server.port=0", "--lavanderia.jwt.secret=${random.value}${random.value}")) {
            var repetida = MockMvcBuilders.webAppContextSetup((WebApplicationContext) reinicio)
                    .apply(springSecurity())
                    .build();
            String token = reinicio.getBean(JwtService.class)
                    .emitir(admin.getId(), admin.getVersionCredenciales().toString()).accessToken();
            repetida.perform(post("/api/movimientos-caja/ingresos-manuales")
                            .headers(cabeceras).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(cuerpo(cajaA, "20", efectivo)))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.cajaId").value(cajaA))
                    .andExpect(header().string(HttpHeaders.LOCATION, confirmada.getHeader(HttpHeaders.LOCATION)))
                    .andExpect(content().bytes(confirmada.getContentAsByteArray()));
        }
        assertThat(contar()).isEqualTo(1);
        assertThat(movimientos.consultarSaldo(cajaA).saldoGeneral()).isEqualByComparingTo("120");
        assertThat(movimientos.consultarSaldo(cajaB).saldoGeneral()).isEqualByComparingTo("70");
    }

    @Test
    void recuperaV032HistoricoSoloParaLaCajaGuardada() throws Exception {
        var admin = usuario(CodigoRol.ADMINISTRADOR);
        long cajaA = abrir(admin, "100");
        long efectivo = metodo("EFECTIVO");
        var clave = UUID.randomUUID().toString();
        var cabeceras = new HttpHeaders();
        cabeceras.set("Idempotency-Key", clave);
        var resultado = idempotencia.crear(cabeceras, admin.getId(),
                IdempotenciaFinanciera.Operacion.INGRESO_MANUAL,
                new IdempotenciaFinanciera.Contenido(
                        null, new BigDecimal("20"), efectivo, "Movimiento manual"), MovimientoCajaResponse.class,
                () -> {
                    var movimiento = movimientos.registrarIngreso(solicitud(cajaA, "20", "EFECTIVO"), admin.getId());
                    return ResponseEntity.created(URI.create("/api/movimientos-caja/" + movimiento.id()))
                            .body(movimiento);
                });
        cerrar(cajaA, "120", admin);
        long cajaB = abrir(admin, "70");
        mvc.perform(postIngreso(admin, cajaB, efectivo, clave)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("IDEMPOTENCIA_CLAVE_REUTILIZADA"));
        mvc.perform(postIngreso(admin, cajaA, efectivo, clave)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(resultado.getBody().id()))
                .andExpect(header().string(HttpHeaders.LOCATION, resultado.getHeaders().getLocation().toString()));
        assertThat(contar()).isEqualTo(1);
    }

    private MockHttpServletRequestBuilder postIngreso(
            Usuario usuario, long caja, long metodo, String clave) {
        return post("/api/movimientos-caja/ingresos-manuales").header(HttpHeaders.AUTHORIZATION, bearer(usuario))
                .header("Idempotency-Key", clave).contentType(MediaType.APPLICATION_JSON).content(cuerpo(caja, "20", metodo));
    }

    @Test
    void ambosRolesRegistranConJwtYLocationIdentificaElMovimiento() throws Exception {
        var admin = usuario(CodigoRol.ADMINISTRADOR);
        var empleado = usuario(CodigoRol.EMPLEADO);
        long caja = abrir(admin, "100");
        long efectivo = metodo("EFECTIVO");
        var ingreso = mvc.perform(post("/api/movimientos-caja/ingresos-manuales")
                        .header(HttpHeaders.AUTHORIZATION, bearer(empleado))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"monto":20,"metodoPagoId":%d,"descripcion":"  Reposición  ",
                                 "cajaId":%d,"usuarioId":999,"tipoMovimientoCodigo":"COBRO_PEDIDO",
                                 "fechaHora":"2020-01-01T00:00:00"}
                                """.formatted(efectivo, caja)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.cajaId").value(caja))
                .andExpect(jsonPath("$.usuarioId").value(empleado.getId()))
                .andExpect(jsonPath("$.tipoMovimientoCodigo").value("INGRESO_MANUAL"))
                .andExpect(jsonPath("$.descripcion").value("Reposición"))
                .andReturn().getResponse();
        mvc.perform(get(ingreso.getHeader(HttpHeaders.LOCATION)).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.monto").value(20))
                .andExpect(jsonPath("$.password").doesNotExist());
        mvc.perform(post("/api/movimientos-caja/egresos-manuales").header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo(caja, "5", efectivo)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.tipoMovimientoCodigo").value("EGRESO_MANUAL"));
        mvc.perform(get("/api/cajas/{id}/saldo", caja).header(HttpHeaders.AUTHORIZATION, bearer(empleado)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.saldoGeneral").value(115))
                .andExpect(jsonPath("$.efectivoDisponible").value(115));
    }

    @ParameterizedTest
    @CsvSource({"25000,0", "24900,-100", "25100,100"})
    void cierraConTransferenciaTarjetaYArqueoFirmado(String contado, String diferencia) {
        var admin = usuario(CodigoRol.ADMINISTRADOR);
        long caja = abrir(admin, "20000");
        movimientos.registrarIngreso(solicitud(caja, "15000", "TRANSFERENCIA"), admin.getId());
        movimientos.registrarIngreso(solicitud(caja, "5000", "EFECTIVO"), admin.getId());
        movimientos.registrarIngreso(solicitud(caja, "2000", "TARJETA"), admin.getId());
        movimientos.registrarEgreso(solicitud(caja, "1000", "TRANSFERENCIA"), admin.getId());

        var abierto = movimientos.consultarSaldo(caja);
        assertThat(abierto.totalIngresos()).isEqualByComparingTo("22000");
        assertThat(abierto.totalEgresos()).isEqualByComparingTo("1000");
        assertThat(abierto.saldoGeneral()).isEqualByComparingTo("41000");
        assertThat(abierto.efectivoDisponible()).isEqualByComparingTo("25000");
        assertThatThrownBy(() -> movimientos.registrarEgreso(solicitud(caja, "26000", "EFECTIVO"), admin.getId()))
                .isInstanceOf(ConflictoException.class).hasMessageContaining("efectivo disponible");

        var cerrada = cerrar(caja, contado, admin);
        assertThat(cerrada.montoFinalCalculado()).isEqualByComparingTo("41000");
        assertThat(cerrada.efectivoEsperado()).isEqualByComparingTo("25000");
        assertThat(cerrada.diferenciaArqueo()).isEqualByComparingTo(diferencia);
        var saldo = movimientos.consultarSaldo(caja);
        assertThat(saldo.estadoCaja()).isEqualTo(EstadoCaja.CERRADA);
        assertThat(saldo.efectivoContado()).isEqualByComparingTo(contado);
        assertThat(saldo.diferenciaArqueo()).isEqualByComparingTo(diferencia);
        assertThat(saldo.usuarioCierreId()).isEqualTo(admin.getId());
        assertThat(movimientos.listar(caja, null, null, null, null, PageRequest.of(0, 20)).getTotalElements())
                .isEqualTo(4);
    }

    @Test
    void clasificaLosCuatroCodigosYConservaSaldoGeneralNegativoAlReiniciar() {
        var admin = usuario(CodigoRol.ADMINISTRADOR);
        long caja = abrir(admin, "0");
        movimientos.registrarIngreso(solicitud(caja, "2", "TARJETA"), admin.getId());
        movimientos.registrarEgreso(solicitud(caja, "4", "TRANSFERENCIA"), admin.getId());
        // Datos del escenario para verificar la clasificación de los códigos reservados.
        insertar(caja, tipo("COBRO_PEDIDO"), metodo("EFECTIVO"), admin.getId(), "3", null);
        insertar(caja, tipo("PAGO_PROVEEDOR"), metodo("EFECTIVO"), admin.getId(), "2", null);
        var cerrada = cerrar(caja, "1", admin);
        assertThat(cerrada.montoFinalCalculado()).isEqualByComparingTo("-1");
        assertThat(cerrada.efectivoEsperado()).isEqualByComparingTo("1");

        try (var reinicio = EsquemaPostgresql.reinicio(MovimientoCajaPostgresqlTest.class).run(
                "--server.port=0", "--lavanderia.jwt.secret=${random.value}${random.value}",
                "--spring.jpa.show-sql=false")) {
            var servicio = reinicio.getBean(MovimientoCajaService.class);
            var saldo = servicio.consultarSaldo(caja);
            assertThat(saldo.saldoGeneral()).isEqualByComparingTo("-1");
            assertThat(saldo.totalIngresos()).isEqualByComparingTo("5");
            assertThat(saldo.totalEgresos()).isEqualByComparingTo("6");
            assertThat(saldo.ingresosEfectivo()).isEqualByComparingTo("3");
            assertThat(saldo.egresosEfectivo()).isEqualByComparingTo("2");
            assertThat(servicio.listar(caja, null, null, null, null, PageRequest.of(0, 20)).getTotalElements())
                    .isEqualTo(4);
        }
    }

    @Test
    void informaRecursosInexistentesYConflictosSinPersistir() throws Exception {
        var admin = usuario(CodigoRol.ADMINISTRADOR);
        var token = bearer(admin);
        long efectivo = metodo("EFECTIVO");
        mvc.perform(get("/api/cajas/999999/saldo").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/cajas/999999/movimientos").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/movimientos-caja/ingresos-manuales").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo(999999, "1", efectivo)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("CAJA_JORNADA_CAMBIADA"));
        long caja = abrir(admin, "0");
        mvc.perform(post("/api/movimientos-caja/ingresos-manuales").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo(caja, "1", 999999)))
                .andExpect(status().isNotFound());
        jdbc.update("update metodos_pago set activo = false where id_metodo_pago = ?", efectivo);
        try {
            mvc.perform(post("/api/movimientos-caja/ingresos-manuales").header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON).content(cuerpo(caja, "1", efectivo)))
                    .andExpect(status().isNotFound());
        } finally {
            jdbc.update("update metodos_pago set activo = true where id_metodo_pago = ?", efectivo);
        }
        jdbc.update("update tipos_movimiento_caja set activo = false where codigo = 'INGRESO_MANUAL'");
        try {
            mvc.perform(post("/api/movimientos-caja/ingresos-manuales").header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON).content(cuerpo(caja, "1", efectivo)))
                    .andExpect(status().isConflict());
        } finally {
            jdbc.update("update tipos_movimiento_caja set activo = true where codigo = 'INGRESO_MANUAL'");
        }
        cerrar(caja, "0", admin);
        mvc.perform(post("/api/movimientos-caja/ingresos-manuales").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo(caja, "1", efectivo)))
                .andExpect(status().isConflict());
        assertThat(contar()).isZero();
    }

    @Test
    void paginaConFiltrosYDesempataPorId() throws Exception {
        var admin = usuario(CodigoRol.ADMINISTRADOR);
        var empleado = usuario(CodigoRol.EMPLEADO);
        long caja = abrir(admin, "100");
        var primera = movimientos.registrarIngreso(solicitud(caja, "10", "EFECTIVO"), admin.getId());
        var segunda = movimientos.registrarIngreso(solicitud(caja, "15", "EFECTIVO"), admin.getId());
        movimientos.registrarIngreso(solicitud(caja, "5", "TRANSFERENCIA"), admin.getId());
        movimientos.registrarEgreso(solicitud(caja, "5", "EFECTIVO"), admin.getId());
        jdbc.update("update movimientos_caja set fecha_hora = timestamp '2026-11-17 23:59:59.999999'");
        var fecha = LocalDate.of(2026, 11, 17).toString();
        mvc.perform(get("/api/cajas/{id}/movimientos", caja).header(HttpHeaders.AUTHORIZATION, bearer(empleado))
                        .param("tipo", "INGRESO_MANUAL").param("metodoPagoId", "" + metodo("EFECTIVO"))
                        .param("desde", fecha).param("hasta", fecha).param("size", "1").param("sort", "id,asc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(segunda.id()));
        var pagina = movimientos.listar(caja, "INGRESO_MANUAL", metodo("EFECTIVO"),
                LocalDate.parse(fecha), LocalDate.parse(fecha), PageRequest.of(1, 1));
        assertThat(pagina.getContent().getFirst().id()).isEqualTo(primera.id());
    }

    @Test
    void dosEgresosConcurrentesNoExcedenEfectivoDisponible() throws Exception {
        var admin = usuario(CodigoRol.ADMINISTRADOR);
        var empleado = usuario(CodigoRol.EMPLEADO);
        long caja = abrir(admin, "100");
        long efectivo = metodo("EFECTIVO");
        var barrera = new CyclicBarrier(2);
        try (var ejecutor = Executors.newFixedThreadPool(2)) {
            var a = ejecutor.submit(() -> egresoConcurrente(barrera, admin, caja, efectivo));
            var b = ejecutor.submit(() -> egresoConcurrente(barrera, empleado, caja, efectivo));
            assertThat(List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(contar()).isEqualTo(1);
        assertThat(movimientos.consultarSaldo(caja).efectivoDisponible()).isEqualByComparingTo("30");
    }

    @Test
    void elCierreIncluyeElMovimientoQueConsiguioPrimeroElBloqueo() throws Exception {
        var admin = usuario(CodigoRol.ADMINISTRADOR);
        long caja = abrir(admin, "100");
        var solicitud = solicitud(caja, "50", "EFECTIVO");
        var resultado = mientrasRetieneBloqueo(
                () -> movimientos.registrarIngreso(solicitud, admin.getId()),
                () -> cerrar(caja, "150", admin));
        assertThat(resultado.get(10, TimeUnit.SECONDS).efectivoEsperado()).isEqualByComparingTo("150");
        assertThat(contar()).isEqualTo(1);
    }

    @Test
    void unMovimientoQueEsperaAlCierreNoSeInsertaDespuesDelCommit() throws Exception {
        var admin = usuario(CodigoRol.ADMINISTRADOR);
        long caja = abrir(admin, "100");
        var solicitud = solicitud(caja, "50", "EFECTIVO");
        var resultado = mientrasRetieneBloqueo(
                () -> {
                    cerrar(caja, "100", admin);
                    repositorioCajas.flush();
                    abrir(admin, "85");
                },
                () -> catchThrowable(() -> movimientos.registrarIngreso(solicitud, admin.getId())));
        assertThat(resultado.get(10, TimeUnit.SECONDS)).isInstanceOf(ConflictoException.class)
                .hasMessageContaining("ya no está abierta");
        assertThat(contar()).isZero();
        assertThat(movimientos.consultarSaldo(cajas.obtenerCajaAbierta().id()).saldoGeneral())
                .isEqualByComparingTo("85");
    }

    @Test
    void validaRangoDeCierreYRevierteUnMovimientoConLaTransaccionExterior() {
        var admin = usuario(CodigoRol.ADMINISTRADOR);
        long caja = abrir(admin, "99999999.99");
        assertThatThrownBy(() -> movimientos.registrarIngreso(solicitud(caja, "0.01", "TARJETA"), admin.getId()))
                .isInstanceOf(ConflictoException.class).hasMessageContaining("rango");
        assertThatThrownBy(() -> transacciones.executeWithoutResult(estado -> {
            movimientos.registrarEgreso(solicitud(caja, "10", "EFECTIVO"), admin.getId());
            throw new IllegalStateException("Falla en la transacción exterior");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(contar()).isZero();
        assertThat(movimientos.consultarSaldo(caja).efectivoDisponible()).isEqualByComparingTo("99999999.99");
        insertar(caja, tipo("INGRESO_MANUAL"), metodo("TARJETA"), admin.getId(), "0.01", "Inserción externa");
        assertThatThrownBy(() -> cerrar(caja, "0", admin)).isInstanceOf(ConflictoException.class)
                .hasMessageContaining("rango");
        assertThat(cajas.obtenerPorId(caja).estado()).isEqualTo(EstadoCaja.ABIERTA);
    }

    @Test
    void postgresImponeCuatroClavesForaneasMontoYDescripcion() {
        var admin = usuario(CodigoRol.ADMINISTRADOR);
        long caja = abrir(admin, "0");
        long tipo = tipo("INGRESO_MANUAL");
        long metodo = metodo("EFECTIVO");
        long usuario = admin.getId();
        long ausente = 999999L;
        for (long[] fila : List.of(new long[]{ausente, tipo, metodo, usuario}, new long[]{caja, ausente, metodo, usuario},
                new long[]{caja, tipo, ausente, usuario}, new long[]{caja, tipo, metodo, ausente})) {
            assertThatThrownBy(() -> insertar(fila[0], fila[1], fila[2], fila[3], "1", "Movimiento"))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
        assertThatThrownBy(() -> insertar(caja, tipo, metodo, usuario, "0", "Movimiento"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertar(caja, tipo, metodo, usuario, "1", "  "))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertar(caja, tipo, metodo, usuario, "1", "x".repeat(251)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(contar()).isZero();
    }

    private <T> Future<T> mientrasRetieneBloqueo(Runnable primera, Callable<T> segunda) throws Exception {
        var listo = new CountDownLatch(1);
        var continuar = new CountDownLatch(1);
        try (var ejecutor = Executors.newFixedThreadPool(2)) {
            var a = ejecutor.submit(() -> transacciones.executeWithoutResult(estado -> {
                primera.run();
                listo.countDown();
                esperar(continuar);
            }));
            try {
                esperar(listo);
                var b = ejecutor.submit(segunda);
                long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                boolean espera;
                do {
                    espera = Boolean.TRUE.equals(jdbc.queryForObject("""
                            select exists (select 1 from pg_stat_activity
                                where pid <> pg_backend_pid() and wait_event_type = 'Lock'
                                and query like '%cajas%' and query ~ 'for (no key )?update')
                            """, Boolean.class));
                    if (!espera) {
                        Thread.sleep(20);
                    }
                } while (!espera && !b.isDone() && System.nanoTime() < limite);
                assertThat(espera).isTrue();
                continuar.countDown();
                a.get(10, TimeUnit.SECONDS);
                return b;
            } finally {
                continuar.countDown();
            }
        }
    }

    private static void esperar(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(error);
        }
    }

    private int egresoConcurrente(CyclicBarrier barrera, Usuario usuario, long caja, long efectivo) throws Exception {
        barrera.await(5, TimeUnit.SECONDS);
        return mvc.perform(post("/api/movimientos-caja/egresos-manuales")
                        .header(HttpHeaders.AUTHORIZATION, bearer(usuario)).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(caja, "70", efectivo))).andReturn().getResponse().getStatus();
    }

    private void insertar(long caja, long tipo, long metodo, long usuario, String monto, String descripcion) {
        jdbc.update("""
                insert into movimientos_caja (id_caja, id_tipo_mov, id_metodo, id_usuario, monto, descripcion, fecha_hora)
                values (?, ?, ?, ?, ?, ?, localtimestamp)
                """, caja, tipo, metodo, usuario, new BigDecimal(monto), descripcion);
    }

    private long contar() {
        return jdbc.queryForObject("select count(*) from movimientos_caja", Long.class);
    }

    private Usuario usuario(CodigoRol codigo) {
        String nombre = UUID.randomUUID().toString();
        return usuarios.save(new Usuario(nombre, "hash-prueba", "Ana", nombre + "@example.com",
                roles.findByNombre(codigo).orElseThrow()));
    }

    private String bearer(Usuario usuario) {
        return "Bearer " + jwt.emitir(usuario.getId(), usuario.getVersionCredenciales().toString()).accessToken();
    }

    private long abrir(Usuario usuario, String monto) {
        return cajas.abrirCaja(new AbrirCajaRequest(new BigDecimal(monto)), usuario.getId()).id();
    }

    private com.miraquetelavo.lavanderia.caja.dto.CajaResponse cerrar(long caja, String contado, Usuario admin) {
        return cajas.cerrarCaja(caja, new CerrarCajaRequest(new BigDecimal(contado)), admin.getId());
    }

    private long metodo(String codigo) {
        return metodos.findByCodigo(codigo).orElseThrow().getId();
    }

    private long tipo(String codigo) {
        return tipos.findByCodigo(codigo).orElseThrow().getId();
    }

    private RegistrarMovimientoManualRequest solicitud(long caja, String monto, String codigo) {
        return new RegistrarMovimientoManualRequest(caja, new BigDecimal(monto), metodo(codigo), "Movimiento manual");
    }

    private static String cuerpo(long caja, String monto, long metodo) {
        return """
                {"cajaId":%d,"monto":%s,"metodoPagoId":%d,"descripcion":"Movimiento manual"}
                """.formatted(caja, monto, metodo);
    }
}
