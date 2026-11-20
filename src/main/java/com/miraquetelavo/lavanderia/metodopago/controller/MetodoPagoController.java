package com.miraquetelavo.lavanderia.metodopago.controller;

import com.miraquetelavo.lavanderia.metodopago.dto.MetodoPagoResponse;
import com.miraquetelavo.lavanderia.metodopago.service.MetodoPagoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/metodos-pago")
public class MetodoPagoController {

    private final MetodoPagoService service;

    public MetodoPagoController(MetodoPagoService service) {
        this.service = service;
    }

    @GetMapping
    public List<MetodoPagoResponse> listar() {
        return service.listarActivos();
    }

    @GetMapping("/{id}")
    public MetodoPagoResponse obtener(@PathVariable Long id) {
        return service.obtener(id);
    }
}
