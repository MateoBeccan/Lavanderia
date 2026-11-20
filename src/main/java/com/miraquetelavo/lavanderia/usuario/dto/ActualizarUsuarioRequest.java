package com.miraquetelavo.lavanderia.usuario.dto;

import com.miraquetelavo.lavanderia.rol.entity.CodigoRol;
import jakarta.validation.constraints.*;

public record ActualizarUsuarioRequest(
        @NotBlank @Pattern(regexp = "[a-zA-Z0-9._-]{3,50}") String username,
        @NotBlank @Size(max = 100) String nombre,
        @NotBlank @Email @Size(max = 100) String email,
        @NotNull CodigoRol rol
) {
    public ActualizarUsuarioRequest {
        email = email == null ? null : email.strip();
        nombre = nombre == null ? null : nombre.strip();
    }
}
