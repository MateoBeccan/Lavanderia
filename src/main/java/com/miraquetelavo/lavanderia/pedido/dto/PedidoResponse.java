package com.miraquetelavo.lavanderia.pedido.dto;

import com.miraquetelavo.lavanderia.detallepedido.dto.DetallePedidoResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record PedidoResponse(
        Long id, Long clienteId, String clienteNombre, String clienteApellido, Long usuarioId, String usuarioNombre,
        Estado estadoPedido, LocalDateTime fechaIngreso, LocalDate fechaEntregaEstimada, BigDecimal total,
        List<DetallePedidoResponse> detalles
) {
    public PedidoResponse {
        detalles = List.copyOf(detalles);
    }

    public record Estado(Long id, String codigo, String nombre) {}
}
