package com.miraquetelavo.lavanderia.categoriainsumo.service.impl;

import com.miraquetelavo.lavanderia.categoriainsumo.repository.CategoriaInsumoRepository;
import com.miraquetelavo.lavanderia.insumo.repository.InsumoRepository;
import com.miraquetelavo.lavanderia.common.exception.ConflictoException;
import com.miraquetelavo.lavanderia.common.exception.RecursoNoEncontradoException;
import com.miraquetelavo.lavanderia.categoriainsumo.dto.CategoriaInsumoRequest;
import com.miraquetelavo.lavanderia.categoriainsumo.dto.CategoriaInsumoResponse;
import com.miraquetelavo.lavanderia.categoriainsumo.entity.CategoriaInsumo;
import com.miraquetelavo.lavanderia.categoriainsumo.service.CategoriaInsumoService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;

import static com.miraquetelavo.lavanderia.common.exception.RestriccionesPostgresql.esViolacionUnica;

@Service
@Validated
@Transactional(readOnly = true)
public class CategoriaInsumoServiceImpl implements CategoriaInsumoService {

    private final CategoriaInsumoRepository categorias;
    private final InsumoRepository insumos;

    public CategoriaInsumoServiceImpl(CategoriaInsumoRepository categorias, InsumoRepository insumos) {
        this.categorias = categorias;
        this.insumos = insumos;
    }

    @Override
    @Transactional
    public CategoriaInsumoResponse registrar(CategoriaInsumoRequest solicitud) {
        return guardar(new CategoriaInsumo(solicitud.nombre()));
    }

    @Override
    public CategoriaInsumoResponse obtener(Long id) {
        return categorias.findByIdAndActivoTrue(id)
                .map(CategoriaInsumoServiceImpl::responder)
                .orElseThrow(() -> noEncontrado(id));
    }

    @Override
    public List<CategoriaInsumoResponse> listarActivos() {
        return categorias.findByActivoTrueOrderByIdAsc().stream()
                .map(CategoriaInsumoServiceImpl::responder)
                .toList();
    }

    @Override
    @Transactional
    public CategoriaInsumoResponse actualizar(Long id, CategoriaInsumoRequest solicitud) {
        var categoria = bloquear(id);
        if (!categoria.getActivo()) {
            throw noEncontrado(id);
        }
        categoria.actualizarNombre(solicitud.nombre());
        return guardar(categoria);
    }

    @Override
    @Transactional
    public void desactivar(Long id) {
        var categoria = bloquear(id);
        if (!categoria.getActivo()) {
            return;
        }
        if (insumos.existsByCategoriaIdAndActivoTrue(id)) {
            throw new ConflictoException("La categoría tiene insumos activos asociados.", null);
        }
        categoria.desactivar();
    }

    @Override
    @Transactional
    public CategoriaInsumoResponse activar(Long id) {
        var categoria = bloquear(id);
        categoria.activar();
        return responder(categoria);
    }

    private CategoriaInsumo bloquear(Long id) {
        return categorias.findOneById(id).orElseThrow(() -> noEncontrado(id));
    }

    private CategoriaInsumoResponse guardar(CategoriaInsumo categoria) {
        try {
            return responder(categorias.saveAndFlush(categoria));
        } catch (DataIntegrityViolationException ex) {
            if (esViolacionUnica(ex, CategoriaInsumo.UK_NOMBRE)) {
                throw new ConflictoException("Ya existe una categoría de insumo con ese nombre.", ex);
            }
            throw ex;
        }
    }

    private static RecursoNoEncontradoException noEncontrado(Long id) {
        return new RecursoNoEncontradoException("No se encontró la categoría de insumo con ID " + id + ".");
    }

    private static CategoriaInsumoResponse responder(CategoriaInsumo categoria) {
        return new CategoriaInsumoResponse(categoria.getId(), categoria.getNombre(), categoria.getActivo());
    }
}
