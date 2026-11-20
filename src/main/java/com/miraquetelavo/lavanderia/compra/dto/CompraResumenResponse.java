package com.miraquetelavo.lavanderia.compra.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CompraResumenResponse(Long id, Long proveedorId, String proveedorNombre, LocalDateTime fechaCompra,
                                    String numeroComprobante, BigDecimal totalCompra, String estadoPagoCodigo) {}
