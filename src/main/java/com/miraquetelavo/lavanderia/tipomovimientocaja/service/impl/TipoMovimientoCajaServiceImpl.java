package com.miraquetelavo.lavanderia.tipomovimientocaja.service.impl;

import com.miraquetelavo.lavanderia.common.exception.RecursoNoEncontradoException;
import com.miraquetelavo.lavanderia.tipomovimientocaja.dto.TipoMovimientoCajaResponse;
import com.miraquetelavo.lavanderia.tipomovimientocaja.entity.TipoMovimientoCaja;
import com.miraquetelavo.lavanderia.tipomovimientocaja.repository.TipoMovimientoCajaRepository;
import com.miraquetelavo.lavanderia.tipomovimientocaja.service.TipoMovimientoCajaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TipoMovimientoCajaServiceImpl implements TipoMovimientoCajaService {

    private final TipoMovimientoCajaRepository tipos;

    @Override
    public List<TipoMovimientoCajaResponse> listarActivos() {
        return tipos.findByActivoTrueOrderByOrdenAscIdAsc().stream()
                .map(TipoMovimientoCajaServiceImpl::responder).toList();
    }

    @Override
    public TipoMovimientoCajaResponse obtenerPorId(Long id) {
        return tipos.findByIdAndActivoTrue(id).map(TipoMovimientoCajaServiceImpl::responder)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontró el tipo de movimiento de caja con ID " + id + "."));
    }

    @Override
    public TipoMovimientoCaja buscarPorCodigo(String codigo) {
        return tipos.findByCodigo(codigo).orElseThrow(() -> new RecursoNoEncontradoException(
                "No se encontró el tipo de movimiento de caja con código " + codigo + "."));
    }

    private static TipoMovimientoCajaResponse responder(TipoMovimientoCaja tipo) {
        return new TipoMovimientoCajaResponse(tipo.getId(), tipo.getCodigo(), tipo.getNombre(),
                tipo.getDescripcion(), tipo.getOrden(), tipo.getActivo());
    }
}
