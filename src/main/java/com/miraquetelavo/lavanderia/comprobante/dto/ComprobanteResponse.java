package com.miraquetelavo.lavanderia.comprobante.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ComprobanteResponse(
        Long id,
        TipoOperacionComprobante tipoOperacion,
        Long cobroId,
        Long pagoProveedorId,
        Long pedidoId,
        Long compraId,
        LocalDate fechaEmision,
        String observaciones,
        BigDecimal montoOperacion,
        String metodoPagoCodigo,
        String metodoPagoNombre,
        Long movimientoCajaId
) {
}
