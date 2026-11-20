package com.miraquetelavo.lavanderia.proveedorinsumo.entity;

import com.miraquetelavo.lavanderia.insumo.entity.Insumo;
import com.miraquetelavo.lavanderia.proveedor.entity.Proveedor;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

// Hibernate genera la PK con id_insumo primero. El índice complementario cubre la búsqueda por proveedor.
@Entity
@Table(name = "proveedores_insumos", indexes = @Index(name = "ix_proveedores_insumos_proveedor", columnList = "id_proveedor"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProveedorInsumo {

    @EmbeddedId
    private ProveedorInsumoId id;

    @MapsId("proveedorId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_proveedor", nullable = false,
            foreignKey = @ForeignKey(name = "fk_proveedores_insumos_proveedor"))
    private Proveedor proveedor;

    @MapsId("insumoId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_insumo", nullable = false,
            foreignKey = @ForeignKey(name = "fk_proveedores_insumos_insumo"))
    private Insumo insumo;
}
