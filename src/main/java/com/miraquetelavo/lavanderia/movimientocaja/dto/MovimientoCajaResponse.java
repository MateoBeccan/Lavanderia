package com.miraquetelavo.lavanderia.movimientocaja.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MovimientoCajaResponse(Long id, Long cajaId, String tipoMovimientoCodigo, String tipoMovimientoNombre,
                                     String metodoPagoCodigo, String metodoPagoNombre, Long usuarioId,
                                     String usuarioNombre, BigDecimal monto, String descripcion, LocalDateTime fechaHora) {
}
