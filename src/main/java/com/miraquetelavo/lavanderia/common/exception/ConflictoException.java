package com.miraquetelavo.lavanderia.common.exception;

public class ConflictoException extends RuntimeException {

    public ConflictoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
