package com.miraquetelavo.lavanderia.tipomovimientocaja.service;

import com.miraquetelavo.lavanderia.tipomovimientocaja.dto.TipoMovimientoCajaResponse;
import com.miraquetelavo.lavanderia.tipomovimientocaja.entity.TipoMovimientoCaja;

import java.util.List;

public interface TipoMovimientoCajaService {

    List<TipoMovimientoCajaResponse> listarActivos();

    TipoMovimientoCajaResponse obtenerPorId(Long id);

    TipoMovimientoCaja buscarPorCodigo(String codigo);
}
