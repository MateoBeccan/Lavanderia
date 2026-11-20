package com.miraquetelavo.lavanderia.reporte.pdf;

import com.miraquetelavo.lavanderia.reporte.service.ReporteService;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.miraquetelavo.lavanderia.reporte.pdf.LimitesReportePdf.excedido;

@Service
public class ExportacionReportePdf {
    public record Archivo(String nombre, byte[] contenido) {}

    private static final Locale ARGENTINA = Locale.forLanguageTag("es-AR");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/uuuu");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm:ss");
    private static final DateTimeFormatter GENERADO = DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm:ss xxx");
    private static final DateTimeFormatter SELLO = DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss");

    private final ReporteService reportes;
    private final Clock reloj;
    private boolean activo;
    private long ultimaSolicitud;

    public ExportacionReportePdf(ReporteService reportes, Clock relojPedidos) {
        this.reportes = reportes;
        this.reloj = relojPedidos;
    }

    public Archivo pedidos(LocalDate desde, LocalDate hasta, Long cliente, String estado, String pago) {
        return exportar("pedidos", "Reporte de pedidos", desde, hasta, filtros(
                filtro("Cliente ID", cliente), filtro("Estado del pedido", estado), filtro("Estado de pago", pago)
        ), () -> {
            var reporte = reportes.pedidosParaPdf(desde, hasta, cliente, estado, pago);
            var resumen = reporte.resumen();
            return new Datos(reporte.totalElementos(), List.of(
                    cantidad(resumen.cantidadPedidos(), "pedido", "pedidos"),
                    "Importe total de pedidos: " + dinero(resumen.totalGeneral()),
                    "El importe corresponde a pedidos registrados. Los cobros se consultan por separado."
            ), new DocumentoReportePdf.Columna[]{
                    columna("Pedido", 56), columna("Ingreso / entrega", 96), columna("Cliente", 132),
                    columna("Estado", 80), columna("Pago", 70), importe("Total", 89)
            }, reporte.datos().stream().map(fila -> new String[]{
                    "#" + fila.pedidoId(),
                    FECHA_HORA.format(fila.fechaIngreso()) + " / "
                            + (fila.fechaEntregaEstimada() == null ? "-" : FECHA.format(fila.fechaEntregaEstimada())),
                    fila.clienteNombreCompleto(), fila.estadoPedidoNombre(),
                    fila.estadoPagoNombre(), dinero(fila.totalPedido())
            }).toList());
        });
    }

    public Archivo movimientos(LocalDate desde, LocalDate hasta, String tipo, String metodo) {
        return exportar("movimientos-caja", "Reporte de movimientos de Caja", desde, hasta, filtros(
                filtro("Tipo de movimiento", tipo), filtro("Método de pago", metodo)
        ), () -> {
            var reporte = reportes.movimientosParaPdf(desde, hasta, tipo, metodo);
            var resumen = reporte.resumen();
            return new Datos(reporte.totalElementos(), List.of(
                    cantidad(resumen.cantidadMovimientos(), "movimiento", "movimientos"),
                    "Ingresos: " + dinero(resumen.totalIngresos()),
                    "Egresos: " + dinero(resumen.totalEgresos()),
                    "Neto de movimientos: " + dinero(resumen.saldoNeto())
            ), new DocumentoReportePdf.Columna[]{
                    columna("Mov. / caja", 62), columna("Fecha y hora", 76), columna("Tipo / método", 109),
                    columna("Descripción / usuario", 187), importe("Monto", 89)
            }, reporte.datos().stream().map(fila -> new String[]{
                    "#" + fila.movimientoId() + " / #" + fila.cajaId(), FECHA_HORA.format(fila.fechaHora()),
                    fila.tipoMovimientoNombre() + " (" + fila.naturaleza() + ") / " + fila.metodoPagoNombre(),
                    (fila.descripcion() == null || fila.descripcion().isBlank() ? "-" : fila.descripcion())
                            + " / " + fila.usuarioNombre(), dinero(fila.monto())
            }).toList());
        });
    }

    public Archivo compras(LocalDate desde, LocalDate hasta, Long proveedor, String pago) {
        return exportar("compras", "Reporte de compras", desde, hasta, filtros(
                filtro("Proveedor ID", proveedor), filtro("Estado de pago", pago)
        ), () -> {
            var reporte = reportes.comprasParaPdf(desde, hasta, proveedor, pago);
            var resumen = reporte.resumen();
            return new Datos(reporte.totalElementos(), List.of(
                    cantidad(resumen.cantidadCompras(), "compra", "compras"),
                    "Importe total de compras: " + dinero(resumen.totalComprado()),
                    "El importe corresponde a compras registradas. Los pagos se consultan por separado."
            ), new DocumentoReportePdf.Columna[]{
                    columna("Compra", 57), columna("Fecha y hora", 76), columna("Proveedor", 131),
                    columna("Comprobante", 100), columna("Pago", 70), importe("Total", 89)
            }, reporte.datos().stream().map(fila -> new String[]{
                    "#" + fila.compraId(), FECHA_HORA.format(fila.fechaCompra()), fila.proveedorNombre(),
                    fila.numeroComprobante(), fila.estadoPagoNombre(), dinero(fila.totalCompra())
            }).toList());
        });
    }

    private Archivo exportar(String tipo, String titulo, LocalDate desde, LocalDate hasta,
                             String filtros, Supplier<Datos> consultar) {
        long inicio = System.nanoTime();
        reservar(inicio);
        try {
            var momento = ZonedDateTime.now(reloj);
            long vence = inicio + TimeUnit.SECONDS.toNanos(15);
            var datos = consultar.get();
            DocumentoReportePdf.verificarTiempo(vence);
            if (datos.total() > LimitesReportePdf.FILAS) {
                throw excedido("El reporte supera el máximo de 1.000 registros.");
            }
            if (datos.total() != datos.filas().size()) {
                throw new IllegalStateException("La exportación no contiene todas las filas de la consulta.");
            }
            try (var documento = new DocumentoReportePdf(titulo, datos.columnas(), vence)) {
                documento.iniciar(GENERADO.format(momento) + " (" + reloj.getZone() + ")",
                        periodo(desde, hasta), filtros, datos.resumen());
                for (var fila : datos.filas()) {
                    documento.fila(fila);
                }
                return new Archivo("mira-que-te-lavo-" + tipo + "-" + SELLO.format(momento) + ".pdf",
                        documento.terminar());
            }
        } catch (QueryTimeoutException | TransactionTimedOutException ex) {
            throw excedido("La consulta superó el tiempo permitido.");
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo generar el PDF.", ex);
        } finally {
            liberar();
        }
    }

    private synchronized void reservar(long ahora) {
        if (activo || ultimaSolicitud != 0 && ahora - ultimaSolicitud < TimeUnit.SECONDS.toNanos(1)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Hay otra exportación en curso o acaba de iniciarse una. Reintentá en unos segundos.");
        }
        ultimaSolicitud = ahora;
        activo = true;
    }

    private synchronized void liberar() {
        activo = false;
    }

    private static String periodo(LocalDate desde, LocalDate hasta) {
        if (desde == null && hasta == null) return "Todo el período";
        if (desde == null) return "Hasta el " + FECHA.format(hasta);
        if (hasta == null) return "Desde el " + FECHA.format(desde);
        return "Del " + FECHA.format(desde) + " al " + FECHA.format(hasta);
    }

    private static String filtro(String nombre, Object valor) {
        return valor == null ? null : nombre + ": " + valor;
    }

    private static String filtros(String... filtros) {
        var activos = Arrays.stream(filtros).filter(Objects::nonNull).collect(Collectors.joining(" · "));
        return activos.isEmpty() ? "Todos" : activos;
    }

    private static String cantidad(long valor, String singular, String plural) {
        return NumberFormat.getIntegerInstance(ARGENTINA).format(valor) + " " + (valor == 1 ? singular : plural);
    }

    private static String dinero(BigDecimal valor) {
        var formato = NumberFormat.getNumberInstance(ARGENTINA);
        formato.setMinimumFractionDigits(2);
        formato.setMaximumFractionDigits(2);
        return (valor.signum() < 0 ? "-$ " : "$ ") + formato.format(valor.abs());
    }

    private static DocumentoReportePdf.Columna columna(String titulo, float ancho) {
        return new DocumentoReportePdf.Columna(titulo, ancho, false);
    }

    private static DocumentoReportePdf.Columna importe(String titulo, float ancho) {
        return new DocumentoReportePdf.Columna(titulo, ancho, true);
    }

    private record Datos(long total, List<String> resumen, DocumentoReportePdf.Columna[] columnas,
                         List<String[]> filas) {}
}
