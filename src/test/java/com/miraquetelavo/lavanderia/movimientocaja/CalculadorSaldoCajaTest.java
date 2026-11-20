package com.miraquetelavo.lavanderia.movimientocaja;

import com.miraquetelavo.lavanderia.caja.entity.Caja;
import com.miraquetelavo.lavanderia.caja.entity.EstadoCaja;
import com.miraquetelavo.lavanderia.common.exception.ConflictoException;
import com.miraquetelavo.lavanderia.movimientocaja.repository.MovimientoCajaRepository;
import com.miraquetelavo.lavanderia.movimientocaja.service.CalculadorSaldoCaja;
import com.miraquetelavo.lavanderia.usuario.entity.Usuario;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static com.miraquetelavo.lavanderia.movimientocaja.entity.NaturalezaMovimiento.EGRESO;
import static com.miraquetelavo.lavanderia.movimientocaja.entity.NaturalezaMovimiento.INGRESO;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CalculadorSaldoCajaTest {

    private final MovimientoCajaRepository movimientos = mock(MovimientoCajaRepository.class);
    private final CalculadorSaldoCaja calculador = new CalculadorSaldoCaja(movimientos);
    private final MovimientoCajaRepository.Resumen resumen = mock(MovimientoCajaRepository.Resumen.class);

    @Test
    void separaElSaldoGeneralDelEfectivoYValidaEgresos() {
        var caja = mock(Caja.class);
        when(caja.getMontoInicial()).thenReturn(new BigDecimal("20000.00"));
        when(caja.getEstado()).thenReturn(EstadoCaja.ABIERTA);
        when(caja.getUsuarioApertura()).thenReturn(mock(Usuario.class));
        when(resumen.getCaja()).thenReturn(caja);
        when(resumen.getIngresos()).thenReturn(new BigDecimal("20000.00"));
        when(resumen.getEgresos()).thenReturn(BigDecimal.ZERO);
        when(resumen.getIngresosEfectivo()).thenReturn(new BigDecimal("5000.00"));
        when(resumen.getEgresosEfectivo()).thenReturn(BigDecimal.ZERO);
        when(movimientos.resumir(1L, INGRESO.codigos(), EGRESO.codigos(), "EFECTIVO")).thenReturn(Optional.of(resumen));

        var saldo = calculador.calcular(1L);
        assertThat(saldo.saldoGeneral()).isEqualByComparingTo("40000.00");
        assertThat(saldo.efectivoDisponible()).isEqualByComparingTo("25000.00");
        assertThatThrownBy(() -> calculador.validarMovimiento(saldo, EGRESO, new BigDecimal("26000.00"), true))
                .isInstanceOf(ConflictoException.class).hasMessageContaining("efectivo disponible");
        assertThatCode(() -> calculador.validarMovimiento(saldo, EGRESO, new BigDecimal("50000.00"), false))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> calculador.validarMovimiento(saldo, INGRESO, new BigDecimal("99999999.99"), false))
                .isInstanceOf(ConflictoException.class).hasMessageContaining("rango");
    }

    @Test
    void rechazaUnTipoSinClasificacionFinanciera() {
        when(resumen.getSinClasificar()).thenReturn(1L);
        when(movimientos.resumir(1L, INGRESO.codigos(), EGRESO.codigos(), "EFECTIVO")).thenReturn(Optional.of(resumen));
        assertThatThrownBy(() -> calculador.calcular(1L)).isInstanceOf(ConflictoException.class);
    }
}
