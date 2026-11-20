package com.miraquetelavo.lavanderia.cobro.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CobroResponse(Long id, Long pedidoId, Long movimientoCajaId, Long cajaId,
                           String metodoPagoCodigo, String metodoPagoNombre, Long usuarioId,
                           String usuarioNombre, LocalDateTime fechaCobro, BigDecimal montoCobrado) {
}
