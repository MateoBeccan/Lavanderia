package com.miraquetelavo.lavanderia.comprobante.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotNull;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

public record CrearComprobanteRequest(
        @NotNull @JsonDeserialize(using = FechaEmisionDeserializer.class) LocalDate fechaEmision,
        String observaciones
) {
    @JsonAnySetter
    public void rechazarCampoDesconocido(String campo, Object valor) {
        throw new IllegalArgumentException("La solicitud contiene un campo no admitido.");
    }

    static final class FechaEmisionDeserializer extends ValueDeserializer<LocalDate> {
        private static final Pattern FORMATO = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");

        @Override
        public LocalDate deserialize(JsonParser parser, DeserializationContext contexto) {
            if (!parser.hasToken(JsonToken.VALUE_STRING) || !FORMATO.matcher(parser.getString()).matches()) {
                return contexto.reportInputMismatch(LocalDate.class, "La fecha de emisión debe tener formato yyyy-MM-dd.");
            }
            try {
                return LocalDate.parse(parser.getString());
            } catch (DateTimeParseException ex) {
                return contexto.reportInputMismatch(LocalDate.class, "La fecha de emisión es inválida.");
            }
        }
    }
}
