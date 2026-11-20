package com.miraquetelavo.lavanderia.pedido.service;

import com.miraquetelavo.lavanderia.pedido.dto.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

public interface PedidoService {
    PedidoResponse registrar(@NotNull @Valid CrearPedidoRequest solicitud, Long usuarioId);
    PedidoResponse obtener(Long id);
    Page<PedidoResumenResponse> listar(Long clienteId, String estado, LocalDate desde, LocalDate hasta, Pageable pageable);
    PedidoResponse actualizar(Long id, @NotNull @Valid ActualizarPedidoRequest solicitud);
    PedidoResponse cambiarEstado(Long id, @NotNull @Valid CambiarEstadoPedidoRequest solicitud, Long usuarioId);
    PedidoResponse cancelar(Long id, @NotNull @Valid CancelarPedidoRequest solicitud, Long usuarioId);
}
