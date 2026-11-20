package com.miraquetelavo.lavanderia.pedido.service.impl;

import com.miraquetelavo.lavanderia.common.exception.ConflictoException;
import com.miraquetelavo.lavanderia.common.exception.SolicitudInvalidaException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class TransicionesPedidoTest {
    @ParameterizedTest
    @CsvSource({"PENDIENTE,EN_PROCESO", "PENDIENTE,CANCELADO", "EN_PROCESO,LISTO", "EN_PROCESO,CANCELADO",
            "LISTO,ENTREGADO"})
    void aceptaLasTransicionesOperativas(String actual, String destino) {
        assertThatCode(() -> TransicionesPedido.validar(actual, destino, "Motivo")).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource({"PENDIENTE,ENTREGADO", "EN_PROCESO,ENTREGADO", "LISTO,EN_PROCESO", "ENTREGADO,PENDIENTE",
            "CANCELADO,PENDIENTE", "PENDIENTE,PENDIENTE", "LISTO,CANCELADO", "PENDIENTE,LISTO",
            "ENTREGADO,CANCELADO", "CANCELADO,CANCELADO"})
    void rechazaSaltosRetrocesosYEstadosTerminales(String actual, String destino) {
        assertThatThrownBy(() -> TransicionesPedido.validar(actual, destino, "Motivo"))
                .isInstanceOf(ConflictoException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  \t"})
    void exigeMotivoSiElDestinoEsCancelado(String motivo) {
        assertThatThrownBy(() -> TransicionesPedido.validar("PENDIENTE", "CANCELADO", motivo))
                .isInstanceOf(SolicitudInvalidaException.class);
    }
}
