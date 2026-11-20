package com.miraquetelavo.lavanderia.auth.controller;

import com.miraquetelavo.lavanderia.auth.dto.*;
import com.miraquetelavo.lavanderia.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest solicitud) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(auth.login(solicitud));
    }

    @GetMapping("/me")
    public ResponseEntity<UsuarioAutenticadoResponse> me(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(auth.me(Long.valueOf(jwt.getSubject())));
    }
}
