package com.miraquetelavo.lavanderia.caja.dto;

import com.miraquetelavo.lavanderia.caja.entity.EstadoCaja;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CajaResumenResponse(
        Long id,
        LocalDateTime fechaApertura,
        LocalDateTime fechaCierre,
        EstadoCaja estado,
        BigDecimal montoInicial,
        BigDecimal montoFinalCalculado,
        BigDecimal diferenciaArqueo
) {
}
