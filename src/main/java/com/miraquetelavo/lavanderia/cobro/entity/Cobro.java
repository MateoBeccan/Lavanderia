package com.miraquetelavo.lavanderia.cobro.entity;

import com.miraquetelavo.lavanderia.movimientocaja.entity.MovimientoCaja;
import com.miraquetelavo.lavanderia.pedido.entity.Pedido;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "cobros",
        indexes = @Index(name = "ix_cobros_pedido_fecha_id", columnList = "id_pedido, fecha_cobro desc, id_cobro desc"),
        check = @CheckConstraint(name = "ck_cobros_monto_positivo", constraint = "monto_cobrado > 0"))
public class Cobro {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cobro")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_pedido", nullable = false, foreignKey = @ForeignKey(name = "fk_cobros_pedido"))
    private Pedido pedido;

    @OneToOne(fetch = FetchType.LAZY, optional = false, cascade = CascadeType.PERSIST)
    @JoinColumn(name = "id_mov_caja", nullable = false, unique = true,
            foreignKey = @ForeignKey(name = "fk_cobros_movimiento"))
    private MovimientoCaja movimientoCaja;

    @Column(name = "fecha_cobro", nullable = false)
    private LocalDateTime fechaCobro;

    @Column(name = "monto_cobrado", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoCobrado;

    public Cobro(Pedido pedido, MovimientoCaja movimientoCaja) {
        if (!"COBRO_PEDIDO".equals(movimientoCaja.getTipoMovimiento().getCodigo())) {
            throw new IllegalArgumentException("El movimiento debe ser de tipo COBRO_PEDIDO.");
        }
        this.pedido = pedido;
        this.movimientoCaja = movimientoCaja;
        this.fechaCobro = movimientoCaja.getFechaHora();
        this.montoCobrado = movimientoCaja.getMonto();
    }
}
