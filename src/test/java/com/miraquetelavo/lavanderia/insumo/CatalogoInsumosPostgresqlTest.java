package com.miraquetelavo.lavanderia.insumo;

import com.miraquetelavo.lavanderia.soporte.EsquemaPostgresql;
import com.miraquetelavo.lavanderia.soporte.IntegracionPostgresql;
import com.miraquetelavo.lavanderia.categoriainsumo.dto.CategoriaInsumoRequest;
import com.miraquetelavo.lavanderia.categoriainsumo.entity.CategoriaInsumo;
import com.miraquetelavo.lavanderia.categoriainsumo.service.CategoriaInsumoService;
import com.miraquetelavo.lavanderia.common.exception.ConflictoException;
import com.miraquetelavo.lavanderia.common.exception.RecursoNoEncontradoException;
import com.miraquetelavo.lavanderia.insumo.dto.InsumoRequest;
import com.miraquetelavo.lavanderia.insumo.dto.InsumoResponse;
import com.miraquetelavo.lavanderia.insumo.entity.Insumo;
import com.miraquetelavo.lavanderia.insumo.service.InsumoService;
import com.miraquetelavo.lavanderia.rol.entity.CodigoRol;
import com.miraquetelavo.lavanderia.rol.repository.RolRepository;
import com.miraquetelavo.lavanderia.security.JwtService;
import com.miraquetelavo.lavanderia.usuario.entity.Usuario;
import com.miraquetelavo.lavanderia.usuario.repository.UsuarioRepository;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.miraquetelavo.lavanderia.common.exception.RestriccionesPostgresql.esViolacionUnica;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegracionPostgresql("insumos")
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "lavanderia.jwt.secret=" + CatalogoInsumosPostgresqlTest.JWT,
        "spring.jpa.show-sql=false"
})
class CatalogoInsumosPostgresqlTest {

    static final String JWT = "dGVzdC1vbmx5LXZlcmlmaWNhY2lvbi1qd3QtbGF2YW5kZXJpYQ==";

    @Autowired
    private CategoriaInsumoService categorias;

    @Autowired
    private InsumoService insumos;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtService jwt;

    @Autowired
    private RolRepository roles;

    @Autowired
    private UsuarioRepository usuarios;

    @Test
    void usaRolesDeUsuariosPersistidosAlValidarJwt() throws Exception {
        var admin = autorizar(CodigoRol.ADMINISTRADOR);
        var empleado = autorizar(CodigoRol.EMPLEADO);
        var id = categoria("Categoría JWT");
        var solicitud = """
                {"nombre":"Insumo JWT","categoriaId":%d}
                """.formatted(id);

        mvc.perform(post("/api/insumos").header(AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content(solicitud))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.categoriaId").value(id.intValue()));
        mvc.perform(get("/api/insumos").param("categoriaId", id.toString()).header(AUTHORIZATION, empleado))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].nombre").value("Insumo JWT"));
        mvc.perform(post("/api/insumos").header(AUTHORIZATION, empleado)
                        .contentType(MediaType.APPLICATION_JSON).content(solicitud))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/categorias-insumo/{id}", id)).andExpect(status().isUnauthorized());
    }

    @Test
    void administraCategoriasYReservaNombresDadosDeBaja() {
        var categoria = categorias.registrar(new CategoriaInsumoRequest("  Detergentes  "));
        assertThat(categoria.nombre()).isEqualTo("Detergentes");
        assertThat(categoria.activo()).isTrue();
        assertThat(categorias.obtener(categoria.id())).isEqualTo(categoria);
        assertThat(categorias.actualizar(categoria.id(), new CategoriaInsumoRequest("DETERGENTES")).nombre())
                .isEqualTo("DETERGENTES");

        categorias.desactivar(categoria.id());
        categorias.desactivar(categoria.id());
        assertThat(categorias.listarActivos()).noneMatch(item -> item.id().equals(categoria.id()));
        assertThatThrownBy(() -> categorias.obtener(categoria.id())).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> categorias.actualizar(categoria.id(), new CategoriaInsumoRequest("Otro")))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> categorias.registrar(new CategoriaInsumoRequest("detergentes")))
                .isInstanceOf(ConflictoException.class);
        assertThatThrownBy(() -> categorias.activar(Long.MAX_VALUE)).isInstanceOf(RecursoNoEncontradoException.class);
        assertThat(categorias.activar(categoria.id()).activo()).isTrue();
        assertThat(categorias.activar(categoria.id()).activo()).isTrue();
    }

    @Test
    void administraInsumosYProtegeLaRelacionConCategorias() {
        var origen = categoria("Jabones");
        var destino = categoria("Suavizantes");
        var inactiva = categoria("Archivada");
        categorias.desactivar(inactiva);

        assertThatThrownBy(() -> insumos.registrar(new InsumoRequest("Gel", Long.MAX_VALUE)))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> insumos.registrar(new InsumoRequest("Gel", inactiva)))
                .isInstanceOf(RecursoNoEncontradoException.class);

        var item = insumos.registrar(new InsumoRequest("  Gel  ", origen));
        assertThat(item.nombre()).isEqualTo("Gel");
        assertThat(item.categoriaNombre()).isEqualTo("Jabones");
        assertThat(item.activo()).isTrue();
        assertThat(insumos.obtener(item.id())).isEqualTo(item);
        assertThatThrownBy(() -> categorias.desactivar(origen)).isInstanceOf(ConflictoException.class);
        assertThatThrownBy(() -> insumos.actualizar(item.id(), new InsumoRequest("Gel", inactiva)))
                .isInstanceOf(ConflictoException.class);

        var editado = insumos.actualizar(item.id(), new InsumoRequest("Gel concentrado", destino));
        assertThat(editado.nombre()).isEqualTo("Gel concentrado");
        assertThat(editado.categoriaId()).isEqualTo(destino);
        categorias.desactivar(origen);

        insumos.desactivar(item.id());
        insumos.desactivar(item.id());
        assertThatThrownBy(() -> insumos.obtener(item.id())).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> insumos.actualizar(item.id(), new InsumoRequest("Gel", destino)))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> insumos.registrar(new InsumoRequest("GEL CONCENTRADO", destino)))
                .isInstanceOf(ConflictoException.class);
        categorias.desactivar(destino);
        assertThatThrownBy(() -> insumos.activar(item.id())).isInstanceOf(ConflictoException.class);
        categorias.activar(destino);
        assertThat(insumos.activar(item.id())).isEqualTo(editado);
        assertThat(insumos.activar(item.id())).isEqualTo(editado);
    }

    @Test
    void validaEntradasTambienFueraDeHttp() {
        assertThatThrownBy(() -> categorias.registrar(new CategoriaInsumoRequest("x".repeat(51))))
                .isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> insumos.registrar(new InsumoRequest("  ", null)))
                .isInstanceOfSatisfying(ConstraintViolationException.class,
                        error -> assertThat(error.getConstraintViolations()).hasSize(2));
        assertThatThrownBy(() -> insumos.listarActivos(-1L, PageRequest.of(0, 20)))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void paginaPorIdYFiltraActivosPorCategoria() {
        var filtrada = categoria("Envases");
        var primero = insumos.registrar(new InsumoRequest("Bolsa de lienzo", filtrada));
        var segundo = insumos.registrar(new InsumoRequest("Bolsa de papel", filtrada));
        var inactivo = insumos.registrar(new InsumoRequest("Bolsa descartable", filtrada));
        insumos.desactivar(inactivo.id());
        insumos.registrar(new InsumoRequest("Papel", categoria("Papelería")));

        var pagina = insumos.listarActivos(filtrada, PageRequest.of(0, 1, Sort.by("id").descending()));
        assertThat(pagina.getContent()).containsExactly(primero);
        assertThat(pagina.getTotalElements()).isEqualTo(2);
        assertThat(insumos.listarActivos(filtrada, PageRequest.of(1, 1)).getContent()).containsExactly(segundo);
        assertThat(insumos.listarActivos(null, PageRequest.of(0, 100)).getContent())
                .contains(primero, segundo).doesNotContain(inactivo);
    }

    @Test
    void postgresqlImponeFkYUnicidadAunqueLaEscrituraSeaSql() {
        var id = categoria("Cepillos");
        insumos.registrar(new InsumoRequest("Cepillo de nylon", id));

        assertThatThrownBy(() -> jdbc.update("insert into categorias_insumo (nombre_cat) values ('CEPILLOS')"))
                .isInstanceOfSatisfying(DataIntegrityViolationException.class,
                        error -> assertThat(esViolacionUnica(error, CategoriaInsumo.UK_NOMBRE)).isTrue());
        assertThatThrownBy(() -> jdbc.update("""
                insert into insumos (nombre_insumo, id_categoria_insumo) values ('CEPILLO DE NYLON', ?)
                """, id)).isInstanceOfSatisfying(DataIntegrityViolationException.class,
                        error -> assertThat(esViolacionUnica(error, Insumo.UK_NOMBRE)).isTrue());
        assertThatThrownBy(() -> jdbc.update("""
                insert into insumos (nombre_insumo, id_categoria_insumo) values ('Sin categoría', ?)
                """, Long.MAX_VALUE)).isInstanceOf(DataIntegrityViolationException.class)
                .rootCause().hasMessageContaining("fk_insumos_categoria");
    }

    @Test
    void soloUnaAltaConcurrenteConNombreEquivalenteProspera() throws Exception {
        var id = categoria("Fibras");
        var resultados = competir(
                () -> insumos.registrar(new InsumoRequest("Esponja", id)),
                () -> insumos.registrar(new InsumoRequest(" ESPONJA ", id)));

        assertThat(resultados).filteredOn(InsumoResponse.class::isInstance).hasSize(1);
        assertThat(resultados).filteredOn(ConflictoException.class::isInstance).hasSize(1);
    }

    @Test
    void dosActualizacionesConcurrentesConservanUnParCompletoDeNombreYCategoria() throws Exception {
        var primera = categoria("Destino algodón");
        var segunda = categoria("Destino microfibra");
        var item = insumos.registrar(new InsumoRequest("Paño", primera));
        var resultados = competir(
                () -> insumos.actualizar(item.id(), new InsumoRequest("Paño de algodón", primera)),
                () -> insumos.actualizar(item.id(), new InsumoRequest("Paño de microfibra", segunda)));

        assertThat(resultados).allMatch(InsumoResponse.class::isInstance);
        assertThat(insumos.obtener(item.id())).isIn(resultados);
    }

    @ParameterizedTest
    @ValueSource(strings = {"alta", "reactivacion", "traslado"})
    void laBajaConcurrenteNuncaDejaUnInsumoActivoEnUnaCategoriaInactiva(String operacion) throws Exception {
        var destino = categoria("Destino " + operacion);
        var operacionInsumo = switch (operacion) {
            case "alta" -> (Callable<?>) () -> insumos.registrar(new InsumoRequest("Alta simultánea", destino));
            case "reactivacion" -> {
                var item = insumos.registrar(new InsumoRequest("Reactivación simultánea", destino));
                insumos.desactivar(item.id());
                yield (Callable<?>) () -> insumos.activar(item.id());
            }
            case "traslado" -> {
                var item = insumos.registrar(new InsumoRequest("Traslado simultáneo", categoria("Origen traslado")));
                yield (Callable<?>) () -> insumos.actualizar(item.id(), new InsumoRequest(item.nombre(), destino));
            }
            default -> throw new AssertionError(operacion);
        };

        var resultados = competir(operacionInsumo, () -> {
            categorias.desactivar(destino);
            return true;
        });

        if (resultados.getFirst() instanceof InsumoResponse) {
            assertThat(resultados.getLast()).isInstanceOf(ConflictoException.class);
            assertThat(categorias.obtener(destino).activo()).isTrue();
        } else {
            assertThat(resultados.getFirst()).isInstanceOf(operacion.equals("alta")
                    ? RecursoNoEncontradoException.class : ConflictoException.class);
            assertThat(resultados.getLast()).isEqualTo(true);
            assertThat(insumos.listarActivos(destino, PageRequest.of(0, 10))).isEmpty();
            assertThatThrownBy(() -> categorias.obtener(destino)).isInstanceOf(RecursoNoEncontradoException.class);
        }
    }

    @Test
    void conservaRelacionYBajaLogicaTrasReiniciar() {
        var id = categoria("Reinicio");
        var insumo = insumos.registrar(new InsumoRequest("Persistido", id));
        insumos.desactivar(insumo.id());
        categorias.desactivar(id);

        try (var app = EsquemaPostgresql.reinicio(CatalogoInsumosPostgresqlTest.class).run(
                "--server.port=0", "--lavanderia.jwt.secret=" + JWT, "--spring.jpa.show-sql=false")) {
            var categoriasReinicio = app.getBean(CategoriaInsumoService.class);
            var insumosReinicio = app.getBean(InsumoService.class);
            assertThatThrownBy(() -> categoriasReinicio.obtener(id)).isInstanceOf(RecursoNoEncontradoException.class);
            assertThatThrownBy(() -> insumosReinicio.obtener(insumo.id())).isInstanceOf(RecursoNoEncontradoException.class);
            assertThatThrownBy(() -> categoriasReinicio.registrar(new CategoriaInsumoRequest("REINICIO")))
                    .isInstanceOf(ConflictoException.class);
            categoriasReinicio.activar(id);
            assertThat(insumosReinicio.activar(insumo.id())).isEqualTo(insumo);
        }
    }

    private Long categoria(String nombre) {
        return categorias.registrar(new CategoriaInsumoRequest(nombre)).id();
    }

    private String autorizar(CodigoRol codigo) {
        var rol = roles.findByNombre(codigo).orElseThrow();
        var nombre = UUID.randomUUID().toString();
        var usuario = usuarios.save(new Usuario(nombre, "hash-de-prueba", "Ana", nombre + "@example.com", rol));
        return "Bearer " + jwt.emitir(usuario.getId(), usuario.getVersionCredenciales().toString()).accessToken();
    }

    private static List<Object> competir(Callable<?> primera, Callable<?> segunda) throws Exception {
        var barrera = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> ejecutar(barrera, primera));
            var b = executor.submit(() -> ejecutar(barrera, segunda));
            return List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
        }
    }

    private static Object ejecutar(CyclicBarrier barrera, Callable<?> operacion) throws Exception {
        barrera.await(10, TimeUnit.SECONDS);
        try {
            return operacion.call();
        } catch (ConflictoException | RecursoNoEncontradoException ex) {
            return ex;
        }
    }
}
