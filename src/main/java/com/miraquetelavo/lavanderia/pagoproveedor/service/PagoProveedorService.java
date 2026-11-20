package com.miraquetelavo.lavanderia.pagoproveedor.service;

import com.miraquetelavo.lavanderia.pagoproveedor.dto.PagoProveedorResponse;
import com.miraquetelavo.lavanderia.pagoproveedor.dto.RegistrarPagoProveedorRequest;
import com.miraquetelavo.lavanderia.pagoproveedor.dto.SaldoCompraResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PagoProveedorService {
    PagoProveedorResponse registrarPago(Long compraId, @NotNull @Valid RegistrarPagoProveedorRequest solicitud,
                            @NotNull @Positive Long usuarioId);

    PagoProveedorResponse obtenerPorId(Long id);

    Page<PagoProveedorResponse> listarPagosCompra(Long compraId, Pageable pageable);

    SaldoCompraResponse consultarSaldoCompra(Long compraId);
}
