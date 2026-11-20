package com.miraquetelavo.lavanderia.estadopago.entity;

import jakarta.persistence.CheckConstraint;
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
@Table(name = "estados_pago",
        uniqueConstraints = @UniqueConstraint(name = "uk_estados_pago_codigo", columnNames = "codigo"),
        check = @CheckConstraint(name = "ck_estados_pago_orden", constraint = "orden >= 0"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EstadoPago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_estado_pago")
    private Long id;

    @Column(nullable = false, length = 30, updatable = false)
    private String codigo;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(length = 250)
    private String descripcion;

    @Column(nullable = false)
    private Integer orden;

    @Column(nullable = false)
    @ColumnDefault("true")
    private Boolean activo = true;
}
