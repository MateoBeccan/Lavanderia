package com.miraquetelavo.lavanderia.detallepedido.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record DetallePedidoRequest(
        @Positive Long detalleId,
        @NotNull @Positive Long prendaId,
        @NotNull @Positive Long servicioId,
        @NotNull @Positive @Digits(integer = 8, fraction = 2) BigDecimal cantidad,
        @Size(max = 50) String estadoPrenda,
        @Size(max = 250) String observaciones
) {}
