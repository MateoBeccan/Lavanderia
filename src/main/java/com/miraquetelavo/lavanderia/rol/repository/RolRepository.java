package com.miraquetelavo.lavanderia.rol.repository;

import com.miraquetelavo.lavanderia.rol.entity.CodigoRol;
import com.miraquetelavo.lavanderia.rol.entity.Rol;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface RolRepository extends JpaRepository<Rol, Long> {

    Optional<Rol> findByNombre(CodigoRol nombre);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Rol> findOneByNombre(CodigoRol nombre);

    @Modifying
    @Query(value = """
            insert into {h-schema}roles (nombre_rol, descripcion)
            values ('ADMINISTRADOR', 'Acceso a administración y gestión integral'),
                   ('EMPLEADO', 'Acceso a operaciones diarias autorizadas')
            on conflict (nombre_rol) do nothing
            """, nativeQuery = true)
    void inicializar();
}
