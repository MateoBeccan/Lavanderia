package com.miraquetelavo.lavanderia.historialestadopedido.dto;

import java.time.LocalDateTime;

public record HistorialEstadoPedidoResponse(
        Long id, String estadoAnteriorCodigo, String estadoAnteriorNombre, String estadoNuevoCodigo,
        String estadoNuevoNombre, Long usuarioId, String usuarioNombre, LocalDateTime fechaCambio, String observacion
) {}
