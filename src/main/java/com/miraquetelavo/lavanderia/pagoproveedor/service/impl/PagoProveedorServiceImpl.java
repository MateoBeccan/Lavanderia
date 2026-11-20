package com.miraquetelavo.lavanderia.pagoproveedor.service.impl;

import com.miraquetelavo.lavanderia.common.exception.ConflictoException;
import com.miraquetelavo.lavanderia.common.exception.RecursoNoEncontradoException;
import com.miraquetelavo.lavanderia.compra.repository.CompraRepository;
import com.miraquetelavo.lavanderia.estadopago.service.EstadoPagoService;
import com.miraquetelavo.lavanderia.movimientocaja.service.RegistroMovimientoCaja;
import com.miraquetelavo.lavanderia.pagoproveedor.dto.PagoProveedorResponse;
import com.miraquetelavo.lavanderia.pagoproveedor.dto.RegistrarPagoProveedorRequest;
import com.miraquetelavo.lavanderia.pagoproveedor.dto.SaldoCompraResponse;
import com.miraquetelavo.lavanderia.pagoproveedor.entity.PagoProveedor;
import com.miraquetelavo.lavanderia.pagoproveedor.repository.PagoProveedorRepository;
import com.miraquetelavo.lavanderia.pagoproveedor.service.PagoProveedorService;
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
public class PagoProveedorServiceImpl implements PagoProveedorService {
    private final PagoProveedorRepository pagos;
    private final CompraRepository compras;
    private final RegistroMovimientoCaja movimientos;
    private final EstadoPagoService estadosPago;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PagoProveedorResponse registrarPago(Long compraId, RegistrarPagoProveedorRequest solicitud, Long usuarioId) {
        var usuario = movimientos.usuarioActivo(usuarioId);
        var caja = movimientos.bloquearCajaAbierta();
        var compra = compras.findOneById(compraId).orElseThrow(() -> compraNoEncontrado(compraId));
        var total = compra.getTotalCompra();
        var pagado = pagos.sumarPorCompra(compraId);
        var estadoActual = compra.getEstadoPago();
        var saldo = saldoValidado(total, pagado, estadoActual == null ? null : estadoActual.getCodigo());
        if (saldo.signum() == 0) {
            throw new ConflictoException("La compra está totalmente pagada.", null);
        }
        if (solicitud.monto().compareTo(saldo) > 0) {
            throw new ConflictoException("El pago supera el saldo pendiente de la compra.", null);
        }

        var movimiento = movimientos.prepararPagoProveedor(caja, compraId, solicitud.metodoPagoId(), solicitud.monto(), usuario);
        var pago = pagos.save(new PagoProveedor(compra, movimiento));
        var codigo = codigoEstado(pagado.add(pago.getMontoPagado()), total);
        var estado = estadosPago.buscarPorCodigo(codigo);
        if (!Boolean.TRUE.equals(estado.getActivo())) {
            throw new ConflictoException("El estado de pago " + codigo + " está inactivo.", null);
        }
        compra.asignarEstadoPago(estado);
        return responder(pago);
    }

    @Override
    public PagoProveedorResponse obtenerPorId(Long id) {
        return pagos.findDetalleById(id).map(PagoProveedorServiceImpl::responder)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró el pago " + id + "."));
    }

    @Override
    public Page<PagoProveedorResponse> listarPagosCompra(Long compraId, Pageable pageable) {
        if (!compras.existsById(compraId)) {
            throw compraNoEncontrado(compraId);
        }
        var pagina = pageable.isPaged() ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()) : pageable;
        return pagos.findByCompraIdOrderByFechaPagoDescIdDesc(compraId, pagina).map(PagoProveedorServiceImpl::responder);
    }

    @Override
    public SaldoCompraResponse consultarSaldoCompra(Long compraId) {
        var datos = pagos.consultarSaldo(compraId).orElseThrow(() -> compraNoEncontrado(compraId));
        var saldo = saldoValidado(datos.getTotal(), datos.getPagado(), datos.getCodigo());
        return new SaldoCompraResponse(compraId, datos.getTotal(), datos.getPagado(), saldo,
                datos.getCodigo(), datos.getNombre());
    }

    private static BigDecimal saldoValidado(BigDecimal total, BigDecimal pagado, String codigo) {
        var saldo = total.subtract(pagado);
        if (pagado.signum() < 0 || saldo.signum() < 0 || !codigoEstado(pagado, total).equals(codigo)) {
            throw new ConflictoException("El estado financiero de la compra es inconsistente.", null);
        }
        return saldo;
    }

    private static String codigoEstado(BigDecimal pagado, BigDecimal total) {
        if (pagado.signum() == 0) {
            return "PENDIENTE";
        }
        return pagado.compareTo(total) == 0 ? "PAGADO" : "PARCIAL";
    }

    private static RecursoNoEncontradoException compraNoEncontrado(Long id) {
        return new RecursoNoEncontradoException("No se encontró la compra " + id + ".");
    }

    private static PagoProveedorResponse responder(PagoProveedor pago) {
        var movimiento = pago.getMovimientoCaja();
        var proveedor = pago.getCompra().getProveedor();
        var metodo = movimiento.getMetodoPago();
        var usuario = movimiento.getUsuario();
        return new PagoProveedorResponse(pago.getId(), pago.getCompra().getId(), proveedor.getId(), proveedor.getNombre(),
                movimiento.getId(), movimiento.getCaja().getId(),
                metodo.getCodigo(), metodo.getNombre(), usuario.getId(), usuario.getNombre(),
                pago.getFechaPago(), pago.getMontoPagado());
    }
}
