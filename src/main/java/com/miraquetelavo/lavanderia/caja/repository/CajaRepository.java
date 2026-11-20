package com.miraquetelavo.lavanderia.caja.repository;

import com.miraquetelavo.lavanderia.caja.entity.Caja;
import com.miraquetelavo.lavanderia.caja.entity.EstadoCaja;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

public interface CajaRepository extends JpaRepository<Caja, Long>, JpaSpecificationExecutor<Caja> {

    @EntityGraph(attributePaths = {"usuarioApertura", "usuarioCierre"})
    Optional<Caja> findByEstado(EstadoCaja estado);

    boolean existsByEstado(EstadoCaja estado);

    @EntityGraph(attributePaths = {"usuarioApertura", "usuarioCierre"})
    Optional<Caja> findDetalleById(Long id);

    @Query("select caja.id from Caja caja where caja.estado = :estado")
    Optional<Long> buscarIdPorEstado(EstadoCaja estado);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select caja from Caja caja where caja.id = :id")
    Optional<Caja> bloquearPorId(Long id);

    // PostgreSQL exige un índice único compatible para ejecutar ON CONFLICT.
    @Modifying
    @Query(value = """
            insert into {h-schema}cajas (id_usuario_apertura, fecha_apertura, monto_inicial, estado)
            values (:usuarioId, :fecha, :monto, 'ABIERTA')
            on conflict (estado) where estado = 'ABIERTA' do nothing
            """, nativeQuery = true)
    int insertarAbierta(Long usuarioId, LocalDateTime fecha, BigDecimal monto);
}
