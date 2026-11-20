package com.miraquetelavo.lavanderia.reporte.service.impl;

import com.miraquetelavo.lavanderia.common.exception.SolicitudInvalidaException;
import com.miraquetelavo.lavanderia.estadopago.repository.EstadoPagoRepository;
import com.miraquetelavo.lavanderia.estadopedido.repository.EstadoPedidoRepository;
import com.miraquetelavo.lavanderia.metodopago.repository.MetodoPagoRepository;
import com.miraquetelavo.lavanderia.reporte.dto.ReporteComprasResponse;
import com.miraquetelavo.lavanderia.reporte.dto.ReporteMovimientosResponse;
import com.miraquetelavo.lavanderia.reporte.dto.ReportePedidosResponse;
import com.miraquetelavo.lavanderia.reporte.repository.ReporteRepository;
import com.miraquetelavo.lavanderia.reporte.pdf.LimitesReportePdf;
import com.miraquetelavo.lavanderia.reporte.service.ReporteService;
import com.miraquetelavo.lavanderia.tipomovimientocaja.repository.TipoMovimientoCajaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.function.Predicate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, propagation = Propagation.REQUIRES_NEW)
public class ReporteServiceImpl implements ReporteService {

    private final ReporteRepository reportes;
    private final EstadoPedidoRepository estadosPedido;
    private final EstadoPagoRepository estadosPago;
    private final TipoMovimientoCajaRepository tipos;
    private final MetodoPagoRepository metodos;

    @Override
    public ReportePedidosResponse pedidos(LocalDate desde, LocalDate hasta, Long clienteId,
                                           String estadoPedido, String estadoPago, int page, int size) {
        return pedidos(desde, hasta, clienteId, estadoPedido, estadoPago, pagina(page, size));
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ,
            propagation = Propagation.REQUIRES_NEW, timeout = 8)
    public ReportePedidosResponse pedidosParaPdf(LocalDate desde, LocalDate hasta, Long clienteId, String estadoPedido, String estadoPago) {
        reportes.limitarTiempoExportacion();
        return pedidos(desde, hasta, clienteId, estadoPedido, estadoPago, PageRequest.of(0, LimitesReportePdf.FILAS));
    }

    private ReportePedidosResponse pedidos(LocalDate desde, LocalDate hasta, Long clienteId, String estadoPedido, String estadoPago,
                                     PageRequest pagina) {
        var fechas = Rango.validar(desde, hasta);
        validarId(clienteId, "clienteId");
        validarCodigo(estadoPedido, "estadoPedidoCodigo", estadosPedido::existsByCodigo);
        validarCodigo(estadoPago, "estadoPagoCodigo", estadosPago::existsByCodigo);
        return reportes.pedidos(fechas.desde(), fechas.hastaExclusivo(), clienteId, estadoPedido, estadoPago, pagina);
    }

    @Override
    public ReporteMovimientosResponse movimientos(LocalDate desde, LocalDate hasta, String tipo,
                                                   String metodo, int page, int size) {
        return movimientos(desde, hasta, tipo, metodo, pagina(page, size));
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ,
            propagation = Propagation.REQUIRES_NEW, timeout = 8)
    public ReporteMovimientosResponse movimientosParaPdf(LocalDate desde, LocalDate hasta, String tipo, String metodo) {
        reportes.limitarTiempoExportacion();
        return movimientos(desde, hasta, tipo, metodo, PageRequest.of(0, LimitesReportePdf.FILAS));
    }

    private ReporteMovimientosResponse movimientos(LocalDate desde, LocalDate hasta, String tipo, String metodo,
                                     PageRequest pagina) {
        var fechas = Rango.validar(desde, hasta);
        validarCodigo(tipo, "tipoMovimientoCodigo", tipos::existsByCodigo);
        validarCodigo(metodo, "metodoPagoCodigo", metodos::existsByCodigo);
        return reportes.movimientos(fechas.desde(), fechas.hastaExclusivo(), tipo, metodo, pagina);
    }

    @Override
    public ReporteComprasResponse compras(LocalDate desde, LocalDate hasta, Long proveedorId,
                                           String estadoPago, int page, int size) {
        return compras(desde, hasta, proveedorId, estadoPago, pagina(page, size));
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ,
            propagation = Propagation.REQUIRES_NEW, timeout = 8)
    public ReporteComprasResponse comprasParaPdf(LocalDate desde, LocalDate hasta, Long proveedorId, String estadoPago) {
        reportes.limitarTiempoExportacion();
        return compras(desde, hasta, proveedorId, estadoPago, PageRequest.of(0, LimitesReportePdf.FILAS));
    }

    private ReporteComprasResponse compras(LocalDate desde, LocalDate hasta, Long proveedorId, String estadoPago,
                                     PageRequest pagina) {
        var fechas = Rango.validar(desde, hasta);
        validarId(proveedorId, "proveedorId");
        validarCodigo(estadoPago, "estadoPagoCodigo", estadosPago::existsByCodigo);
        return reportes.compras(fechas.desde(), fechas.hastaExclusivo(), proveedorId, estadoPago, pagina);
    }

    private static PageRequest pagina(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new SolicitudInvalidaException("page debe ser mayor o igual a 0 y size debe estar entre 1 y 100.");
        }
        return PageRequest.of(page, size);
    }

    private static void validarId(Long valor, String campo) {
        if (valor != null && valor <= 0) {
            throw new SolicitudInvalidaException(campo + " debe ser positivo.");
        }
    }

    private static void validarCodigo(String codigo, String campo, Predicate<String> existe) {
        if (codigo != null && (codigo.isBlank() || codigo.length() > 30 || !existe.test(codigo))) {
            throw new SolicitudInvalidaException(campo + " contiene un código desconocido.");
        }
    }

    private record Rango(LocalDateTime desde, LocalDateTime hastaExclusivo) {
        private static Rango validar(LocalDate desde, LocalDate hasta) {
            validarAnio(desde);
            validarAnio(hasta);
            if (desde != null && hasta != null && desde.isAfter(hasta)) {
                throw new SolicitudInvalidaException("fechaDesde debe ser anterior o igual a fechaHasta.");
            }
            return new Rango(desde == null ? null : desde.atStartOfDay(),
                    hasta == null ? null : hasta.plusDays(1).atStartOfDay());
        }

        private static void validarAnio(LocalDate fecha) {
            if (fecha != null && (fecha.getYear() < 1 || fecha.getYear() > 9999)) {
                throw new SolicitudInvalidaException("El año de la fecha debe estar entre 0001 y 9999.");
            }
        }
    }
}
