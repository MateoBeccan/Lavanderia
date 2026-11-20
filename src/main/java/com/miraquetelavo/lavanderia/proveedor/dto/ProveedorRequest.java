package com.miraquetelavo.lavanderia.proveedor.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import static com.miraquetelavo.lavanderia.proveedor.entity.Proveedor.LONGITUD_CUIT;
import static com.miraquetelavo.lavanderia.proveedor.entity.Proveedor.LONGITUD_NOMBRE;
import static com.miraquetelavo.lavanderia.proveedor.entity.Proveedor.LONGITUD_RUBRO;
import static com.miraquetelavo.lavanderia.proveedor.entity.Proveedor.LONGITUD_TELEFONO;

public record ProveedorRequest(
        @NotBlank @Size(max = LONGITUD_NOMBRE) String nombre,
        @NotBlank @Size(max = LONGITUD_CUIT) String cuit,
        @NotBlank @Size(max = LONGITUD_TELEFONO) String telefono,
        @Size(max = LONGITUD_RUBRO) String rubro) {

    public ProveedorRequest {
        nombre = nombre == null ? null : nombre.strip();
        telefono = telefono == null ? null : telefono.strip();
        rubro = rubro == null || rubro.isBlank() ? null : rubro.strip();
    }

    @JsonAnySetter
    public void rechazarCampoDesconocido(String campo, Object valor) {
        throw new IllegalArgumentException("Campo no admitido para proveedor: " + campo);
    }
}
