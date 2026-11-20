package com.miraquetelavo.lavanderia.movimientocaja.dto;

import com.miraquetelavo.lavanderia.caja.entity.EstadoCaja;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SaldoCajaResponse(
        Long cajaId, EstadoCaja estadoCaja, BigDecimal montoInicial, BigDecimal totalIngresos, BigDecimal totalEgresos,
        BigDecimal saldoGeneral, BigDecimal ingresosEfectivo, BigDecimal egresosEfectivo, BigDecimal efectivoDisponible,
        BigDecimal efectivoEsperado, BigDecimal efectivoContado, BigDecimal diferenciaArqueo,
        LocalDateTime fechaApertura, Long usuarioAperturaId, String usuarioAperturaNombre,
        LocalDateTime fechaCierre, Long usuarioCierreId, String usuarioCierreNombre
) {
}
