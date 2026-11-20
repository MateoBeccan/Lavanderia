package com.miraquetelavo.lavanderia.reporte.dto;

import java.math.BigDecimal;

public record ResumenComprasResponse(
        long cantidadCompras, BigDecimal totalComprado) {
}
