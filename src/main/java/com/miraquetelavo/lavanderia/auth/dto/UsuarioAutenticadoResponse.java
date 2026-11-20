package com.miraquetelavo.lavanderia.auth.dto;

import com.miraquetelavo.lavanderia.rol.entity.CodigoRol;

public record UsuarioAutenticadoResponse(Long id, String username, String nombre, String email, CodigoRol rol) {
}
