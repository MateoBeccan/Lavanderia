package com.miraquetelavo.lavanderia.estadopago.service;

import com.miraquetelavo.lavanderia.estadopago.dto.EstadoPagoResponse;
import com.miraquetelavo.lavanderia.estadopago.entity.EstadoPago;

import java.util.List;

public interface EstadoPagoService {

    List<EstadoPagoResponse> listarActivos();

    EstadoPagoResponse obtener(Long id);

    EstadoPago buscarPorCodigo(String codigo);
}
