package com.miraquetelavo.lavanderia.usuario.service;

import com.miraquetelavo.lavanderia.rol.service.RolService;
import com.miraquetelavo.lavanderia.usuario.dto.CrearUsuarioRequest;
import com.miraquetelavo.lavanderia.usuario.service.impl.UsuarioServiceImpl;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import static com.miraquetelavo.lavanderia.rol.entity.CodigoRol.ADMINISTRADOR;

@Component
@Profile("test-postgresql")
public class InicializadorUsuarios implements ApplicationRunner {

    private final RolService roles;
    private final UsuarioServiceImpl usuarios;
    private final Environment env;

    public InicializadorUsuarios(RolService roles, UsuarioServiceImpl usuarios, Environment env) {
        this.roles = roles;
        this.usuarios = usuarios;
        this.env = env;
    }

    @Override
    public void run(ApplicationArguments args) {
        roles.inicializar();
        if (env.getProperty("lavanderia.bootstrap.enabled", Boolean.class, false)) {
            usuarios.inicializarAdministrador(() -> new CrearUsuarioRequest(
                    env.getProperty("LAVANDERIA_BOOTSTRAP_USERNAME"),
                    env.getProperty("LAVANDERIA_BOOTSTRAP_PASSWORD"),
                    env.getProperty("LAVANDERIA_BOOTSTRAP_NOMBRE"),
                    env.getProperty("LAVANDERIA_BOOTSTRAP_EMAIL"), ADMINISTRADOR));
        }
    }
}
