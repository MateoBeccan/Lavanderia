package com.miraquetelavo.lavanderia.compra;

import com.miraquetelavo.lavanderia.common.exception.SolicitudInvalidaException;
import com.miraquetelavo.lavanderia.compra.entity.Compra;
import com.miraquetelavo.lavanderia.detallecompra.entity.DetalleCompra;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImportesCompraTest {
    @Test
    void sumaLosSubtotalesYaRedondeadosHalfUp() {
        var detalle = detalle("0.50", "1.01");
        var otroDetalle = detalle("0.50", "1.01");
        var compra = new Compra(null, null, null, null, null, List.of(detalle, otroDetalle));

        assertThat(detalle.getSubtotal()).isEqualTo(new BigDecimal("0.51"));
        assertThat(otroDetalle.getSubtotal()).isEqualTo(new BigDecimal("0.51"));
        assertThat(compra.getTotalCompra()).isEqualTo(new BigDecimal("1.02"));
        assertThat(detalle.getCompra()).isSameAs(compra);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1.001", "1.000", "100000000"})
    void rechazaCantidadYPrecioFueraDeRango(String invalido) {
        assertThatThrownBy(() -> detalle(invalido, "1")).isInstanceOf(SolicitudInvalidaException.class);
        assertThatThrownBy(() -> detalle("1", invalido)).isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void rechazaSubtotalCeroYOverflowAntesDePersistir() {
        assertThatThrownBy(() -> detalle("0.01", "0.01")).isInstanceOf(SolicitudInvalidaException.class);
        assertThatThrownBy(() -> detalle("2", "99999999.99")).isInstanceOf(SolicitudInvalidaException.class);
        assertThatThrownBy(() -> new Compra(null, null, null, null, null,
                List.of(detalle("1", "99999999.99"), detalle("1", "0.01"))))
                .isInstanceOf(SolicitudInvalidaException.class);
        assertThatThrownBy(() -> new Compra(null, null, null, null, null, List.of()))
                .isInstanceOf(SolicitudInvalidaException.class);
    }

    private static DetalleCompra detalle(String cantidad, String precio) {
        return new DetalleCompra(null, new BigDecimal(cantidad), new BigDecimal(precio));
    }
}
