package com.miraquetelavo.lavanderia.movimientocaja.controller;

import com.miraquetelavo.lavanderia.common.idempotencia.IdempotenciaFinanciera;
import com.miraquetelavo.lavanderia.movimientocaja.dto.*;
import com.miraquetelavo.lavanderia.movimientocaja.service.MovimientoCajaService;
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
@RequestMapping("/api")
@RequiredArgsConstructor
public class MovimientoCajaController {

    private final MovimientoCajaService movimientos;
    private final IdempotenciaFinanciera idempotencia;

    @PostMapping("/movimientos-caja/ingresos-manuales")
    public ResponseEntity<MovimientoCajaResponse> registrarIngreso(@Valid @RequestBody RegistrarMovimientoManualRequest solicitud,
                                                                    @RequestHeader HttpHeaders cabeceras,
                                                                    @AuthenticationPrincipal Jwt jwt) {
        long usuarioId = Long.parseLong(jwt.getSubject());
        return idempotencia.crear(cabeceras, usuarioId, IdempotenciaFinanciera.Operacion.INGRESO_MANUAL,
                contenido(solicitud), MovimientoCajaResponse.class,
                () -> creado(movimientos.registrarIngreso(solicitud, usuarioId)));
    }

    @PostMapping("/movimientos-caja/egresos-manuales")
    public ResponseEntity<MovimientoCajaResponse> registrarEgreso(@Valid @RequestBody RegistrarMovimientoManualRequest solicitud,
                                                                   @RequestHeader HttpHeaders cabeceras,
                                                                   @AuthenticationPrincipal Jwt jwt) {
        long usuarioId = Long.parseLong(jwt.getSubject());
        return idempotencia.crear(cabeceras, usuarioId, IdempotenciaFinanciera.Operacion.EGRESO_MANUAL,
                contenido(solicitud), MovimientoCajaResponse.class,
                () -> creado(movimientos.registrarEgreso(solicitud, usuarioId)));
    }

    @GetMapping("/movimientos-caja/{id}")
    public MovimientoCajaResponse obtener(@PathVariable @Positive Long id) {
        return movimientos.obtener(id);
    }

    @GetMapping("/cajas/{id}/movimientos")
    public PagedModel<MovimientoCajaResponse> listar(
            @PathVariable @Positive Long id,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) @Positive Long metodoPagoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @PageableDefault(size = 20) Pageable pageable) {
        return new PagedModel<>(movimientos.listar(id, tipo, metodoPagoId, desde, hasta, pageable));
    }

    @GetMapping("/cajas/{id}/saldo")
    public SaldoCajaResponse saldo(@PathVariable @Positive Long id) {
        return movimientos.consultarSaldo(id);
    }

    private static IdempotenciaFinanciera.Contenido contenido(RegistrarMovimientoManualRequest solicitud) {
        return new IdempotenciaFinanciera.Contenido(solicitud.cajaId(), solicitud.monto(), solicitud.metodoPagoId(),
                solicitud.descripcion().strip());
    }

    private static ResponseEntity<MovimientoCajaResponse> creado(MovimientoCajaResponse movimiento) {
        var ubicacion = ServletUriComponentsBuilder.fromCurrentContextPath().path("/api/movimientos-caja/{id}")
                .buildAndExpand(movimiento.id()).toUri();
        return ResponseEntity.created(ubicacion).body(movimiento);
    }
}
