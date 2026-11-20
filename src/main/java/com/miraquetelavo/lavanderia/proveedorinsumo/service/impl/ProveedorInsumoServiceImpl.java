package com.miraquetelavo.lavanderia.proveedorinsumo.service.impl;

import com.miraquetelavo.lavanderia.categoriainsumo.repository.CategoriaInsumoRepository;
import com.miraquetelavo.lavanderia.common.exception.ConflictoException;
import com.miraquetelavo.lavanderia.common.exception.RecursoNoEncontradoException;
import com.miraquetelavo.lavanderia.insumo.entity.Insumo;
import com.miraquetelavo.lavanderia.insumo.repository.InsumoRepository;
import com.miraquetelavo.lavanderia.proveedor.entity.Proveedor;
import com.miraquetelavo.lavanderia.proveedor.repository.ProveedorRepository;
import com.miraquetelavo.lavanderia.proveedorinsumo.dto.AsociarInsumoRequest;
import com.miraquetelavo.lavanderia.proveedorinsumo.dto.ProveedorInsumoResponse;
import com.miraquetelavo.lavanderia.proveedorinsumo.dto.ProveedorResumenResponse;
import com.miraquetelavo.lavanderia.proveedorinsumo.entity.ProveedorInsumoId;
import com.miraquetelavo.lavanderia.proveedorinsumo.repository.ProveedorInsumoRepository;
import com.miraquetelavo.lavanderia.proveedorinsumo.service.ProveedorInsumoService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@Transactional(readOnly = true)
public class ProveedorInsumoServiceImpl implements ProveedorInsumoService {

    private final ProveedorInsumoRepository relaciones;
    private final ProveedorRepository proveedores;
    private final InsumoRepository insumos;
    private final CategoriaInsumoRepository categorias;

    public ProveedorInsumoServiceImpl(ProveedorInsumoRepository relaciones, ProveedorRepository proveedores,
                                       InsumoRepository insumos, CategoriaInsumoRepository categorias) {
        this.relaciones = relaciones;
        this.proveedores = proveedores;
        this.insumos = insumos;
        this.categorias = categorias;
    }

    @Override
    @Transactional
    public ProveedorInsumoResponse asociar(Long proveedorId, AsociarInsumoRequest solicitud) {
        // Proveedor, luego Insumo, luego CategoriaInsumo, que conserva el orden del catálogo.
        var proveedor = bloquearProveedor(proveedorId);
        var insumo = bloquearInsumo(solicitud.insumoId());
        if (!proveedor.getActivo()) {
            throw new ConflictoException("El proveedor está inactivo.", null);
        }
        if (!insumo.getActivo()) {
            throw new ConflictoException("El insumo está inactivo.", null);
        }
        var categoriaId = insumo.getCategoria().getId();
        var categoria = categorias.bloquearParaInsumo(categoriaId)
                .orElseThrow(() -> noEncontrado("la categoría de insumo", categoriaId));
        if (!categoria.getActivo()) {
            throw new ConflictoException("La categoría del insumo está inactiva.", null);
        }

        if (relaciones.insertar(proveedorId, insumo.getId()) == 0) {
            throw new ConflictoException("El proveedor ya está asociado con ese insumo.", null);
        }
        return new ProveedorInsumoResponse(proveedorId, proveedor.getNombre(), insumo.getId(), insumo.getNombre(),
                categoriaId, categoria.getNombre());
    }

    @Override
    @Transactional
    public void desasociar(Long proveedorId, Long insumoId) {
        bloquearProveedor(proveedorId);
        bloquearInsumo(insumoId);
        relaciones.eliminar(new ProveedorInsumoId(proveedorId, insumoId));
    }

    @Override
    public Page<ProveedorInsumoResponse> listarInsumos(Long proveedorId, Pageable pageable) {
        if (!proveedores.existsById(proveedorId)) {
            throw noEncontrado("el proveedor", proveedorId);
        }
        return relaciones.listarInsumos(proveedorId, sinOrdenExterno(pageable));
    }

    @Override
    public Page<ProveedorResumenResponse> listarProveedores(Long insumoId, Pageable pageable) {
        if (!insumos.existsById(insumoId)) {
            throw noEncontrado("el insumo", insumoId);
        }
        return relaciones.listarProveedores(insumoId, sinOrdenExterno(pageable));
    }

    private Proveedor bloquearProveedor(Long id) {
        return proveedores.findOneById(id).orElseThrow(() -> noEncontrado("el proveedor", id));
    }

    private Insumo bloquearInsumo(Long id) {
        return insumos.findOneById(id).orElseThrow(() -> noEncontrado("el insumo", id));
    }

    private static Pageable sinOrdenExterno(Pageable pageable) {
        return pageable.isPaged() ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()) : Pageable.unpaged();
    }

    private static RecursoNoEncontradoException noEncontrado(String recurso, Long id) {
        return new RecursoNoEncontradoException("No se encontró " + recurso + " con ID " + id + ".");
    }
}
