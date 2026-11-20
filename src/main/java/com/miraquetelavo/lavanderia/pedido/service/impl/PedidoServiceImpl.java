package com.miraquetelavo.lavanderia.pedido.service.impl;

import com.miraquetelavo.lavanderia.cliente.repository.ClienteRepository;
import com.miraquetelavo.lavanderia.cobro.repository.CobroRepository;
import com.miraquetelavo.lavanderia.estadopago.service.EstadoPagoService;
import com.miraquetelavo.lavanderia.common.exception.ConflictoException;
import com.miraquetelavo.lavanderia.common.exception.RecursoNoEncontradoException;
import com.miraquetelavo.lavanderia.common.exception.SolicitudInvalidaException;
import com.miraquetelavo.lavanderia.detallepedido.dto.DetallePedidoRequest;
import com.miraquetelavo.lavanderia.detallepedido.dto.DetallePedidoResponse;
import com.miraquetelavo.lavanderia.detallepedido.entity.DetallePedido;
import com.miraquetelavo.lavanderia.estadopedido.repository.EstadoPedidoRepository;
import com.miraquetelavo.lavanderia.historialestadopedido.service.HistorialEstadoPedidoService;
import com.miraquetelavo.lavanderia.pedido.dto.*;
import com.miraquetelavo.lavanderia.pedido.entity.Pedido;
import com.miraquetelavo.lavanderia.pedido.repository.PedidoRepository;
import com.miraquetelavo.lavanderia.pedido.service.PedidoService;
import com.miraquetelavo.lavanderia.prenda.entity.Prenda;
import com.miraquetelavo.lavanderia.prenda.repository.PrendaRepository;
import com.miraquetelavo.lavanderia.servicio.entity.ModalidadCobro;
import com.miraquetelavo.lavanderia.servicio.entity.Servicio;
import com.miraquetelavo.lavanderia.servicio.repository.ServicioRepository;
import com.miraquetelavo.lavanderia.usuario.repository.UsuarioRepository;
import com.miraquetelavo.lavanderia.usuario.entity.Usuario;
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

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PedidoServiceImpl implements PedidoService {
    private static final Set<String> CAMPOS_ORDEN = Set.of("id", "fechaIngreso", "fechaEntregaEstimada", "total");

    private final PedidoRepository pedidos;
    private final ClienteRepository clientes;
    private final UsuarioRepository usuarios;
    private final EstadoPedidoRepository estados;
    private final PrendaRepository prendas;
    private final ServicioRepository servicios;
    private final Clock relojPedidos;
    private final HistorialEstadoPedidoService historial;
    private final EstadoPagoService estadosPago;
    private final CobroRepository cobros;

    @Override
    @Transactional
    public PedidoResponse registrar(CrearPedidoRequest solicitud, Long usuarioId) {
        var usuario = usuarioActivo(usuarioId);
        var cliente = clientes.findOneById(solicitud.clienteId()).filter(c -> c.isActivo())
                .orElseThrow(() -> noDisponible("Cliente", solicitud.clienteId()));
        var estado = estados.findByCodigo("PENDIENTE").filter(e -> Boolean.TRUE.equals(e.getActivo()))
                .orElseThrow(() -> new ConflictoException("El estado inicial PENDIENTE no está disponible.", null));
        if (solicitud.detalles() != null && solicitud.detalles().stream().anyMatch(d -> d != null && d.detalleId() != null)) {
            throw new SolicitudInvalidaException("detalleId solo se admite en la actualización.");
        }
        var ingreso = ahora();
        validarEntrega(solicitud.fechaEntregaEstimada(), ingreso.toLocalDate());
        var pedido = new Pedido(cliente, usuario, estado, ingreso);
        var estadoPago = estadosPago.buscarPorCodigo("PENDIENTE");
        if (!Boolean.TRUE.equals(estadoPago.getActivo())) {
            throw new ConflictoException("El estado de pago inicial PENDIENTE está inactivo.", null);
        }
        pedido.asignarEstadoPago(estadoPago);
        pedido.actualizar(solicitud.fechaEntregaEstimada(), prepararDetalles(pedido, solicitud.detalles()));
        pedidos.saveAndFlush(pedido);
        historial.registrar(pedido, null, usuario, ingreso, "Pedido registrado");
        return respuesta(pedido);
    }

    @Override
    public PedidoResponse obtener(Long id) {
        return respuesta(pedidos.findCompletoById(id).orElseThrow(() -> noEncontrado(id)));
    }

    @Override
    public Page<PedidoResumenResponse> listar(Long clienteId, String estado, LocalDate desde, LocalDate hasta, Pageable pageable) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new SolicitudInvalidaException("Desde debe ser anterior o igual a hasta.");
        }
        var orden = pageable.getSort();
        if (orden.stream().anyMatch(campo -> !CAMPOS_ORDEN.contains(campo.getProperty()))) {
            throw new SolicitudInvalidaException("Campo de orden inválido.");
        }
        if (pageable.isPaged() && orden.getOrderFor("id") == null) {
            pageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), orden.and(Sort.by("id")));
        }

        Specification<Pedido> filtro = Specification.unrestricted();
        if (clienteId != null) {
            filtro = filtro.and((raiz, consulta, cb) -> cb.equal(raiz.get("cliente").get("id"), clienteId));
        }
        if (estado != null) {
            var codigo = estado.strip().toUpperCase(Locale.ROOT);
            filtro = filtro.and((raiz, consulta, cb) -> cb.equal(raiz.get("estadoPedido").get("codigo"), codigo));
        }
        if (desde != null) {
            filtro = filtro.and((raiz, consulta, cb) -> cb.greaterThanOrEqualTo(raiz.get("fechaIngreso"), desde.atStartOfDay()));
        }
        if (hasta != null) {
            var fin = hasta.atTime(LocalTime.MAX.truncatedTo(ChronoUnit.MICROS));
            filtro = filtro.and((raiz, consulta, cb) -> cb.lessThanOrEqualTo(raiz.get("fechaIngreso"), fin));
        }
        return pedidos.findAll(filtro, pageable).map(p -> new PedidoResumenResponse(
                p.getId(), p.getCliente().getId(), p.getCliente().getNombre(), p.getCliente().getApellido(),
                p.getEstadoPedido().getCodigo(), p.getFechaIngreso(), p.getFechaEntregaEstimada(), p.getTotal()));
    }

    @Override
    @Transactional
    public PedidoResponse actualizar(Long id, ActualizarPedidoRequest solicitud) {
        var pedido = bloquearPedidoCompleto(id);
        if (cobros.existsByPedidoId(id)) {
            throw new ConflictoException("No se puede modificar un pedido con cobros.", null);
        }
        if (TransicionesPedido.esTerminal(pedido.getEstadoPedido().getCodigo())) {
            throw new ConflictoException("El estado del pedido impide su modificación.", null);
        }
        validarEntrega(solicitud.fechaEntregaEstimada(), LocalDate.now(relojPedidos));
        pedido.actualizar(solicitud.fechaEntregaEstimada(), prepararDetalles(pedido, solicitud.detalles()));
        pedidos.flush();
        return respuesta(pedido);
    }

    @Override
    @Transactional
    public PedidoResponse cambiarEstado(Long id, CambiarEstadoPedidoRequest solicitud, Long usuarioId) {
        var codigo = solicitud.codigoEstado().strip().toUpperCase(Locale.ROOT);
        return aplicarTransicion(id, codigo, solicitud.observacion(), usuarioId);
    }

    @Override
    @Transactional
    public PedidoResponse cancelar(Long id, CancelarPedidoRequest solicitud, Long usuarioId) {
        return aplicarTransicion(id, TransicionesPedido.CANCELADO, solicitud.observacion(), usuarioId);
    }

    private PedidoResponse aplicarTransicion(Long id, String codigo, String observacion, Long usuarioId) {
        var usuario = usuarioActivo(usuarioId);
        var pedido = bloquearPedidoCompleto(id);
        if (TransicionesPedido.CANCELADO.equals(codigo) && cobros.existsByPedidoId(id)) {
            throw new ConflictoException("No se puede cancelar un pedido con cobros.", null);
        }
        var anterior = pedido.getEstadoPedido();
        var destino = estados.findByCodigo(codigo).filter(e -> Boolean.TRUE.equals(e.getActivo()))
                .orElseThrow(() -> new SolicitudInvalidaException("El código de estado es inexistente o está inactivo."));
        TransicionesPedido.validar(anterior.getCodigo(), destino.getCodigo(), observacion);
        pedido.cambiarEstado(destino);
        historial.registrar(pedido, anterior, usuario, ahora(), observacion);
        return respuesta(pedido);
    }

    private Pedido bloquearPedidoCompleto(Long id) {
        pedidos.findOneById(id).orElseThrow(() -> noEncontrado(id));
        // Leer los detalles después de bloquear la fila que comparten el PUT y las transiciones.
        return pedidos.findCompletoById(id).orElseThrow(() -> noEncontrado(id));
    }

    private Usuario usuarioActivo(Long id) {
        return usuarios.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new BadCredentialsException("Cuenta inválida."));
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(relojPedidos).truncatedTo(ChronoUnit.MICROS);
    }

    private List<DetallePedido> prepararDetalles(Pedido pedido, List<DetallePedidoRequest> solicitudes) {
        if (solicitudes == null || solicitudes.isEmpty() || solicitudes.stream().anyMatch(d -> d == null)) {
            throw new SolicitudInvalidaException("El pedido requiere al menos un detalle válido.");
        }
        var existentes = pedido.getDetalles().stream()
                .collect(Collectors.toMap(DetallePedido::getId, Function.identity()));
        var ids = new HashSet<Long>();
        for (var detalle : solicitudes) {
            var id = detalle.detalleId();
            if (id != null && (!existentes.containsKey(id) || !ids.add(id))) {
                throw new SolicitudInvalidaException("detalleId debe pertenecer al pedido y aparecer una sola vez.");
            }
        }

        var prendasPorId = prendas.bloquearPorIds(solicitudes.stream().map(DetallePedidoRequest::prendaId).distinct().toList())
                .stream().filter(p -> Boolean.TRUE.equals(p.getActivo()))
                .collect(Collectors.toMap(Prenda::getId, Function.identity()));
        var serviciosPorId = servicios.bloquearPorIds(solicitudes.stream().map(DetallePedidoRequest::servicioId).distinct().toList())
                .stream().filter(s -> Boolean.TRUE.equals(s.getActivo()))
                .collect(Collectors.toMap(Servicio::getId, Function.identity()));

        return solicitudes.stream().map(linea -> {
            var prenda = prendasPorId.get(linea.prendaId());
            if (prenda == null) {
                throw noDisponible("Prenda", linea.prendaId());
            }
            var servicio = serviciosPorId.get(linea.servicioId());
            if (servicio == null) {
                throw noDisponible("Servicio", linea.servicioId());
            }
            var cantidad = validarCantidad(linea.cantidad(), servicio.getModalidadCobro());
            var anterior = existentes.get(linea.detalleId());
            if (anterior == null) {
                return new DetallePedido(prenda, servicio, cantidad, servicio.getPrecio(), linea.estadoPrenda(), linea.observaciones());
            }
            var conservaPrecio = anterior.getServicio().getId().equals(servicio.getId())
                    && anterior.getPrenda().getId().equals(prenda.getId());
            var precio = conservaPrecio ? anterior.getPrecioUnitario() : servicio.getPrecio();
            anterior.actualizar(prenda, servicio, cantidad, precio, linea.estadoPrenda(), linea.observaciones());
            return anterior;
        }).toList();
    }

    private static BigDecimal validarCantidad(BigDecimal cantidad, ModalidadCobro modalidad) {
        if (cantidad == null || cantidad.scale() > 2) {
            throw new SolicitudInvalidaException("La cantidad requiere un valor positivo con hasta dos decimales.");
        }
        if (modalidad != ModalidadCobro.KILO && cantidad.stripTrailingZeros().scale() > 0) {
            throw new SolicitudInvalidaException("La cantidad debe ser entera para " + modalidad + ".");
        }
        return DetallePedido.importeExacto(cantidad, "La cantidad");
    }

    private static void validarEntrega(LocalDate fecha, LocalDate hoy) {
        if (fecha == null || fecha.isBefore(hoy)) {
            throw new SolicitudInvalidaException("La fecha estimada de entrega debe ser igual o posterior a hoy.");
        }
    }

    private static RecursoNoEncontradoException noEncontrado(Long id) {
        return new RecursoNoEncontradoException("No se encontró el pedido " + id + ".");
    }

    private static RecursoNoEncontradoException noDisponible(String recurso, Long id) {
        return new RecursoNoEncontradoException(recurso + " " + id + " inexistente o inactivo.");
    }

    private static PedidoResponse respuesta(Pedido pedido) {
        var cliente = pedido.getCliente();
        var usuario = pedido.getUsuario();
        var estado = pedido.getEstadoPedido();
        var detalles = pedido.getDetalles().stream()
                .map(d -> new DetallePedidoResponse(d.getId(), d.getPrenda().getId(), d.getPrenda().getNombre(),
                        d.getServicio().getId(), d.getServicio().getNombre(), d.getServicio().getModalidadCobro(),
                        d.getCantidad(), d.getPrecioUnitario(), d.getSubtotal(), d.getEstadoPrenda(), d.getObservaciones()))
                .toList();
        return new PedidoResponse(pedido.getId(), cliente.getId(), cliente.getNombre(), cliente.getApellido(),
                usuario.getId(), usuario.getNombre(), new PedidoResponse.Estado(estado.getId(), estado.getCodigo(), estado.getNombre()),
                pedido.getFechaIngreso(), pedido.getFechaEntregaEstimada(), pedido.getTotal(), detalles);
    }
}
