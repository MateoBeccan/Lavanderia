package com.miraquetelavo.lavanderia.categoriainsumo.controller;

import com.miraquetelavo.lavanderia.categoriainsumo.dto.CategoriaInsumoRequest;
import com.miraquetelavo.lavanderia.categoriainsumo.dto.CategoriaInsumoResponse;
import com.miraquetelavo.lavanderia.categoriainsumo.service.CategoriaInsumoService;
import jakarta.validation.Valid;
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

import java.util.List;

@RestController
@RequestMapping("/api/categorias-insumo")
public class CategoriaInsumoController {

    private final CategoriaInsumoService service;

    public CategoriaInsumoController(CategoriaInsumoService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<CategoriaInsumoResponse> registrar(@Valid @RequestBody CategoriaInsumoRequest solicitud) {
        var respuesta = service.registrar(solicitud);
        var ubicacion = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}").buildAndExpand(respuesta.id()).toUri();
        return ResponseEntity.created(ubicacion).body(respuesta);
    }

    @GetMapping
    public List<CategoriaInsumoResponse> listar() {
        return service.listarActivos();
    }

    @GetMapping("/{id}")
    public CategoriaInsumoResponse obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @PutMapping("/{id}")
    public CategoriaInsumoResponse actualizar(@PathVariable Long id, @Valid @RequestBody CategoriaInsumoRequest solicitud) {
        return service.actualizar(id, solicitud);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactivar(@PathVariable Long id) {
        service.desactivar(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/activar")
    public CategoriaInsumoResponse activar(@PathVariable Long id) {
        return service.activar(id);
    }
}
