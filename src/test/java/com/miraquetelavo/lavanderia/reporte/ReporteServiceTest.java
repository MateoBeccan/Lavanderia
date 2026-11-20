package com.miraquetelavo.lavanderia.reporte;

import com.miraquetelavo.lavanderia.common.exception.SolicitudInvalidaException;
import com.miraquetelavo.lavanderia.estadopago.repository.EstadoPagoRepository;
import com.miraquetelavo.lavanderia.estadopedido.repository.EstadoPedidoRepository;
import com.miraquetelavo.lavanderia.metodopago.repository.MetodoPagoRepository;
import com.miraquetelavo.lavanderia.reporte.repository.ReporteRepository;
import com.miraquetelavo.lavanderia.reporte.service.impl.ReporteServiceImpl;
import com.miraquetelavo.lavanderia.tipomovimientocaja.repository.TipoMovimientoCajaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReporteServiceTest {
    private static final LocalDate DIA = LocalDate.of(2026, 11, 30);

    @Mock ReporteRepository repository;
    @Mock EstadoPedidoRepository estadosPedido;
    @Mock EstadoPagoRepository estadosPago;
    @Mock TipoMovimientoCajaRepository tipos;
    @Mock MetodoPagoRepository metodos;
    @InjectMocks ReporteServiceImpl servicio;

    @Test
    void aplicaLimitesYFiltrosCombinados() {
        when(estadosPedido.existsByCodigo("CANCELADO")).thenReturn(true);
        when(estadosPago.existsByCodigo("PAGADO")).thenReturn(true);
        servicio.pedidos(DIA, DIA, 3L, "CANCELADO", "PAGADO", 1, 100);
        verify(repository).pedidos(DIA.atStartOfDay(), DIA.plusDays(1).atStartOfDay(),
                3L, "CANCELADO", "PAGADO", PageRequest.of(1, 100));
    }

    @Test
    void permiteLimitesIndependientesYFechaMaxima() {
        var maxima = LocalDate.of(9999, 12, 31);
        servicio.compras(null, maxima, null, null, 0, 20);
        servicio.movimientos(DIA, null, null, null, 0, 20);
        verify(repository).compras(null, maxima.plusDays(1).atStartOfDay(), null, null, PageRequest.of(0, 20));
        verify(repository).movimientos(DIA.atStartOfDay(), null, null, null, PageRequest.of(0, 20));
    }

    @ParameterizedTest
    @CsvSource({"-1,20", "0,0", "0,101"})
    void rechazaPaginacionInvalida(int page, int size) {
        assertThatThrownBy(() -> servicio.pedidos(null, null, null, null, null, page, size))
                .isInstanceOf(SolicitudInvalidaException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void rechazaRangoInvertidoYFechasExtremas() {
        assertThatThrownBy(() -> servicio.compras(DIA, DIA.minusDays(1), null, null, 0, 20))
                .isInstanceOf(SolicitudInvalidaException.class);
        assertThatThrownBy(() -> servicio.compras(null, LocalDate.MAX, null, null, 0, 20))
                .isInstanceOf(SolicitudInvalidaException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void validaCodigosContraCatalogos() {
        assertThatThrownBy(() -> servicio.pedidos(null, null, null, "INEXISTENTE", null, 0, 20))
                .isInstanceOf(SolicitudInvalidaException.class).hasMessageContaining("estadoPedidoCodigo");
        assertThatThrownBy(() -> servicio.compras(null, null, null, "INEXISTENTE", 0, 20))
                .isInstanceOf(SolicitudInvalidaException.class).hasMessageContaining("estadoPagoCodigo");
        assertThatThrownBy(() -> servicio.movimientos(null, null, "INEXISTENTE", null, 0, 20))
                .isInstanceOf(SolicitudInvalidaException.class).hasMessageContaining("tipoMovimientoCodigo");
        assertThatThrownBy(() -> servicio.movimientos(null, null, null, "INEXISTENTE", 0, 20))
                .isInstanceOf(SolicitudInvalidaException.class).hasMessageContaining("metodoPagoCodigo");
        verifyNoInteractions(repository);
    }
}
