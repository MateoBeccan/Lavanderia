package com.miraquetelavo.lavanderia.proveedor.dto;

public record ProveedorResponse(Long id, String nombre, String cuit, String telefono, String rubro, Boolean activo) {
}
