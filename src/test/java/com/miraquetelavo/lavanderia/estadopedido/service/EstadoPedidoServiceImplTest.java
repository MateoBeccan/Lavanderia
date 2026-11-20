package com.miraquetelavo.lavanderia.estadopedido.service;

import com.miraquetelavo.lavanderia.common.exception.RecursoNoEncontradoException;
import com.miraquetelavo.lavanderia.estadopedido.dto.EstadoPedidoResponse;
import com.miraquetelavo.lavanderia.estadopedido.entity.EstadoPedido;
import com.miraquetelavo.lavanderia.estadopedido.repository.EstadoPedidoRepository;
import com.miraquetelavo.lavanderia.estadopedido.service.impl.EstadoPedidoServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EstadoPedidoServiceImplTest {

    @Mock
    private EstadoPedidoRepository repository;

    @InjectMocks
    private EstadoPedidoServiceImpl service;

    @Test
    void conservaElOrdenDelRepositorio() {
        var primero = mock(EstadoPedido.class);
        var segundo = mock(EstadoPedido.class);
        when(primero.getId()).thenReturn(9L);
        when(segundo.getId()).thenReturn(3L);
        when(repository.findByActivoTrueOrderByOrdenAscIdAsc()).thenReturn(List.of(primero, segundo));

        assertThat(service.listarActivos()).extracting(EstadoPedidoResponse::id).containsExactly(9L, 3L);
    }

    @Test
    void convierteElEstadoActivoEnDto() {
        var estado = mock(EstadoPedido.class);
        when(estado.getId()).thenReturn(7L);
        when(estado.getCodigo()).thenReturn("PENDIENTE");
        when(estado.getNombre()).thenReturn("Pendiente");
        when(estado.getDescripcion()).thenReturn("En espera");
        when(estado.getOrden()).thenReturn(1);
        when(estado.getActivo()).thenReturn(true);
        when(repository.findByIdAndActivoTrue(7L)).thenReturn(Optional.of(estado));

        assertThat(service.obtener(7L))
                .isEqualTo(new EstadoPedidoResponse(7L, "PENDIENTE", "Pendiente", "En espera", 1, true));
    }

    @Test
    void fallaSiNoHayUnEstadoActivoConEseId() {
        assertThatThrownBy(() -> service.obtener(7L))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("ID 7");
    }

    @Test
    void obtieneLaEntidadPorCodigoParaUsoInterno() {
        var estado = mock(EstadoPedido.class);
        when(repository.findByCodigo("PENDIENTE")).thenReturn(Optional.of(estado));

        assertThat(service.buscarPorCodigo("PENDIENTE")).isSameAs(estado);
    }

    @Test
    void fallaSiElCodigoNoExiste() {
        assertThatThrownBy(() -> service.buscarPorCodigo("INEXISTENTE"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("INEXISTENTE");
    }
}
