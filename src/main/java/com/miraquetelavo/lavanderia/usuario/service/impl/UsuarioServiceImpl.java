package com.miraquetelavo.lavanderia.usuario.service.impl;

import com.miraquetelavo.lavanderia.common.exception.ConflictoException;
import com.miraquetelavo.lavanderia.common.exception.RecursoNoEncontradoException;
import com.miraquetelavo.lavanderia.rol.entity.CodigoRol;
import com.miraquetelavo.lavanderia.rol.entity.Rol;
import com.miraquetelavo.lavanderia.rol.service.RolService;
import com.miraquetelavo.lavanderia.usuario.dto.*;
import com.miraquetelavo.lavanderia.usuario.entity.Usuario;
import com.miraquetelavo.lavanderia.usuario.repository.UsuarioRepository;
import com.miraquetelavo.lavanderia.usuario.service.UsuarioService;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.function.Supplier;

import static com.miraquetelavo.lavanderia.common.exception.RestriccionesPostgresql.esViolacionUnica;
import static com.miraquetelavo.lavanderia.rol.entity.CodigoRol.ADMINISTRADOR;

@Service
@Validated
@Transactional(readOnly = true)
public class UsuarioServiceImpl implements UsuarioService {

    private final UsuarioRepository repository;
    private final RolService roles;
    private final PasswordEncoder encoder;
    private final Validator validator;

    public UsuarioServiceImpl(UsuarioRepository repository, RolService roles, PasswordEncoder encoder,
                              Validator validator) {
        this.repository = repository;
        this.roles = roles;
        this.encoder = encoder;
        this.validator = validator;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public UsuarioResponse crear(Long actor, CrearUsuarioRequest solicitud) {
        verificarAdministrador(actor);
        return crearUsuario(solicitud, roles.obtener(solicitud.rol()));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void inicializarAdministrador(Supplier<CrearUsuarioRequest> credenciales) {
        Rol administrador = roles.bloquearAdministracion();
        if (repository.count() == 0) {
            var solicitud = credenciales.get();
            var errores = validator.validate(solicitud);
            if (!errores.isEmpty()) {
                throw new ConstraintViolationException("Configuración inválida del administrador inicial.", errores);
            }
            crearUsuario(solicitud, administrador);
        }
    }

    @Override
    public UsuarioResponse obtener(Long id) {
        return responder(buscar(id));
    }

    @Override
    public Page<UsuarioResponse> listar(Pageable pagina) {
        return repository.findAll(pagina).map(UsuarioServiceImpl::responder);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public UsuarioResponse actualizar(Long actor, Long id, ActualizarUsuarioRequest solicitud) {
        verificarAdministrador(actor);
        Usuario usuario = buscar(id);
        if (solicitud.rol() != ADMINISTRADOR) {
            protegerAdministrador(actor, usuario, "quitarse el rol de administrador");
        }
        usuario.actualizar(solicitud.username(), solicitud.nombre(), solicitud.email(), roles.obtener(solicitud.rol()));
        return guardar(usuario);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void desactivar(Long actor, Long id) {
        verificarAdministrador(actor);
        Usuario usuario = buscar(id);
        protegerAdministrador(actor, usuario, "desactivar su propia cuenta");
        usuario.desactivar();
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public UsuarioResponse activar(Long actor, Long id) {
        verificarAdministrador(actor);
        Usuario usuario = buscar(id);
        usuario.activar();
        return responder(usuario);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void cambiarPassword(Long actor, Long id, CambiarPasswordRequest solicitud) {
        verificarAdministrador(actor);
        buscar(id).cambiarPassword(encoder.encode(solicitud.password()));
    }

    private void verificarAdministrador(Long actor) {
        roles.bloquearAdministracion();
        var administrador = repository.findByIdAndActivoTrue(actor);
        if (administrador.isEmpty() || administrador.get().getRol().getNombre() != ADMINISTRADOR) {
            throw new AccessDeniedException("Se requiere una cuenta administradora activa.");
        }
    }

    private void protegerAdministrador(Long actor, Usuario usuario, String accion) {
        if (usuario.isActivo() && usuario.getRol().getNombre() == ADMINISTRADOR
                && repository.countByRolNombreAndActivoTrue(ADMINISTRADOR) <= 1) {
            throw new ConflictoException("Debe quedar al menos un administrador activo.", null);
        }
        if (usuario.getId().equals(actor)) {
            throw new ConflictoException("Un administrador no puede " + accion + ".", null);
        }
    }

    private UsuarioResponse crearUsuario(CrearUsuarioRequest solicitud, Rol rol) {
        return guardar(new Usuario(solicitud.username(), encoder.encode(solicitud.password()),
                solicitud.nombre(), solicitud.email(), rol));
    }

    private Usuario buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró el usuario con ID " + id + "."));
    }

    private UsuarioResponse guardar(Usuario usuario) {
        try {
            return responder(repository.saveAndFlush(usuario));
        } catch (DataIntegrityViolationException ex) {
            if (esViolacionUnica(ex, Usuario.UK_USERNAME)) {
                throw new ConflictoException("El username ya está reservado.", ex);
            }
            if (esViolacionUnica(ex, Usuario.UK_EMAIL)) {
                throw new ConflictoException("El email ya está reservado.", ex);
            }
            throw ex;
        }
    }

    private static UsuarioResponse responder(Usuario usuario) {
        return new UsuarioResponse(usuario.getId(), usuario.getUsername(), usuario.getNombre(), usuario.getEmail(),
                usuario.getRol().getNombre(), usuario.isActivo(), usuario.getUltimoAcceso());
    }
}
