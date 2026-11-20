package com.miraquetelavo.lavanderia.compra.entity;

import com.miraquetelavo.lavanderia.common.exception.SolicitudInvalidaException;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class ImportesCompra {
    private static final BigDecimal MAXIMO = new BigDecimal("99999999.99");

    private ImportesCompra() {
    }

    public static BigDecimal validarEntrada(BigDecimal valor, String campo) {
        if (valor == null || valor.scale() > 2) {
            throw new SolicitudInvalidaException(campo + " es obligatorio y admite hasta dos decimales.");
        }
        return validarRango(valor, campo).setScale(2, RoundingMode.UNNECESSARY);
    }

    public static BigDecimal subtotal(BigDecimal cantidad, BigDecimal precio) {
        return validarRango(cantidad.multiply(precio).setScale(2, RoundingMode.HALF_UP), "El subtotal");
    }

    public static BigDecimal validarRango(BigDecimal importe, String campo) {
        if (importe.signum() <= 0 || importe.compareTo(MAXIMO) > 0) {
            throw new SolicitudInvalidaException(campo + " debe ser positivo y caber en NUMERIC(10,2).");
        }
        return importe;
    }
}
