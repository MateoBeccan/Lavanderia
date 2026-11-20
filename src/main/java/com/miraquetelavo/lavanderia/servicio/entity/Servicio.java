package com.miraquetelavo.lavanderia.servicio.entity;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import java.math.BigDecimal;

@Entity
@Table(name = "servicios",
        uniqueConstraints = @UniqueConstraint(name = Servicio.UK_NOMBRE, columnNames = "nombre_normalizado"),
        check = @CheckConstraint(name = "ck_servicios_precio_positivo", constraint = "precio > 0"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Servicio {

    public static final String UK_NOMBRE = "uk_servicios_nombre";
    public static final int LONGITUD_NOMBRE = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_servicio")
    private Long id;

    @Column(name = "nombre_servicio", nullable = false, length = LONGITUD_NOMBRE)
    private String nombre;

    @Getter(AccessLevel.NONE)
    @GeneratedColumn("lower(nombre_servicio)")
    @Column(name = "nombre_normalizado", nullable = false, insertable = false, updatable = false,
            columnDefinition = "text", options = "not null")
    private String nombreNormalizado;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precio;

    @Enumerated(EnumType.STRING)
    @Column(name = "modalidad_cobro", nullable = false, length = 20)
    private ModalidadCobro modalidadCobro;

    @Column(nullable = false)
    @ColumnDefault("true")
    private Boolean activo = true;

    public Servicio(String nombre, BigDecimal precio, ModalidadCobro modalidadCobro) {
        actualizarDatos(nombre, precio, modalidadCobro);
    }

    public void actualizarDatos(String nombre, BigDecimal precio, ModalidadCobro modalidadCobro) {
        this.nombre = nombre;
        this.precio = precio;
        this.modalidadCobro = modalidadCobro;
    }

    public void desactivar() {
        activo = false;
    }

    public void activar() {
        activo = true;
    }
}
