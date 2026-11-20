package com.miraquetelavo.lavanderia.proveedor.service;

import com.miraquetelavo.lavanderia.proveedor.dto.ProveedorRequest;
import com.miraquetelavo.lavanderia.proveedor.dto.ProveedorResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProveedorService {

    ProveedorResponse registrar(@NotNull @Valid ProveedorRequest solicitud);

    ProveedorResponse obtener(Long id);

    Page<ProveedorResponse> listarActivos(String nombre, String cuit, String telefono, String rubro, Pageable pageable);

    ProveedorResponse actualizar(Long id, @NotNull @Valid ProveedorRequest solicitud);

    void desactivar(Long id);

    ProveedorResponse activar(Long id);
}
