package com.miraquetelavo.lavanderia.detallecompra.entity;

import com.miraquetelavo.lavanderia.compra.entity.Compra;
import com.miraquetelavo.lavanderia.compra.entity.ImportesCompra;
import com.miraquetelavo.lavanderia.insumo.entity.Insumo;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "detalles_compra", indexes = {
        @Index(name = "ix_detalles_compra", columnList = "id_compra, id_detalle_compra"),
        @Index(name = "ix_detalles_compra_insumo", columnList = "id_insumo")
}, check = @CheckConstraint(name = "ck_detalles_compra_importes",
        constraint = "cantidad > 0 and precio_compra > 0 and subtotal > 0 and subtotal = round(cantidad * precio_compra, 2)"))
public class DetalleCompra {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_detalle_compra")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_compra", nullable = false, foreignKey = @ForeignKey(name = "fk_detalles_compra"))
    private Compra compra;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_insumo", nullable = false, foreignKey = @ForeignKey(name = "fk_detalles_compra_insumo"))
    private Insumo insumo;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal cantidad;

    @Column(name = "precio_compra", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioCompra;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    public DetalleCompra(Insumo insumo, BigDecimal cantidad, BigDecimal precioCompra) {
        actualizar(insumo, cantidad, precioCompra);
    }

    public void actualizar(Insumo insumo, BigDecimal cantidad, BigDecimal precioCompra) {
        var cantidadValidada = ImportesCompra.validarEntrada(cantidad, "La cantidad");
        var precioValidado = ImportesCompra.validarEntrada(precioCompra, "El precio de compra");
        this.subtotal = ImportesCompra.subtotal(cantidadValidada, precioValidado);
        this.insumo = insumo;
        this.cantidad = cantidadValidada;
        this.precioCompra = precioValidado;
    }

    public void vincularA(Compra compra) {
        if (this.compra != null && this.compra != compra) {
            throw new IllegalStateException("El detalle ya pertenece a otra compra.");
        }
        this.compra = compra;
    }

    public void desvincularDe(Compra compra) {
        if (this.compra == compra) {
            this.compra = null;
        }
    }
}
