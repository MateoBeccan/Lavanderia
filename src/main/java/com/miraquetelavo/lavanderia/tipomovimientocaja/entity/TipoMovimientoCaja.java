package com.miraquetelavo.lavanderia.tipomovimientocaja.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "tipos_movimiento_caja", uniqueConstraints = {
        @UniqueConstraint(name = "uk_tipos_movimiento_caja_codigo", columnNames = "codigo"),
        @UniqueConstraint(name = "uk_tipos_movimiento_caja_nombre", columnNames = "nombre")
}, check = @CheckConstraint(name = "ck_tipos_movimiento_caja_orden", constraint = "orden >= 0"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TipoMovimientoCaja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tipo_movimiento_caja")
    private Long id;

    @Column(nullable = false, length = 30, updatable = false)
    private String codigo;

    @Column(nullable = false, length = 30)
    private String nombre;

    @Column(length = 250)
    private String descripcion;

    @Column(nullable = false)
    private Integer orden;

    @Column(nullable = false)
    @ColumnDefault("true")
    private Boolean activo = true;
}
