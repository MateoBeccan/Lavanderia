package com.miraquetelavo.lavanderia.cobro.dto;

import java.math.BigDecimal;

public record SaldoPedidoResponse(Long pedidoId, BigDecimal totalPedido, BigDecimal totalCobrado,
                                 BigDecimal saldoPendiente, String estadoPagoCodigo, String estadoPagoNombre) {
}
