package com.miraquetelavo.lavanderia.cliente.repository;

import com.miraquetelavo.lavanderia.cliente.entity.Cliente;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    Page<Cliente> findByActivoTrue(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Cliente> findOneById(Long id);
}
