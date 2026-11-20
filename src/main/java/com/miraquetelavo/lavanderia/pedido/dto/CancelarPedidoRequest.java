package com.miraquetelavo.lavanderia.pedido.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelarPedidoRequest(@NotBlank @Size(max = 250) String observacion) {}
