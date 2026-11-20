package com.miraquetelavo.lavanderia.detallepedido.dto;

import com.miraquetelavo.lavanderia.servicio.entity.ModalidadCobro;

import java.math.BigDecimal;

public record DetallePedidoResponse(
        Long id, Long prendaId, String prendaNombre, Long servicioId, String servicioNombre,
        ModalidadCobro modalidadCobro, BigDecimal cantidad, BigDecimal precioUnitario, BigDecimal subtotal,
        String estadoPrenda, String observaciones
) {}
