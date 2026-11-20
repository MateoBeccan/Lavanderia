package com.miraquetelavo.lavanderia.detallepedido.entity;

import com.miraquetelavo.lavanderia.common.exception.SolicitudInvalidaException;
import com.miraquetelavo.lavanderia.pedido.entity.Pedido;
import com.miraquetelavo.lavanderia.prenda.entity.Prenda;
import com.miraquetelavo.lavanderia.servicio.entity.Servicio;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "detalles_pedido", indexes = {
        @Index(name = "ix_detalles_pedido", columnList = "id_pedido, id_detalle_pedido"),
        @Index(name = "ix_detalles_prenda", columnList = "id_prenda"),
        @Index(name = "ix_detalles_servicio", columnList = "id_servicio")
}, check = @CheckConstraint(name = "ck_detalles_importes",
        constraint = "cantidad > 0 and precio_unitario > 0 and subtotal = cantidad * precio_unitario"))
public class DetallePedido {
    private static final BigDecimal MAXIMO_DECIMAL = new BigDecimal("99999999.99");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_detalle_pedido")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_pedido", nullable = false, foreignKey = @ForeignKey(name = "fk_detalles_pedido"))
    private Pedido pedido;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_prenda", nullable = false, foreignKey = @ForeignKey(name = "fk_detalles_prenda"))
    private Prenda prenda;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_servicio", nullable = false, foreignKey = @ForeignKey(name = "fk_detalles_servicio"))
    private Servicio servicio;

    @Column(name = "estado_prenda", length = 50)
    private String estadoPrenda;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal cantidad;

    @Column(name = "precio_unitario", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioUnitario;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(length = 250)
    private String observaciones;

    public DetallePedido(Prenda prenda, Servicio servicio, BigDecimal cantidad, BigDecimal precio,
                         String estadoPrenda, String observaciones) {
        actualizar(prenda, servicio, cantidad, precio, estadoPrenda, observaciones);
    }

    public void actualizar(Prenda prenda, Servicio servicio, BigDecimal cantidad, BigDecimal precio,
                           String estadoPrenda, String observaciones) {
        var importe = importeExacto(cantidad.multiply(precio), "El subtotal");
        this.prenda = prenda;
        this.servicio = servicio;
        this.cantidad = cantidad;
        this.precioUnitario = precio;
        this.subtotal = importe;
        this.estadoPrenda = estadoPrenda;
        this.observaciones = observaciones;
    }

    public void vincularA(Pedido pedido) {
        if (this.pedido != null && this.pedido != pedido) {
            throw new IllegalStateException("El detalle ya pertenece a otro pedido.");
        }
        this.pedido = pedido;
    }

    public void desvincularDe(Pedido pedido) {
        if (this.pedido == pedido) {
            this.pedido = null;
        }
    }

    public static BigDecimal importeExacto(BigDecimal importe, String campo) {
        if (importe.signum() <= 0 || importe.compareTo(MAXIMO_DECIMAL) > 0
                || importe.stripTrailingZeros().scale() > 2) {
            throw new SolicitudInvalidaException(campo + " no se puede representar exactamente en DECIMAL(10,2).");
        }
        return importe.setScale(2, RoundingMode.UNNECESSARY);
    }
}
