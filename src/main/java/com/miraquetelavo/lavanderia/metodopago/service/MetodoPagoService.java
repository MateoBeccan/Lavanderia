package com.miraquetelavo.lavanderia.metodopago.service;

import com.miraquetelavo.lavanderia.metodopago.dto.MetodoPagoResponse;
import com.miraquetelavo.lavanderia.metodopago.entity.MetodoPago;

import java.util.List;

public interface MetodoPagoService {

    List<MetodoPagoResponse> listarActivos();

    MetodoPagoResponse obtener(Long id);

    MetodoPago buscarPorCodigo(String codigo);
}
