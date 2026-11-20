package com.miraquetelavo.lavanderia.compra.repository;

import com.miraquetelavo.lavanderia.compra.entity.Compra;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface CompraRepository extends JpaRepository<Compra, Long>, JpaSpecificationExecutor<Compra> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Compra> findOneById(Long id);

    @EntityGraph(attributePaths = {"proveedor", "usuario", "estadoPago", "detalles.insumo"})
    Optional<Compra> findCompletaById(Long id);

    @Override
    @EntityGraph(attributePaths = {"proveedor", "estadoPago"})
    Page<Compra> findAll(Specification<Compra> filtro, Pageable pageable);
}
