package com.miraquetelavo.lavanderia.proveedorinsumo.controller;

import com.miraquetelavo.lavanderia.proveedorinsumo.dto.AsociarInsumoRequest;
import com.miraquetelavo.lavanderia.proveedorinsumo.dto.ProveedorInsumoResponse;
import com.miraquetelavo.lavanderia.proveedorinsumo.dto.ProveedorResumenResponse;
import com.miraquetelavo.lavanderia.proveedorinsumo.service.ProveedorInsumoService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ProveedorInsumoController {

    private final ProveedorInsumoService service;

    public ProveedorInsumoController(ProveedorInsumoService service) {
        this.service = service;
    }

    @PostMapping("/proveedores/{id}/insumos")
    @ResponseStatus(HttpStatus.CREATED)
    public ProveedorInsumoResponse asociar(@PathVariable Long id, @Valid @RequestBody AsociarInsumoRequest solicitud) {
        return service.asociar(id, solicitud);
    }

    @GetMapping("/proveedores/{id}/insumos")
    public PagedModel<ProveedorInsumoResponse> listarInsumos(@PathVariable Long id,
                                                             @PageableDefault(size = 20) Pageable pageable) {
        return new PagedModel<>(service.listarInsumos(id, pageable));
    }

    @DeleteMapping("/proveedores/{id}/insumos/{insumoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desasociar(@PathVariable Long id, @PathVariable Long insumoId) {
        service.desasociar(id, insumoId);
    }

    @GetMapping("/insumos/{id}/proveedores")
    public PagedModel<ProveedorResumenResponse> listarProveedores(@PathVariable Long id,
                                                                   @PageableDefault(size = 20) Pageable pageable) {
        return new PagedModel<>(service.listarProveedores(id, pageable));
    }
}
