package com.miraquetelavo.lavanderia.pedido.entity;

import com.miraquetelavo.lavanderia.cliente.entity.Cliente;
import com.miraquetelavo.lavanderia.detallepedido.entity.DetallePedido;
import com.miraquetelavo.lavanderia.estadopedido.entity.EstadoPedido;
import com.miraquetelavo.lavanderia.estadopago.entity.EstadoPago;
import com.miraquetelavo.lavanderia.usuario.entity.Usuario;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "pedidos", indexes = {
        @Index(name = "ix_pedidos_cliente_fecha", columnList = "id_cliente, fecha_ingreso"),
        @Index(name = "ix_pedidos_estado_fecha", columnList = "id_estado_pedido, fecha_ingreso"),
        @Index(name = "ix_pedidos_fecha", columnList = "fecha_ingreso, id_pedido"),
        @Index(name = "ix_pedidos_usuario", columnList = "id_usuario")
}, check = @CheckConstraint(name = "ck_pedidos_total", constraint = "total_pedido > 0"))
public class Pedido {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pedido")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_cliente", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_pedidos_cliente"))
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_pedidos_usuario"))
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_estado_pedido", nullable = false, foreignKey = @ForeignKey(name = "fk_pedidos_estado"))
    private EstadoPedido estadoPedido;

    // La evolución aditiva agrega la columna nullable y luego exige NOT NULL.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_estado_pago", foreignKey = @ForeignKey(name = "fk_pedidos_estado_pago"))
    private EstadoPago estadoPago;

    @Column(name = "fecha_ingreso", nullable = false, updatable = false)
    private LocalDateTime fechaIngreso;

    @Column(name = "fecha_entrega_est", nullable = false)
    private LocalDate fechaEntregaEstimada;

    @Column(name = "total_pedido", nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    @Getter(AccessLevel.NONE)
    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<DetallePedido> detalles = new ArrayList<>();

    public Pedido(Cliente cliente, Usuario usuario, EstadoPedido estadoPedido, LocalDateTime fechaIngreso) {
        this.cliente = cliente;
        this.usuario = usuario;
        this.estadoPedido = estadoPedido;
        this.fechaIngreso = fechaIngreso;
    }

    public List<DetallePedido> getDetalles() {
        return List.copyOf(detalles);
    }

    public void asignarEstadoPago(EstadoPago nuevoEstado) {
        estadoPago = Objects.requireNonNull(nuevoEstado);
    }

    public void cambiarEstado(EstadoPedido nuevoEstado) {
        estadoPedido = Objects.requireNonNull(nuevoEstado);
    }

    public void actualizar(LocalDate entrega, List<DetallePedido> lineas) {
        if (lineas.isEmpty()) {
            throw new IllegalArgumentException("El pedido requiere al menos un detalle.");
        }
        var nuevoTotal = lineas.stream().map(DetallePedido::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        total = DetallePedido.importeExacto(nuevoTotal, "El total");
        fechaEntregaEstimada = entrega;
        detalles.removeIf(detalle -> {
            if (lineas.contains(detalle)) {
                return false;
            }
            detalle.desvincularDe(this);
            return true;
        });
        for (var detalle : lineas) {
            if (!detalles.contains(detalle)) {
                detalle.vincularA(this);
                detalles.add(detalle);
            }
        }
    }
}
