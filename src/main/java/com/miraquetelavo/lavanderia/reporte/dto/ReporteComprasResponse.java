package com.miraquetelavo.lavanderia.reporte.dto;

import java.util.List;

public record ReporteComprasResponse(
        List<ReporteCompraItemResponse> datos, ResumenComprasResponse resumen,
        int pagina, int tamanio, long totalElementos, long totalPaginas) {
}
