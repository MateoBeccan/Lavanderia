package com.miraquetelavo.lavanderia.proveedor.repository;

import com.miraquetelavo.lavanderia.proveedor.entity.Proveedor;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface ProveedorRepository extends JpaRepository<Proveedor, Long>, JpaSpecificationExecutor<Proveedor> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Proveedor> findOneById(Long id);

    Optional<Proveedor> findByIdAndActivoTrue(Long id);

    Optional<Proveedor> findByCuit(String cuit);
}
