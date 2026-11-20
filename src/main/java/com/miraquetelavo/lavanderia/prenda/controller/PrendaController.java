package com.miraquetelavo.lavanderia.prenda.controller;

import com.miraquetelavo.lavanderia.prenda.dto.PrendaRequest;
import com.miraquetelavo.lavanderia.prenda.dto.PrendaResponse;
import com.miraquetelavo.lavanderia.prenda.service.PrendaService;
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
@RequestMapping("/api/prendas")
public class PrendaController {

    private final PrendaService service;

    public PrendaController(PrendaService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<PrendaResponse> registrar(@Valid @RequestBody PrendaRequest solicitud) {
        var prenda = service.registrar(solicitud);
        var ubicacion = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}").buildAndExpand(prenda.id()).toUri();
        return ResponseEntity.created(ubicacion).body(prenda);
    }

    @GetMapping
    public PagedModel<PrendaResponse> listar(@PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return new PagedModel<>(service.listarActivos(pageable));
    }

    @GetMapping("/{id}")
    public PrendaResponse obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @PutMapping("/{id}")
    public PrendaResponse actualizar(@PathVariable Long id, @Valid @RequestBody PrendaRequest solicitud) {
        return service.actualizar(id, solicitud);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactivar(@PathVariable Long id) {
        service.desactivar(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/activar")
    public PrendaResponse activar(@PathVariable Long id) {
        return service.activar(id);
    }
}
