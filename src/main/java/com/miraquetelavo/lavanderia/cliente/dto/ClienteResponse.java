package com.miraquetelavo.lavanderia.cliente.dto;

import java.time.LocalDateTime;

public record ClienteResponse(
        Long id,
        String nombre,
        String apellido,
        String dni,
        String telefono,
        String email,
        boolean activo,
        LocalDateTime fechaCreacion
) {
}
