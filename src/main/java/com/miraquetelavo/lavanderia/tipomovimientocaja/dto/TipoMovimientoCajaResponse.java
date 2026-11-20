package com.miraquetelavo.lavanderia.tipomovimientocaja.dto;

public record TipoMovimientoCajaResponse(Long id, String codigo, String nombre, String descripcion,
                                         Integer orden, Boolean activo) {
}
