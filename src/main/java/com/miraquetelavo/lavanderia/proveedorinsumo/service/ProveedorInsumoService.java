package com.miraquetelavo.lavanderia.proveedorinsumo.service;

import com.miraquetelavo.lavanderia.proveedorinsumo.dto.AsociarInsumoRequest;
import com.miraquetelavo.lavanderia.proveedorinsumo.dto.ProveedorInsumoResponse;
import com.miraquetelavo.lavanderia.proveedorinsumo.dto.ProveedorResumenResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProveedorInsumoService {

    ProveedorInsumoResponse asociar(Long proveedorId, @NotNull @Valid AsociarInsumoRequest solicitud);

    void desasociar(Long proveedorId, Long insumoId);

    Page<ProveedorInsumoResponse> listarInsumos(Long proveedorId, Pageable pageable);

    Page<ProveedorResumenResponse> listarProveedores(Long insumoId, Pageable pageable);
}
