package com.miraquetelavo.lavanderia.cliente.controller;

import com.miraquetelavo.lavanderia.cliente.dto.ClienteRequest;
import com.miraquetelavo.lavanderia.cliente.dto.ClienteResponse;
import com.miraquetelavo.lavanderia.cliente.service.ClienteService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/clientes")
public class ClienteController {

    private final ClienteService service;

    public ClienteController(ClienteService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ClienteResponse> registrar(@Valid @RequestBody ClienteRequest solicitud) {
        var cliente = service.registrar(solicitud);
        var ubicacion = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}").buildAndExpand(cliente.id()).toUri();
        return ResponseEntity.created(ubicacion).body(cliente);
    }

    @GetMapping
    public PagedModel<ClienteResponse> listar(@PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return new PagedModel<>(service.listarActivos(pageable));
    }

    @GetMapping("/{id}")
    public ClienteResponse obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @PutMapping("/{id}")
    public ClienteResponse actualizar(@PathVariable Long id, @Valid @RequestBody ClienteRequest solicitud) {
        return service.actualizar(id, solicitud);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactivar(@PathVariable Long id) {
        service.desactivar(id);
        return ResponseEntity.noContent().build();
    }
}
