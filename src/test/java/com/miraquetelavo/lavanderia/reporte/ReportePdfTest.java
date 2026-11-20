package com.miraquetelavo.lavanderia.reporte;

import com.miraquetelavo.lavanderia.common.exception.SolicitudInvalidaException;
import com.miraquetelavo.lavanderia.reporte.dto.*;
import com.miraquetelavo.lavanderia.reporte.pdf.ExportacionReportePdf;
import com.miraquetelavo.lavanderia.reporte.service.ReporteService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReportePdfTest {
    private static final LocalDate DIA = LocalDate.of(2026, 11, 19);
    private final ReporteService reportes = mock(ReporteService.class);
    private final ExportacionReportePdf exportacion = new ExportacionReportePdf(reportes,
            Clock.fixed(Instant.parse("2026-11-19T18:00:00Z"), ZoneId.of("America/Argentina/Buenos_Aires")));

    @Test
    void pedidosIncluyeTodasLasFilasConEncabezadosRepetidosYTotalesExactos() throws Exception {
        var filas = IntStream.rangeClosed(1, 70).mapToObj(i ->
                new ReportePedidoItemResponse((long) i, DIA.atTime(23, 59, 59), null, 9L,
                        "Cliente exacto " + i + " — Ana Pérez (local) \\ prueba", "PENDIENTE", "Pendiente",
                        null, null, new BigDecimal("0.10"))).toList();
        when(reportes.pedidosParaPdf(DIA, DIA, 9L, "PENDIENTE", null)).thenReturn(
                new ReportePedidosResponse(filas, new ResumenPedidosResponse(70, new BigDecimal("7.00")),
                        0, 1000, 70, 1));

        var pdf = exportacion.pedidos(DIA, DIA, 9L, "PENDIENTE", null);
        assertThat(pdf.nombre()).isEqualTo("mira-que-te-lavo-pedidos-20261119-150000.pdf");
        guardarMuestra(pdf);
        try (var documento = Loader.loadPDF(pdf.contenido())) {
            int total = documento.getNumberOfPages();
            assertThat(total).isGreaterThan(1);
            var extractor = new PDFTextStripper();
            String texto = extractor.getText(documento);
            assertThat(texto).contains("70 pedidos", "$ 7,00", "Cliente ID: 9", "Estado del pedido: PENDIENTE",
                    "Del 19/11/2026 al 19/11/2026", "15:00:00 -03:00", "Pérez (local)", "23:59:59");
            assertThat(texto.lines().filter(linea -> linea.contains("Cliente exacto"))).hasSize(70);
            for (int n = 1; n <= total; n++) {
                extractor.setStartPage(n);
                extractor.setEndPage(n);
                assertThat(extractor.getText(documento)).contains("Ingreso / entrega",
                        "Sin validez fiscal", "Página " + n + " de " + total);
            }
        }
    }

    @Test
    void cajaPresentaIngresosEgresosYNetoConSignoYCentavos() throws Exception {
        var fila = new ReporteMovimientoItemResponse(12L, 1L, DIA.atStartOfDay(),
                "EGRESO_MANUAL", "Egreso manual", "EFECTIVO", "Efectivo", 1L, "Operación", null,
                new BigDecimal("1234.50"));
        when(reportes.movimientosParaPdf(null, DIA, "EGRESO_MANUAL", "EFECTIVO"))
                .thenReturn(new ReporteMovimientosResponse(List.of(fila), new ResumenMovimientosResponse(
                        1, new BigDecimal("0.00"), new BigDecimal("1234.50"), new BigDecimal("-1234.50")),
                        0, 1000, 1, 1));
        var pdf = exportacion.movimientos(null, DIA, "EGRESO_MANUAL", "EFECTIVO");
        guardarMuestra(pdf);
        try (var documento = Loader.loadPDF(pdf.contenido())) {
            assertThat(new PDFTextStripper().getText(documento)).contains("1 movimiento", "EGRESO", "Método de pago: EFECTIVO",
                    "Ingresos: $ 0,00", "Egresos: $ 1.234,50", "Neto de movimientos: -$ 1.234,50",
                    "- / Operación");
        }
    }

    @Test
    void generaDocumentoValidoParaComprasSinResultados() throws Exception {
        when(reportes.comprasParaPdf(DIA, null, 5L, "PENDIENTE")).thenReturn(new ReporteComprasResponse(
                List.of(), new ResumenComprasResponse(0, BigDecimal.ZERO), 0, 1000, 0, 0));
        var pdf = exportacion.compras(DIA, null, 5L, "PENDIENTE");
        guardarMuestra(pdf);
        try (var documento = Loader.loadPDF(pdf.contenido())) {
            assertThat(documento.getNumberOfPages()).isEqualTo(1);
            assertThat(new PDFTextStripper().getText(documento)).contains("0 compras", "$ 0,00", "Proveedor ID: 5",
                    "Sin registros para los filtros aplicados.", "Página 1 de 1");
        }
    }

    @Test
    void rechazaExportacionesExcedidasYRegulaSolicitudesRepetidas() {
        when(reportes.pedidosParaPdf(null, null, null, null, null)).thenReturn(new ReportePedidosResponse(
                List.of(), new ResumenPedidosResponse(1001, BigDecimal.ZERO), 0, 1000, 1001, 2));
        assertThatThrownBy(() -> exportacion.pedidos(null, null, null, null, null))
                .isInstanceOf(SolicitudInvalidaException.class).hasMessageContaining("1.000 registros");
        assertThatThrownBy(() -> exportacion.compras(null, null, null, null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(429));
        verify(reportes).pedidosParaPdf(null, null, null, null, null);
        verifyNoMoreInteractions(reportes);
    }

    @Test
    void indicaComoAcotarUnaConsultaQueAgotaElTiempo() {
        when(reportes.pedidosParaPdf(null, null, null, null, null))
                .thenThrow(new QueryTimeoutException("timeout interno"));
        assertThatThrownBy(() -> exportacion.pedidos(null, null, null, null, null))
                .isInstanceOf(SolicitudInvalidaException.class)
                .hasMessageContaining("tiempo permitido").hasMessageContaining("Acotá las fechas");
    }

    private static void guardarMuestra(ExportacionReportePdf.Archivo pdf) throws Exception {
        String configuracion = System.getProperty("reportes.pdf.muestras");
        if (configuracion != null) {
            var directorio = Files.createDirectories(Path.of(configuracion));
            Files.write(directorio.resolve(pdf.nombre()), pdf.contenido());
        }
    }
}
