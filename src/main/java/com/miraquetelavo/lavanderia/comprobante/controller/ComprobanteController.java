package com.miraquetelavo.lavanderia.comprobante.controller;

import com.miraquetelavo.lavanderia.comprobante.dto.ComprobanteResponse;
import com.miraquetelavo.lavanderia.comprobante.dto.ComprobanteResumenResponse;
import com.miraquetelavo.lavanderia.comprobante.dto.CrearComprobanteRequest;
import com.miraquetelavo.lavanderia.comprobante.service.ComprobanteService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ComprobanteController {
    private final ComprobanteService comprobantes;

    @PostMapping("/cobros/{id}/comprobante")
    public ResponseEntity<ComprobanteResponse> crearDeCobro(@PathVariable @Positive Long id,
                                                            @Valid @RequestBody CrearComprobanteRequest solicitud) {
        return creado(comprobantes.crearDeCobro(id, solicitud));
    }

    @PostMapping("/pagos-proveedor/{id}/comprobante")
    public ResponseEntity<ComprobanteResponse> crearDePagoProveedor(@PathVariable @Positive Long id,
                                                                    @Valid @RequestBody CrearComprobanteRequest solicitud) {
        return creado(comprobantes.crearDePagoProveedor(id, solicitud));
    }

    @GetMapping("/comprobantes/{id}")
    public ComprobanteResponse obtener(@PathVariable @Positive Long id) {
        return comprobantes.obtener(id);
    }

    @GetMapping("/cobros/{id}/comprobante")
    public ComprobanteResponse obtenerDeCobro(@PathVariable @Positive Long id) {
        return comprobantes.obtenerDeCobro(id);
    }

    @GetMapping("/pagos-proveedor/{id}/comprobante")
    public ComprobanteResponse obtenerDePagoProveedor(@PathVariable @Positive Long id) {
        return comprobantes.obtenerDePagoProveedor(id);
    }

    @GetMapping("/pedidos/{id}/comprobantes")
    public PagedModel<ComprobanteResumenResponse> listarPorPedido(@PathVariable @Positive Long id,
                                                                  @PageableDefault(size = 20) Pageable pageable) {
        return new PagedModel<>(comprobantes.listarPorPedido(id, pageable));
    }

    @GetMapping("/compras/{id}/comprobantes")
    public PagedModel<ComprobanteResumenResponse> listarPorCompra(@PathVariable @Positive Long id,
                                                                  @PageableDefault(size = 20) Pageable pageable) {
        return new PagedModel<>(comprobantes.listarPorCompra(id, pageable));
    }

    private static ResponseEntity<ComprobanteResponse> creado(ComprobanteResponse comprobante) {
        return ResponseEntity.created(URI.create("/api/comprobantes/" + comprobante.id())).body(comprobante);
    }
}
