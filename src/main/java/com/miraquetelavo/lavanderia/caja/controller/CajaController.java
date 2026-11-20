package com.miraquetelavo.lavanderia.caja.controller;

import com.miraquetelavo.lavanderia.caja.dto.*;
import com.miraquetelavo.lavanderia.caja.entity.EstadoCaja;
import com.miraquetelavo.lavanderia.caja.service.CajaService;
import com.miraquetelavo.lavanderia.common.idempotencia.IdempotenciaFinanciera;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/cajas")
@RequiredArgsConstructor
public class CajaController {

    private final CajaService service;
    private final IdempotenciaFinanciera idempotencia;

    @PostMapping("/abrir")
    public ResponseEntity<CajaResponse> abrir(@Valid @RequestBody AbrirCajaRequest solicitud,
                                               @RequestHeader HttpHeaders cabeceras,
                                               @AuthenticationPrincipal Jwt jwt) {
        long usuarioId = Long.parseLong(jwt.getSubject());
        var contenido = new IdempotenciaFinanciera.Contenido(null, solicitud.montoInicial(), null, null);
        return idempotencia.crear(cabeceras, usuarioId, IdempotenciaFinanciera.Operacion.APERTURA_CAJA,
                contenido, CajaResponse.class, () -> creado(service.abrirCaja(solicitud, usuarioId)));
    }

    private static ResponseEntity<CajaResponse> creado(CajaResponse caja) {
        var ubicacion = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/cajas/{id}").buildAndExpand(caja.id()).toUri();
        return ResponseEntity.created(ubicacion).body(caja);
    }

    @GetMapping("/abierta")
    public CajaResponse obtenerAbierta() {
        return service.obtenerCajaAbierta();
    }

    @GetMapping("/{id}")
    public CajaResponse obtener(@PathVariable @Positive Long id) {
        return service.obtenerPorId(id);
    }

    @GetMapping
    public PagedModel<CajaResumenResponse> listar(
            @RequestParam(required = false) EstadoCaja estado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @PageableDefault(size = 20) Pageable pageable) {
        return new PagedModel<>(service.listarCajas(estado, desde, hasta, pageable));
    }

    @PatchMapping("/{id}/cerrar")
    public CajaResponse cerrar(@PathVariable @Positive Long id, @Valid @RequestBody CerrarCajaRequest solicitud,
                              @AuthenticationPrincipal Jwt jwt) {
        return service.cerrarCaja(id, solicitud, Long.valueOf(jwt.getSubject()));
    }
}
