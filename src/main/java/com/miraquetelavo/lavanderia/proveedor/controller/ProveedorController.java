package com.miraquetelavo.lavanderia.proveedor.controller;

import com.miraquetelavo.lavanderia.proveedor.dto.ProveedorRequest;
import com.miraquetelavo.lavanderia.proveedor.dto.ProveedorResponse;
import com.miraquetelavo.lavanderia.proveedor.service.ProveedorService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/proveedores")
public class ProveedorController {

    private final ProveedorService service;

    public ProveedorController(ProveedorService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ProveedorResponse> registrar(@Valid @RequestBody ProveedorRequest solicitud) {
        var respuesta = service.registrar(solicitud);
        var ubicacion = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}").buildAndExpand(respuesta.id()).toUri();
        return ResponseEntity.created(ubicacion).body(respuesta);
    }

    @GetMapping
    public PagedModel<ProveedorResponse> listar(@RequestParam(required = false) String nombre,
                                               @RequestParam(required = false) String cuit,
                                               @RequestParam(required = false) String telefono,
                                               @RequestParam(required = false) String rubro,
                                               @PageableDefault(size = 20) Pageable pageable) {
        return new PagedModel<>(service.listarActivos(nombre, cuit, telefono, rubro, pageable));
    }

    @GetMapping("/{id}")
    public ProveedorResponse obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @PutMapping("/{id}")
    public ProveedorResponse actualizar(@PathVariable Long id, @Valid @RequestBody ProveedorRequest solicitud) {
        return service.actualizar(id, solicitud);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desactivar(@PathVariable Long id) {
        service.desactivar(id);
    }

    @PatchMapping("/{id}/activar")
    public ProveedorResponse activar(@PathVariable Long id) {
        return service.activar(id);
    }
}
