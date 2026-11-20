package com.miraquetelavo.lavanderia.prenda.service;

import com.miraquetelavo.lavanderia.soporte.IntegracionPostgresql;
import com.miraquetelavo.lavanderia.common.exception.ConflictoException;
import com.miraquetelavo.lavanderia.common.exception.RecursoNoEncontradoException;
import com.miraquetelavo.lavanderia.prenda.dto.PrendaRequest;
import com.miraquetelavo.lavanderia.prenda.dto.PrendaResponse;
import com.miraquetelavo.lavanderia.prenda.entity.Prenda;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.miraquetelavo.lavanderia.common.exception.RestriccionesPostgresql.esViolacionUnica;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegracionPostgresql("prenda")
@SpringBootTest(properties = {
        "lavanderia.jwt.secret=dGVzdC1vbmx5LXZlcmlmaWNhY2lvbi1qd3QtbGF2YW5kZXJpYQ=="
})
class PrendaPostgresqlTest {

    @Autowired
    private PrendaService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void instalaLaRestriccionUnicaEnElCatalogoDePostgresql() {
        var definicion = jdbc.queryForObject("""
                select pg_get_constraintdef(oid)
                from pg_constraint
                where conrelid = 'prendas'::regclass and conname = ? and contype = 'u' and convalidated
                """, String.class, Prenda.UK_NOMBRE);

        assertThat(definicion).isEqualTo("UNIQUE (nombre_normalizado)");
        assertThat(jdbc.queryForObject("""
                select generation_expression from information_schema.columns
                where table_schema = current_schema() and table_name = 'prendas'
                    and column_name = 'nombre_normalizado' and is_generated = 'ALWAYS'
                """, String.class)).contains("lower", "nombre_prenda");
    }

    @Test
    void soloUnaDeDosAltasConcurrentesConNombresEquivalentesProspera() throws Exception {
        var barrera = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var primera = executor.submit(() -> registrarEnParalelo(barrera, "Camisa"));
            var segunda = executor.submit(() -> registrarEnParalelo(barrera, " CAMISA "));
            var resultados = List.of(primera.get(15, TimeUnit.SECONDS), segunda.get(15, TimeUnit.SECONDS));

            assertThat(resultados).filteredOn(PrendaResponse.class::isInstance).hasSize(1);
            assertThat(resultados).filteredOn(ConflictoException.class::isInstance).hasSize(1);
        }

        assertThat(jdbc.queryForObject("select count(*) from prendas where nombre_normalizado = 'camisa'",
                Long.class)).isEqualTo(1L);
    }

    @Test
    void reservaElNombreInclusoSiLaPrendaSeDesactiva() {
        var prenda = service.registrar(new PrendaRequest("Bufanda"));
        service.desactivar(prenda.id());
        service.desactivar(prenda.id());

        assertThat(service.listarActivos(PageRequest.of(0, 20))).noneMatch(item -> item.id().equals(prenda.id()));
        assertThatThrownBy(() -> service.obtener(prenda.id())).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> service.registrar(new PrendaRequest("BUFANDA"))).isInstanceOf(ConflictoException.class);
        assertThatThrownBy(() -> jdbc.update("insert into prendas (nombre_prenda) values (?)", "BUFANDA"))
                .isInstanceOfSatisfying(DataIntegrityViolationException.class,
                        error -> assertThat(esViolacionUnica(error, Prenda.UK_NOMBRE)).isTrue());

        assertThat(service.activar(prenda.id())).isEqualTo(prenda);
        assertThat(service.activar(prenda.id())).isEqualTo(prenda);
    }

    private Object registrarEnParalelo(CyclicBarrier barrera, String nombre) throws Exception {
        barrera.await(10, TimeUnit.SECONDS);
        try {
            return service.registrar(new PrendaRequest(nombre));
        } catch (ConflictoException ex) {
            return ex;
        }
    }
}
