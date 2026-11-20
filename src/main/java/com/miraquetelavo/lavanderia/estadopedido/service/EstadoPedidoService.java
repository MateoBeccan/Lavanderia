package com.miraquetelavo.lavanderia.estadopedido.service;

import com.miraquetelavo.lavanderia.estadopedido.dto.EstadoPedidoResponse;
import com.miraquetelavo.lavanderia.estadopedido.entity.EstadoPedido;

import java.util.List;

public interface EstadoPedidoService {

    List<EstadoPedidoResponse> listarActivos();

    EstadoPedidoResponse obtener(Long id);

    EstadoPedido buscarPorCodigo(String codigo);
}
