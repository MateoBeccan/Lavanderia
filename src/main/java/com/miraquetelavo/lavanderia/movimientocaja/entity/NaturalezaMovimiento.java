package com.miraquetelavo.lavanderia.movimientocaja.entity;

import java.util.Arrays;
import java.util.Set;

public enum NaturalezaMovimiento {
    INGRESO(Set.of("INGRESO_MANUAL", "COBRO_PEDIDO")),
    EGRESO(Set.of("EGRESO_MANUAL", "PAGO_PROVEEDOR"));

    private final Set<String> codigos;

    NaturalezaMovimiento(Set<String> codigos) {
        this.codigos = codigos;
    }

    public Set<String> codigos() {
        return codigos;
    }

    public static NaturalezaMovimiento desdeCodigo(String codigo) {
        return Arrays.stream(values()).filter(naturaleza -> naturaleza.codigos.contains(codigo))
                .findFirst().orElseThrow(() -> new IllegalStateException("Tipo de movimiento sin naturaleza: " + codigo));
    }
}
