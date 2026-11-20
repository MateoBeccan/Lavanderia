package com.miraquetelavo.lavanderia.estadopedido.service;

import com.miraquetelavo.lavanderia.soporte.IntegracionPostgresql;
import com.miraquetelavo.lavanderia.common.exception.RecursoNoEncontradoException;
import com.miraquetelavo.lavanderia.estadopedido.dto.EstadoPedidoResponse;
import com.miraquetelavo.lavanderia.estadopedido.inicializacion.EstadoPedidoInitializer;
import com.miraquetelavo.lavanderia.estadopedido.repository.EstadoPedidoRepository;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.miraquetelavo.lavanderia.common.exception.RestriccionesPostgresql.esViolacionUnica;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegracionPostgresql("estado_pedido")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SpringBootTest(properties = {
        "lavanderia.jwt.secret=dGVzdC1vbmx5LXZlcmlmaWNhY2lvbi1qd3QtbGF2YW5kZXJpYQ=="
})
class EstadoPedidoPostgresqlTest {

    private static List<EstadoPedidoResponse> catalogoAntesDelReinicio;

    @Autowired
    private EstadoPedidoInitializer initializer;

    @Autowired
    private EstadoPedidoRepository repository;

    @Autowired
    private EstadoPedidoService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @Order(1)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void cargaYConservaElCatalogoYAplicaLasRestricciones() throws Exception {
        var iniciales = service.listarActivos();
        assertThat(iniciales).extracting(EstadoPedidoResponse::codigo)
                .containsExactly("PENDIENTE", "EN_PROCESO", "LISTO", "ENTREGADO", "CANCELADO");
        assertThat(iniciales).extracting(EstadoPedidoResponse::orden).containsExactly(1, 2, 3, 4, 5);
        assertThat(iniciales).extracting(EstadoPedidoResponse::id).doesNotContainNull().doesNotHaveDuplicates();
        assertThat(repository.count()).isEqualTo(5);
        var pendiente = iniciales.getFirst();

        jdbc.update("""
                update estados_pedido set nombre = 'En espera', descripcion = 'Personalizado', orden = 10, activo = false
                where codigo = 'PENDIENTE'
                """);
        jdbc.update("insert into estados_pedido (codigo, nombre, orden) values ('REVISION', 'Revisión', 2)");
        jdbc.update("delete from estados_pedido where codigo = 'CANCELADO'");

        var barrera = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var ejecuciones = List.of(executor.submit(() -> cargar(barrera)), executor.submit(() -> cargar(barrera)));
            for (var ejecucion : ejecuciones) {
                ejecucion.get(15, TimeUnit.SECONDS);
            }
        }

        assertThat(repository.count()).isEqualTo(6);
        assertThat(repository.existsByCodigo("CANCELADO")).isTrue();
        assertThat(service.listarActivos()).extracting(EstadoPedidoResponse::codigo)
                .containsExactly("EN_PROCESO", "REVISION", "LISTO", "ENTREGADO", "CANCELADO");
        assertThat(service.obtener(iniciales.get(1).id())).isEqualTo(iniciales.get(1));
        assertThatThrownBy(() -> service.obtener(pendiente.id())).isInstanceOf(RecursoNoEncontradoException.class);
        var conservado = service.buscarPorCodigo("PENDIENTE");
        assertThat(conservado.getId()).isEqualTo(pendiente.id());
        assertThat(conservado.getNombre()).isEqualTo("En espera");
        assertThat(conservado.getDescripcion()).isEqualTo("Personalizado");
        assertThat(conservado.getOrden()).isEqualTo(10);
        assertThat(conservado.getActivo()).isFalse();

        assertThatThrownBy(() -> jdbc.update(
                "insert into estados_pedido (codigo, nombre, orden) values ('PENDIENTE', 'Otro', 1)"))
                .isInstanceOfSatisfying(DataIntegrityViolationException.class,
                        error -> assertThat(esViolacionUnica(error, "uk_estados_pedido_codigo")).isTrue());
        assertThatThrownBy(() -> jdbc.update(
                "insert into estados_pedido (codigo, nombre, orden) values ('INVALIDO', 'Inválido', -1)"))
                .isInstanceOf(DataIntegrityViolationException.class);
        catalogoAntesDelReinicio = service.listarActivos();
    }

    @Test
    @Order(2)
    void conservaLosRegistrosAlVolverAIniciarSpring() {
        assertThat(service.listarActivos()).isEqualTo(catalogoAntesDelReinicio);
        assertThat(repository.count()).isEqualTo(6);
        var pendiente = service.buscarPorCodigo("PENDIENTE");
        assertThat(pendiente.getActivo()).isFalse();
        assertThat(pendiente.getNombre()).isEqualTo("En espera");
    }

    private Void cargar(CyclicBarrier barrera) throws Exception {
        barrera.await(10, TimeUnit.SECONDS);
        initializer.run(null);
        return null;
    }
}
