package com.miraquetelavo.lavanderia.reporte.controller;

import com.miraquetelavo.lavanderia.reporte.pdf.ExportacionReportePdf;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.miraquetelavo.lavanderia.reporte.controller.ReporteController.fecha;
import static com.miraquetelavo.lavanderia.reporte.controller.ReporteController.id;

@RestController
@RequestMapping("/api/reportes")
@RequiredArgsConstructor
public class ReportePdfController {
    private final ExportacionReportePdf exportacion;

    @GetMapping("/pedidos/pdf")
    public ResponseEntity<byte[]> pedidos(@RequestParam(required = false) String fechaDesde,
                                          @RequestParam(required = false) String fechaHasta,
                                          @RequestParam(required = false) String clienteId,
                                          @RequestParam(required = false) String estadoPedidoCodigo,
                                          @RequestParam(required = false) String estadoPagoCodigo) {
        return descargar(exportacion.pedidos(fecha(fechaDesde, "fechaDesde"), fecha(fechaHasta, "fechaHasta"),
                id(clienteId, "clienteId"), estadoPedidoCodigo, estadoPagoCodigo));
    }

    @GetMapping("/movimientos-caja/pdf")
    public ResponseEntity<byte[]> movimientos(@RequestParam(required = false) String fechaDesde,
                                               @RequestParam(required = false) String fechaHasta,
                                               @RequestParam(required = false) String tipoMovimientoCodigo,
                                               @RequestParam(required = false) String metodoPagoCodigo) {
        return descargar(exportacion.movimientos(fecha(fechaDesde, "fechaDesde"), fecha(fechaHasta, "fechaHasta"),
                tipoMovimientoCodigo, metodoPagoCodigo));
    }

    @GetMapping("/compras/pdf")
    public ResponseEntity<byte[]> compras(@RequestParam(required = false) String fechaDesde,
                                          @RequestParam(required = false) String fechaHasta,
                                          @RequestParam(required = false) String proveedorId,
                                          @RequestParam(required = false) String estadoPagoCodigo) {
        return descargar(exportacion.compras(fecha(fechaDesde, "fechaDesde"), fecha(fechaHasta, "fechaHasta"),
                id(proveedorId, "proveedorId"), estadoPagoCodigo));
    }

    private static ResponseEntity<byte[]> descargar(ExportacionReportePdf.Archivo archivo) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(archivo.contenido().length)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(archivo.nombre()).build().toString())
                .body(archivo.contenido());
    }
}
