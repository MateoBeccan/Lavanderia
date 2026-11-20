package com.miraquetelavo.lavanderia.movimientocaja.dto;

import jakarta.validation.constraints.*;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.math.BigDecimal;

public record RegistrarMovimientoManualRequest(
        @NotNull @Positive @JsonDeserialize(using = CajaIdDeserializer.class) Long cajaId,
        @NotNull @Positive @Digits(integer = 8, fraction = 2) BigDecimal monto,
        @NotNull @Positive Long metodoPagoId,
        @NotBlank @Size(max = 250) String descripcion
) {
    static final class CajaIdDeserializer extends ValueDeserializer<Long> {
        @Override
        public Long deserialize(JsonParser parser, DeserializationContext contexto) {
            if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
                return contexto.reportInputMismatch(Long.class, "El ID de caja debe ser un entero JSON.");
            }
            return parser.getLongValue();
        }
    }
}
