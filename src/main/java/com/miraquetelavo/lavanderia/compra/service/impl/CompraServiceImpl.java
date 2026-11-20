package com.miraquetelavo.lavanderia.compra.service.impl;

import com.miraquetelavo.lavanderia.categoriainsumo.repository.CategoriaInsumoRepository;
import com.miraquetelavo.lavanderia.common.exception.ConflictoException;
import com.miraquetelavo.lavanderia.common.exception.RecursoNoEncontradoException;
import com.miraquetelavo.lavanderia.common.exception.SolicitudInvalidaException;
import com.miraquetelavo.lavanderia.compra.dto.*;
import com.miraquetelavo.lavanderia.compra.entity.Compra;
import com.miraquetelavo.lavanderia.compra.repository.CompraRepository;
import com.miraquetelavo.lavanderia.compra.service.CompraService;
import com.miraquetelavo.lavanderia.detallecompra.dto.ActualizarDetalleCompraRequest;
import com.miraquetelavo.lavanderia.detallecompra.dto.DetalleCompraResponse;
import com.miraquetelavo.lavanderia.detallecompra.entity.DetalleCompra;
import com.miraquetelavo.lavanderia.estadopago.repository.EstadoPagoRepository;
import com.miraquetelavo.lavanderia.insumo.entity.Insumo;
import com.miraquetelavo.lavanderia.insumo.repository.InsumoRepository;
import com.miraquetelavo.lavanderia.pagoproveedor.repository.PagoProveedorRepository;
import com.miraquetelavo.lavanderia.proveedor.entity.Proveedor;
import com.miraquetelavo.lavanderia.proveedor.repository.ProveedorRepository;
import com.miraquetelavo.lavanderia.proveedorinsumo.entity.ProveedorInsumoId;
import com.miraquetelavo.lavanderia.proveedorinsumo.repository.ProveedorInsumoRepository;
import com.miraquetelavo.lavanderia.usuario.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompraServiceImpl implements CompraService {
    private static final String PENDIENTE = "PENDIENTE";
    private static final Sort ORDEN = Sort.by(Sort.Direction.DESC, "fechaCompra", "id");

    private final CompraRepository compras;
    private final PagoProveedorRepository pagos;
    private final ProveedorRepository proveedores;
    private final InsumoRepository insumos;
    private final CategoriaInsumoRepository categorias;
    private final ProveedorInsumoRepository asociaciones;
    private final UsuarioRepository usuarios;
    private final EstadoPagoRepository estadosPago;
    private final Clock relojPedidos;

    @Override
    @Transactional
    public CompraResponse crearCompra(CrearCompraRequest solicitud, Long usuarioId) {
        var usuario = usuarios.findByIdAndActivoTrue(usuarioId)
                .orElseThrow(() -> new BadCredentialsException("Cuenta inválida."));
        var proveedor = bloquearProveedor(solicitud.proveedorId());
        var solicitudes = solicitud.detalles().stream()
                .map(d -> new ActualizarDetalleCompraRequest(null, d.insumoId(), d.cantidad(), d.precioCompra()))
                .toList();
        var detalles = prepararDetalles(proveedor, solicitudes, Map.of());
        var estado = estadosPago.findByCodigo(PENDIENTE).filter(e -> Boolean.TRUE.equals(e.getActivo()))
                .orElseThrow(() -> new ConflictoException("El estado de pago inicial PENDIENTE no está disponible.", null));
        var fecha = LocalDateTime.now(relojPedidos).truncatedTo(ChronoUnit.MICROS);
        var compra = new Compra(proveedor, usuario, fecha, estado, solicitud.numeroComprobante(), detalles);
        return responder(compras.saveAndFlush(compra));
    }

    @Override
    @Transactional
    public CompraResponse actualizarCompra(Long id, ActualizarCompraRequest solicitud) {
        var compra = compras.findOneById(id).orElseThrow(() -> noEncontrado("la compra", id));
        validarModificacionFinanciera(compra);
        var proveedor = bloquearProveedor(solicitud.proveedorId());
        var existentes = compra.getDetalles().stream()
                .collect(Collectors.toMap(DetalleCompra::getId, Function.identity()));
        var detalles = prepararDetalles(proveedor, solicitud.detalles(), existentes);
        compra.actualizar(proveedor, solicitud.numeroComprobante(), detalles);
        compras.flush();
        return responder(compra);
    }

    private void validarModificacionFinanciera(Compra compra) {
        if (pagos.existsByCompraId(compra.getId())) {
            throw new ConflictoException("No se puede modificar una compra con pagos confirmados.", null);
        }

        if (!PENDIENTE.equals(compra.getEstadoPago().getCodigo())) {
            throw new ConflictoException("Solo se pueden modificar compras con estado de pago PENDIENTE.", null);
        }
    }

    private List<DetalleCompra> prepararDetalles(Proveedor proveedor, List<ActualizarDetalleCompraRequest> solicitudes,
                                                Map<Long, DetalleCompra> existentes) {
        var detalleIds = new HashSet<Long>();
        var insumoIds = new TreeSet<Long>();
        for (var solicitud : solicitudes) {
            var detalleId = solicitud.detalleId();
            if (detalleId != null && (!existentes.containsKey(detalleId) || !detalleIds.add(detalleId))) {
                throw new SolicitudInvalidaException("El detalle " + detalleId + " no pertenece a la compra o está repetido.");
            }
            if (!insumoIds.add(solicitud.insumoId())) {
                throw new SolicitudInvalidaException("El insumo " + solicitud.insumoId() + " está repetido en la compra.");
            }
        }
        var insumosValidos = new HashMap<Long, Insumo>();
        var categoriaIds = new TreeSet<Long>();
        for (var id : insumoIds) {
            var insumo = insumos.findOneById(id).orElseThrow(() -> noEncontrado("el insumo", id));
            if (!insumo.getActivo()) {
                throw new ConflictoException("El insumo " + id + " está inactivo.", null);
            }
            insumosValidos.put(id, insumo);
            categoriaIds.add(insumo.getCategoria().getId());
        }
        for (var id : categoriaIds) {
            var categoria = categorias.bloquearParaInsumo(id)
                    .orElseThrow(() -> noEncontrado("la categoría de insumo", id));
            if (!categoria.getActivo()) {
                throw new ConflictoException("La categoría de insumo " + id + " está inactiva.", null);
            }
        }
        // Asociar y desasociar toman estos mismos bloqueos de proveedor e insumo antes de cambiar la relación.
        for (var id : insumoIds) {
            if (!asociaciones.existsById(new ProveedorInsumoId(proveedor.getId(), id))) {
                throw new ConflictoException("El proveedor no suministra el insumo " + id + ".", null);
            }
        }
        return solicitudes.stream().map(s -> {
            var existente = s.detalleId() == null ? null : existentes.get(s.detalleId());
            return prepararDetalle(s, insumosValidos.get(s.insumoId()), existente);
        }).toList();
    }

    private static DetalleCompra prepararDetalle(ActualizarDetalleCompraRequest solicitud, Insumo insumo,
                                                 DetalleCompra existente) {
        var precio = solicitud.precioCompra();
        if (precio == null) {
            if (existente == null || !existente.getInsumo().getId().equals(insumo.getId())) {
                throw new SolicitudInvalidaException("El precio de compra es obligatorio para un insumo nuevo o reemplazado.");
            }
            precio = existente.getPrecioCompra();
        }
        if (existente == null) {
            return new DetalleCompra(insumo, solicitud.cantidad(), precio);
        }
        existente.actualizar(insumo, solicitud.cantidad(), precio);
        return existente;
    }

    private Proveedor bloquearProveedor(Long id) {
        var proveedor = proveedores.findOneById(id).orElseThrow(() -> noEncontrado("el proveedor", id));
        if (!proveedor.getActivo()) {
            throw new ConflictoException("El proveedor está inactivo.", null);
        }
        return proveedor;
    }

    @Override
    public CompraResponse obtenerPorId(Long id) {
        return responder(buscarCompleta(id));
    }

    private Compra buscarCompleta(Long id) {
        return compras.findCompletaById(id).orElseThrow(() -> noEncontrado("la compra", id));
    }

    @Override
    public Page<CompraResumenResponse> listarCompras(Long proveedorId, LocalDate fechaDesde, LocalDate fechaHasta,
                                                    String estadoPagoCodigo, String numeroComprobante, Pageable pageable) {
        if (fechaDesde != null && fechaHasta != null && fechaDesde.isAfter(fechaHasta)) {
            throw new SolicitudInvalidaException("fechaDesde debe ser anterior o igual a fechaHasta.");
        }
        Specification<Compra> filtro = Specification.unrestricted();
        if (proveedorId != null) {
            filtro = filtro.and((raiz, consulta, cb) -> cb.equal(raiz.get("proveedor").get("id"), proveedorId));
        }
        if (fechaDesde != null) {
            filtro = filtro.and((raiz, consulta, cb) -> cb.greaterThanOrEqualTo(
                    raiz.get("fechaCompra"), fechaDesde.atStartOfDay()));
        }
        if (fechaHasta != null) {
            filtro = filtro.and((raiz, consulta, cb) -> cb.lessThanOrEqualTo(
                    raiz.get("fechaCompra"), fechaHasta.atTime(23, 59, 59, 999_999_000)));
        }
        if (estadoPagoCodigo != null && !estadoPagoCodigo.isBlank()) {
            var codigo = estadoPagoCodigo.strip().toUpperCase(Locale.ROOT);
            filtro = filtro.and((raiz, consulta, cb) -> cb.equal(raiz.get("estadoPago").get("codigo"), codigo));
        }
        if (numeroComprobante != null) {
            filtro = filtro.and((raiz, consulta, cb) -> cb.equal(raiz.get("numeroComprobante"), numeroComprobante.strip()));
        }
        var pagina = pageable.isPaged() ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), ORDEN)
                : Pageable.unpaged(ORDEN);
        return compras.findAll(filtro, pagina).map(CompraServiceImpl::resumir);
    }

    @Override
    public Page<CompraResumenResponse> listarComprasProveedor(Long proveedorId, Pageable pageable) {
        if (!proveedores.existsById(proveedorId)) {
            throw noEncontrado("el proveedor", proveedorId);
        }
        return listarCompras(proveedorId, null, null, null, null, pageable);
    }

    private static RecursoNoEncontradoException noEncontrado(String recurso, Long id) {
        return new RecursoNoEncontradoException("No se encontró " + recurso + " con ID " + id + ".");
    }

    private static CompraResumenResponse resumir(Compra compra) {
        return new CompraResumenResponse(compra.getId(), compra.getProveedor().getId(), compra.getProveedor().getNombre(),
                compra.getFechaCompra(), compra.getNumeroComprobante(), compra.getTotalCompra(), compra.getEstadoPago().getCodigo());
    }

    private static CompraResponse responder(Compra compra) {
        var detalles = compra.getDetalles().stream()
                .map(d -> new DetalleCompraResponse(d.getId(), d.getInsumo().getId(), d.getInsumo().getNombre(),
                        d.getCantidad(), d.getPrecioCompra(), d.getSubtotal()))
                .toList();
        return new CompraResponse(compra.getId(), compra.getProveedor().getId(), compra.getProveedor().getNombre(),
                compra.getUsuario().getId(), compra.getUsuario().getNombre(), compra.getFechaCompra(),
                compra.getNumeroComprobante(), compra.getTotalCompra(), compra.getEstadoPago().getCodigo(),
                compra.getEstadoPago().getNombre(), detalles);
    }
}
