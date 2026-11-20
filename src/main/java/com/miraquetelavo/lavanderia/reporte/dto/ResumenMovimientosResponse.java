package com.miraquetelavo.lavanderia.reporte.dto;

import java.math.BigDecimal;

public record ResumenMovimientosResponse(
        long cantidadMovimientos, BigDecimal totalIngresos, BigDecimal totalEgresos, BigDecimal saldoNeto) {
}
