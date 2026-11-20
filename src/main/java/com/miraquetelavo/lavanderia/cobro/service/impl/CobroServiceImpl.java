package com.miraquetelavo.lavanderia.cobro.service.impl;

import com.miraquetelavo.lavanderia.cobro.dto.CobroResponse;
import com.miraquetelavo.lavanderia.cobro.dto.RegistrarCobroRequest;
import com.miraquetelavo.lavanderia.cobro.dto.SaldoPedidoResponse;
import com.miraquetelavo.lavanderia.cobro.entity.Cobro;
import com.miraquetelavo.lavanderia.cobro.repository.CobroRepository;
import com.miraquetelavo.lavanderia.cobro.service.CobroService;
import com.miraquetelavo.lavanderia.common.exception.ConflictoException;
import com.miraquetelavo.lavanderia.common.exception.RecursoNoEncontradoException;
import com.miraquetelavo.lavanderia.estadopago.service.EstadoPagoService;
import com.miraquetelavo.lavanderia.movimientocaja.service.RegistroMovimientoCaja;
import com.miraquetelavo.lavanderia.pedido.repository.PedidoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

@Service
@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CobroServiceImpl implements CobroService {
    private final CobroRepository cobros;
    private final PedidoRepository pedidos;
    private final RegistroMovimientoCaja movimientos;
    private final EstadoPagoService estadosPago;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CobroResponse registrar(Long pedidoId, RegistrarCobroRequest solicitud, Long usuarioId) {
        var usuario = movimientos.usuarioActivo(usuarioId);
        var caja = movimientos.bloquearCajaAbierta();
        var pedido = pedidos.findOneById(pedidoId).orElseThrow(() -> pedidoNoEncontrado(pedidoId));
        if ("CANCELADO".equals(pedido.getEstadoPedido().getCodigo())) {
            throw new ConflictoException("No se puede cobrar un pedido cancelado.", null);
        }

        var total = pedido.getTotal();
        var cobrado = cobros.sumarPorPedido(pedidoId);
        var estadoActual = pedido.getEstadoPago();
        var saldo = saldoValidado(total, cobrado, estadoActual == null ? null : estadoActual.getCodigo());
        if (saldo.signum() == 0) {
            throw new ConflictoException("El pedido está totalmente pagado.", null);
        }
        if (solicitud.monto().compareTo(saldo) > 0) {
            throw new ConflictoException("El cobro supera el saldo pendiente del pedido.", null);
        }

        var movimiento = movimientos.prepararCobro(caja, pedido, solicitud.metodoPagoId(), solicitud.monto(), usuario);
        var cobro = cobros.save(new Cobro(pedido, movimiento));
        var codigo = codigoEstado(cobrado.add(cobro.getMontoCobrado()), total);
        var estado = estadosPago.buscarPorCodigo(codigo);
        if (!Boolean.TRUE.equals(estado.getActivo())) {
            throw new ConflictoException("El estado de pago " + codigo + " está inactivo.", null);
        }
        pedido.asignarEstadoPago(estado);
        return responder(cobro);
    }

    @Override
    public CobroResponse obtener(Long id) {
        return cobros.findDetalleById(id).map(CobroServiceImpl::responder)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró el cobro " + id + "."));
    }

    @Override
    public Page<CobroResponse> listar(Long pedidoId, Pageable pageable) {
        if (!pedidos.existsById(pedidoId)) {
            throw pedidoNoEncontrado(pedidoId);
        }
        var pagina = pageable.isPaged() ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()) : pageable;
        return cobros.findByPedidoIdOrderByFechaCobroDescIdDesc(pedidoId, pagina).map(CobroServiceImpl::responder);
    }

    @Override
    public SaldoPedidoResponse consultarSaldo(Long pedidoId) {
        var datos = cobros.consultarSaldo(pedidoId).orElseThrow(() -> pedidoNoEncontrado(pedidoId));
        var saldo = saldoValidado(datos.getTotal(), datos.getCobrado(), datos.getCodigo());
        return new SaldoPedidoResponse(pedidoId, datos.getTotal(), datos.getCobrado(), saldo,
                datos.getCodigo(), datos.getNombre());
    }

    private static BigDecimal saldoValidado(BigDecimal total, BigDecimal cobrado, String codigo) {
        var saldo = total.subtract(cobrado);
        if (saldo.signum() < 0 || !codigoEstado(cobrado, total).equals(codigo)) {
            throw new ConflictoException("El estado financiero del pedido es inconsistente.", null);
        }
        return saldo;
    }

    private static String codigoEstado(BigDecimal cobrado, BigDecimal total) {
        if (cobrado.signum() == 0) {
            return "PENDIENTE";
        }
        return cobrado.compareTo(total) == 0 ? "PAGADO" : "PARCIAL";
    }

    private static RecursoNoEncontradoException pedidoNoEncontrado(Long id) {
        return new RecursoNoEncontradoException("No se encontró el pedido " + id + ".");
    }

    private static CobroResponse responder(Cobro cobro) {
        var movimiento = cobro.getMovimientoCaja();
        var metodo = movimiento.getMetodoPago();
        var usuario = movimiento.getUsuario();
        return new CobroResponse(cobro.getId(), cobro.getPedido().getId(), movimiento.getId(), movimiento.getCaja().getId(),
                metodo.getCodigo(), metodo.getNombre(), usuario.getId(), usuario.getNombre(),
                cobro.getFechaCobro(), cobro.getMontoCobrado());
    }
}
