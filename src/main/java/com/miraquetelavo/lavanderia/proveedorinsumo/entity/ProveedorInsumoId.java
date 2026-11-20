package com.miraquetelavo.lavanderia.proveedorinsumo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProveedorInsumoId implements Serializable {

    @Column(name = "id_proveedor")
    private Long proveedorId;

    @Column(name = "id_insumo")
    private Long insumoId;

    public ProveedorInsumoId(Long proveedorId, Long insumoId) {
        this.proveedorId = proveedorId;
        this.insumoId = insumoId;
    }
}
