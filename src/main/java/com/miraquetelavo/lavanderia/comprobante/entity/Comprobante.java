package com.miraquetelavo.lavanderia.comprobante.entity;

import com.miraquetelavo.lavanderia.cobro.entity.Cobro;
import com.miraquetelavo.lavanderia.pagoproveedor.entity.PagoProveedor;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.LocalDate;
import java.util.Objects;

@Entity
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "comprobantes", uniqueConstraints = {
        @UniqueConstraint(name = Comprobante.UK_COBRO, columnNames = "id_cobro"),
        @UniqueConstraint(name = Comprobante.UK_PAGO, columnNames = "id_pago")
}, check = @CheckConstraint(name = Comprobante.CK_OPERACION, constraint = Comprobante.CHECK_OPERACION))
public class Comprobante {
    public static final String UK_COBRO = "uk_comprobantes_cobro";
    public static final String UK_PAGO = "uk_comprobantes_pago";
    public static final String CK_OPERACION = "ck_comprobantes_operacion";
    public static final String CHECK_OPERACION = "(id_cobro is null) <> (id_pago is null)";
    public static final int MAX_OBSERVACIONES = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_comprobante")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_cobro", updatable = false, foreignKey = @ForeignKey(name = "fk_comprobantes_cobro"))
    private Cobro cobro;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_pago", updatable = false, foreignKey = @ForeignKey(name = "fk_comprobantes_pago"))
    private PagoProveedor pagoProveedor;

    @Column(name = "fecha_emision", nullable = false, updatable = false)
    private LocalDate fechaEmision;

    @Column(length = MAX_OBSERVACIONES, updatable = false)
    private String observaciones;

    private Comprobante(Cobro cobro, PagoProveedor pagoProveedor, LocalDate fechaEmision, String observaciones) {
        this.cobro = cobro;
        this.pagoProveedor = pagoProveedor;
        this.fechaEmision = Objects.requireNonNull(fechaEmision);
        this.observaciones = observaciones;
    }

    public static Comprobante deCobro(Cobro cobro, LocalDate fechaEmision, String observaciones) {
        return new Comprobante(Objects.requireNonNull(cobro), null, fechaEmision, observaciones);
    }

    public static Comprobante dePagoProveedor(PagoProveedor pago, LocalDate fechaEmision, String observaciones) {
        return new Comprobante(null, Objects.requireNonNull(pago), fechaEmision, observaciones);
    }
}
