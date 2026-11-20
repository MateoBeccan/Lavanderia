package com.miraquetelavo.lavanderia.usuario;

import com.miraquetelavo.lavanderia.soporte.IntegracionPostgresql;
import com.miraquetelavo.lavanderia.common.exception.ConflictoException;
import com.miraquetelavo.lavanderia.rol.repository.RolRepository;
import com.miraquetelavo.lavanderia.rol.service.RolService;
import com.miraquetelavo.lavanderia.usuario.dto.CrearUsuarioRequest;
import com.miraquetelavo.lavanderia.usuario.repository.UsuarioRepository;
import com.miraquetelavo.lavanderia.usuario.service.UsuarioService;
import com.miraquetelavo.lavanderia.usuario.service.impl.UsuarioServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.miraquetelavo.lavanderia.rol.entity.CodigoRol.ADMINISTRADOR;
import static com.miraquetelavo.lavanderia.rol.entity.CodigoRol.EMPLEADO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "lavanderia.jwt.secret=dGVzdC1vbmx5LXZlcmlmaWNhY2lvbi1qd3QtbGF2YW5kZXJpYQ==",
        "lavanderia.bootstrap.enabled=false",
        "lavanderia.cors.origins=http://localhost:5173"
})
@AutoConfigureMockMvc
@IntegracionPostgresql("usuarios")
class UsuarioPostgresqlTest {

    private static final String PASSWORD = "contraseña de prueba";
    private static final String USUARIOS = "/api/usuarios";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate sql;
    @Autowired private RolService roles;
    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioService usuarios;
    @Autowired private UsuarioServiceImpl bootstrap;
    @Autowired private UsuarioRepository repository;
    @Autowired private PasswordEncoder encoder;

    @Test
    void inicializaRolesYAdministradorUnaSolaVezInclusoEnParalelo() throws Exception {
        roles.inicializar();
        roles.inicializar();
        assertThat(rolRepository.findAll()).extracting(rol -> rol.getNombre())
                .containsExactlyInAnyOrder(ADMINISTRADOR, EMPLEADO);
        assertThat(rolRepository.findByNombre(ADMINISTRADOR)).isPresent();
        assertThatThrownBy(() -> sql.update("insert into roles(nombre_rol) values ('ADMINISTRADOR')"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> sql.update("insert into roles(nombre_rol) values ('OTRO')"))
                .isInstanceOf(DataIntegrityViolationException.class);

        var crear = new CrearUsuarioRequest("admin.inicial", PASSWORD, "Administración", "admin@example.test",
                ADMINISTRADOR);
        paralelo(() -> { bootstrap.inicializarAdministrador(() -> crear); return true; },
                () -> { bootstrap.inicializarAdministrador(() -> crear); return true; });
        assertThat(repository.count()).isEqualTo(1);
        bootstrap.inicializarAdministrador(() -> { throw new AssertionError("Ya existe un usuario"); });
        var administrador = repository.findByUsernameNormalizado("admin.inicial").orElseThrow();
        assertThat(administrador.getPassword()).isNotEqualTo(PASSWORD);
        assertThat(encoder.matches(PASSWORD, administrador.getPassword())).isTrue();

        String admin = token("ADMIN.Inicial", PASSWORD);
        mvc.perform(get("/api/auth/me").header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rol").value("ADMINISTRADOR"));
        assertThat(repository.findById(administrador.getId()).orElseThrow().getUltimoAcceso()).isNotNull();
        mvc.perform(delete(USUARIOS + "/" + administrador.getId()).header("Authorization", admin))
                .andExpect(status().isConflict());
        mvc.perform(put(USUARIOS + "/" + administrador.getId()).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(datos("admin.inicial", "admin@example.test", EMPLEADO.name())))
                .andExpect(status().isConflict());

        verificarCuentasYPermisos(admin);
        verificarConcurrencia(administrador.getId());
    }

    private void verificarCuentasYPermisos(String admin) throws Exception {
        alta(admin, "a b", "persona@example.test").andExpect(status().isBadRequest());
        alta(admin, "persona", "incorrecto").andExpect(status().isBadRequest());
        var cuerpo = alta(admin, "Persona", " Persona@Example.test ").andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("persona"))
                .andExpect(jsonPath("$.email").value("persona@example.test"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(cuerpo).path("id").asLong();
        var ruta = USUARIOS + "/" + id;
        assertThatThrownBy(() -> sql.update("update usuarios set username = 'ADMIN.INICIAL' where id_usuario = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> sql.update("update usuarios set email = 'ADMIN@EXAMPLE.TEST' where id_usuario = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);

        mvc.perform(get(USUARIOS).param("size", "1").header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page.size").value(1))
                .andExpect(jsonPath("$.page.totalElements").value(2));
        mvc.perform(get(USUARIOS + "/999999").header("Authorization", admin)).andExpect(status().isNotFound());
        mvc.perform(get(USUARIOS)).andExpect(status().isUnauthorized());
        mvc.perform(get(USUARIOS).header("Authorization", "Bearer invalido")).andExpect(status().isUnauthorized());

        String empleado = token("persona", PASSWORD);
        login("persona", "credencial incorrecta").andExpect(status().isUnauthorized());
        login("cuenta inexistente", PASSWORD).andExpect(status().isUnauthorized());
        mvc.perform(get(USUARIOS).header("Authorization", empleado)).andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/me").header("Authorization", empleado))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        for (String rutaCatalogo : List.of("/api/clientes", "/api/servicios", "/api/prendas", "/api/estados-pedido")) {
            mvc.perform(get(rutaCatalogo).header("Authorization", empleado)).andExpect(status().isOk());
        }
        mvc.perform(post("/api/clientes").header("Authorization", empleado).contentType(MediaType.APPLICATION_JSON)
                        .content("{}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/clientes/1").header("Authorization", empleado).contentType(MediaType.APPLICATION_JSON)
                        .content("{}")).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/clientes/1").header("Authorization", empleado)).andExpect(status().isForbidden());
        for (String catalogo : List.of("/api/servicios", "/api/prendas", "/api/estados-pedido")) {
            mvc.perform(post(catalogo).header("Authorization", empleado)).andExpect(status().isForbidden());
        }
        mvc.perform(options(USUARIOS).header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mvc.perform(options(USUARIOS).header("Origin", "https://otro.example")
                        .header("Access-Control-Request-Method", "GET")).andExpect(status().isForbidden());

        mvc.perform(delete(ruta).header("Authorization", admin)).andExpect(status().isNoContent());
        assertThat(repository.findById(id).orElseThrow().isActivo()).isFalse();
        alta(admin, "pErSoNa", "otro@example.test").andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("El username ya está reservado."));
        alta(admin, "otra", "PERSONA@EXAMPLE.TEST").andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("El email ya está reservado."));
        login("persona", PASSWORD).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header("Authorization", empleado)).andExpect(status().isUnauthorized());
        mvc.perform(patch(ruta + "/activar").header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(true));
        mvc.perform(get("/api/auth/me").header("Authorization", empleado)).andExpect(status().isUnauthorized());
        empleado = token("persona", PASSWORD);
        mvc.perform(put(ruta).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content(datos("persona.renombrada", "persona2@example.test", ADMINISTRADOR.name())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rol").value("ADMINISTRADOR"))
                .andExpect(jsonPath("$.nombre").value("Persona"));
        mvc.perform(get(USUARIOS).header("Authorization", empleado)).andExpect(status().isOk());
        mvc.perform(patch(ruta + "/password").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("password", "contraseña nueva de prueba"))))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").header("Authorization", empleado)).andExpect(status().isUnauthorized());
        login("persona.renombrada", PASSWORD).andExpect(status().isUnauthorized());
        token("persona.renombrada", "contraseña nueva de prueba");
        mvc.perform(delete(ruta).header("Authorization", admin)).andExpect(status().isNoContent());
    }

    private void verificarConcurrencia(Long admin) throws Exception {
        var duplicado = new CrearUsuarioRequest("paralelo", PASSWORD, "Persona", "paralelo@example.test", EMPLEADO);
        Callable<Boolean> alta = () -> {
            try {
                usuarios.crear(admin, duplicado);
                return true;
            } catch (ConflictoException ex) {
                return false;
            }
        };
        assertThat(paralelo(alta, alta)).containsExactlyInAnyOrder(true, false);

        Long otro = usuarios.crear(admin, new CrearUsuarioRequest("otro.admin", PASSWORD, "Otro",
                "otro.admin@example.test", ADMINISTRADOR)).id();
        assertThat(paralelo(() -> intentarDesactivar(admin, otro), () -> intentarDesactivar(otro, admin)))
                .containsExactlyInAnyOrder(true, false);
        assertThat(repository.countByRolNombreAndActivoTrue(ADMINISTRADOR)).isEqualTo(1);
    }

    private boolean intentarDesactivar(Long actor, Long objetivo) {
        try {
            usuarios.desactivar(actor, objetivo);
            return true;
        } catch (AccessDeniedException ex) {
            return false;
        }
    }

    private static List<Boolean> paralelo(Callable<Boolean> primera, Callable<Boolean> segunda) throws Exception {
        var barrera = new CyclicBarrier(2);
        try (var ejecutor = Executors.newFixedThreadPool(2)) {
            var a = ejecutor.submit(() -> { barrera.await(10, TimeUnit.SECONDS); return primera.call(); });
            var b = ejecutor.submit(() -> { barrera.await(10, TimeUnit.SECONDS); return segunda.call(); });
            return List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        }
    }

    private ResultActions alta(String admin, String username, String email) throws Exception {
        var datos = json.createObjectNode().put("username", username).put("nombre", " Persona ")
                .put("email", email).put("password", PASSWORD).put("rol", EMPLEADO.name());
        return mvc.perform(post(USUARIOS).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(datos)));
    }

    private String datos(String username, String email, String rol) {
        return json.writeValueAsString(json.createObjectNode().put("username", username)
                .put("nombre", " Persona ").put("email", email).put("rol", rol));
    }

    private ResultActions login(String username, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(json.createObjectNode().put("username", username).put("password", password))));
    }

    private String token(String username, String password) throws Exception {
        String respuesta = login(username, password).andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer")).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(respuesta).path("accessToken").asText();
    }
}
