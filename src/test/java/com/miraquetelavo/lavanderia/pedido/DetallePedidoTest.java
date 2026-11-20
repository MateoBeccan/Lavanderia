package com.miraquetelavo.lavanderia.pedido;

import com.miraquetelavo.lavanderia.common.exception.SolicitudInvalidaException;
import com.miraquetelavo.lavanderia.detallepedido.entity.DetallePedido;
import com.miraquetelavo.lavanderia.pedido.entity.Pedido;
import com.miraquetelavo.lavanderia.prenda.entity.Prenda;
import com.miraquetelavo.lavanderia.servicio.entity.ModalidadCobro;
import com.miraquetelavo.lavanderia.servicio.entity.Servicio;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class DetallePedidoTest {
    @Test
    void calculaYReemplazaManteniendoAmbosLadosDeLaRelacion() {
        var pedido = new Pedido(null, null, null, LocalDateTime.of(2026, 11, 17, 8, 30));
        var entrega = pedido.getFechaIngreso().toLocalDate();
        var primera = linea("1.25", "90.28");
        var segunda = linea("2", "13.00");
        pedido.actualizar(entrega, List.of(primera, segunda));
        assertThat(primera.getSubtotal()).isEqualTo(new BigDecimal("112.85"));
        assertThat(pedido.getTotal()).isEqualTo(new BigDecimal("138.85"));
        assertThat(primera.getPedido()).isSameAs(pedido);

        pedido.actualizar(entrega, List.of(segunda));
        assertThat(pedido.getDetalles()).containsExactly(segunda);
        assertThat(primera.getPedido()).isNull();
        assertThat(pedido.getTotal()).isEqualTo(new BigDecimal("26.00"));
    }

    @ParameterizedTest
    @CsvSource({"0.01, 0.01", "2, 99999999.99"})
    void rechazaSubtotalesQueNoCabenExactamente(String cantidad, String precio) {
        assertThatThrownBy(() -> linea(cantidad, precio)).isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void rechazaTotalFueraDeRango() {
        var pedido = new Pedido(null, null, null, LocalDateTime.of(2026, 11, 17, 8, 30));
        assertThatThrownBy(() -> pedido.actualizar(LocalDate.of(2026, 11, 17),
                List.of(linea("1", "99999999.99"), linea("1", "0.01"))))
                .isInstanceOf(SolicitudInvalidaException.class);
        assertThat(pedido.getDetalles()).isEmpty();
    }

    private DetallePedido linea(String cantidad, String precio) {
        var importe = new BigDecimal(precio);
        return new DetallePedido(new Prenda("Camisa"), new Servicio("Lavado", importe, ModalidadCobro.KILO),
                new BigDecimal(cantidad), importe, null, null);
    }
}
