package com.miraquetelavo.lavanderia.servicio.dto;

import com.miraquetelavo.lavanderia.servicio.entity.ModalidadCobro;

import java.math.BigDecimal;

public record ServicioResponse(
        Long id,
        String nombre,
        BigDecimal precio,
        ModalidadCobro modalidadCobro,
        Boolean activo
) {
}
