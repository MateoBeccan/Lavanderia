package com.miraquetelavo.lavanderia.prenda.repository;

import com.miraquetelavo.lavanderia.prenda.entity.Prenda;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PrendaRepository extends JpaRepository<Prenda, Long> {

    Page<Prenda> findByActivoTrue(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Prenda> findOneById(Long id);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select e from Prenda e where e.id in :ids order by e.id")
    List<Prenda> bloquearPorIds(@Param("ids") List<Long> ids);

    @Query("""
            select count(p) > 0 from Prenda p
            where p.nombreNormalizado = lower(:nombre)
            """)
    boolean existeNombre(@Param("nombre") String nombre);

    @Query("""
            select count(p) > 0 from Prenda p
            where p.nombreNormalizado = lower(:nombre) and p.id <> :id
            """)
    boolean existeNombreEnOtraPrenda(@Param("nombre") String nombre, @Param("id") Long id);
}
