package com.miraquetelavo.lavanderia.usuario.service;

import com.miraquetelavo.lavanderia.usuario.dto.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UsuarioService {

    UsuarioResponse crear(Long actor, @NotNull @Valid CrearUsuarioRequest solicitud);

    UsuarioResponse obtener(Long id);

    Page<UsuarioResponse> listar(Pageable pagina);

    UsuarioResponse actualizar(Long actor, Long id, @NotNull @Valid ActualizarUsuarioRequest solicitud);

    void desactivar(Long actor, Long id);

    UsuarioResponse activar(Long actor, Long id);

    void cambiarPassword(Long actor, Long id, @NotNull @Valid CambiarPasswordRequest solicitud);
}
