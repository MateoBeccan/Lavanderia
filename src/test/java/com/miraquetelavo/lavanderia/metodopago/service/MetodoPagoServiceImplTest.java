package com.miraquetelavo.lavanderia.metodopago.service;

import com.miraquetelavo.lavanderia.common.exception.RecursoNoEncontradoException;
import com.miraquetelavo.lavanderia.metodopago.dto.MetodoPagoResponse;
import com.miraquetelavo.lavanderia.metodopago.entity.MetodoPago;
import com.miraquetelavo.lavanderia.metodopago.repository.MetodoPagoRepository;
import com.miraquetelavo.lavanderia.metodopago.service.impl.MetodoPagoServiceImpl;
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
class MetodoPagoServiceImplTest {

    @Mock
    private MetodoPagoRepository repository;

    @InjectMocks
    private MetodoPagoServiceImpl service;

    @Test
    void conservaElOrdenDelRepositorio() {
        var primero = mock(MetodoPago.class);
        var segundo = mock(MetodoPago.class);
        when(primero.getId()).thenReturn(9L);
        when(segundo.getId()).thenReturn(3L);
        when(repository.listarActivosEnOrdenComercial()).thenReturn(List.of(primero, segundo));

        assertThat(service.listarActivos()).extracting(MetodoPagoResponse::id).containsExactly(9L, 3L);
    }

    @Test
    void convierteElEstadoActivoEnDto() {
        var estado = mock(MetodoPago.class);
        when(estado.getId()).thenReturn(7L);
        when(estado.getCodigo()).thenReturn("EFECTIVO");
        when(estado.getNombre()).thenReturn("Efectivo");
        when(estado.getDescripcion()).thenReturn("En espera");
        when(estado.getActivo()).thenReturn(true);
        when(repository.findByIdAndActivoTrue(7L)).thenReturn(Optional.of(estado));

        assertThat(service.obtener(7L))
                .isEqualTo(new MetodoPagoResponse(7L, "EFECTIVO", "Efectivo", "En espera", true));
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
        var estado = mock(MetodoPago.class);
        when(repository.findByCodigo("EFECTIVO")).thenReturn(Optional.of(estado));

        assertThat(service.buscarPorCodigo("EFECTIVO")).isSameAs(estado);
    }

    @Test
    void fallaSiElCodigoNoExiste() {
        assertThatThrownBy(() -> service.buscarPorCodigo("INEXISTENTE"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("INEXISTENTE");
    }
}
