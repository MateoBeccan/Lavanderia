package com.miraquetelavo.lavanderia.reporte.service;

import com.miraquetelavo.lavanderia.reporte.dto.ReporteComprasResponse;
import com.miraquetelavo.lavanderia.reporte.dto.ReporteMovimientosResponse;
import com.miraquetelavo.lavanderia.reporte.dto.ReportePedidosResponse;

import java.time.LocalDate;

public interface ReporteService {

    ReportePedidosResponse pedidos(LocalDate desde, LocalDate hasta, Long clienteId,
                                  String estadoPedido, String estadoPago, int page, int size);

    ReporteMovimientosResponse movimientos(LocalDate desde, LocalDate hasta, String tipo,
                                            String metodo, int page, int size);

    ReporteComprasResponse compras(LocalDate desde, LocalDate hasta, Long proveedorId,
                                  String estadoPago, int page, int size);

    ReportePedidosResponse pedidosParaPdf(LocalDate desde, LocalDate hasta, Long clienteId, String estadoPedido, String estadoPago);

    ReporteMovimientosResponse movimientosParaPdf(LocalDate desde, LocalDate hasta, String tipo, String metodo);

    ReporteComprasResponse comprasParaPdf(LocalDate desde, LocalDate hasta, Long proveedorId, String estadoPago);
}
