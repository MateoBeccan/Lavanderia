package com.miraquetelavo.lavanderia.insumo.repository;

import com.miraquetelavo.lavanderia.insumo.entity.Insumo;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface InsumoRepository extends JpaRepository<Insumo, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Insumo> findOneById(Long id);

    @EntityGraph(attributePaths = "categoria")
    Optional<Insumo> findByIdAndActivoTrue(Long id);

    @EntityGraph(attributePaths = "categoria")
    Page<Insumo> findByActivoTrue(Pageable pageable);

    @EntityGraph(attributePaths = "categoria")
    Page<Insumo> findByCategoriaIdAndActivoTrue(Long categoriaId, Pageable pageable);

    boolean existsByCategoriaIdAndActivoTrue(Long categoriaId);
}
