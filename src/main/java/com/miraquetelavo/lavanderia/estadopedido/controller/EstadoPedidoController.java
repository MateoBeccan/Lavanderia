package com.miraquetelavo.lavanderia.estadopedido.controller;

import com.miraquetelavo.lavanderia.estadopedido.dto.EstadoPedidoResponse;
import com.miraquetelavo.lavanderia.estadopedido.service.EstadoPedidoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/estados-pedido")
public class EstadoPedidoController {

    private final EstadoPedidoService service;

    public EstadoPedidoController(EstadoPedidoService service) {
        this.service = service;
    }

    @GetMapping
    public List<EstadoPedidoResponse> listar() {
        return service.listarActivos();
    }

    @GetMapping("/{id}")
    public EstadoPedidoResponse obtener(@PathVariable Long id) {
        return service.obtener(id);
    }
}
