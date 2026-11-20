package com.miraquetelavo.lavanderia.insumo.dto;

public record InsumoResponse(Long id, String nombre, Long categoriaId, String categoriaNombre, Boolean activo) {
}
