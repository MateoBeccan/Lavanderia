package com.miraquetelavo.lavanderia.usuario.dto;

import com.miraquetelavo.lavanderia.rol.entity.CodigoRol;
import java.time.LocalDateTime;

public record UsuarioResponse(Long id, String username, String nombre, String email, CodigoRol rol,
                              boolean activo, LocalDateTime ultimoAcceso) {
}
