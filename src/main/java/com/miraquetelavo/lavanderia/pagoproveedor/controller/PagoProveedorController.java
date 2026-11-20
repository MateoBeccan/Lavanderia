package com.miraquetelavo.lavanderia.pagoproveedor.controller;

import com.miraquetelavo.lavanderia.common.idempotencia.IdempotenciaFinanciera;
import com.miraquetelavo.lavanderia.pagoproveedor.dto.PagoProveedorResponse;
import com.miraquetelavo.lavanderia.pagoproveedor.dto.RegistrarPagoProveedorRequest;
import com.miraquetelavo.lavanderia.pagoproveedor.dto.SaldoCompraResponse;
import com.miraquetelavo.lavanderia.pagoproveedor.service.PagoProveedorService;
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

import java.net.URI;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PagoProveedorController {
    private final PagoProveedorService pagos;
    private final IdempotenciaFinanciera idempotencia;

    @PostMapping("/compras/{id}/pagos")
    public ResponseEntity<PagoProveedorResponse> registrar(@PathVariable @Positive Long id,
                                                            @Valid @RequestBody RegistrarPagoProveedorRequest solicitud,
                                                            @RequestHeader HttpHeaders cabeceras,
                                                            @AuthenticationPrincipal Jwt jwt) {
        long usuarioId = Long.parseLong(jwt.getSubject());
        var contenido = new IdempotenciaFinanciera.Contenido(id, solicitud.monto(), solicitud.metodoPagoId(), null);
        return idempotencia.crear(cabeceras, usuarioId, IdempotenciaFinanciera.Operacion.PAGO_PROVEEDOR,
                contenido, PagoProveedorResponse.class, () -> creado(pagos.registrarPago(id, solicitud, usuarioId)));
    }

    private static ResponseEntity<PagoProveedorResponse> creado(PagoProveedorResponse pago) {
        var ubicacion = URI.create("/api/pagos-proveedor/" + pago.id());
        return ResponseEntity.created(ubicacion).body(pago);
    }

    @GetMapping("/compras/{id}/pagos")
    public PagedModel<PagoProveedorResponse> listar(@PathVariable @Positive Long id, @PageableDefault(size = 20) Pageable pageable) {
        return new PagedModel<>(pagos.listarPagosCompra(id, pageable));
    }

    @GetMapping("/compras/{id}/saldo")
    public SaldoCompraResponse saldo(@PathVariable @Positive Long id) {
        return pagos.consultarSaldoCompra(id);
    }

    @GetMapping("/pagos-proveedor/{id}")
    public PagoProveedorResponse obtener(@PathVariable @Positive Long id) {
        return pagos.obtenerPorId(id);
    }
}
