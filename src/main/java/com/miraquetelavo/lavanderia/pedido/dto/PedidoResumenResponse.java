package com.miraquetelavo.lavanderia.pedido.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record PedidoResumenResponse(
        Long id, Long clienteId, String clienteNombre, String clienteApellido, String estadoCodigo,
        LocalDateTime fechaIngreso, LocalDate fechaEntregaEstimada, BigDecimal total
) {}
