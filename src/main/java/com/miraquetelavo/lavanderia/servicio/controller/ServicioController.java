package com.miraquetelavo.lavanderia.servicio.controller;

import com.miraquetelavo.lavanderia.servicio.dto.ServicioRequest;
import com.miraquetelavo.lavanderia.servicio.dto.ServicioResponse;
import com.miraquetelavo.lavanderia.servicio.service.ServicioService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/servicios")
public class ServicioController {

    private final ServicioService service;

    public ServicioController(ServicioService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ServicioResponse> registrar(@Valid @RequestBody ServicioRequest solicitud) {
        var servicio = service.registrar(solicitud);
        var ubicacion = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}").buildAndExpand(servicio.id()).toUri();
        return ResponseEntity.created(ubicacion).body(servicio);
    }

    @GetMapping
    public PagedModel<ServicioResponse> listar(@PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return new PagedModel<>(service.listarActivos(pageable));
    }

    @GetMapping("/{id}")
    public ServicioResponse obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @PutMapping("/{id}")
    public ServicioResponse actualizar(@PathVariable Long id, @Valid @RequestBody ServicioRequest solicitud) {
        return service.actualizar(id, solicitud);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactivar(@PathVariable Long id) {
        service.desactivar(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/activar")
    public ServicioResponse activar(@PathVariable Long id) {
        return service.activar(id);
    }
}
