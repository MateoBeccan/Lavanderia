package com.miraquetelavo.lavanderia.servicio.repository;

import com.miraquetelavo.lavanderia.servicio.entity.Servicio;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ServicioRepository extends JpaRepository<Servicio, Long> {

    Page<Servicio> findByActivoTrue(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Servicio> findOneById(Long id);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select e from Servicio e where e.id in :ids order by e.id")
    List<Servicio> bloquearPorIds(@Param("ids") List<Long> ids);

    @Query("""
            select count(s) > 0 from Servicio s
            where s.nombreNormalizado = lower(:nombre)
            """)
    boolean existeNombre(@Param("nombre") String nombre);

    @Query("""
            select count(s) > 0 from Servicio s
            where s.nombreNormalizado = lower(:nombre) and s.id <> :id
            """)
    boolean existeNombreEnOtroServicio(@Param("nombre") String nombre, @Param("id") Long id);
}
