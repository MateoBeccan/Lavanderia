package com.miraquetelavo.lavanderia.usuario.controller;

import com.miraquetelavo.lavanderia.usuario.dto.*;
import com.miraquetelavo.lavanderia.usuario.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService usuarios;

    public UsuarioController(UsuarioService usuarios) {
        this.usuarios = usuarios;
    }

    @PostMapping
    public ResponseEntity<UsuarioResponse> crear(@AuthenticationPrincipal Jwt jwt,
                                                 @Valid @RequestBody CrearUsuarioRequest solicitud) {
        var usuario = usuarios.crear(actor(jwt), solicitud);
        var uri = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}").buildAndExpand(usuario.id()).toUri();
        return ResponseEntity.created(uri).body(usuario);
    }

    @GetMapping
    public PagedModel<UsuarioResponse> listar(@PageableDefault(size = 20, sort = "id") Pageable pagina) {
        return new PagedModel<>(usuarios.listar(pagina));
    }

    @GetMapping("/{id}")
    public UsuarioResponse obtener(@PathVariable Long id) {
        return usuarios.obtener(id);
    }

    @PutMapping("/{id}")
    public UsuarioResponse actualizar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                       @Valid @RequestBody ActualizarUsuarioRequest solicitud) {
        return usuarios.actualizar(actor(jwt), id, solicitud);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactivar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        usuarios.desactivar(actor(jwt), id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/activar")
    public UsuarioResponse activar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return usuarios.activar(actor(jwt), id);
    }

    @PatchMapping("/{id}/password")
    public ResponseEntity<Void> cambiarPassword(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                                 @Valid @RequestBody CambiarPasswordRequest solicitud) {
        usuarios.cambiarPassword(actor(jwt), id, solicitud);
        return ResponseEntity.noContent().build();
    }

    private static Long actor(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
