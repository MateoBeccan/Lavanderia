package com.miraquetelavo.lavanderia.caja.service;

import com.miraquetelavo.lavanderia.caja.dto.*;
import com.miraquetelavo.lavanderia.caja.entity.EstadoCaja;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

public interface CajaService {

    CajaResponse abrirCaja(@Valid AbrirCajaRequest solicitud, Long usuarioId);

    CajaResponse obtenerCajaAbierta();

    CajaResponse obtenerPorId(Long id);

    Page<CajaResumenResponse> listarCajas(EstadoCaja estado, LocalDate desde, LocalDate hasta, Pageable pageable);

    CajaResponse cerrarCaja(Long id, @Valid CerrarCajaRequest solicitud, Long usuarioId);
}
