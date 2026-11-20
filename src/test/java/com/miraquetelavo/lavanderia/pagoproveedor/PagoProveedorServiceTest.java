package com.miraquetelavo.lavanderia.pagoproveedor;

import com.miraquetelavo.lavanderia.common.exception.ConflictoException;
import com.miraquetelavo.lavanderia.compra.repository.CompraRepository;
import com.miraquetelavo.lavanderia.estadopago.service.EstadoPagoService;
import com.miraquetelavo.lavanderia.movimientocaja.service.RegistroMovimientoCaja;
import com.miraquetelavo.lavanderia.pagoproveedor.repository.PagoProveedorRepository;
import com.miraquetelavo.lavanderia.pagoproveedor.service.impl.PagoProveedorServiceImpl;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PagoProveedorServiceTest {
    private final PagoProveedorRepository pagos = mock(PagoProveedorRepository.class);
    private final PagoProveedorServiceImpl servicio = new PagoProveedorServiceImpl(pagos,
            mock(CompraRepository.class), mock(RegistroMovimientoCaja.class), mock(EstadoPagoService.class));

    @ParameterizedTest
    @CsvSource({"0.00, PENDIENTE, 100.00", "40.00, PARCIAL, 60.00", "100.00, PAGADO, 0.00"})
    void calculaSaldoUsandoLaProyeccionConfirmada(String pagado, String codigo, String pendiente) {
        prepararSaldo(pagado, codigo);
        var saldo = servicio.consultarSaldoCompra(1L);
        assertThat(saldo.totalPagado()).isEqualByComparingTo(pagado);
        assertThat(saldo.saldoPendiente()).isEqualByComparingTo(pendiente);
        assertThat(saldo.estadoPagoCodigo()).isEqualTo(codigo);
    }

    @ParameterizedTest
    @CsvSource({"0.00, PAGADO", "40.00, PENDIENTE", "100.00, PARCIAL", "100.01, PARCIAL", "-1.00, PARCIAL"})
    void rechazaInconsistenciasEntreCatalogoYAgrupacion(String pagado, String codigo) {
        prepararSaldo(pagado, codigo);
        assertThatThrownBy(() -> servicio.consultarSaldoCompra(1L))
                .isInstanceOf(ConflictoException.class).hasMessageContaining("inconsistente");
    }

    private void prepararSaldo(String pagado, String codigo) {
        var datos = mock(PagoProveedorRepository.Saldo.class);
        when(datos.getTotal()).thenReturn(new BigDecimal("100.00"));
        when(datos.getPagado()).thenReturn(new BigDecimal(pagado));
        when(datos.getCodigo()).thenReturn(codigo);
        when(datos.getNombre()).thenReturn(codigo);
        when(pagos.consultarSaldo(1L)).thenReturn(Optional.of(datos));
    }
}
