package com.miraquetelavo.lavanderia.prenda.service;

import com.miraquetelavo.lavanderia.prenda.dto.PrendaRequest;
import com.miraquetelavo.lavanderia.prenda.dto.PrendaResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PrendaService {

    PrendaResponse registrar(@NotNull @Valid PrendaRequest solicitud);

    PrendaResponse obtener(Long id);

    Page<PrendaResponse> listarActivos(Pageable pageable);

    PrendaResponse actualizar(Long id, @NotNull @Valid PrendaRequest solicitud);

    void desactivar(Long id);

    PrendaResponse activar(Long id);
}
