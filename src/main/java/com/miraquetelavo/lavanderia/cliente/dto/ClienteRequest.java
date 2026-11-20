package com.miraquetelavo.lavanderia.cliente.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import static com.miraquetelavo.lavanderia.cliente.entity.Cliente.LONGITUD_EMAIL;
import static com.miraquetelavo.lavanderia.cliente.entity.Cliente.LONGITUD_NOMBRE;
import static com.miraquetelavo.lavanderia.cliente.entity.Cliente.LONGITUD_TELEFONO;

public record ClienteRequest(
        @NotBlank @Size(max = LONGITUD_NOMBRE) String nombre,
        @NotBlank @Size(max = LONGITUD_NOMBRE) String apellido,
        @NotBlank(message = "El DNI es obligatorio.")
        @Pattern(regexp = "[0-9]{7,8}", message = "El DNI debe tener 7 u 8 dígitos sin separadores.") String dni,
        @Size(max = LONGITUD_TELEFONO) String telefono,
        @Email @Size(max = LONGITUD_EMAIL) String email
) {
}
