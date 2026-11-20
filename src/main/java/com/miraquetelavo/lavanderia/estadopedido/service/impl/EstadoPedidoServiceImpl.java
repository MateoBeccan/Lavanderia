package com.miraquetelavo.lavanderia.estadopedido.service.impl;

import com.miraquetelavo.lavanderia.common.exception.RecursoNoEncontradoException;
import com.miraquetelavo.lavanderia.estadopedido.dto.EstadoPedidoResponse;
import com.miraquetelavo.lavanderia.estadopedido.entity.EstadoPedido;
import com.miraquetelavo.lavanderia.estadopedido.repository.EstadoPedidoRepository;
import com.miraquetelavo.lavanderia.estadopedido.service.EstadoPedidoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class EstadoPedidoServiceImpl implements EstadoPedidoService {

    private final EstadoPedidoRepository repository;

    public EstadoPedidoServiceImpl(EstadoPedidoRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<EstadoPedidoResponse> listarActivos() {
        return repository.findByActivoTrueOrderByOrdenAscIdAsc().stream()
                .map(EstadoPedidoServiceImpl::responder)
                .toList();
    }

    @Override
    public EstadoPedidoResponse obtener(Long id) {
        return repository.findByIdAndActivoTrue(id)
                .map(EstadoPedidoServiceImpl::responder)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontró el estado de pedido con ID " + id + "."));
    }

    @Override
    public EstadoPedido buscarPorCodigo(String codigo) {
        return repository.findByCodigo(codigo)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontró el estado de pedido con código " + codigo + "."));
    }

    private static EstadoPedidoResponse responder(EstadoPedido estado) {
        return new EstadoPedidoResponse(estado.getId(), estado.getCodigo(), estado.getNombre(),
                estado.getDescripcion(), estado.getOrden(), estado.getActivo());
    }
}
