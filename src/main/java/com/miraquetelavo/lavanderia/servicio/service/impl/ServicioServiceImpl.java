package com.miraquetelavo.lavanderia.servicio.service.impl;

import com.miraquetelavo.lavanderia.common.exception.ConflictoException;
import com.miraquetelavo.lavanderia.common.exception.RecursoNoEncontradoException;
import com.miraquetelavo.lavanderia.servicio.dto.ServicioRequest;
import com.miraquetelavo.lavanderia.servicio.dto.ServicioResponse;
import com.miraquetelavo.lavanderia.servicio.entity.Servicio;
import com.miraquetelavo.lavanderia.servicio.repository.ServicioRepository;
import com.miraquetelavo.lavanderia.servicio.service.ServicioService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import static com.miraquetelavo.lavanderia.common.exception.RestriccionesPostgresql.esViolacionUnica;

@Service
@Validated
@Transactional(readOnly = true)
public class ServicioServiceImpl implements ServicioService {

    private final ServicioRepository repository;

    public ServicioServiceImpl(ServicioRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public ServicioResponse registrar(ServicioRequest solicitud) {
        if (repository.existeNombre(solicitud.nombre())) {
            throw nombreDuplicado(null);
        }
        var servicio = new Servicio(solicitud.nombre(), solicitud.precio(), solicitud.modalidadCobro());
        return guardar(servicio);
    }

    @Override
    public ServicioResponse obtener(Long id) {
        return repository.findById(id)
                .filter(Servicio::getActivo)
                .map(ServicioServiceImpl::responder)
                .orElseThrow(() -> noEncontrado(id));
    }

    @Override
    public Page<ServicioResponse> listarActivos(Pageable pageable) {
        return repository.findByActivoTrue(pageable).map(ServicioServiceImpl::responder);
    }

    @Override
    @Transactional
    public ServicioResponse actualizar(Long id, ServicioRequest solicitud) {
        var servicio = bloquear(id);
        if (!servicio.getActivo()) {
            throw noEncontrado(id);
        }
        if (repository.existeNombreEnOtroServicio(solicitud.nombre(), id)) {
            throw nombreDuplicado(null);
        }

        servicio.actualizarDatos(solicitud.nombre(), solicitud.precio(), solicitud.modalidadCobro());
        return guardar(servicio);
    }

    @Override
    @Transactional
    public void desactivar(Long id) {
        bloquear(id).desactivar();
    }

    @Override
    @Transactional
    public ServicioResponse activar(Long id) {
        var servicio = bloquear(id);
        servicio.activar();
        return responder(servicio);
    }

    private Servicio bloquear(Long id) {
        return repository.findOneById(id).orElseThrow(() -> noEncontrado(id));
    }

    private ServicioResponse guardar(Servicio servicio) {
        try {
            return responder(repository.saveAndFlush(servicio));
        } catch (DataIntegrityViolationException ex) {
            if (esViolacionUnica(ex, Servicio.UK_NOMBRE)) {
                throw nombreDuplicado(ex);
            }
            throw ex;
        }
    }

    private static ConflictoException nombreDuplicado(Throwable causa) {
        return new ConflictoException("Ya existe un servicio con ese nombre.", causa);
    }

    private static RecursoNoEncontradoException noEncontrado(Long id) {
        return new RecursoNoEncontradoException("No se encontró el servicio con ID " + id + ".");
    }

    private static ServicioResponse responder(Servicio servicio) {
        return new ServicioResponse(servicio.getId(), servicio.getNombre(), servicio.getPrecio(),
                servicio.getModalidadCobro(), servicio.getActivo());
    }
}
