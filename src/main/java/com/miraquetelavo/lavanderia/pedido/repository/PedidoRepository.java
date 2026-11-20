package com.miraquetelavo.lavanderia.pedido.repository;

import com.miraquetelavo.lavanderia.pedido.entity.Pedido;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface PedidoRepository extends JpaRepository<Pedido, Long>, JpaSpecificationExecutor<Pedido> {
    @EntityGraph(attributePaths = {"cliente", "usuario", "estadoPedido", "detalles", "detalles.prenda", "detalles.servicio"})
    Optional<Pedido> findCompletoById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Pedido> findOneById(Long id);

    @Override
    @EntityGraph(attributePaths = {"cliente", "estadoPedido"})
    Page<Pedido> findAll(Specification<Pedido> filtro, Pageable pageable);
}
