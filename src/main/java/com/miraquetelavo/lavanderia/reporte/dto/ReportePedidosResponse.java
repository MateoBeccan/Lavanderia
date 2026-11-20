package com.miraquetelavo.lavanderia.reporte.dto;

import java.util.List;

public record ReportePedidosResponse(
        List<ReportePedidoItemResponse> datos, ResumenPedidosResponse resumen,
        int pagina, int tamanio, long totalElementos, long totalPaginas) {
}
