package com.miraquetelavo.lavanderia.pedido.service.impl;

import com.miraquetelavo.lavanderia.common.exception.ConflictoException;
import com.miraquetelavo.lavanderia.common.exception.SolicitudInvalidaException;

import java.util.Map;
import java.util.Set;

final class TransicionesPedido {
    static final String CANCELADO = "CANCELADO";
    private static final Map<String, Set<String>> DESTINOS = Map.of(
            "PENDIENTE", Set.of("EN_PROCESO", CANCELADO),
            "EN_PROCESO", Set.of("LISTO", CANCELADO),
            "LISTO", Set.of("ENTREGADO")
    );
    private static final Set<String> TERMINALES = Set.of("ENTREGADO", CANCELADO);

    private TransicionesPedido() {}

    static boolean esTerminal(String codigo) {
        return TERMINALES.contains(codigo);
    }

    static void validar(String actual, String destino, String observacion) {
        if (CANCELADO.equals(destino) && (observacion == null || observacion.isBlank())) {
            throw new SolicitudInvalidaException("Se requiere un motivo para cancelar el pedido.");
        }
        if (!DESTINOS.getOrDefault(actual, Set.of()).contains(destino)) {
            throw new ConflictoException("Transición no permitida de " + actual + " a " + destino + ".", null);
        }
    }
}
