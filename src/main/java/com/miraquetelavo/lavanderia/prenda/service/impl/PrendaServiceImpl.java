package com.miraquetelavo.lavanderia.prenda.service.impl;

import com.miraquetelavo.lavanderia.common.exception.ConflictoException;
import com.miraquetelavo.lavanderia.common.exception.RecursoNoEncontradoException;
import com.miraquetelavo.lavanderia.prenda.dto.PrendaRequest;
import com.miraquetelavo.lavanderia.prenda.dto.PrendaResponse;
import com.miraquetelavo.lavanderia.prenda.entity.Prenda;
import com.miraquetelavo.lavanderia.prenda.repository.PrendaRepository;
import com.miraquetelavo.lavanderia.prenda.service.PrendaService;
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
public class PrendaServiceImpl implements PrendaService {

    private final PrendaRepository repository;

    public PrendaServiceImpl(PrendaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public PrendaResponse registrar(PrendaRequest solicitud) {
        if (repository.existeNombre(solicitud.nombre())) {
            throw nombreDuplicado(null);
        }
        var prenda = new Prenda(solicitud.nombre());
        return guardar(prenda);
    }

    @Override
    public PrendaResponse obtener(Long id) {
        return repository.findById(id)
                .filter(Prenda::getActivo)
                .map(PrendaServiceImpl::responder)
                .orElseThrow(() -> noEncontrado(id));
    }

    @Override
    public Page<PrendaResponse> listarActivos(Pageable pageable) {
        return repository.findByActivoTrue(pageable).map(PrendaServiceImpl::responder);
    }

    @Override
    @Transactional
    public PrendaResponse actualizar(Long id, PrendaRequest solicitud) {
        var prenda = bloquear(id);
        if (!prenda.getActivo()) {
            throw noEncontrado(id);
        }
        if (repository.existeNombreEnOtraPrenda(solicitud.nombre(), id)) {
            throw nombreDuplicado(null);
        }

        prenda.actualizarNombre(solicitud.nombre());
        return guardar(prenda);
    }

    @Override
    @Transactional
    public void desactivar(Long id) {
        bloquear(id).desactivar();
    }

    @Override
    @Transactional
    public PrendaResponse activar(Long id) {
        var prenda = bloquear(id);
        prenda.activar();
        return responder(prenda);
    }

    private Prenda bloquear(Long id) {
        return repository.findOneById(id).orElseThrow(() -> noEncontrado(id));
    }

    private PrendaResponse guardar(Prenda prenda) {
        try {
            return responder(repository.saveAndFlush(prenda));
        } catch (DataIntegrityViolationException ex) {
            if (esViolacionUnica(ex, Prenda.UK_NOMBRE)) {
                throw nombreDuplicado(ex);
            }
            throw ex;
        }
    }

    private static ConflictoException nombreDuplicado(Throwable causa) {
        return new ConflictoException("Ya existe una prenda con ese nombre.", causa);
    }

    private static RecursoNoEncontradoException noEncontrado(Long id) {
        return new RecursoNoEncontradoException("No se encontró la prenda con ID " + id + ".");
    }

    private static PrendaResponse responder(Prenda prenda) {
        return new PrendaResponse(prenda.getId(), prenda.getNombre(), prenda.getActivo());
    }
}
