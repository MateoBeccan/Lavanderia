package com.miraquetelavo.lavanderia.prenda.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.GeneratedColumn;

@Entity
@Table(name = "prendas",
        uniqueConstraints = @UniqueConstraint(name = Prenda.UK_NOMBRE, columnNames = "nombre_normalizado"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Prenda {

    public static final String UK_NOMBRE = "uk_prendas_nombre";
    public static final int LONGITUD_NOMBRE = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_prenda")
    private Long id;

    @Column(name = "nombre_prenda", nullable = false, length = LONGITUD_NOMBRE)
    private String nombre;

    @Getter(AccessLevel.NONE)
    @GeneratedColumn("lower(nombre_prenda)")
    // El dialecto de Hibernate 7.4 omite NOT NULL después de la expresión generada.
    @Column(name = "nombre_normalizado", nullable = false, insertable = false, updatable = false,
            columnDefinition = "text", options = "not null")
    private String nombreNormalizado;

    @Column(nullable = false)
    @ColumnDefault("true")
    private Boolean activo = true;

    public Prenda(String nombre) {
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
