package com.miraquetelavo.lavanderia.reporte.dto;

import java.math.BigDecimal;

public record ResumenPedidosResponse(
        long cantidadPedidos, BigDecimal totalGeneral) {
}
