package com.miraquetelavo.lavanderia.movimientocaja.service;

import com.miraquetelavo.lavanderia.caja.entity.EstadoCaja;
import com.miraquetelavo.lavanderia.common.exception.ConflictoException;
import com.miraquetelavo.lavanderia.common.exception.RecursoNoEncontradoException;
import com.miraquetelavo.lavanderia.movimientocaja.dto.SaldoCajaResponse;
import com.miraquetelavo.lavanderia.movimientocaja.entity.NaturalezaMovimiento;
import com.miraquetelavo.lavanderia.movimientocaja.repository.MovimientoCajaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

import static com.miraquetelavo.lavanderia.movimientocaja.entity.NaturalezaMovimiento.EGRESO;
import static com.miraquetelavo.lavanderia.movimientocaja.entity.NaturalezaMovimiento.INGRESO;

@Component
@RequiredArgsConstructor
public class CalculadorSaldoCaja {

    public static final String EFECTIVO = "EFECTIVO";
    private static final BigDecimal MAX_SALDO = new BigDecimal("99999999.99");
    private static final BigDecimal MAX_TOTAL = new BigDecimal("9999999999999999.99");

    private final MovimientoCajaRepository movimientos;

    public SaldoCajaResponse calcular(Long cajaId) {
        var resumen = movimientos.resumir(cajaId, INGRESO.codigos(), EGRESO.codigos(), EFECTIVO)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró la caja con ID " + cajaId + "."));
        if (resumen.getSinClasificar() != 0) {
            throw new ConflictoException("La caja contiene movimientos de un tipo sin clasificación financiera.", null);
        }

        var caja = resumen.getCaja();
        var montoInicial = caja.getMontoInicial();
        var apertura = caja.getUsuarioApertura();
        var cierre = caja.getUsuarioCierre();
        boolean cerrada = caja.getEstado() == EstadoCaja.CERRADA;
        var general = cerrada ? caja.getMontoFinalCalculado()
                : montoInicial.add(resumen.getIngresos()).subtract(resumen.getEgresos());
        var efectivo = cerrada ? caja.getEfectivoEsperado()
                : montoInicial.add(resumen.getIngresosEfectivo()).subtract(resumen.getEgresosEfectivo());
        return new SaldoCajaResponse(cajaId, caja.getEstado(), montoInicial, resumen.getIngresos(),
                resumen.getEgresos(), general, resumen.getIngresosEfectivo(), resumen.getEgresosEfectivo(),
                efectivo, caja.getEfectivoEsperado(), caja.getEfectivoContado(), caja.getDiferenciaArqueo(),
                caja.getFechaApertura(), apertura.getId(), apertura.getNombre(), caja.getFechaCierre(),
                cierre == null ? null : cierre.getId(), cierre == null ? null : cierre.getNombre());
    }

    public void validarCierre(SaldoCajaResponse saldo) {
        validarLimites(saldo.saldoGeneral(), saldo.efectivoDisponible(), saldo.totalIngresos(), saldo.totalEgresos());
    }

    public void validarMovimiento(SaldoCajaResponse saldo, NaturalezaMovimiento naturaleza,
                                  BigDecimal monto, boolean enEfectivo) {
        boolean ingreso = naturaleza == INGRESO;
        if (!ingreso && enEfectivo && monto.compareTo(saldo.efectivoDisponible()) > 0) {
            throw new ConflictoException("El egreso supera el efectivo disponible de la caja.", null);
        }
        var variacion = ingreso ? monto : monto.negate();
        validarLimites(saldo.saldoGeneral().add(variacion),
                enEfectivo ? saldo.efectivoDisponible().add(variacion) : saldo.efectivoDisponible(),
                ingreso ? saldo.totalIngresos().add(monto) : saldo.totalIngresos(),
                ingreso ? saldo.totalEgresos() : saldo.totalEgresos().add(monto));
    }

    private static void validarLimites(BigDecimal general, BigDecimal efectivo, BigDecimal ingresos, BigDecimal egresos) {
        if (general.abs().compareTo(MAX_SALDO) > 0 || efectivo.signum() < 0 || efectivo.compareTo(MAX_SALDO) > 0
                || ingresos.compareTo(MAX_TOTAL) > 0 || egresos.compareTo(MAX_TOTAL) > 0) {
            throw new ConflictoException("La operación deja los saldos de caja fuera del rango admitido.", null);
        }
    }
}
