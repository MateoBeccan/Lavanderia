package com.miraquetelavo.lavanderia.categoriainsumo.service;

import com.miraquetelavo.lavanderia.categoriainsumo.dto.CategoriaInsumoRequest;
import com.miraquetelavo.lavanderia.categoriainsumo.dto.CategoriaInsumoResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public interface CategoriaInsumoService {

    CategoriaInsumoResponse registrar(@NotNull @Valid CategoriaInsumoRequest solicitud);

    CategoriaInsumoResponse obtener(Long id);

    List<CategoriaInsumoResponse> listarActivos();

    CategoriaInsumoResponse actualizar(Long id, @NotNull @Valid CategoriaInsumoRequest solicitud);

    void desactivar(Long id);

    CategoriaInsumoResponse activar(Long id);
}
