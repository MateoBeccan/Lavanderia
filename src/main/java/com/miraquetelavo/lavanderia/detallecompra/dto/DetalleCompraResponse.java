package com.miraquetelavo.lavanderia.detallecompra.dto;

import java.math.BigDecimal;

public record DetalleCompraResponse(Long id, Long insumoId, String insumoNombre, BigDecimal cantidad,
                                   BigDecimal precioCompra, BigDecimal subtotal) {}
