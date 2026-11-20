package com.miraquetelavo.lavanderia.insumo.service;

import com.miraquetelavo.lavanderia.insumo.dto.InsumoRequest;
import com.miraquetelavo.lavanderia.insumo.dto.InsumoResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface InsumoService {

    InsumoResponse registrar(@NotNull @Valid InsumoRequest solicitud);

    InsumoResponse obtener(Long id);

    Page<InsumoResponse> listarActivos(@Positive Long categoriaId, Pageable pageable);

    InsumoResponse actualizar(Long id, @NotNull @Valid InsumoRequest solicitud);

    void desactivar(Long id);

    InsumoResponse activar(Long id);
}
