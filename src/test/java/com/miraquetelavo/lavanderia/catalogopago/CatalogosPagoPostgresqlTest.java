package com.miraquetelavo.lavanderia.catalogopago;

import com.miraquetelavo.lavanderia.soporte.IntegracionPostgresql;
import com.miraquetelavo.lavanderia.common.exception.RecursoNoEncontradoException;
import com.miraquetelavo.lavanderia.estadopago.dto.EstadoPagoResponse;
import com.miraquetelavo.lavanderia.estadopago.inicializacion.EstadoPagoInitializer;
import com.miraquetelavo.lavanderia.estadopago.repository.EstadoPagoRepository;
import com.miraquetelavo.lavanderia.estadopago.service.EstadoPagoService;
import com.miraquetelavo.lavanderia.metodopago.dto.MetodoPagoResponse;
import com.miraquetelavo.lavanderia.metodopago.inicializacion.MetodoPagoInitializer;
import com.miraquetelavo.lavanderia.metodopago.repository.MetodoPagoRepository;
import com.miraquetelavo.lavanderia.metodopago.service.MetodoPagoService;
import com.miraquetelavo.lavanderia.rol.entity.CodigoRol;
import com.miraquetelavo.lavanderia.rol.repository.RolRepository;
import com.miraquetelavo.lavanderia.security.JwtService;
import com.miraquetelavo.lavanderia.usuario.entity.Usuario;
import com.miraquetelavo.lavanderia.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.miraquetelavo.lavanderia.common.exception.RestriccionesPostgresql.esViolacionUnica;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegracionPostgresql("catalogos_pago")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "lavanderia.jwt.secret=${random.value}${random.value}"
})
class CatalogosPagoPostgresqlTest {

    @Autowired EstadoPagoInitializer inicializadorEstados;
    @Autowired MetodoPagoInitializer inicializadorMetodos;
    @Autowired EstadoPagoRepository estados;
    @Autowired MetodoPagoRepository metodos;
    @Autowired EstadoPagoService servicioEstados;
    @Autowired MetodoPagoService servicioMetodos;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @Autowired RolRepository roles;
    @Autowired UsuarioRepository usuarios;

    @Test
    @Order(1)
    void cargaCatalogosLimpiosYPermiteConsultarlosConJwt() throws Exception {
        assertThat(estados.count()).isEqualTo(3);
        assertThat(metodos.count()).isEqualTo(4);
        assertThat(servicioEstados.listarActivos()).extracting(EstadoPagoResponse::codigo)
                .containsExactly("PENDIENTE", "PARCIAL", "PAGADO");
        assertThat(servicioEstados.listarActivos()).extracting(EstadoPagoResponse::orden)
                .containsExactly(1, 2, 3);
        assertThat(servicioMetodos.listarActivos()).extracting(MetodoPagoResponse::codigo)
                .containsExactly("EFECTIVO", "TRANSFERENCIA", "TARJETA", "OTROS");

        Long parcial = servicioEstados.buscarPorCodigo("PARCIAL").getId();
        Long tarjeta = servicioMetodos.buscarPorCodigo("TARJETA").getId();
        for (CodigoRol codigoRol : CodigoRol.values()) {
            String bearer = autorizar(codigoRol);
            mvc.perform(get("/api/estados-pago").header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
            mvc.perform(get("/api/estados-pago/{id}", parcial).header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.codigo").value("PARCIAL"));
            mvc.perform(get("/api/metodos-pago").header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4));
            mvc.perform(get("/api/metodos-pago/{id}", tarjeta).header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.codigo").value("TARJETA"));
        }
    }

    @Test
    @Order(2)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void completaFaltantesConcurrentementeYConservaCambiosExistentes() throws Exception {
        var pendiente = servicioEstados.buscarPorCodigo("PENDIENTE");
        var otros = servicioMetodos.buscarPorCodigo("OTROS");
        jdbc.update("update estados_pago set nombre = 'En espera', activo = false where codigo = 'PENDIENTE'");
        jdbc.update("update metodos_pago set nombre = 'Otros medios', activo = false where codigo = 'OTROS'");
        jdbc.update("delete from estados_pago where codigo = 'PARCIAL'");
        jdbc.update("delete from metodos_pago where codigo = 'EFECTIVO'");

        var barrera = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var primera = executor.submit(() -> inicializar(barrera));
            var segunda = executor.submit(() -> inicializar(barrera));
            primera.get(15, TimeUnit.SECONDS);
            segunda.get(15, TimeUnit.SECONDS);
        }

        verificarCatalogosConCambios();
        assertThat(servicioEstados.buscarPorCodigo("PENDIENTE").getId()).isEqualTo(pendiente.getId());
        assertThat(servicioMetodos.buscarPorCodigo("OTROS").getId()).isEqualTo(otros.getId());

        String bearer = autorizar(CodigoRol.EMPLEADO);
        mvc.perform(get("/api/estados-pago/{id}", pendiente.getId()).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/metodos-pago/{id}", otros.getId()).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/metodos-pago/0").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(3)
    void conservaLosCatalogosAlReiniciarYAplicaLasRestricciones() {
        verificarCatalogosConCambios();
        verificarUnicidad("insert into estados_pago (codigo, nombre, orden) values ('PAGADO', 'Otro', 1)",
                "uk_estados_pago_codigo");
        verificarUnicidad("insert into metodos_pago (codigo, nombre) values ('EFECTIVO', 'Otro')",
                "uk_metodos_pago_codigo");
        verificarUnicidad("insert into metodos_pago (codigo, nombre) values ('EXTERNO', 'Efectivo')",
                "uk_metodos_pago_nombre");
        assertThatThrownBy(() -> jdbc.update(
                "insert into estados_pago (codigo, nombre, orden) values ('INVALIDO', 'Inválido', -1)"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Order(4)
    void informaUnNombreOcupadoYRevierteTodaLaCargaDelCatalogo() {
        jdbc.update("delete from metodos_pago where codigo in ('EFECTIVO', 'TARJETA')");
        jdbc.update("insert into metodos_pago (codigo, nombre) values ('EXTERNO', 'Tarjeta')");

        assertThatThrownBy(() -> inicializadorMetodos.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("un nombre inicial está asignado a otro código");
        assertThat(metodos.existsByCodigo("EFECTIVO")).isFalse();
        assertThat(metodos.existsByCodigo("TARJETA")).isFalse();
        assertThat(servicioMetodos.buscarPorCodigo("EXTERNO").getNombre()).isEqualTo("Tarjeta");
    }

    private Void inicializar(CyclicBarrier barrera) throws Exception {
        barrera.await(10, TimeUnit.SECONDS);
        inicializadorEstados.run(null);
        inicializadorMetodos.run(null);
        return null;
    }

    private void verificarCatalogosConCambios() {
        assertThat(estados.count()).isEqualTo(3);
        assertThat(metodos.count()).isEqualTo(4);
        assertThat(estados.existsByCodigo("PARCIAL")).isTrue();
        assertThat(metodos.existsByCodigo("EFECTIVO")).isTrue();
        assertThat(servicioEstados.listarActivos()).extracting(EstadoPagoResponse::codigo)
                .containsExactly("PARCIAL", "PAGADO");
        assertThat(servicioMetodos.listarActivos()).extracting(MetodoPagoResponse::codigo)
                .containsExactly("EFECTIVO", "TRANSFERENCIA", "TARJETA");
        var pendiente = servicioEstados.buscarPorCodigo("PENDIENTE");
        var otros = servicioMetodos.buscarPorCodigo("OTROS");
        assertThat(pendiente.getNombre()).isEqualTo("En espera");
        assertThat(otros.getNombre()).isEqualTo("Otros medios");
        assertThatThrownBy(() -> servicioEstados.obtener(pendiente.getId()))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicioMetodos.obtener(otros.getId()))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    private void verificarUnicidad(String sql, String restriccion) {
        assertThatThrownBy(() -> jdbc.update(sql))
                .isInstanceOfSatisfying(DataIntegrityViolationException.class,
                        error -> assertThat(esViolacionUnica(error, restriccion)).isTrue());
    }

    private String autorizar(CodigoRol codigoRol) {
        var rol = roles.findByNombre(codigoRol).orElseThrow();
        String sufijo = UUID.randomUUID().toString();
        var usuario = usuarios.save(new Usuario(sufijo, "hash-de-prueba", "Ana", sufijo + "@example.com", rol));
        return "Bearer " + jwt.emitir(usuario.getId(), usuario.getVersionCredenciales().toString()).accessToken();
    }
}
