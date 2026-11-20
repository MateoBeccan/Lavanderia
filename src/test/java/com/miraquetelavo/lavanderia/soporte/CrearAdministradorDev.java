package com.miraquetelavo.lavanderia.soporte;

import com.miraquetelavo.lavanderia.LavanderiaApplication;
import com.miraquetelavo.lavanderia.auth.dto.LoginRequest;
import com.miraquetelavo.lavanderia.auth.dto.LoginResponse;
import com.miraquetelavo.lavanderia.auth.dto.UsuarioAutenticadoResponse;
import com.miraquetelavo.lavanderia.usuario.dto.CrearUsuarioRequest;
import com.miraquetelavo.lavanderia.usuario.service.impl.UsuarioServiceImpl;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Arrays;
import java.util.regex.Pattern;

import static com.miraquetelavo.lavanderia.rol.entity.CodigoRol.ADMINISTRADOR;

/** Comando local de una sola ejecución, fuera del artefacto operativo. */
public final class CrearAdministradorDev {
    private static final String USUARIO = "admin";
    private static final String EMAIL = "admin.dev@example.com";
    private static final Pattern URL_DEV = Pattern.compile(
            "jdbc:postgresql://(?:127\\.0\\.0\\.1|localhost):5432/lavanderia_dev"
                    + "(?:\\?(?:currentSchema=public|ApplicationName=[a-zA-Z0-9_-]+)"
                    + "(?:&(?:currentSchema=public|ApplicationName=[a-zA-Z0-9_-]+))?)?");

    public static void main(String[] args) {
        if (args.length != 0) {
            throw new IllegalArgumentException("La contraseña se recibe por variable de entorno.");
        }
        var password = System.getenv("LAVANDERIA_BOOTSTRAP_PASSWORD");
        if (password == null || password.isBlank() || password.length() < 12 || password.length() > 128) {
            throw new IllegalStateException("Cargá LAVANDERIA_BOOTSTRAP_PASSWORD con una contraseña de 12 a 128 caracteres.");
        }
        System.setProperty("spring.devtools.restart.enabled", "false");
        try (var contexto = new SpringApplicationBuilder(LavanderiaApplication.class)
                .profiles("dev")
                .initializers(app -> verificarConfiguracion(app.getEnvironment()))
                .run("--server.address=127.0.0.1", "--server.port=0", "--spring.jpa.hibernate.ddl-auto=none",
                        "--spring.jpa.properties.hibernate.hbm2ddl.auto=none",
                        "--spring.jpa.properties.jakarta.persistence.schema-generation.database.action=none",
                        "--spring.flyway.enabled=false", "--spring.sql.init.mode=never")) {
            var jdbc = contexto.getBean(JdbcTemplate.class);
            var destinoCorrecto = jdbc.queryForObject("""
                    select current_database() = 'lavanderia_dev'
                       and session_user = 'lavanderia_dev_app' and current_user = session_user
                       and inet_server_addr() in (inet '127.0.0.1', inet '::1') and inet_server_port() = 5432
                       and current_schema() = 'public'
                    """, Boolean.class);
            if (!Boolean.TRUE.equals(destinoCorrecto)) {
                throw new IllegalStateException("El datasource efectivo no es el login de aplicación en lavanderia_dev.");
            }
            contexto.getBean(UsuarioServiceImpl.class).inicializarAdministrador(
                    () -> new CrearUsuarioRequest(USUARIO, password, "Administrador de desarrollo", EMAIL, ADMINISTRADOR));
            var cuentaCorrecta = jdbc.queryForObject("""
                    select exists (
                        select from public.usuarios u join public.roles r on r.id_rol = u.id_rol
                        where u.username_normalizado = ? and u.email_normalizado = ?
                          and u.activo and r.nombre_rol = 'ADMINISTRADOR'
                    )
                    """, Boolean.class, USUARIO, EMAIL);
            if (!Boolean.TRUE.equals(cuentaCorrecta)) {
                throw new IllegalStateException("El alta inicial requiere una tabla de usuarios vacía. Revisá las cuentas existentes.");
            }
            verificarLogin(((WebServerApplicationContext) contexto).getWebServer().getPort(), password);
        }
    }

    private static void verificarConfiguracion(Environment entorno) {
        var url = entorno.getProperty("spring.datasource.url", "");
        if (!Arrays.equals(entorno.getActiveProfiles(), new String[]{"dev"}) || !URL_DEV.matcher(url).matches()
                || !"lavanderia_dev_app".equals(entorno.getProperty("spring.datasource.username"))
                || entorno.containsProperty("spring.datasource.hikari.jdbc-url")
                || entorno.containsProperty("spring.datasource.hikari.username")
                || entorno.containsProperty("spring.datasource.hikari.data-source-class-name")
                || entorno.containsProperty("spring.datasource.jndi-name")) {
            throw new IllegalStateException("Se requiere el perfil dev y el datasource directo de lavanderia_dev en loopback:5432.");
        }
    }

    private static void verificarLogin(int puerto, String password) {
        var api = RestClient.create("http://127.0.0.1:" + puerto + "/api/auth");
        try {
            var login = api.post().uri("/login").body(new LoginRequest(USUARIO, password))
                    .retrieve().toEntity(LoginResponse.class);
            if (login.getStatusCode().value() != 200 || login.getBody() == null
                    || login.getBody().accessToken() == null || login.getBody().accessToken().isBlank()) {
                throw new IllegalStateException("POST /api/auth/login no devolvió un token válido.");
            }
            var me = api.get().uri("/me").headers(headers -> headers.setBearerAuth(login.getBody().accessToken()))
                    .retrieve().toEntity(UsuarioAutenticadoResponse.class);
            var perfil = me.getBody();
            if (me.getStatusCode().value() != 200 || perfil == null || !USUARIO.equals(perfil.username())
                    || !EMAIL.equals(perfil.email()) || perfil.rol() != ADMINISTRADOR) {
                throw new IllegalStateException("GET /api/auth/me no confirmó la identidad administrativa de dev.");
            }
            System.out.println("POST /api/auth/login 200 y GET /api/auth/me 200 para admin (ADMINISTRADOR).");
        } catch (RestClientResponseException ex) {
            throw new IllegalStateException("Falló la verificación HTTP de la cuenta inicial: " + ex.getStatusCode().value());
        }
    }
}
