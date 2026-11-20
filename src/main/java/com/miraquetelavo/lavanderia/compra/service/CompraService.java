package com.miraquetelavo.lavanderia.compra.service;

import com.miraquetelavo.lavanderia.compra.dto.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

public interface CompraService {
    CompraResponse crearCompra(@NotNull @Valid CrearCompraRequest solicitud, @NotNull @Positive Long usuarioId);

    CompraResponse actualizarCompra(@Positive Long id, @NotNull @Valid ActualizarCompraRequest solicitud);

    CompraResponse obtenerPorId(@Positive Long id);

    Page<CompraResumenResponse> listarCompras(@Positive Long proveedorId, LocalDate fechaDesde, LocalDate fechaHasta,
                                             String estadoPagoCodigo, String numeroComprobante, Pageable pageable);

    Page<CompraResumenResponse> listarComprasProveedor(@Positive Long proveedorId, Pageable pageable);
}
