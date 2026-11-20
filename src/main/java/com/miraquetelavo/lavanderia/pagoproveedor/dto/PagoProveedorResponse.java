package com.miraquetelavo.lavanderia.pagoproveedor.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PagoProveedorResponse(
        Long id, Long compraId, Long proveedorId, String proveedorNombre,
        Long movimientoCajaId, Long cajaId, String metodoPagoCodigo, String metodoPagoNombre,
        Long usuarioId, String usuarioNombre, LocalDateTime fechaPago, BigDecimal montoPagado
) {
}
