package com.miraquetelavo.lavanderia.reporte.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;

public record ReportePedidoItemResponse(
        Long pedidoId, LocalDateTime fechaIngreso, LocalDate fechaEntregaEstimada,
        Long clienteId, String clienteNombreCompleto, String estadoPedidoCodigo, String estadoPedidoNombre,
        String estadoPagoCodigo, String estadoPagoNombre, BigDecimal totalPedido) {
}
