package com.miraquetelavo.lavanderia.historialestadopedido.entity;

import com.miraquetelavo.lavanderia.estadopedido.entity.EstadoPedido;
import com.miraquetelavo.lavanderia.pedido.entity.Pedido;
import com.miraquetelavo.lavanderia.usuario.entity.Usuario;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

@Entity
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "historial_estados_pedido", indexes = {
        @Index(name = "ix_historial_pedido_fecha", columnList = "id_pedido, fecha_cambio, id_historial_estado_pedido")
})
public class HistorialEstadoPedido {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_historial_estado_pedido")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_pedido", nullable = false, foreignKey = @ForeignKey(name = "fk_historial_pedido"))
    private Pedido pedido;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_estado_anterior", foreignKey = @ForeignKey(name = "fk_historial_estado_anterior"))
    private EstadoPedido estadoAnterior;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_estado_nuevo", nullable = false, foreignKey = @ForeignKey(name = "fk_historial_estado_nuevo"))
    private EstadoPedido estadoNuevo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario", nullable = false, foreignKey = @ForeignKey(name = "fk_historial_usuario"))
    private Usuario usuario;

    @Column(name = "fecha_cambio", nullable = false)
    private LocalDateTime fechaCambio;

    @Column(length = 250)
    private String observacion;

    public HistorialEstadoPedido(Pedido pedido, EstadoPedido estadoAnterior, EstadoPedido estadoNuevo,
                                 Usuario usuario, LocalDateTime fechaCambio, String observacion) {
        this.pedido = pedido;
        this.estadoAnterior = estadoAnterior;
        this.estadoNuevo = estadoNuevo;
        this.usuario = usuario;
        this.fechaCambio = fechaCambio;
        this.observacion = observacion;
    }
}
