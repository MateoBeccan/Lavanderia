package com.miraquetelavo.lavanderia.categoriainsumo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "categorias_insumo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CategoriaInsumo {

    public static final String UK_NOMBRE = "uk_categorias_insumo_nombre";
    public static final int LONGITUD_NOMBRE = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_categoria_insumo")
    private Long id;

    @Column(name = "nombre_cat", nullable = false, length = LONGITUD_NOMBRE)
    private String nombre;

    @Column(nullable = false)
    @ColumnDefault("true")
    private Boolean activo = true;

    public CategoriaInsumo(String nombre) {
        actualizarNombre(nombre);
    }

    public void actualizarNombre(String nombre) {
        this.nombre = nombre;
    }

    public void desactivar() {
        activo = false;
    }

    public void activar() {
        activo = true;
    }
}
