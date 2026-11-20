package com.miraquetelavo.lavanderia.insumo.controller;

import com.miraquetelavo.lavanderia.insumo.dto.InsumoRequest;
import com.miraquetelavo.lavanderia.insumo.dto.InsumoResponse;
import com.miraquetelavo.lavanderia.insumo.service.InsumoService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/insumos")
public class InsumoController {

    private final InsumoService service;

    public InsumoController(InsumoService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<InsumoResponse> registrar(@Valid @RequestBody InsumoRequest solicitud) {
        var respuesta = service.registrar(solicitud);
        var ubicacion = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}").buildAndExpand(respuesta.id()).toUri();
        return ResponseEntity.created(ubicacion).body(respuesta);
    }

    @GetMapping
    public PagedModel<InsumoResponse> listar(@RequestParam(required = false) Long categoriaId,
                                            @PageableDefault(size = 20) Pageable pageable) {
        return new PagedModel<>(service.listarActivos(categoriaId, pageable));
    }

    @GetMapping("/{id}")
    public InsumoResponse obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @PutMapping("/{id}")
    public InsumoResponse actualizar(@PathVariable Long id, @Valid @RequestBody InsumoRequest solicitud) {
        return service.actualizar(id, solicitud);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactivar(@PathVariable Long id) {
        service.desactivar(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/activar")
    public InsumoResponse activar(@PathVariable Long id) {
        return service.activar(id);
    }
}
