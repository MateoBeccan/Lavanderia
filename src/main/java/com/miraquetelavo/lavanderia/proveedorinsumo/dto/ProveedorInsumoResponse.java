package com.miraquetelavo.lavanderia.proveedorinsumo.dto;

public record ProveedorInsumoResponse(Long proveedorId, String proveedorNombre, Long insumoId, String insumoNombre,
                                      Long categoriaId, String categoriaNombre) {
}
