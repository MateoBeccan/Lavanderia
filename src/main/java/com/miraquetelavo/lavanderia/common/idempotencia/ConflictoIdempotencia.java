package com.miraquetelavo.lavanderia.common.idempotencia;

public class ConflictoIdempotencia extends RuntimeException {
    public enum Motivo {
        EN_CURSO, CLAVE_REUTILIZADA
    }

    private final Motivo motivo;

    public ConflictoIdempotencia(Motivo motivo) {
        this.motivo = motivo;
    }

    public Motivo motivo() {
        return motivo;
    }
}
