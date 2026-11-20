package com.miraquetelavo.lavanderia.reporte.pdf;

import com.miraquetelavo.lavanderia.common.exception.SolicitudInvalidaException;

public final class LimitesReportePdf {
    public static final int FILAS = 1_000;
    static final int BYTES = 8 * 1024 * 1024;
    static final int PAGINAS = 160;

    private LimitesReportePdf() {}

    static SolicitudInvalidaException excedido(String motivo) {
        return new SolicitudInvalidaException(motivo + " Acotá las fechas o aplicá más filtros.");
    }
}
