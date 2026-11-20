package com.miraquetelavo.lavanderia.movimientocaja.service;

import com.miraquetelavo.lavanderia.caja.entity.Caja;
import com.miraquetelavo.lavanderia.caja.entity.EstadoCaja;
import com.miraquetelavo.lavanderia.caja.repository.CajaRepository;
import com.miraquetelavo.lavanderia.common.exception.ConflictoException;
import com.miraquetelavo.lavanderia.common.exception.RecursoNoEncontradoException;
import com.miraquetelavo.lavanderia.common.exception.SolicitudInvalidaException;
import com.miraquetelavo.lavanderia.metodopago.entity.MetodoPago;
import com.miraquetelavo.lavanderia.metodopago.repository.MetodoPagoRepository;
import com.miraquetelavo.lavanderia.movimientocaja.dto.RegistrarMovimientoManualRequest;
import com.miraquetelavo.lavanderia.movimientocaja.entity.MovimientoCaja;
import com.miraquetelavo.lavanderia.movimientocaja.entity.NaturalezaMovimiento;
import com.miraquetelavo.lavanderia.movimientocaja.repository.MovimientoCajaRepository;
import com.miraquetelavo.lavanderia.pedido.entity.Pedido;
import com.miraquetelavo.lavanderia.tipomovimientocaja.repository.TipoMovimientoCajaRepository;
import com.miraquetelavo.lavanderia.usuario.entity.Usuario;
import com.miraquetelavo.lavanderia.usuario.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static com.miraquetelavo.lavanderia.movimientocaja.entity.NaturalezaMovimiento.EGRESO;
import static com.miraquetelavo.lavanderia.movimientocaja.entity.NaturalezaMovimiento.INGRESO;
import static com.miraquetelavo.lavanderia.movimientocaja.service.CalculadorSaldoCaja.EFECTIVO;

@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class RegistroMovimientoCaja {
    private final CajaRepository cajas;
    private final MovimientoCajaRepository movimientos;
    private final TipoMovimientoCajaRepository tipos;
    private final MetodoPagoRepository metodos;
    private final UsuarioRepository usuarios;
    private final CalculadorSaldoCaja calculador;
    private final Clock relojPedidos;

    public MovimientoCaja registrarManual(RegistrarMovimientoManualRequest solicitud, Long usuarioId,
                                          String codigo, NaturalezaMovimiento naturaleza) {
        if (!"INGRESO_MANUAL".equals(codigo) && !"EGRESO_MANUAL".equals(codigo)) {
            throw new IllegalArgumentException("Se requiere un tipo de movimiento manual.");
        }
        var usuario = usuarioActivo(usuarioId);
        var caja = cajas.bloquearPorId(solicitud.cajaId())
                .filter(esperada -> esperada.getEstado() == EstadoCaja.ABIERTA)
                .orElseThrow(JornadaCajaCambiadaException::new);
        var metodo = metodos.findByIdAndActivoTrue(solicitud.metodoPagoId()).orElseThrow(() ->
                new RecursoNoEncontradoException("No se encontró un método de pago activo con ID "
                        + solicitud.metodoPagoId() + "."));
        return movimientos.save(preparar(caja, codigo, naturaleza, metodo, usuario,
                solicitud.monto(), solicitud.descripcion().strip()));
    }

    // Cobro persiste este movimiento mediante PERSIST en su propia transacción.
    public MovimientoCaja prepararCobro(Caja caja, Pedido pedido, Long metodoId, BigDecimal monto, Usuario usuario) {
        return preparar(caja, "COBRO_PEDIDO", INGRESO, metodoActivo(metodoId), usuario, monto,
                "Cobro del pedido #" + pedido.getId());
    }

    // El pago persiste este movimiento en la transacción que mantiene bloqueadas Caja y Compra.
    public MovimientoCaja prepararPagoProveedor(Caja caja, Long compraId, Long metodoId,
                                                 BigDecimal monto, Usuario usuario) {
        return preparar(caja, "PAGO_PROVEEDOR", EGRESO, metodoActivo(metodoId), usuario, monto,
                "Pago de compra #" + compraId);
    }

    private MetodoPago metodoActivo(Long metodoId) {
        var metodo = metodos.findById(metodoId).orElseThrow(() ->
                new RecursoNoEncontradoException("No se encontró el método de pago " + metodoId + "."));
        if (!Boolean.TRUE.equals(metodo.getActivo())) {
            throw new SolicitudInvalidaException("El método de pago está inactivo.");
        }
        return metodo;
    }

    public Usuario usuarioActivo(Long id) {
        return usuarios.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new BadCredentialsException("El usuario autenticado no está activo."));
    }

    public Caja bloquearCajaAbierta() {
        // Selecciona el ID sin precargar la entidad. Después de esperar el bloqueo, se relee el estado.
        var caja = cajas.buscarIdPorEstado(EstadoCaja.ABIERTA).flatMap(cajas::bloquearPorId)
                .orElseThrow(() -> new ConflictoException("No hay una caja abierta.", null));
        if (caja.getEstado() != EstadoCaja.ABIERTA) {
            throw new ConflictoException("La caja ya está cerrada.", null);
        }
        return caja;
    }

    private MovimientoCaja preparar(Caja caja, String codigo, NaturalezaMovimiento naturaleza, MetodoPago metodo,
                                     Usuario usuario, BigDecimal monto, String descripcion) {
        var tipo = tipos.findByCodigo(codigo).filter(t -> Boolean.TRUE.equals(t.getActivo()))
                .orElseThrow(() -> new ConflictoException("El tipo de movimiento solicitado no está disponible.", null));
        var saldo = calculador.calcular(caja.getId());
        calculador.validarMovimiento(saldo, naturaleza, monto, EFECTIVO.equals(metodo.getCodigo()));
        var fecha = LocalDateTime.now(relojPedidos).truncatedTo(ChronoUnit.MICROS);
        return new MovimientoCaja(caja, tipo, metodo, usuario, monto.setScale(2), descripcion, fecha);
    }
}
