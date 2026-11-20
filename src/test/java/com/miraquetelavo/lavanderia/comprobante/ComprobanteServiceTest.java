package com.miraquetelavo.lavanderia.comprobante;

import com.miraquetelavo.lavanderia.cobro.entity.Cobro;
import com.miraquetelavo.lavanderia.cobro.repository.CobroRepository;
import com.miraquetelavo.lavanderia.common.exception.ConflictoException;
import com.miraquetelavo.lavanderia.common.exception.RecursoNoEncontradoException;
import com.miraquetelavo.lavanderia.common.exception.SolicitudInvalidaException;
import com.miraquetelavo.lavanderia.compra.repository.CompraRepository;
import com.miraquetelavo.lavanderia.comprobante.dto.CrearComprobanteRequest;
import com.miraquetelavo.lavanderia.comprobante.repository.ComprobanteRepository;
import com.miraquetelavo.lavanderia.comprobante.service.impl.ComprobanteServiceImpl;
import com.miraquetelavo.lavanderia.pagoproveedor.entity.PagoProveedor;
import com.miraquetelavo.lavanderia.pagoproveedor.repository.PagoProveedorRepository;
import com.miraquetelavo.lavanderia.pedido.repository.PedidoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComprobanteServiceTest {
    private static final LocalDate HOY = LocalDate.of(2026, 11, 17);

    @Mock ComprobanteRepository comprobantes;
    @Mock CobroRepository cobros;
    @Mock PagoProveedorRepository pagos;
    @Mock PedidoRepository pedidos;
    @Mock CompraRepository compras;

    private ComprobanteServiceImpl servicio() {
        var reloj = Clock.fixed(Instant.parse("2026-11-18T01:00:00Z"), ZoneId.of("America/Argentina/Buenos_Aires"));
        return new ComprobanteServiceImpl(comprobantes, cobros, pagos, pedidos, compras, reloj);
    }

    @Test
    void rechazaFechaFuturaSegunElRelojDeNegocio() {
        when(cobros.findDetalleById(1L)).thenReturn(Optional.of(mock(Cobro.class)));
        assertThatThrownBy(() -> servicio().crearDeCobro(1L, new CrearComprobanteRequest(HOY.plusDays(1), null)))
                .isInstanceOf(SolicitudInvalidaException.class);
        verify(comprobantes, never()).saveAndFlush(any());
    }

    @Test
    void validaLongitudDespuesDeNormalizar() {
        when(pagos.findDetalleById(1L)).thenReturn(Optional.of(mock(PagoProveedor.class)));
        assertThatThrownBy(() -> servicio().crearDePagoProveedor(1L, new CrearComprobanteRequest(HOY, " " + "a".repeat(201) + " ")))
                .isInstanceOf(SolicitudInvalidaException.class);
        verify(comprobantes, never()).saveAndFlush(any());
    }

    @Test
    void rechazaOperacionesYPadresInexistentes() {
        var servicio = servicio();
        var solicitud = new CrearComprobanteRequest(HOY, null);
        assertThatThrownBy(() -> servicio.crearDeCobro(1L, solicitud)).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.crearDePagoProveedor(1L, solicitud)).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.listarPorPedido(1L, Pageable.unpaged())).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.listarPorCompra(1L, Pageable.unpaged())).isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(comprobantes);
    }

    @Test
    void rechazaDuplicadosDeAmbasOperaciones() {
        when(cobros.findDetalleById(1L)).thenReturn(Optional.of(mock(Cobro.class)));
        when(pagos.findDetalleById(1L)).thenReturn(Optional.of(mock(PagoProveedor.class)));
        when(comprobantes.existsByCobroId(1L)).thenReturn(true);
        when(comprobantes.existsByPagoProveedorId(1L)).thenReturn(true);
        var solicitud = new CrearComprobanteRequest(HOY, null);
        var servicio = servicio();
        assertThatThrownBy(() -> servicio.crearDeCobro(1L, solicitud)).isInstanceOf(ConflictoException.class);
        assertThatThrownBy(() -> servicio.crearDePagoProveedor(1L, solicitud)).isInstanceOf(ConflictoException.class);
        verify(comprobantes, never()).saveAndFlush(any());
    }
}
