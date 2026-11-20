package com.miraquetelavo.lavanderia.insumo.service.impl;

import com.miraquetelavo.lavanderia.categoriainsumo.repository.CategoriaInsumoRepository;
import com.miraquetelavo.lavanderia.insumo.repository.InsumoRepository;
import com.miraquetelavo.lavanderia.categoriainsumo.entity.CategoriaInsumo;
import com.miraquetelavo.lavanderia.common.exception.ConflictoException;
import com.miraquetelavo.lavanderia.common.exception.RecursoNoEncontradoException;
import com.miraquetelavo.lavanderia.insumo.dto.InsumoRequest;
import com.miraquetelavo.lavanderia.insumo.dto.InsumoResponse;
import com.miraquetelavo.lavanderia.insumo.entity.Insumo;
import com.miraquetelavo.lavanderia.insumo.service.InsumoService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import static com.miraquetelavo.lavanderia.common.exception.RestriccionesPostgresql.esViolacionUnica;

@Service
@Validated
@Transactional(readOnly = true)
public class InsumoServiceImpl implements InsumoService {

    private final CategoriaInsumoRepository categorias;
    private final InsumoRepository insumos;

    public InsumoServiceImpl(CategoriaInsumoRepository categorias, InsumoRepository insumos) {
        this.categorias = categorias;
        this.insumos = insumos;
    }

    @Override
    @Transactional
    public InsumoResponse registrar(InsumoRequest solicitud) {
        var categoria = bloquearCategoriaActiva(solicitud.categoriaId(), false);
        return guardar(new Insumo(solicitud.nombre(), categoria));
    }

    @Override
    public InsumoResponse obtener(Long id) {
        return insumos.findByIdAndActivoTrue(id)
                .map(InsumoServiceImpl::responder)
                .orElseThrow(() -> noEncontrado(id));
    }

    @Override
    public Page<InsumoResponse> listarActivos(Long categoriaId, Pageable pageable) {
        var orden = Sort.by("id");
        var ordenado = pageable.isPaged()
                ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), orden)
                : Pageable.unpaged(orden);
        var pagina = categoriaId == null ? insumos.findByActivoTrue(ordenado)
                : insumos.findByCategoriaIdAndActivoTrue(categoriaId, ordenado);
        return pagina.map(InsumoServiceImpl::responder);
    }

    @Override
    @Transactional
    public InsumoResponse actualizar(Long id, InsumoRequest solicitud) {
        var insumo = bloquear(id);
        if (!insumo.getActivo()) {
            throw noEncontrado(id);
        }
        var categoria = bloquearCategoriaActiva(solicitud.categoriaId(), true);
        insumo.actualizarDatos(solicitud.nombre(), categoria);
        return guardar(insumo);
    }

    @Override
    @Transactional
    public void desactivar(Long id) {
        bloquear(id).desactivar();
    }

    @Override
    @Transactional
    public InsumoResponse activar(Long id) {
        var insumo = bloquear(id);
        bloquearCategoriaActiva(insumo.getCategoria().getId(), true);
        insumo.activar();
        return responder(insumo);
    }

    private Insumo bloquear(Long id) {
        return insumos.findOneById(id).orElseThrow(() -> noEncontrado(id));
    }

    private CategoriaInsumo bloquearCategoriaActiva(Long id, boolean conflictoSiInactiva) {
        var categoria = categorias.bloquearParaInsumo(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró la categoría de insumo con ID " + id + "."));
        if (!categoria.getActivo()) {
            if (conflictoSiInactiva) {
                throw new ConflictoException("La categoría de insumo está inactiva.", null);
            }
            throw new RecursoNoEncontradoException("No se encontró una categoría de insumo activa con ID " + id + ".");
        }
        return categoria;
    }

    private InsumoResponse guardar(Insumo insumo) {
        try {
            return responder(insumos.saveAndFlush(insumo));
        } catch (DataIntegrityViolationException ex) {
            if (esViolacionUnica(ex, Insumo.UK_NOMBRE)) {
                throw new ConflictoException("Ya existe un insumo con ese nombre.", ex);
            }
            throw ex;
        }
    }

    private static RecursoNoEncontradoException noEncontrado(Long id) {
        return new RecursoNoEncontradoException("No se encontró el insumo con ID " + id + ".");
    }

    private static InsumoResponse responder(Insumo insumo) {
        var categoria = insumo.getCategoria();
        return new InsumoResponse(insumo.getId(), insumo.getNombre(), categoria.getId(),
                categoria.getNombre(), insumo.getActivo());
    }
}
