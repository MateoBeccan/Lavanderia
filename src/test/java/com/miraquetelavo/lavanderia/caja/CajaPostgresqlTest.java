package com.miraquetelavo.lavanderia.caja;

import com.miraquetelavo.lavanderia.soporte.EsquemaPostgresql;
import com.miraquetelavo.lavanderia.soporte.IntegracionPostgresql;
import com.miraquetelavo.lavanderia.caja.dto.AbrirCajaRequest;
import com.miraquetelavo.lavanderia.caja.dto.CerrarCajaRequest;
import com.miraquetelavo.lavanderia.caja.entity.EstadoCaja;
import com.miraquetelavo.lavanderia.caja.inicializacion.CajaSchemaInitializer;
import com.miraquetelavo.lavanderia.caja.repository.CajaRepository;
import com.miraquetelavo.lavanderia.caja.service.CajaService;
import com.miraquetelavo.lavanderia.common.exception.ConflictoException;
import com.miraquetelavo.lavanderia.rol.entity.CodigoRol;
import com.miraquetelavo.lavanderia.rol.repository.RolRepository;
import com.miraquetelavo.lavanderia.security.JwtService;
import com.miraquetelavo.lavanderia.tipomovimientocaja.dto.TipoMovimientoCajaResponse;
import com.miraquetelavo.lavanderia.tipomovimientocaja.inicializacion.TipoMovimientoCajaInitializer;
import com.miraquetelavo.lavanderia.tipomovimientocaja.service.TipoMovimientoCajaService;
import com.miraquetelavo.lavanderia.usuario.entity.Usuario;
import com.miraquetelavo.lavanderia.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@IntegracionPostgresql("caja")
@Sql(scripts = "/db/migration/V032__idempotencia_financiera.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "lavanderia.jwt.secret=${random.value}${random.value}"
})
class CajaPostgresqlTest {

    @Autowired CajaService servicio;
    @Autowired CajaRepository cajas;
    @Autowired CajaSchemaInitializer esquema;
    @Autowired TipoMovimientoCajaInitializer inicializadorTipos;
    @Autowired TipoMovimientoCajaService tipos;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transacciones;
    @Autowired RolRepository roles;
    @Autowired UsuarioRepository usuarios;
    @Autowired JwtService jwt;
    @Autowired MockMvc mvc;

    @BeforeEach
    void limpiarJornadas() {
        jdbc.update("delete from movimientos_caja");
        jdbc.update("delete from cajas");
    }

    @Test
    void reintentarLaAperturaDeUnaCajaCerradaNoAbreOtraJornada() throws Exception {
        var admin = usuario(CodigoRol.ADMINISTRADOR);
        var solicitud = post("/api/cajas/abrir").header(HttpHeaders.AUTHORIZATION, bearer(admin))
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"montoInicial\":100}");
        var original = mvc.perform(solicitud).andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("ABIERTA")).andReturn().getResponse();
        var caja = servicio.obtenerCajaAbierta();
        servicio.cerrarCaja(caja.id(), cierre(), admin.getId());

        var repetida = mvc.perform(solicitud).andExpect(status().isCreated()).andReturn().getResponse();
        assertThat(repetida.getContentAsByteArray()).isEqualTo(original.getContentAsByteArray());
        assertThat(repetida.getHeader(HttpHeaders.LOCATION)).isEqualTo(original.getHeader(HttpHeaders.LOCATION));
        assertThat(cajas.count()).isEqualTo(1);
        assertThat(servicio.obtenerPorId(caja.id()).estado()).isEqualTo(EstadoCaja.CERRADA);
    }

    @ParameterizedTest
    @CsvSource({"100.00,0.00", "105.29,5.29", "99.75,-0.25"})
    void abreYArqueaUnaCajaSinMovimientos(String contado, String diferencia) throws Exception {
        var empleado = usuario(CodigoRol.EMPLEADO);
        var admin = usuario(CodigoRol.ADMINISTRADOR);
        var abierta = servicio.abrirCaja(apertura(), empleado.getId());
        assertThat(abierta.usuarioAperturaId()).isEqualTo(empleado.getId());
        assertThat(abierta.usuarioCierreId()).isNull();
        assertThat(abierta.efectivoEsperado()).isNull();
        assertThat(abierta.estado()).isEqualTo(EstadoCaja.ABIERTA);

        mvc.perform(get("/api/cajas/abierta").header(HttpHeaders.AUTHORIZATION, bearer(empleado)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(abierta.id()))
                .andExpect(jsonPath("$.usuarioAperturaNombre").value(empleado.getNombre()))
                .andExpect(jsonPath("$.password").doesNotExist());
        mvc.perform(patch("/api/cajas/{id}/cerrar", abierta.id()).header(HttpHeaders.AUTHORIZATION, bearer(empleado))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"efectivoContado\":" + contado + "}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/cajas/{id}/cerrar", abierta.id()).header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"efectivoContado\":" + contado + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.diferenciaArqueo")
                        .value(comparesEqualTo(new BigDecimal(diferencia)), BigDecimal.class));

        var cerrada = servicio.obtenerPorId(abierta.id());
        assertThat(cerrada.usuarioCierreId()).isEqualTo(admin.getId());
        assertThat(cerrada.fechaCierre()).isAfterOrEqualTo(abierta.fechaApertura());
        assertThat(cerrada.fechaApertura()).isEqualTo(abierta.fechaApertura());
        assertThat(cerrada.montoInicial()).isEqualByComparingTo("100.00");
        assertThat(cerrada.montoFinalCalculado()).isEqualByComparingTo("100.00");
        assertThat(cerrada.efectivoEsperado()).isEqualByComparingTo("100.00");
        assertThat(cerrada.efectivoContado()).isEqualByComparingTo(contado);
        assertThat(cerrada.diferenciaArqueo()).isEqualByComparingTo(diferencia);
        assertThat(cerrada.estado()).isEqualTo(EstadoCaja.CERRADA);

        mvc.perform(get("/api/cajas/{id}", abierta.id()).header(HttpHeaders.AUTHORIZATION, bearer(empleado)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CERRADA"));
        mvc.perform(get("/api/cajas/abierta").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNotFound());
        assertThatThrownBy(() -> servicio.cerrarCaja(abierta.id(), cierre(), admin.getId()))
                .isInstanceOf(ConflictoException.class);
    }

    @Test
    void paginaConOrdenEstableYFiltraPorDiasInclusivos() throws Exception {
        var admin = usuario(CodigoRol.ADMINISTRADOR);
        Long primera = cerrarJornada(admin);
        Long segunda = cerrarJornada(admin);
        Long tercera = cerrarJornada(admin);
        Long abierta = servicio.abrirCaja(apertura(), admin.getId()).id();
        var dia = LocalDateTime.of(2026, 11, 16, 0, 0);
        jdbc.update("update cajas set fecha_apertura = ? where id_caja in (?, ?)", dia, primera, segunda);
        jdbc.update("update cajas set fecha_apertura = ? where id_caja = ?", dia.plusDays(1), tercera);
        jdbc.update("update cajas set fecha_apertura = ? where id_caja = ?", dia.plusHours(2), abierta);

        mvc.perform(get("/api/cajas").param("estado", "CERRADA").param("desde", "2026-11-16")
                        .param("hasta", "2026-11-16").param("size", "1").param("sort", "id,asc")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(segunda));
        mvc.perform(get("/api/cajas").param("estado", "CERRADA").param("desde", "2026-11-16")
                        .param("hasta", "2026-11-16").param("size", "1").param("page", "1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(primera));
        mvc.perform(get("/api/cajas").param("desde", "2026-11-17").param("hasta", "2026-11-16")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))).andExpect(status().isBadRequest());
    }

    @Test
    void dosAperturasConcurrentesDanUn201YUn409() throws Exception {
        var empleado = usuario(CodigoRol.EMPLEADO);
        var admin = usuario(CodigoRol.ADMINISTRADOR);
        assertThat(enParalelo(() -> abrirHttp(empleado), () -> abrirHttp(admin)))
                .containsExactlyInAnyOrder(201, 409);
        assertThat(cajas.count()).isEqualTo(1);
        assertThat(cajas.existsByEstado(EstadoCaja.ABIERTA)).isTrue();
        assertThat(abrirHttp(empleado)).isEqualTo(409);
    }

    @Test
    void dosCierresConcurrentesDanUn200YUn409() throws Exception {
        var admin1 = usuario(CodigoRol.ADMINISTRADOR);
        var admin2 = usuario(CodigoRol.ADMINISTRADOR);
        Long id = servicio.abrirCaja(apertura(), admin1.getId()).id();
        assertThat(enParalelo(() -> cerrarHttp(id, admin1), () -> cerrarHttp(id, admin2)))
                .containsExactlyInAnyOrder(200, 409);
        assertThat(servicio.obtenerPorId(id).usuarioCierreId()).isIn(admin1.getId(), admin2.getId());
        assertThat(cajas.existsByEstado(EstadoCaja.ABIERTA)).isFalse();
    }

    @Test
    void aperturaEsperaLaConfirmacionDelCierreEnCurso() throws Exception {
        var admin = usuario(CodigoRol.ADMINISTRADOR);
        var empleado = usuario(CodigoRol.EMPLEADO);
        Long anterior = servicio.abrirCaja(apertura(), admin.getId()).id();
        var cierreEnBase = new CountDownLatch(1);
        var confirmar = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var cierrePendiente = executor.submit(() -> transacciones.executeWithoutResult(transaccion -> {
                servicio.cerrarCaja(anterior, cierre(), admin.getId());
                cajas.flush();
                cierreEnBase.countDown();
                esperar(confirmar);
            }));
            try {
                esperar(cierreEnBase);
                var aperturaPendiente = executor.submit(() -> servicio.abrirCaja(apertura(), empleado.getId()));
                // Verifica que el INSERT llegó al índice y está esperando el cierre, antes del commit.
                long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                boolean bloqueada;
                do {
                    bloqueada = Boolean.TRUE.equals(jdbc.queryForObject("""
                            select exists (select 1 from pg_stat_activity
                                where pid <> pg_backend_pid() and wait_event_type = 'Lock'
                                  and query like '%on conflict (estado)%')
                            """, Boolean.class));
                    if (!bloqueada) {
                        Thread.sleep(20);
                    }
                } while (!bloqueada && !aperturaPendiente.isDone() && System.nanoTime() < limite);
                assertThat(bloqueada).isTrue();
                confirmar.countDown();
                cierrePendiente.get(10, TimeUnit.SECONDS);
                assertThat(aperturaPendiente.get(10, TimeUnit.SECONDS).id()).isNotEqualTo(anterior);
            } finally {
                confirmar.countDown();
            }
        }
        assertThat(cajas.count()).isEqualTo(2);
        assertThat(servicio.obtenerPorId(anterior).estado()).isEqualTo(EstadoCaja.CERRADA);
    }

    @Test
    void postgresProtegeUnicidadImportesEstadosYReferencias() {
        var admin = usuario(CodigoRol.ADMINISTRADOR);
        var abierta = servicio.abrirCaja(apertura(), admin.getId());
        assertThatThrownBy(() -> jdbc.update("""
                insert into cajas (estado, id_usuario_apertura, fecha_apertura, monto_inicial)
                values ('ABIERTA', ?, localtimestamp, 0)
                """, admin.getId())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update cajas set monto_inicial = -1 where id_caja = ?", abierta.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update cajas set estado = 'CERRADA' where id_caja = ?", abierta.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update cajas set id_usuario_cierre = ? where id_caja = ?",
                admin.getId(), abierta.id())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("delete from usuarios where id_usuario = ?", admin.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);

        servicio.cerrarCaja(abierta.id(), cierre(), admin.getId());
        assertThatThrownBy(() -> jdbc.update("update cajas set diferencia_arqueo = 1 where id_caja = ?", abierta.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void fallaCerradoSiElIndiceArbitroNoExiste() {
        var admin = usuario(CodigoRol.ADMINISTRADOR);
        jdbc.execute("drop index ux_cajas_unica_abierta");
        try {
            assertThatThrownBy(() -> servicio.abrirCaja(apertura(), admin.getId()))
                    .isInstanceOf(DataAccessException.class);
            assertThat(cajas.count()).isZero();
        } finally {
            esquema.run(null);
        }
    }

    @Test
    void actualizaElCheckLegadoYConservaUnaCajaExistente() {
        var admin = usuario(CodigoRol.ADMINISTRADOR);
        Long id = servicio.abrirCaja(apertura(), admin.getId()).id();
        jdbc.execute("""
                alter table cajas drop constraint ck_cajas_importes,
                add constraint ck_cajas_importes check (monto_inicial >= 0 and monto_final_calc >= 0
                    and efectivo_esperado >= 0 and efectivo_contado >= 0
                    and diferencia_arqueo = efectivo_contado - efectivo_esperado)
                """);
        try {
            esquema.run(null);
            var definicion = jdbc.queryForObject("""
                    select pg_get_constraintdef(oid) from pg_constraint
                    where conrelid = 'cajas'::regclass and conname = 'ck_cajas_importes'
                    """, String.class);
            assertThat(definicion).contains("diferencia_arqueo").doesNotContain("monto_final_calc");
            assertThat(servicio.cerrarCaja(id, cierre(), admin.getId()).estado()).isEqualTo(EstadoCaja.CERRADA);
        } finally {
            esquema.run(null);
        }
    }

    @Test
    void inicializaLosCuatroTiposYAmbosRolesLosPuedenConsultar() throws Exception {
        assertThat(tipos.listarActivos()).extracting(TipoMovimientoCajaResponse::codigo)
                .containsExactly("INGRESO_MANUAL", "EGRESO_MANUAL", "COBRO_PEDIDO", "PAGO_PROVEEDOR");
        var tipo = tipos.buscarPorCodigo("COBRO_PEDIDO");
        for (var rol : CodigoRol.values()) {
            var token = bearer(usuario(rol));
            mvc.perform(get("/api/tipos-movimiento-caja").header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4));
            mvc.perform(get("/api/tipos-movimiento-caja/{id}", tipo.getId()).header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.codigo").value("COBRO_PEDIDO"));
        }
    }

    @Test
    void inicializaConcurrentementeRespetaInactivosYPersisteAlReiniciar() throws Exception {
        var tipo = tipos.buscarPorCodigo("INGRESO_MANUAL");
        var admin = usuario(CodigoRol.ADMINISTRADOR);
        Long jornada = cerrarJornada(admin);
        jdbc.update("update tipos_movimiento_caja set activo = false, descripcion = 'Personalizada' where codigo = 'INGRESO_MANUAL'");
        jdbc.update("delete from tipos_movimiento_caja where codigo = 'COBRO_PEDIDO'");
        try {
            enParalelo(this::inicializarTipos, this::inicializarTipos);
            assertThat(tipos.listarActivos()).hasSize(3);
            mvc.perform(get("/api/tipos-movimiento-caja/{id}", tipo.getId())
                            .header(HttpHeaders.AUTHORIZATION, bearer(admin))).andExpect(status().isNotFound());
            try (var reinicio = EsquemaPostgresql.reinicio(CajaPostgresqlTest.class).run(
                    "--server.port=0", "--lavanderia.jwt.secret=${random.value}${random.value}",
                    "--spring.jpa.show-sql=false")) {
                assertThat(reinicio.getBean(CajaService.class).obtenerPorId(jornada).estado())
                        .isEqualTo(EstadoCaja.CERRADA);
                var repositorio = reinicio.getBean(JdbcTemplate.class);
                assertThat(repositorio.queryForObject("select count(*) from tipos_movimiento_caja", Long.class))
                        .isEqualTo(4L);
                var persistido = reinicio.getBean(TipoMovimientoCajaService.class).buscarPorCodigo("INGRESO_MANUAL");
                assertThat(persistido.getId()).isEqualTo(tipo.getId());
                assertThat(persistido.getDescripcion()).isEqualTo("Personalizada");
                assertThat(persistido.getActivo()).isFalse();
            }

            assertThatThrownBy(() -> jdbc.update("""
                    insert into tipos_movimiento_caja (codigo, nombre, orden) values ('COBRO_PEDIDO', 'Otro', 1)
                    """)).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("""
                    insert into tipos_movimiento_caja (codigo, nombre, orden) values ('OTRO', 'Cobro pedido', 1)
                    """)).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("""
                    insert into tipos_movimiento_caja (codigo, nombre, orden) values ('OTRO', 'Otro', -1)
                    """)).isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            jdbc.update("update tipos_movimiento_caja set activo = true, descripcion = ? where codigo = 'INGRESO_MANUAL'",
                    tipo.getDescripcion());
            inicializadorTipos.run(null);
        }
    }

    private int inicializarTipos() {
        inicializadorTipos.run(null);
        return 0;
    }

    private Long cerrarJornada(Usuario admin) {
        Long id = servicio.abrirCaja(apertura(), admin.getId()).id();
        servicio.cerrarCaja(id, cierre(), admin.getId());
        return id;
    }

    private int abrirHttp(Usuario usuario) throws Exception {
        var respuesta = mvc.perform(post("/api/cajas/abrir").header(HttpHeaders.AUTHORIZATION, bearer(usuario))
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"montoInicial":100}
                        """)).andReturn().getResponse();
        if (respuesta.getStatus() == 409) {
            assertThat(respuesta.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        }
        return respuesta.getStatus();
    }

    private int cerrarHttp(Long id, Usuario usuario) throws Exception {
        var respuesta = mvc.perform(patch("/api/cajas/{id}/cerrar", id).header(HttpHeaders.AUTHORIZATION, bearer(usuario))
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"efectivoContado":100}
                        """)).andReturn().getResponse();
        if (respuesta.getStatus() == 409) {
            assertThat(respuesta.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        }
        return respuesta.getStatus();
    }

    private static <T> List<T> enParalelo(Callable<T> primera, Callable<T> segunda) throws Exception {
        var barrera = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> {
                barrera.await(10, TimeUnit.SECONDS);
                return primera.call();
            });
            var b = executor.submit(() -> {
                barrera.await(10, TimeUnit.SECONDS);
                return segunda.call();
            });
            return List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
        }
    }

    private static void esperar(CountDownLatch senal) {
        try {
            assertThat(senal.await(15, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(error);
        }
    }

    private Usuario usuario(CodigoRol codigo) {
        var rol = roles.findByNombre(codigo).orElseThrow();
        String sufijo = UUID.randomUUID().toString();
        return usuarios.save(new Usuario(sufijo, "hash-de-prueba", "Ana", sufijo + "@example.com", rol));
    }

    private String bearer(Usuario usuario) {
        return "Bearer " + jwt.emitir(usuario.getId(), usuario.getVersionCredenciales().toString()).accessToken();
    }

    private static AbrirCajaRequest apertura() {
        return new AbrirCajaRequest(new BigDecimal("100.00"));
    }

    private static CerrarCajaRequest cierre() {
        return new CerrarCajaRequest(new BigDecimal("100.00"));
    }
}
