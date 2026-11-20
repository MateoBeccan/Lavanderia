package com.miraquetelavo.lavanderia.historialestadopedido.service;

import com.miraquetelavo.lavanderia.estadopedido.entity.EstadoPedido;
import com.miraquetelavo.lavanderia.historialestadopedido.dto.HistorialEstadoPedidoResponse;
import com.miraquetelavo.lavanderia.historialestadopedido.entity.HistorialEstadoPedido;
import com.miraquetelavo.lavanderia.pedido.entity.Pedido;
import com.miraquetelavo.lavanderia.pedido.repository.PedidoRepository;
import com.miraquetelavo.lavanderia.common.exception.RecursoNoEncontradoException;
import com.miraquetelavo.lavanderia.historialestadopedido.repository.HistorialEstadoPedidoRepository;
import com.miraquetelavo.lavanderia.usuario.entity.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HistorialEstadoPedidoService {
    private final HistorialEstadoPedidoRepository historial;
    private final PedidoRepository pedidos;

    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(Pedido pedido, EstadoPedido anterior, Usuario usuario,
                          LocalDateTime fechaCambio, String observacion) {
        historial.save(new HistorialEstadoPedido(pedido, anterior, pedido.getEstadoPedido(), usuario, fechaCambio, observacion));
    }

    @Transactional(readOnly = true)
    public List<HistorialEstadoPedidoResponse> listar(Long pedidoId) {
        if (!pedidos.existsById(pedidoId)) {
            throw new RecursoNoEncontradoException("No se encontró el pedido " + pedidoId + ".");
        }
        return historial.findByPedido_IdOrderByFechaCambioAscIdAsc(pedidoId).stream()
                .map(HistorialEstadoPedidoService::respuesta)
                .toList();
    }

    private static HistorialEstadoPedidoResponse respuesta(HistorialEstadoPedido entrada) {
        var anterior = entrada.getEstadoAnterior();
        var nuevo = entrada.getEstadoNuevo();
        var usuario = entrada.getUsuario();
        return new HistorialEstadoPedidoResponse(entrada.getId(), anterior == null ? null : anterior.getCodigo(),
                anterior == null ? null : anterior.getNombre(), nuevo.getCodigo(), nuevo.getNombre(),
                usuario.getId(), usuario.getNombre(), entrada.getFechaCambio(), entrada.getObservacion());
    }
}
