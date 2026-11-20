package com.miraquetelavo.lavanderia.caja.service.impl;

import com.miraquetelavo.lavanderia.caja.dto.*;
import com.miraquetelavo.lavanderia.caja.entity.Caja;
import com.miraquetelavo.lavanderia.caja.entity.EstadoCaja;
import com.miraquetelavo.lavanderia.caja.repository.CajaRepository;
import com.miraquetelavo.lavanderia.caja.service.CajaService;
import com.miraquetelavo.lavanderia.common.exception.ConflictoException;
import com.miraquetelavo.lavanderia.common.exception.RecursoNoEncontradoException;
import com.miraquetelavo.lavanderia.common.exception.SolicitudInvalidaException;
import com.miraquetelavo.lavanderia.movimientocaja.service.CalculadorSaldoCaja;
import com.miraquetelavo.lavanderia.rol.entity.CodigoRol;
import com.miraquetelavo.lavanderia.usuario.entity.Usuario;
import com.miraquetelavo.lavanderia.usuario.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CajaServiceImpl implements CajaService {

    private static final Sort ORDEN = Sort.by(Sort.Direction.DESC, "fechaApertura", "id");

    private final CajaRepository cajas;
    private final UsuarioRepository usuarios;
    private final Clock relojPedidos;
    private final CalculadorSaldoCaja calculador;

    @Override
    @Transactional
    public CajaResponse abrirCaja(AbrirCajaRequest solicitud, Long usuarioId) {
        var usuario = usuarioActivo(usuarioId);
        if (cajas.insertarAbierta(usuario.getId(), ahora(), solicitud.montoInicial()) == 0) {
            throw new ConflictoException("Ya existe una caja abierta.", null);
        }
        return obtenerCajaAbierta();
    }

    @Override
    public CajaResponse obtenerCajaAbierta() {
        return cajas.findByEstado(EstadoCaja.ABIERTA).map(CajaServiceImpl::responder)
                .orElseThrow(() -> new RecursoNoEncontradoException("No hay una caja abierta."));
    }

    @Override
    public CajaResponse obtenerPorId(Long id) {
        return cajas.findDetalleById(id).map(CajaServiceImpl::responder)
                .orElseThrow(() -> noEncontrada(id));
    }

    @Override
    public Page<CajaResumenResponse> listarCajas(EstadoCaja estado, LocalDate desde, LocalDate hasta, Pageable pageable) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new SolicitudInvalidaException("Desde debe ser anterior o igual a hasta.");
        }
        Specification<Caja> filtro = Specification.unrestricted();
        if (estado != null) {
            filtro = filtro.and((raiz, consulta, cb) -> cb.equal(raiz.get("estado"), estado));
        }
        if (desde != null) {
            filtro = filtro.and((raiz, consulta, cb) ->
                    cb.greaterThanOrEqualTo(raiz.get("fechaApertura"), desde.atStartOfDay()));
        }
        if (hasta != null) {
            filtro = filtro.and((raiz, consulta, cb) ->
                    cb.lessThan(raiz.get("fechaApertura"), hasta.plusDays(1).atStartOfDay()));
        }

        var paginacion = pageable.isPaged()
                ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), ORDEN)
                : Pageable.unpaged(ORDEN);
        return cajas.findAll(filtro, paginacion).map(caja -> new CajaResumenResponse(caja.getId(),
                caja.getFechaApertura(), caja.getFechaCierre(), caja.getEstado(), caja.getMontoInicial(),
                caja.getMontoFinalCalculado(), caja.getDiferenciaArqueo()));
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CajaResponse cerrarCaja(Long id, CerrarCajaRequest solicitud, Long usuarioId) {
        var administrador = usuarioActivo(usuarioId);
        if (administrador.getRol().getNombre() != CodigoRol.ADMINISTRADOR) {
            throw new AccessDeniedException("El cierre de caja requiere un administrador.");
        }
        var caja = cajas.bloquearPorId(id).orElseThrow(() -> noEncontrada(id));
        if (caja.getEstado() != EstadoCaja.ABIERTA) {
            throw new ConflictoException("La caja ya está cerrada.", null);
        }
        var saldo = calculador.calcular(id);
        calculador.validarCierre(saldo);
        caja.cerrar(administrador, ahora(), solicitud.efectivoContado().setScale(2),
                saldo.saldoGeneral(), saldo.efectivoDisponible());
        return responder(caja);
    }

    private Usuario usuarioActivo(Long id) {
        return usuarios.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new BadCredentialsException("El usuario autenticado no está activo."));
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(relojPedidos).truncatedTo(ChronoUnit.MICROS);
    }

    private static RecursoNoEncontradoException noEncontrada(Long id) {
        return new RecursoNoEncontradoException("No se encontró la caja con ID " + id + ".");
    }

    private static CajaResponse responder(Caja caja) {
        var apertura = caja.getUsuarioApertura();
        var cierre = caja.getUsuarioCierre();
        return new CajaResponse(caja.getId(), apertura.getId(), apertura.getNombre(),
                cierre == null ? null : cierre.getId(), cierre == null ? null : cierre.getNombre(),
                caja.getFechaApertura(), caja.getFechaCierre(), caja.getMontoInicial(),
                caja.getMontoFinalCalculado(), caja.getEfectivoEsperado(), caja.getEfectivoContado(),
                caja.getDiferenciaArqueo(), caja.getEstado());
    }
}
