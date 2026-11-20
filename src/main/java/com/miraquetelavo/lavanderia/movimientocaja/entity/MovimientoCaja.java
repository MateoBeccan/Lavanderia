package com.miraquetelavo.lavanderia.movimientocaja.entity;

import com.miraquetelavo.lavanderia.caja.entity.Caja;
import com.miraquetelavo.lavanderia.metodopago.entity.MetodoPago;
import com.miraquetelavo.lavanderia.tipomovimientocaja.entity.TipoMovimientoCaja;
import com.miraquetelavo.lavanderia.usuario.entity.Usuario;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Immutable
@Table(name = "movimientos_caja",
        indexes = @Index(name = "ix_movimientos_caja_fecha_id", columnList = "id_caja, fecha_hora desc, id_mov_caja desc"),
        check = {
                @CheckConstraint(name = "ck_movimientos_caja_monto", constraint = "monto > 0"),
                @CheckConstraint(name = "ck_movimientos_caja_descripcion",
                        constraint = "descripcion is null or descripcion ~ '[^[:space:]]'")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MovimientoCaja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_mov_caja")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_caja", nullable = false, foreignKey = @ForeignKey(name = "fk_movimientos_caja_caja"))
    private Caja caja;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_tipo_mov", nullable = false, foreignKey = @ForeignKey(name = "fk_movimientos_caja_tipo"))
    private TipoMovimientoCaja tipoMovimiento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_metodo", nullable = false, foreignKey = @ForeignKey(name = "fk_movimientos_caja_metodo"))
    private MetodoPago metodoPago;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario", nullable = false, foreignKey = @ForeignKey(name = "fk_movimientos_caja_usuario"))
    private Usuario usuario;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;

    @Column(length = 250)
    private String descripcion;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    public MovimientoCaja(Caja caja, TipoMovimientoCaja tipoMovimiento, MetodoPago metodoPago,
                          Usuario usuario, BigDecimal monto, String descripcion, LocalDateTime fechaHora) {
        this.caja = caja;
        this.tipoMovimiento = tipoMovimiento;
        this.metodoPago = metodoPago;
        this.usuario = usuario;
        this.monto = monto;
        this.descripcion = descripcion;
        this.fechaHora = fechaHora;
    }
}
