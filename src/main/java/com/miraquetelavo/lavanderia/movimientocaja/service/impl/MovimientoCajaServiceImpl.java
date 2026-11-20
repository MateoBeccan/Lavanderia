package com.miraquetelavo.lavanderia.movimientocaja.service.impl;

import com.miraquetelavo.lavanderia.caja.repository.CajaRepository;
import com.miraquetelavo.lavanderia.common.exception.RecursoNoEncontradoException;
import com.miraquetelavo.lavanderia.common.exception.SolicitudInvalidaException;
import com.miraquetelavo.lavanderia.movimientocaja.dto.*;
import com.miraquetelavo.lavanderia.movimientocaja.entity.MovimientoCaja;
import com.miraquetelavo.lavanderia.movimientocaja.repository.MovimientoCajaRepository;
import com.miraquetelavo.lavanderia.movimientocaja.service.CalculadorSaldoCaja;
import com.miraquetelavo.lavanderia.movimientocaja.service.MovimientoCajaService;
import com.miraquetelavo.lavanderia.movimientocaja.service.RegistroMovimientoCaja;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;

import static com.miraquetelavo.lavanderia.movimientocaja.entity.NaturalezaMovimiento.EGRESO;
import static com.miraquetelavo.lavanderia.movimientocaja.entity.NaturalezaMovimiento.INGRESO;

@Service
@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MovimientoCajaServiceImpl implements MovimientoCajaService {

    private static final Sort ORDEN = Sort.by(Sort.Direction.DESC, "fechaHora", "id");

    private final CajaRepository cajas;
    private final MovimientoCajaRepository movimientos;
    private final CalculadorSaldoCaja calculador;
    private final RegistroMovimientoCaja registro;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public MovimientoCajaResponse registrarIngreso(RegistrarMovimientoManualRequest solicitud, Long usuarioId) {
        return responder(registro.registrarManual(solicitud, usuarioId, "INGRESO_MANUAL", INGRESO));
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public MovimientoCajaResponse registrarEgreso(RegistrarMovimientoManualRequest solicitud, Long usuarioId) {
        return responder(registro.registrarManual(solicitud, usuarioId, "EGRESO_MANUAL", EGRESO));
    }

    @Override
    public MovimientoCajaResponse obtener(Long id) {
        return movimientos.findDetalleById(id).map(MovimientoCajaServiceImpl::responder)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró el movimiento con ID " + id + "."));
    }

    @Override
    public Page<MovimientoCajaResponse> listar(Long cajaId, String tipo, Long metodoPagoId, LocalDate desde,
                                              LocalDate hasta, Pageable pageable) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new SolicitudInvalidaException("Desde debe ser anterior o igual a hasta.");
        }
        if (!cajas.existsById(cajaId)) {
            throw new RecursoNoEncontradoException("No se encontró la caja con ID " + cajaId + ".");
        }
        Specification<MovimientoCaja> filtro = (raiz, consulta, cb) -> cb.equal(raiz.get("caja").get("id"), cajaId);
        if (tipo != null) {
            filtro = filtro.and((raiz, consulta, cb) -> cb.equal(raiz.get("tipoMovimiento").get("codigo"), tipo));
        }
        if (metodoPagoId != null) {
            filtro = filtro.and((raiz, consulta, cb) -> cb.equal(raiz.get("metodoPago").get("id"), metodoPagoId));
        }
        if (desde != null) {
            filtro = filtro.and((raiz, consulta, cb) ->
                    cb.greaterThanOrEqualTo(raiz.get("fechaHora"), desde.atStartOfDay()));
        }
        if (hasta != null) {
            filtro = filtro.and((raiz, consulta, cb) ->
                    cb.lessThan(raiz.get("fechaHora"), hasta.plusDays(1).atStartOfDay()));
        }
        var paginacion = pageable.isPaged()
                ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), ORDEN)
                : Pageable.unpaged(ORDEN);
        return movimientos.findAll(filtro, paginacion).map(MovimientoCajaServiceImpl::responder);
    }

    @Override
    public SaldoCajaResponse consultarSaldo(Long cajaId) {
        return calculador.calcular(cajaId);
    }

    private static MovimientoCajaResponse responder(MovimientoCaja movimiento) {
        var tipo = movimiento.getTipoMovimiento();
        var metodo = movimiento.getMetodoPago();
        var usuario = movimiento.getUsuario();
        return new MovimientoCajaResponse(movimiento.getId(), movimiento.getCaja().getId(),
                tipo.getCodigo(), tipo.getNombre(), metodo.getCodigo(), metodo.getNombre(), usuario.getId(),
                usuario.getNombre(), movimiento.getMonto(), movimiento.getDescripcion(), movimiento.getFechaHora());
    }
}
