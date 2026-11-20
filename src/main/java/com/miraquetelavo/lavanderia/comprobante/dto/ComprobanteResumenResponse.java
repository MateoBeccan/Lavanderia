package com.miraquetelavo.lavanderia.comprobante.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ComprobanteResumenResponse(
        Long id,
        TipoOperacionComprobante tipoOperacion,
        LocalDate fechaEmision,
        BigDecimal montoOperacion,
        String observaciones
) {
}
