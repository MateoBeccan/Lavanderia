package com.miraquetelavo.lavanderia.estadopago.controller;

import com.miraquetelavo.lavanderia.estadopago.dto.EstadoPagoResponse;
import com.miraquetelavo.lavanderia.estadopago.service.EstadoPagoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/estados-pago")
public class EstadoPagoController {

    private final EstadoPagoService service;

    public EstadoPagoController(EstadoPagoService service) {
        this.service = service;
    }

    @GetMapping
    public List<EstadoPagoResponse> listar() {
        return service.listarActivos();
    }

    @GetMapping("/{id}")
    public EstadoPagoResponse obtener(@PathVariable Long id) {
        return service.obtener(id);
    }
}
