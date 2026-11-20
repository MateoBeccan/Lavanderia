package com.miraquetelavo.lavanderia.proveedor.entity;

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

@Entity
@Table(name = "proveedores", uniqueConstraints = @UniqueConstraint(name = Proveedor.UK_CUIT, columnNames = "cuit"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Proveedor {

    public static final String UK_CUIT = "uk_proveedores_cuit";
    public static final int LONGITUD_NOMBRE = 150;
    public static final int LONGITUD_CUIT = 20;
    public static final int LONGITUD_TELEFONO = 20;
    public static final int LONGITUD_RUBRO = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_proveedor")
    private Long id;

    @Column(name = "nombre_prov", nullable = false, length = LONGITUD_NOMBRE)
    private String nombre;

    @Column(nullable = false, length = LONGITUD_CUIT)
    private String cuit;

    @Column(nullable = false, length = LONGITUD_TELEFONO)
    private String telefono;

    @Column(length = LONGITUD_RUBRO)
    private String rubro;

    @Column(nullable = false)
    @ColumnDefault("true")
    private Boolean activo = true;

    public Proveedor(String nombre, String cuit, String telefono, String rubro) {
        actualizarDatos(nombre, cuit, telefono, rubro);
    }

    public void actualizarDatos(String nombre, String cuit, String telefono, String rubro) {
        this.nombre = nombre;
        this.cuit = cuit;
        this.telefono = telefono;
        this.rubro = rubro;
    }

    public void activar() {
        activo = true;
    }

    public void desactivar() {
        activo = false;
    }
}
