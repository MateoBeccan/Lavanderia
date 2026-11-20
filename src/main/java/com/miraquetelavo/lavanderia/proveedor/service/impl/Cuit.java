package com.miraquetelavo.lavanderia.proveedor.service.impl;

import com.miraquetelavo.lavanderia.common.exception.SolicitudInvalidaException;

import java.util.regex.Pattern;

final class Cuit {

    private static final Pattern FORMATO = Pattern.compile("(?:[0-9]{11}|[0-9]{2}-[0-9]{8}-[0-9])");
    private static final int[] PESOS = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};

    private Cuit() {
    }

    static String normalizar(String cuit) {
        if (cuit == null || !FORMATO.matcher(cuit).matches()) {
            throw new SolicitudInvalidaException("El CUIT debe tener 11 dígitos o el formato XX-XXXXXXXX-X.");
        }

        var digitos = cuit.replace("-", "");
        int suma = 0;
        for (int i = 0; i < PESOS.length; i++) {
            suma += (digitos.charAt(i) - '0') * PESOS[i];
        }
        int verificador = (11 - suma % 11) % 11;
        if (verificador != digitos.charAt(10) - '0') {
            throw new SolicitudInvalidaException("El dígito verificador del CUIT es inválido.");
        }
        return digitos;
    }
}
