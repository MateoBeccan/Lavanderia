package com.miraquetelavo.lavanderia.reporte.dto;

import java.util.List;

public record ReporteMovimientosResponse(
        List<ReporteMovimientoItemResponse> datos, ResumenMovimientosResponse resumen,
        int pagina, int tamanio, long totalElementos, long totalPaginas) {
}
