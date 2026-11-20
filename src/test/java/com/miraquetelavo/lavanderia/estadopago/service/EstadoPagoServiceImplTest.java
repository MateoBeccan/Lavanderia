package com.miraquetelavo.lavanderia.estadopago.service;

import com.miraquetelavo.lavanderia.common.exception.RecursoNoEncontradoException;
import com.miraquetelavo.lavanderia.estadopago.dto.EstadoPagoResponse;
import com.miraquetelavo.lavanderia.estadopago.entity.EstadoPago;
import com.miraquetelavo.lavanderia.estadopago.repository.EstadoPagoRepository;
import com.miraquetelavo.lavanderia.estadopago.service.impl.EstadoPagoServiceImpl;
import com.miraquetelavo.lavanderia.common.exception.SolicitudInvalidaException;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EstadoPagoServiceImplTest {

    @Mock
    private EstadoPagoRepository repository;

    @InjectMocks
    private EstadoPagoServiceImpl service;

    @Test
    void conservaElOrdenDelRepositorio() {
        var primero = mock(EstadoPago.class);
        var segundo = mock(EstadoPago.class);
        when(primero.getId()).thenReturn(9L);
        when(segundo.getId()).thenReturn(3L);
        when(repository.findByActivoTrueOrderByOrdenAscIdAsc()).thenReturn(List.of(primero, segundo));

        assertThat(service.listarActivos()).extracting(EstadoPagoResponse::id).containsExactly(9L, 3L);
    }

    @Test
    void convierteElEstadoActivoEnDto() {
        var estado = mock(EstadoPago.class);
        when(estado.getId()).thenReturn(7L);
        when(estado.getCodigo()).thenReturn("PENDIENTE");
        when(estado.getNombre()).thenReturn("Pendiente");
        when(estado.getDescripcion()).thenReturn("En espera");
        when(estado.getOrden()).thenReturn(1);
        when(estado.getActivo()).thenReturn(true);
        when(repository.findByIdAndActivoTrue(7L)).thenReturn(Optional.of(estado));

        assertThat(service.obtener(7L))
                .isEqualTo(new EstadoPagoResponse(7L, "PENDIENTE", "Pendiente", "En espera", 1, true));
    }

    @Test
    void rechazaIdsNoPositivos() {
        assertThatThrownBy(() -> service.obtener(0L))
                .isInstanceOf(SolicitudInvalidaException.class);
        assertThatThrownBy(() -> service.obtener(-1L))
                .isInstanceOf(SolicitudInvalidaException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void fallaSiNoHayUnEstadoActivoConEseId() {
        assertThatThrownBy(() -> service.obtener(7L))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("ID 7");
    }

    @Test
    void obtieneLaEntidadPorCodigoParaUsoInterno() {
        var estado = mock(EstadoPago.class);
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
