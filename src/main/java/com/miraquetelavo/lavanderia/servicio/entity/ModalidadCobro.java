package com.miraquetelavo.lavanderia.servicio.entity;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ModalidadCobro {
    UNIDAD,
    KILO,
    PRENDA;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static ModalidadCobro desdeJson(String valor) {
        return valueOf(valor);
    }
}
