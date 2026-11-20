package com.miraquetelavo.lavanderia.pagoproveedor.dto;

import java.math.BigDecimal;

public record SaldoCompraResponse(
        Long compraId, BigDecimal totalCompra, BigDecimal totalPagado, BigDecimal saldoPendiente,
        String estadoPagoCodigo, String estadoPagoNombre
) {
}
