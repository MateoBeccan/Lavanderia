package com.miraquetelavo.lavanderia.pedido.dto;

import com.miraquetelavo.lavanderia.detallepedido.dto.DetallePedidoRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record ActualizarPedidoRequest(
        @NotNull LocalDate fechaEntregaEstimada,
        @NotEmpty List<@NotNull @Valid DetallePedidoRequest> detalles
) {
    public ActualizarPedidoRequest {
        if (detalles != null) {
            detalles = Collections.unmodifiableList(new ArrayList<>(detalles));
        }
    }
}
