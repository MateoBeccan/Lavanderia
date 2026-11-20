package com.miraquetelavo.lavanderia.comprobante.service;

import com.miraquetelavo.lavanderia.comprobante.dto.ComprobanteResponse;
import com.miraquetelavo.lavanderia.comprobante.dto.ComprobanteResumenResponse;
import com.miraquetelavo.lavanderia.comprobante.dto.CrearComprobanteRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ComprobanteService {
    ComprobanteResponse crearDeCobro(Long cobroId, CrearComprobanteRequest solicitud);
    ComprobanteResponse crearDePagoProveedor(Long pagoId, CrearComprobanteRequest solicitud);
    ComprobanteResponse obtener(Long id);
    ComprobanteResponse obtenerDeCobro(Long cobroId);
    ComprobanteResponse obtenerDePagoProveedor(Long pagoId);
    Page<ComprobanteResumenResponse> listarPorPedido(Long pedidoId, Pageable pageable);
    Page<ComprobanteResumenResponse> listarPorCompra(Long compraId, Pageable pageable);
}
