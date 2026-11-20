package com.miraquetelavo.lavanderia.tipomovimientocaja.controller;

import com.miraquetelavo.lavanderia.tipomovimientocaja.dto.TipoMovimientoCajaResponse;
import com.miraquetelavo.lavanderia.tipomovimientocaja.service.TipoMovimientoCajaService;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tipos-movimiento-caja")
@RequiredArgsConstructor
public class TipoMovimientoCajaController {

    private final TipoMovimientoCajaService service;

    @GetMapping
    public List<TipoMovimientoCajaResponse> listar() {
        return service.listarActivos();
    }

    @GetMapping("/{id}")
    public TipoMovimientoCajaResponse obtener(@PathVariable @Positive Long id) {
        return service.obtenerPorId(id);
    }
}
