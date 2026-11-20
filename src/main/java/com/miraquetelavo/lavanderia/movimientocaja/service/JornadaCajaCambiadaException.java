package com.miraquetelavo.lavanderia.movimientocaja.service;

import com.miraquetelavo.lavanderia.common.exception.ConflictoException;

public class JornadaCajaCambiadaException extends ConflictoException {
    public JornadaCajaCambiadaException() {
        super("La caja de este movimiento ya no está abierta. La jornada pudo haber cambiado.", null);
    }
}
