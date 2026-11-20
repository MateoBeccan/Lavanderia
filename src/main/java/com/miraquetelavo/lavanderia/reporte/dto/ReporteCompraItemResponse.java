package com.miraquetelavo.lavanderia.reporte.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ReporteCompraItemResponse(
        Long compraId, LocalDateTime fechaCompra, Long proveedorId, String proveedorNombre,
        String numeroComprobante, String estadoPagoCodigo, String estadoPagoNombre, BigDecimal totalCompra) {
}
