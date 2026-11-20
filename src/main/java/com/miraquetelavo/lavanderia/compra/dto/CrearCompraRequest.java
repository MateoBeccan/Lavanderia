package com.miraquetelavo.lavanderia.compra.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.miraquetelavo.lavanderia.common.exception.SolicitudInvalidaException;
import com.miraquetelavo.lavanderia.detallecompra.dto.DetalleCompraRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CrearCompraRequest(
        @NotNull @Positive Long proveedorId,
        @Size(max = 50) String numeroComprobante,
        @NotEmpty List<@NotNull @Valid DetalleCompraRequest> detalles
) {
    public CrearCompraRequest {
        if (numeroComprobante != null) {
            numeroComprobante = numeroComprobante.strip();
        }
    }

    @JsonAnySetter
    public void rechazarCampo(String nombre, Object valor) {
        throw new SolicitudInvalidaException("Campo no permitido: " + nombre + ".");
    }
}
