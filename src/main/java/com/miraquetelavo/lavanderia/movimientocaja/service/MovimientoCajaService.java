package com.miraquetelavo.lavanderia.movimientocaja.service;

import com.miraquetelavo.lavanderia.movimientocaja.dto.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

public interface MovimientoCajaService {
    MovimientoCajaResponse registrarIngreso(@NotNull @Valid RegistrarMovimientoManualRequest solicitud, Long usuarioId);

    MovimientoCajaResponse registrarEgreso(@NotNull @Valid RegistrarMovimientoManualRequest solicitud, Long usuarioId);

    MovimientoCajaResponse obtener(Long id);

    Page<MovimientoCajaResponse> listar(Long cajaId, String tipo, Long metodoPagoId, LocalDate desde, LocalDate hasta,
                                       Pageable pageable);

    SaldoCajaResponse consultarSaldo(Long cajaId);
}
