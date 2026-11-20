package com.miraquetelavo.lavanderia.usuario.repository;

import com.miraquetelavo.lavanderia.rol.entity.CodigoRol;
import com.miraquetelavo.lavanderia.usuario.entity.Usuario;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    @EntityGraph(attributePaths = "rol")
    Optional<Usuario> findByUsernameNormalizado(String username);

    @EntityGraph(attributePaths = "rol")
    Optional<Usuario> findByIdAndActivoTrue(Long id);

    long countByRolNombreAndActivoTrue(CodigoRol rol);

    @Modifying
    @Query("""
            update Usuario u set u.ultimoAcceso = :fecha
            where u.id = :id and u.activo = true and u.versionCredenciales = :version
            """)
    int registrarAcceso(Long id, UUID version, LocalDateTime fecha);
}
