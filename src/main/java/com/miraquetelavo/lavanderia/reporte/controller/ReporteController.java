package com.miraquetelavo.lavanderia.reporte.controller;

import com.miraquetelavo.lavanderia.common.exception.SolicitudInvalidaException;
import com.miraquetelavo.lavanderia.reporte.dto.ReporteComprasResponse;
import com.miraquetelavo.lavanderia.reporte.dto.ReporteMovimientosResponse;
import com.miraquetelavo.lavanderia.reporte.dto.ReportePedidosResponse;
import com.miraquetelavo.lavanderia.reporte.service.ReporteService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/reportes")
@RequiredArgsConstructor
public class ReporteController {

    private static final Pattern FECHA_ISO = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");

    private final ReporteService reportes;

    @GetMapping("/pedidos")
    public ReportePedidosResponse pedidos(
            @RequestParam(required = false) String fechaDesde,
            @RequestParam(required = false) String fechaHasta,
            @RequestParam(required = false) String clienteId,
            @RequestParam(required = false) String estadoPedidoCodigo,
            @RequestParam(required = false) String estadoPagoCodigo,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size) {
        return reportes.pedidos(fecha(fechaDesde, "fechaDesde"), fecha(fechaHasta, "fechaHasta"),
                id(clienteId, "clienteId"), estadoPedidoCodigo, estadoPagoCodigo,
                entero(page, "page", 0), entero(size, "size", 20));
    }

    @GetMapping("/movimientos-caja")
    public ReporteMovimientosResponse movimientos(
            @RequestParam(required = false) String fechaDesde,
            @RequestParam(required = false) String fechaHasta,
            @RequestParam(required = false) String tipoMovimientoCodigo,
            @RequestParam(required = false) String metodoPagoCodigo,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size) {
        return reportes.movimientos(fecha(fechaDesde, "fechaDesde"), fecha(fechaHasta, "fechaHasta"),
                tipoMovimientoCodigo, metodoPagoCodigo, entero(page, "page", 0), entero(size, "size", 20));
    }

    @GetMapping("/compras")
    public ReporteComprasResponse compras(
            @RequestParam(required = false) String fechaDesde,
            @RequestParam(required = false) String fechaHasta,
            @RequestParam(required = false) String proveedorId,
            @RequestParam(required = false) String estadoPagoCodigo,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size) {
        return reportes.compras(fecha(fechaDesde, "fechaDesde"), fecha(fechaHasta, "fechaHasta"),
                id(proveedorId, "proveedorId"), estadoPagoCodigo, entero(page, "page", 0), entero(size, "size", 20));
    }

    static LocalDate fecha(String valor, String parametro) {
        if (valor == null) {
            return null;
        }
        if (FECHA_ISO.matcher(valor).matches()) {
            try {
                return LocalDate.parse(valor);
            } catch (DateTimeParseException ex) {
                throw new SolicitudInvalidaException(parametro + " debe ser una fecha válida.");
            }
        }
        throw new SolicitudInvalidaException(parametro + " debe tener formato yyyy-MM-dd.");
    }

    private static int entero(String valor, String parametro, int predeterminado) {
        try {
            return valor == null ? predeterminado : Integer.parseInt(valor);
        } catch (NumberFormatException ex) {
            throw new SolicitudInvalidaException(parametro + " debe ser un número entero.");
        }
    }

    static Long id(String valor, String parametro) {
        try {
            return valor == null ? null : Long.valueOf(valor);
        } catch (NumberFormatException ex) {
            throw new SolicitudInvalidaException(parametro + " debe ser un número entero.");
        }
    }
}
