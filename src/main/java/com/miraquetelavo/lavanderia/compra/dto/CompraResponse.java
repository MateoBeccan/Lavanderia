package com.miraquetelavo.lavanderia.compra.dto;

import com.miraquetelavo.lavanderia.detallecompra.dto.DetalleCompraResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record CompraResponse(Long id, Long proveedorId, String proveedorNombre, Long usuarioId,
                             String usuarioNombre, LocalDateTime fechaCompra, String numeroComprobante,
                             BigDecimal totalCompra, String estadoPagoCodigo, String estadoPagoNombre,
                             List<DetalleCompraResponse> detalles) {}
