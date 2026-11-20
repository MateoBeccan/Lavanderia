package com.miraquetelavo.lavanderia.historialestadopedido.repository;

import com.miraquetelavo.lavanderia.historialestadopedido.entity.HistorialEstadoPedido;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.Repository;

import java.util.List;

public interface HistorialEstadoPedidoRepository extends Repository<HistorialEstadoPedido, Long> {
    HistorialEstadoPedido save(HistorialEstadoPedido entrada);

    @EntityGraph(attributePaths = {"estadoAnterior", "estadoNuevo", "usuario"})
    List<HistorialEstadoPedido> findByPedido_IdOrderByFechaCambioAscIdAsc(Long pedidoId);
}
