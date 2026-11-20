package com.miraquetelavo.lavanderia.categoriainsumo.repository;

import com.miraquetelavo.lavanderia.categoriainsumo.entity.CategoriaInsumo;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CategoriaInsumoRepository extends JpaRepository<CategoriaInsumo, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CategoriaInsumo> findOneById(Long id);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select c from CategoriaInsumo c where c.id = :id")
    Optional<CategoriaInsumo> bloquearParaInsumo(Long id);

    Optional<CategoriaInsumo> findByIdAndActivoTrue(Long id);

    List<CategoriaInsumo> findByActivoTrueOrderByIdAsc();
}
