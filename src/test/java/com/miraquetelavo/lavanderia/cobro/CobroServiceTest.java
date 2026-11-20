package com.miraquetelavo.lavanderia.cobro;

import com.miraquetelavo.lavanderia.caja.entity.Caja;
import com.miraquetelavo.lavanderia.cobro.dto.RegistrarCobroRequest;
import com.miraquetelavo.lavanderia.cobro.entity.Cobro;
import com.miraquetelavo.lavanderia.cobro.repository.CobroRepository;
import com.miraquetelavo.lavanderia.cobro.service.impl.CobroServiceImpl;
import com.miraquetelavo.lavanderia.common.exception.ConflictoException;
import com.miraquetelavo.lavanderia.estadopago.entity.EstadoPago;
import com.miraquetelavo.lavanderia.estadopago.service.EstadoPagoService;
import com.miraquetelavo.lavanderia.estadopedido.entity.EstadoPedido;
import com.miraquetelavo.lavanderia.metodopago.entity.MetodoPago;
import com.miraquetelavo.lavanderia.movimientocaja.entity.MovimientoCaja;
import com.miraquetelavo.lavanderia.movimientocaja.service.RegistroMovimientoCaja;
import com.miraquetelavo.lavanderia.pedido.entity.Pedido;
import com.miraquetelavo.lavanderia.pedido.repository.PedidoRepository;
import com.miraquetelavo.lavanderia.tipomovimientocaja.entity.TipoMovimientoCaja;
import com.miraquetelavo.lavanderia.usuario.entity.Usuario;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CobroServiceTest {
    private final CobroRepository cobros = mock(CobroRepository.class);
    private final PedidoRepository pedidos = mock(PedidoRepository.class);
    private final RegistroMovimientoCaja movimientos = mock(RegistroMovimientoCaja.class);
    private final EstadoPagoService estados = mock(EstadoPagoService.class);
    private final CobroServiceImpl servicio = new CobroServiceImpl(cobros, pedidos, movimientos, estados);

    @ParameterizedTest
    @CsvSource({"0,10000,PENDIENTE,PAGADO", "0,3000,PENDIENTE,PARCIAL", "3000,2000,PARCIAL,PARCIAL",
            "3000,7000,PARCIAL,PAGADO"})
    void coordinaLaEscrituraEnOrdenYDeterminaElEstado(String acumulado, String importe, String anterior, String nuevo) {
        var monto = new BigDecimal(importe);
        var pedido = mock(Pedido.class);
        var caja = mock(Caja.class);
        var usuario = mock(Usuario.class);
        var formaPago = mock(MetodoPago.class);
        var operativo = mock(EstadoPedido.class);
        var tipo = mock(TipoMovimientoCaja.class);
        var estadoActual = mock(EstadoPago.class);
        var estadoNuevo = mock(EstadoPago.class);
        var fecha = LocalDateTime.of(2026, 11, 17, 10, 0);
        var movimiento = new MovimientoCaja(caja, tipo, formaPago, usuario, monto, "Cobro", fecha);
        when(tipo.getCodigo()).thenReturn("COBRO_PEDIDO");
        when(operativo.getCodigo()).thenReturn("PENDIENTE");
        when(pedido.getEstadoPedido()).thenReturn(operativo);
        when(pedido.getEstadoPago()).thenReturn(estadoActual);
        when(pedido.getTotal()).thenReturn(new BigDecimal("10000"));
        when(estadoActual.getCodigo()).thenReturn(anterior);
        when(estadoNuevo.getActivo()).thenReturn(true);
        when(movimientos.usuarioActivo(3L)).thenReturn(usuario);
        when(movimientos.bloquearCajaAbierta()).thenReturn(caja);
        when(pedidos.findOneById(1L)).thenReturn(Optional.of(pedido));
        when(cobros.sumarPorPedido(1L)).thenReturn(new BigDecimal(acumulado));
        when(movimientos.prepararCobro(caja, pedido, 2L, monto, usuario)).thenReturn(movimiento);
        when(cobros.save(any(Cobro.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
        when(estados.buscarPorCodigo(nuevo)).thenReturn(estadoNuevo);

        var cobro = servicio.registrar(1L, new RegistrarCobroRequest(monto, 2L), 3L);

        var orden = inOrder(movimientos, pedidos, cobros, estados, pedido);
        orden.verify(movimientos).usuarioActivo(3L);
        orden.verify(movimientos).bloquearCajaAbierta();
        orden.verify(pedidos).findOneById(1L);
        orden.verify(cobros).sumarPorPedido(1L);
        orden.verify(movimientos).prepararCobro(caja, pedido, 2L, monto, usuario);
        orden.verify(cobros).save(any(Cobro.class));
        orden.verify(estados).buscarPorCodigo(nuevo);
        orden.verify(pedido).asignarEstadoPago(estadoNuevo);
        verify(pedido, never()).cambiarEstado(any());
        assertThat(cobro.montoCobrado()).isEqualByComparingTo(monto);
        assertThat(cobro.fechaCobro()).isEqualTo(fecha);
    }

    @Test
    void noDevuelveUnSaldoContradictorioConElEstadoPersistido() {
        var datos = mock(CobroRepository.Saldo.class);
        when(datos.getTotal()).thenReturn(new BigDecimal("10000"));
        when(datos.getCobrado()).thenReturn(new BigDecimal("1"));
        when(datos.getCodigo()).thenReturn("PENDIENTE");
        when(cobros.consultarSaldo(1L)).thenReturn(Optional.of(datos));

        assertThatThrownBy(() -> servicio.consultarSaldo(1L)).isInstanceOf(ConflictoException.class);
    }
}
