package com.miraquetelavo.lavanderia.comprobante.service.impl;

import com.miraquetelavo.lavanderia.cobro.repository.CobroRepository;
import com.miraquetelavo.lavanderia.common.exception.ConflictoException;
import com.miraquetelavo.lavanderia.common.exception.RecursoNoEncontradoException;
import com.miraquetelavo.lavanderia.common.exception.RestriccionesPostgresql;
import com.miraquetelavo.lavanderia.common.exception.SolicitudInvalidaException;
import com.miraquetelavo.lavanderia.compra.repository.CompraRepository;
import com.miraquetelavo.lavanderia.comprobante.dto.ComprobanteResponse;
import com.miraquetelavo.lavanderia.comprobante.dto.ComprobanteResumenResponse;
import com.miraquetelavo.lavanderia.comprobante.dto.CrearComprobanteRequest;
import com.miraquetelavo.lavanderia.comprobante.dto.TipoOperacionComprobante;
import com.miraquetelavo.lavanderia.comprobante.entity.Comprobante;
import com.miraquetelavo.lavanderia.comprobante.repository.ComprobanteRepository;
import com.miraquetelavo.lavanderia.comprobante.service.ComprobanteService;
import com.miraquetelavo.lavanderia.pagoproveedor.repository.PagoProveedorRepository;
import com.miraquetelavo.lavanderia.pedido.repository.PedidoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

import static com.miraquetelavo.lavanderia.comprobante.dto.TipoOperacionComprobante.COBRO;
import static com.miraquetelavo.lavanderia.comprobante.dto.TipoOperacionComprobante.PAGO_PROVEEDOR;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ComprobanteServiceImpl implements ComprobanteService {
    private final ComprobanteRepository comprobantes;
    private final CobroRepository cobros;
    private final PagoProveedorRepository pagos;
    private final PedidoRepository pedidos;
    private final CompraRepository compras;
    private final Clock relojPedidos;

    @Override
    @Transactional
    public ComprobanteResponse crearDeCobro(Long cobroId, CrearComprobanteRequest solicitud) {
        var cobro = cobros.findDetalleById(cobroId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró el cobro " + cobroId + "."));
        if (comprobantes.existsByCobroId(cobroId)) {
            throw duplicado(null);
        }
        var observaciones = validarYNormalizar(solicitud);
        return guardar(Comprobante.deCobro(cobro, solicitud.fechaEmision(), observaciones), Comprobante.UK_COBRO);
    }

    @Override
    @Transactional
    public ComprobanteResponse crearDePagoProveedor(Long pagoId, CrearComprobanteRequest solicitud) {
        var pago = pagos.findDetalleById(pagoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró el pago " + pagoId + "."));
        if (comprobantes.existsByPagoProveedorId(pagoId)) {
            throw duplicado(null);
        }
        var observaciones = validarYNormalizar(solicitud);
        return guardar(Comprobante.dePagoProveedor(pago, solicitud.fechaEmision(), observaciones), Comprobante.UK_PAGO);
    }

    private String validarYNormalizar(CrearComprobanteRequest solicitud) {
        if (solicitud.fechaEmision() == null) {
            throw new SolicitudInvalidaException("La fecha de emisión es obligatoria.");
        }
        if (solicitud.fechaEmision().getYear() < 1 || solicitud.fechaEmision().isAfter(LocalDate.now(relojPedidos))) {
            throw new SolicitudInvalidaException("La fecha de emisión debe ser válida y no puede ser futura.");
        }
        var observaciones = solicitud.observaciones();
        if (observaciones == null || observaciones.isBlank()) {
            return null;
        }
        var normalizadas = observaciones.strip();
        if (normalizadas.codePointCount(0, normalizadas.length()) > Comprobante.MAX_OBSERVACIONES) {
            throw new SolicitudInvalidaException("Las observaciones pueden tener hasta 200 caracteres.");
        }
        return normalizadas;
    }

    private ComprobanteResponse guardar(Comprobante comprobante, String restriccionUnica) {
        try {
            return responder(comprobantes.saveAndFlush(comprobante));
        } catch (DataIntegrityViolationException ex) {
            if (RestriccionesPostgresql.esViolacionUnica(ex, restriccionUnica)) {
                throw duplicado(ex);
            }
            throw ex;
        }
    }

    private static ConflictoException duplicado(Throwable causa) {
        return new ConflictoException("La operación ya tiene un comprobante.", causa);
    }

    @Override
    public ComprobanteResponse obtener(Long id) {
        return comprobantes.findById(id).map(ComprobanteServiceImpl::responder)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró el comprobante " + id + "."));
    }

    @Override
    public ComprobanteResponse obtenerDeCobro(Long cobroId) {
        return comprobantes.findByCobroId(cobroId).map(ComprobanteServiceImpl::responder)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró un comprobante para el cobro " + cobroId + "."));
    }

    @Override
    public ComprobanteResponse obtenerDePagoProveedor(Long pagoId) {
        return comprobantes.findByPagoProveedorId(pagoId).map(ComprobanteServiceImpl::responder)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró un comprobante para el pago " + pagoId + "."));
    }

    @Override
    public Page<ComprobanteResumenResponse> listarPorPedido(Long pedidoId, Pageable pageable) {
        if (!pedidos.existsById(pedidoId)) {
            throw new RecursoNoEncontradoException("No se encontró el pedido " + pedidoId + ".");
        }
        return comprobantes.findByCobroPedidoIdOrderByFechaEmisionDescIdDesc(pedidoId, sinOrdenExterno(pageable))
                .map(c -> resumir(c, COBRO));
    }

    @Override
    public Page<ComprobanteResumenResponse> listarPorCompra(Long compraId, Pageable pageable) {
        if (!compras.existsById(compraId)) {
            throw new RecursoNoEncontradoException("No se encontró la compra " + compraId + ".");
        }
        return comprobantes.findByPagoProveedorCompraIdOrderByFechaEmisionDescIdDesc(compraId, sinOrdenExterno(pageable))
                .map(c -> resumir(c, PAGO_PROVEEDOR));
    }

    private static Pageable sinOrdenExterno(Pageable pageable) {
        return pageable.isPaged() ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()) : Pageable.unpaged();
    }

    private static ComprobanteResumenResponse resumir(Comprobante comprobante, TipoOperacionComprobante tipo) {
        var monto = tipo == COBRO ? comprobante.getCobro().getMontoCobrado()
                : comprobante.getPagoProveedor().getMontoPagado();
        return new ComprobanteResumenResponse(comprobante.getId(), tipo, comprobante.getFechaEmision(),
                monto, comprobante.getObservaciones());
    }

    private static ComprobanteResponse responder(Comprobante comprobante) {
        var cobro = comprobante.getCobro();
        if (cobro != null) {
            var movimiento = cobro.getMovimientoCaja();
            var metodo = movimiento.getMetodoPago();
            return new ComprobanteResponse(comprobante.getId(), COBRO, cobro.getId(), null,
                    cobro.getPedido().getId(), null, comprobante.getFechaEmision(), comprobante.getObservaciones(),
                    cobro.getMontoCobrado(), metodo.getCodigo(), metodo.getNombre(), movimiento.getId());
        }
        var pago = comprobante.getPagoProveedor();
        var movimiento = pago.getMovimientoCaja();
        var metodo = movimiento.getMetodoPago();
        return new ComprobanteResponse(comprobante.getId(), PAGO_PROVEEDOR, null, pago.getId(),
                null, pago.getCompra().getId(), comprobante.getFechaEmision(), comprobante.getObservaciones(),
                pago.getMontoPagado(), metodo.getCodigo(), metodo.getNombre(), movimiento.getId());
    }
}
