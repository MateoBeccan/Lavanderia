package com.miraquetelavo.lavanderia.auth.service;

import com.miraquetelavo.lavanderia.auth.dto.*;
import com.miraquetelavo.lavanderia.security.CustomUserDetailsService;
import com.miraquetelavo.lavanderia.security.JwtService;
import com.miraquetelavo.lavanderia.usuario.repository.UsuarioRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class AuthService {

    private final AuthenticationManager autenticador;
    private final JwtService jwt;
    private final UsuarioRepository usuarios;

    public AuthService(AuthenticationManager autenticador, JwtService jwt, UsuarioRepository usuarios) {
        this.autenticador = autenticador;
        this.jwt = jwt;
        this.usuarios = usuarios;
    }

    @Transactional
    public LoginResponse login(LoginRequest solicitud) {
        var autenticacion = autenticador.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(solicitud.username(), solicitud.password()));
        var cuenta = (CustomUserDetailsService.Cuenta) autenticacion.getPrincipal();
        int registrados = usuarios.registrarAcceso(cuenta.id(), UUID.fromString(cuenta.versionCredenciales()),
                LocalDateTime.now(ZoneOffset.UTC));
        if (registrados == 0) {
            throw new BadCredentialsException("Credenciales inválidas.");
        }
        return jwt.emitir(cuenta.id(), cuenta.versionCredenciales());
    }

    @Transactional(readOnly = true)
    public UsuarioAutenticadoResponse me(Long id) {
        var usuario = usuarios.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new BadCredentialsException("Credenciales inválidas."));
        return new UsuarioAutenticadoResponse(usuario.getId(), usuario.getUsername(), usuario.getNombre(),
                usuario.getEmail(), usuario.getRol().getNombre());
    }
}
