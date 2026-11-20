package com.miraquetelavo.lavanderia.insumo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import com.miraquetelavo.lavanderia.categoriainsumo.entity.CategoriaInsumo;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "insumos", indexes = @Index(name = "ix_insumos_categoria_activo", columnList = "id_categoria_insumo, activo"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Insumo {

    public static final String UK_NOMBRE = "uk_insumos_nombre";
    public static final int LONGITUD_NOMBRE = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_insumo")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_categoria_insumo", nullable = false,
            foreignKey = @ForeignKey(name = "fk_insumos_categoria"))
    private CategoriaInsumo categoria;

    @Column(name = "nombre_insumo", nullable = false, length = LONGITUD_NOMBRE)
    private String nombre;

    @Column(nullable = false)
    @ColumnDefault("true")
    private Boolean activo = true;

    public Insumo(String nombre, CategoriaInsumo categoria) {
        actualizarDatos(nombre, categoria);
    }

    public void actualizarDatos(String nombre, CategoriaInsumo categoria) {
        this.nombre = nombre;
        this.categoria = categoria;
    }

    public void desactivar() {
        activo = false;
    }

    public void activar() {
        activo = true;
    }
}
