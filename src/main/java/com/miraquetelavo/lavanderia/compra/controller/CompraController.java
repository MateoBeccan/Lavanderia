package com.miraquetelavo.lavanderia.compra.controller;

import com.miraquetelavo.lavanderia.compra.dto.*;
import com.miraquetelavo.lavanderia.compra.service.CompraService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDate;

@RestController
@RequestMapping("/api")
public class CompraController {
    private final CompraService compras;

    public CompraController(CompraService compras) {
        this.compras = compras;
    }

    @PostMapping("/compras")
    public ResponseEntity<CompraResponse> crear(@Valid @RequestBody CrearCompraRequest solicitud,
                                               @AuthenticationPrincipal Jwt jwt) {
        var compra = compras.crearCompra(solicitud, Long.valueOf(jwt.getSubject()));
        return ResponseEntity.created(URI.create("/api/compras/" + compra.id())).body(compra);
    }

    @GetMapping("/compras/{id}")
    public CompraResponse obtener(@PathVariable @Positive Long id) {
        return compras.obtenerPorId(id);
    }

    @PutMapping("/compras/{id}")
    public CompraResponse actualizar(@PathVariable @Positive Long id, @Valid @RequestBody ActualizarCompraRequest solicitud) {
        return compras.actualizarCompra(id, solicitud);
    }

    @GetMapping("/compras")
    public PagedModel<CompraResumenResponse> listar(
            @RequestParam(required = false) @Positive Long proveedorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @RequestParam(required = false) String estadoPagoCodigo,
            @RequestParam(required = false) String numeroComprobante,
            @PageableDefault(size = 20) Pageable pageable) {
        return new PagedModel<>(compras.listarCompras(proveedorId, fechaDesde, fechaHasta, estadoPagoCodigo,
                numeroComprobante, pageable));
    }

    @GetMapping("/proveedores/{id}/compras")
    public PagedModel<CompraResumenResponse> listarProveedor(@PathVariable @Positive Long id,
                                                            @PageableDefault(size = 20) Pageable pageable) {
        return new PagedModel<>(compras.listarComprasProveedor(id, pageable));
    }
}
