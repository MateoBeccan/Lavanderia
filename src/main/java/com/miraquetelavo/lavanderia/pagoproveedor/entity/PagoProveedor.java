package com.miraquetelavo.lavanderia.pagoproveedor.entity;

import com.miraquetelavo.lavanderia.movimientocaja.entity.MovimientoCaja;
import com.miraquetelavo.lavanderia.compra.entity.Compra;
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
@Table(name = "pagos",
        indexes = @Index(name = "ix_pagos_compra_fecha_id", columnList = "id_compra, fecha_pago desc, id_pago desc"),
        check = @CheckConstraint(name = "ck_pagos_monto_positivo", constraint = "monto_pagado > 0"))
public class PagoProveedor {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pago")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_compra", nullable = false, foreignKey = @ForeignKey(name = "fk_pagos_compra"))
    private Compra compra;

    @OneToOne(fetch = FetchType.LAZY, optional = false, cascade = CascadeType.PERSIST)
    @JoinColumn(name = "id_mov_caja", nullable = false, unique = true,
            foreignKey = @ForeignKey(name = "fk_pagos_movimiento"))
    private MovimientoCaja movimientoCaja;

    @Column(name = "fecha_pago", nullable = false)
    private LocalDateTime fechaPago;

    @Column(name = "monto_pagado", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoPagado;

    public PagoProveedor(Compra compra, MovimientoCaja movimientoCaja) {
        if (!"PAGO_PROVEEDOR".equals(movimientoCaja.getTipoMovimiento().getCodigo())) {
            throw new IllegalArgumentException("El movimiento debe ser de tipo PAGO_PROVEEDOR.");
        }
        this.compra = compra;
        this.movimientoCaja = movimientoCaja;
        this.fechaPago = movimientoCaja.getFechaHora();
        this.montoPagado = movimientoCaja.getMonto();
    }
}
