package com.miraquetelavo.lavanderia.usuario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CambiarPasswordRequest(@NotBlank @Size(min = 12, max = 128) String password) {
}
