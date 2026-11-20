package com.miraquetelavo.lavanderia.metodopago.entity;

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
@Table(name = "metodos_pago",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_metodos_pago_codigo", columnNames = "codigo"),
                @UniqueConstraint(name = "uk_metodos_pago_nombre", columnNames = "nombre")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MetodoPago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_metodo_pago")
    private Long id;

    @Column(nullable = false, length = 30, updatable = false)
    private String codigo;

    @Column(nullable = false, length = 30)
    private String nombre;

    @Column(length = 250)
    private String descripcion;

    @Column(nullable = false)
    @ColumnDefault("true")
    private Boolean activo = true;
}
