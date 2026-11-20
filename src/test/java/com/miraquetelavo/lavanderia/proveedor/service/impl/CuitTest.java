package com.miraquetelavo.lavanderia.proveedor.service.impl;

import com.miraquetelavo.lavanderia.common.exception.SolicitudInvalidaException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CuitTest {

    @ParameterizedTest
    @ValueSource(strings = {"30-12345678-1", "30123456781", "20-12345670-0", "20-12345678-6"})
    void normalizaCuitsValidos(String valor) {
        assertThat(Cuit.normalizar(valor)).isEqualTo(valor.replace("-", ""));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "30-12345678-9", "30123456789", "30-123456781", "3012345678-1",
            "3012345678", "301234567811", "30.12345678.1", "30-1234567A-1", " 30123456781", "30123456781 ",
            "20-12345676-0"})
    void rechazaFormatoODigitoIncorrecto(String valor) {
        assertThatThrownBy(() -> Cuit.normalizar(valor)).isInstanceOf(SolicitudInvalidaException.class);
    }
}
