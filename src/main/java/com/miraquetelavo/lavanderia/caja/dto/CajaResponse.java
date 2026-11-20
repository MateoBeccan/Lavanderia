package com.miraquetelavo.lavanderia.caja.dto;

import com.miraquetelavo.lavanderia.caja.entity.EstadoCaja;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CajaResponse(
        Long id,
        Long usuarioAperturaId,
        String usuarioAperturaNombre,
        Long usuarioCierreId,
        String usuarioCierreNombre,
        LocalDateTime fechaApertura,
        LocalDateTime fechaCierre,
        BigDecimal montoInicial,
        BigDecimal montoFinalCalculado,
        BigDecimal efectivoEsperado,
        BigDecimal efectivoContado,
        BigDecimal diferenciaArqueo,
        EstadoCaja estado
) {
}
