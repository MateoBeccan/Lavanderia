package com.miraquetelavo.lavanderia.cobro.controller;

import com.miraquetelavo.lavanderia.cobro.dto.CobroResponse;
import com.miraquetelavo.lavanderia.cobro.dto.RegistrarCobroRequest;
import com.miraquetelavo.lavanderia.cobro.dto.SaldoPedidoResponse;
import com.miraquetelavo.lavanderia.cobro.service.CobroService;
import com.miraquetelavo.lavanderia.common.idempotencia.IdempotenciaFinanciera;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CobroController {
    private final CobroService cobros;
    private final IdempotenciaFinanciera idempotencia;

    @PostMapping("/pedidos/{id}/cobros")
    public ResponseEntity<CobroResponse> registrar(@PathVariable @Positive Long id,
                                                    @Valid @RequestBody RegistrarCobroRequest solicitud,
                                                    @RequestHeader HttpHeaders cabeceras,
                                                    @AuthenticationPrincipal Jwt jwt) {
        long usuarioId = Long.parseLong(jwt.getSubject());
        var contenido = new IdempotenciaFinanciera.Contenido(id, solicitud.monto(), solicitud.metodoPagoId(), null);
        return idempotencia.crear(cabeceras, usuarioId, IdempotenciaFinanciera.Operacion.COBRO,
                contenido, CobroResponse.class, () -> creado(cobros.registrar(id, solicitud, usuarioId)));
    }

    private static ResponseEntity<CobroResponse> creado(CobroResponse cobro) {
        var ubicacion = ServletUriComponentsBuilder.fromCurrentContextPath().path("/api/cobros/{id}")
                .buildAndExpand(cobro.id()).toUri();
        return ResponseEntity.created(ubicacion).body(cobro);
    }

    @GetMapping("/pedidos/{id}/cobros")
    public PagedModel<CobroResponse> listar(@PathVariable @Positive Long id, @PageableDefault(size = 20) Pageable pageable) {
        return new PagedModel<>(cobros.listar(id, pageable));
    }

    @GetMapping("/pedidos/{id}/saldo")
    public SaldoPedidoResponse saldo(@PathVariable @Positive Long id) {
        return cobros.consultarSaldo(id);
    }

    @GetMapping("/cobros/{id}")
    public CobroResponse obtener(@PathVariable @Positive Long id) {
        return cobros.obtener(id);
    }
}
