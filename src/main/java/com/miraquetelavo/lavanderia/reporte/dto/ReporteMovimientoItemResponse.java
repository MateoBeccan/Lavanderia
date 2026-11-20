package com.miraquetelavo.lavanderia.reporte.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.miraquetelavo.lavanderia.movimientocaja.entity.NaturalezaMovimiento;

public record ReporteMovimientoItemResponse(
        Long movimientoId, Long cajaId, LocalDateTime fechaHora,
        String tipoMovimientoCodigo, String tipoMovimientoNombre, String metodoPagoCodigo, String metodoPagoNombre,
        Long usuarioId, String usuarioNombre, String descripcion, BigDecimal monto, NaturalezaMovimiento naturaleza) {

    public ReporteMovimientoItemResponse(Long movimientoId, Long cajaId, LocalDateTime fechaHora,
                                         String tipoMovimientoCodigo, String tipoMovimientoNombre,
                                         String metodoPagoCodigo, String metodoPagoNombre, Long usuarioId,
                                         String usuarioNombre, String descripcion, BigDecimal monto) {
        this(movimientoId, cajaId, fechaHora, tipoMovimientoCodigo, tipoMovimientoNombre, metodoPagoCodigo,
                metodoPagoNombre, usuarioId, usuarioNombre, descripcion, monto,
                NaturalezaMovimiento.desdeCodigo(tipoMovimientoCodigo));
    }
}
