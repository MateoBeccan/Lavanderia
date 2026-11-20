package com.miraquetelavo.lavanderia.proveedor.service.impl;

import com.miraquetelavo.lavanderia.common.exception.ConflictoException;
import com.miraquetelavo.lavanderia.common.exception.RecursoNoEncontradoException;
import com.miraquetelavo.lavanderia.proveedor.dto.ProveedorRequest;
import com.miraquetelavo.lavanderia.proveedor.dto.ProveedorResponse;
import com.miraquetelavo.lavanderia.proveedor.entity.Proveedor;
import com.miraquetelavo.lavanderia.proveedor.repository.ProveedorRepository;
import com.miraquetelavo.lavanderia.proveedor.service.ProveedorService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.Locale;
import java.util.Objects;

import static com.miraquetelavo.lavanderia.common.exception.RestriccionesPostgresql.esViolacionUnica;

@Service
@Validated
@Transactional(readOnly = true)
public class ProveedorServiceImpl implements ProveedorService {

    private final ProveedorRepository proveedores;

    public ProveedorServiceImpl(ProveedorRepository proveedores) {
        this.proveedores = proveedores;
    }

    @Override
    @Transactional
    public ProveedorResponse registrar(ProveedorRequest solicitud) {
        var cuit = Cuit.normalizar(solicitud.cuit());
        validarDisponibilidad(cuit, null);
        return guardar(new Proveedor(solicitud.nombre(), cuit, solicitud.telefono(), solicitud.rubro()));
    }

    @Override
    public ProveedorResponse obtener(Long id) {
        return proveedores.findByIdAndActivoTrue(id).map(ProveedorServiceImpl::responder)
                .orElseThrow(() -> noEncontrado(id));
    }

    @Override
    public Page<ProveedorResponse> listarActivos(String nombre, String cuit, String telefono, String rubro,
                                                Pageable pageable) {
        Specification<Proveedor> filtro = (raiz, consulta, cb) -> cb.isTrue(raiz.get("activo"));
        if (cuit != null) {
            var normalizado = Cuit.normalizar(cuit);
            filtro = filtro.and((raiz, consulta, cb) -> cb.equal(raiz.get("cuit"), normalizado));
        }
        filtro = filtro.and(contiene("nombre", nombre)).and(contiene("telefono", telefono))
                .and(contiene("rubro", rubro));
        var orden = Sort.by("id");
        var pagina = pageable.isPaged()
                ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), orden)
                : Pageable.unpaged(orden);
        return proveedores.findAll(filtro, pagina).map(ProveedorServiceImpl::responder);
    }

    @Override
    @Transactional
    public ProveedorResponse actualizar(Long id, ProveedorRequest solicitud) {
        var proveedor = bloquear(id);
        if (!proveedor.getActivo()) {
            throw noEncontrado(id);
        }
        var cuit = Cuit.normalizar(solicitud.cuit());
        validarDisponibilidad(cuit, id);
        proveedor.actualizarDatos(solicitud.nombre(), cuit, solicitud.telefono(), solicitud.rubro());
        return guardar(proveedor);
    }

    @Override
    @Transactional
    public void desactivar(Long id) {
        bloquear(id).desactivar();
    }

    @Override
    @Transactional
    public ProveedorResponse activar(Long id) {
        var proveedor = bloquear(id);
        proveedor.activar();
        return responder(proveedor);
    }

    private Proveedor bloquear(Long id) {
        return proveedores.findOneById(id).orElseThrow(() -> noEncontrado(id));
    }

    private void validarDisponibilidad(String cuit, Long id) {
        if (proveedores.findByCuit(cuit).filter(otro -> !Objects.equals(otro.getId(), id)).isPresent()) {
            throw cuitDuplicado(null);
        }
    }

    private ProveedorResponse guardar(Proveedor proveedor) {
        try {
            return responder(proveedores.saveAndFlush(proveedor));
        } catch (DataIntegrityViolationException ex) {
            if (esViolacionUnica(ex, Proveedor.UK_CUIT)) {
                throw cuitDuplicado(ex);
            }
            throw ex;
        }
    }

    private static Specification<Proveedor> contiene(String campo, String valor) {
        if (valor == null || valor.isBlank()) {
            return Specification.unrestricted();
        }
        var literal = valor.strip().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return (raiz, consulta, cb) -> cb.like(cb.lower(raiz.get(campo)), "%" + literal + "%", '!');
    }

    private static ConflictoException cuitDuplicado(Throwable causa) {
        return new ConflictoException("Ya existe un proveedor con ese CUIT.", causa);
    }

    private static RecursoNoEncontradoException noEncontrado(Long id) {
        return new RecursoNoEncontradoException("No se encontró el proveedor con ID " + id + ".");
    }

    private static ProveedorResponse responder(Proveedor proveedor) {
        return new ProveedorResponse(proveedor.getId(), proveedor.getNombre(), proveedor.getCuit(),
                proveedor.getTelefono(), proveedor.getRubro(), proveedor.getActivo());
    }
}
