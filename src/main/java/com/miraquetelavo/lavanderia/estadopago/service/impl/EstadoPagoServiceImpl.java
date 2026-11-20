package com.miraquetelavo.lavanderia.estadopago.service.impl;

import com.miraquetelavo.lavanderia.common.exception.RecursoNoEncontradoException;
import com.miraquetelavo.lavanderia.common.exception.SolicitudInvalidaException;
import com.miraquetelavo.lavanderia.estadopago.dto.EstadoPagoResponse;
import com.miraquetelavo.lavanderia.estadopago.entity.EstadoPago;
import com.miraquetelavo.lavanderia.estadopago.repository.EstadoPagoRepository;
import com.miraquetelavo.lavanderia.estadopago.service.EstadoPagoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class EstadoPagoServiceImpl implements EstadoPagoService {

    private final EstadoPagoRepository repository;

    public EstadoPagoServiceImpl(EstadoPagoRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<EstadoPagoResponse> listarActivos() {
        return repository.findByActivoTrueOrderByOrdenAscIdAsc().stream()
                .map(EstadoPagoServiceImpl::responder)
                .toList();
    }

    @Override
    public EstadoPagoResponse obtener(Long id) {
        if (id == null || id <= 0) {
            throw new SolicitudInvalidaException("El ID debe ser un entero positivo.");
        }
        return repository.findByIdAndActivoTrue(id)
                .map(EstadoPagoServiceImpl::responder)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontró el estado de pago con ID " + id + "."));
    }

    @Override
    public EstadoPago buscarPorCodigo(String codigo) {
        return repository.findByCodigo(codigo)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontró el estado de pago con código " + codigo + "."));
    }

    private static EstadoPagoResponse responder(EstadoPago estado) {
        return new EstadoPagoResponse(estado.getId(), estado.getCodigo(), estado.getNombre(),
                estado.getDescripcion(), estado.getOrden(), estado.getActivo());
    }
}
