package com.miraquetelavo.lavanderia.pedido.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CambiarEstadoPedidoRequest(
        @NotBlank @Size(max = 30) String codigoEstado,
        @Size(max = 250) String observacion
) {}
