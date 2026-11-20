package com.miraquetelavo.lavanderia.metodopago.service.impl;

import com.miraquetelavo.lavanderia.common.exception.RecursoNoEncontradoException;
import com.miraquetelavo.lavanderia.common.exception.SolicitudInvalidaException;
import com.miraquetelavo.lavanderia.metodopago.dto.MetodoPagoResponse;
import com.miraquetelavo.lavanderia.metodopago.entity.MetodoPago;
import com.miraquetelavo.lavanderia.metodopago.repository.MetodoPagoRepository;
import com.miraquetelavo.lavanderia.metodopago.service.MetodoPagoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class MetodoPagoServiceImpl implements MetodoPagoService {

    private final MetodoPagoRepository repository;

    public MetodoPagoServiceImpl(MetodoPagoRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<MetodoPagoResponse> listarActivos() {
        return repository.listarActivosEnOrdenComercial().stream()
                .map(MetodoPagoServiceImpl::responder)
                .toList();
    }

    @Override
    public MetodoPagoResponse obtener(Long id) {
        if (id == null || id <= 0) {
            throw new SolicitudInvalidaException("El ID debe ser un entero positivo.");
        }
        return repository.findByIdAndActivoTrue(id)
                .map(MetodoPagoServiceImpl::responder)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontró el método de pago con ID " + id + "."));
    }

    @Override
    public MetodoPago buscarPorCodigo(String codigo) {
        return repository.findByCodigo(codigo)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontró el método de pago con código " + codigo + "."));
    }

    private static MetodoPagoResponse responder(MetodoPago estado) {
        return new MetodoPagoResponse(estado.getId(), estado.getCodigo(), estado.getNombre(),
                estado.getDescripcion(), estado.getActivo());
    }
}
