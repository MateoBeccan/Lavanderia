package com.miraquetelavo.lavanderia.pedido.controller;

import com.miraquetelavo.lavanderia.pedido.dto.*;
import com.miraquetelavo.lavanderia.historialestadopedido.dto.HistorialEstadoPedidoResponse;
import com.miraquetelavo.lavanderia.historialestadopedido.service.HistorialEstadoPedidoService;
import com.miraquetelavo.lavanderia.pedido.service.PedidoService;
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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {
    private final PedidoService service;
    private final HistorialEstadoPedidoService historial;

    public PedidoController(PedidoService service, HistorialEstadoPedidoService historial) {
        this.service = service;
        this.historial = historial;
    }

    @PostMapping
    public ResponseEntity<PedidoResponse> registrar(@Valid @RequestBody CrearPedidoRequest solicitud,
                                                    @AuthenticationPrincipal Jwt jwt) {
        var pedido = service.registrar(solicitud, Long.valueOf(jwt.getSubject()));
        var ubicacion = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}").buildAndExpand(pedido.id()).toUri();
        return ResponseEntity.created(ubicacion).body(pedido);
    }

    @GetMapping
    public PagedModel<PedidoResumenResponse> listar(
            @RequestParam(required = false) @Positive Long clienteId,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return new PagedModel<>(service.listar(clienteId, estado, desde, hasta, pageable));
    }

    @GetMapping("/{id}")
    public PedidoResponse obtener(@PathVariable @Positive Long id) {
        return service.obtener(id);
    }

    @PutMapping("/{id}")
    public PedidoResponse actualizar(@PathVariable @Positive Long id, @Valid @RequestBody ActualizarPedidoRequest solicitud) {
        return service.actualizar(id, solicitud);
    }

    @PatchMapping("/{id}/estado")
    public PedidoResponse cambiarEstado(@PathVariable @Positive Long id,
                                        @Valid @RequestBody CambiarEstadoPedidoRequest solicitud,
                                        @AuthenticationPrincipal Jwt jwt) {
        return service.cambiarEstado(id, solicitud, Long.valueOf(jwt.getSubject()));
    }

    @PatchMapping("/{id}/cancelar")
    public PedidoResponse cancelar(@PathVariable @Positive Long id,
                                   @Valid @RequestBody CancelarPedidoRequest solicitud,
                                   @AuthenticationPrincipal Jwt jwt) {
        return service.cancelar(id, solicitud, Long.valueOf(jwt.getSubject()));
    }

    @GetMapping("/{id}/historial")
    public List<HistorialEstadoPedidoResponse> listarHistorial(@PathVariable @Positive Long id) {
        return historial.listar(id);
    }
}
