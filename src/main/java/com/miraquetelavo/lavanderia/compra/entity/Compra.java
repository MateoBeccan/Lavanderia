package com.miraquetelavo.lavanderia.compra.entity;

import com.miraquetelavo.lavanderia.detallecompra.entity.DetalleCompra;
import com.miraquetelavo.lavanderia.estadopago.entity.EstadoPago;
import com.miraquetelavo.lavanderia.proveedor.entity.Proveedor;
import com.miraquetelavo.lavanderia.usuario.entity.Usuario;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "compras", indexes = {
        @Index(name = "ix_compras_fecha", columnList = "fecha_compra, id_compra"),
        @Index(name = "ix_compras_proveedor_fecha", columnList = "id_proveedor, fecha_compra, id_compra"),
        @Index(name = "ix_compras_usuario", columnList = "id_usuario"),
        @Index(name = "ix_compras_estado_pago", columnList = "id_estado_pago"),
        @Index(name = "ix_compras_comprobante", columnList = "nro_comprobante")
}, check = @CheckConstraint(name = "ck_compras_total", constraint = "total_compra > 0"))
public class Compra {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_compra")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_proveedor", nullable = false, foreignKey = @ForeignKey(name = "fk_compras_proveedor"))
    private Proveedor proveedor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_compras_usuario"))
    private Usuario usuario;

    @Column(name = "fecha_compra", nullable = false, updatable = false)
    private LocalDateTime fechaCompra;

    @Column(name = "nro_comprobante", length = 50)
    private String numeroComprobante;

    @Column(name = "total_compra", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalCompra;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_estado_pago", nullable = false, foreignKey = @ForeignKey(name = "fk_compras_estado_pago"))
    private EstadoPago estadoPago;

    @Getter(AccessLevel.NONE)
    @OneToMany(mappedBy = "compra", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<DetalleCompra> detalles = new ArrayList<>();

    public Compra(Proveedor proveedor, Usuario usuario, LocalDateTime fechaCompra, EstadoPago estadoPago,
                  String numeroComprobante, List<DetalleCompra> detalles) {
        this.usuario = usuario;
        this.fechaCompra = fechaCompra;
        this.estadoPago = estadoPago;
        actualizar(proveedor, numeroComprobante, detalles);
    }

    public void asignarEstadoPago(EstadoPago nuevoEstado) {
        estadoPago = Objects.requireNonNull(nuevoEstado);
    }

    public List<DetalleCompra> getDetalles() {
        return List.copyOf(detalles);
    }

    public void actualizar(Proveedor proveedor, String numeroComprobante, List<DetalleCompra> nuevosDetalles) {
        var total = nuevosDetalles.stream().map(DetalleCompra::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        this.totalCompra = ImportesCompra.validarRango(total, "El total de la compra");
        this.proveedor = proveedor;
        this.numeroComprobante = numeroComprobante;
        detalles.removeIf(detalle -> {
            if (nuevosDetalles.contains(detalle)) {
                return false;
            }
            detalle.desvincularDe(this);
            return true;
        });
        for (var detalle : nuevosDetalles) {
            if (detalle.getCompra() != this) {
                detalle.vincularA(this);
                detalles.add(detalle);
            }
        }
    }
}
