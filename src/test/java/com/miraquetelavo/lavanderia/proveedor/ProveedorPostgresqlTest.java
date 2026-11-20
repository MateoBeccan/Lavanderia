package com.miraquetelavo.lavanderia.proveedor;

import com.miraquetelavo.lavanderia.soporte.EsquemaPostgresql;
import com.miraquetelavo.lavanderia.soporte.IntegracionPostgresql;
import com.miraquetelavo.lavanderia.categoriainsumo.dto.CategoriaInsumoRequest;
import com.miraquetelavo.lavanderia.categoriainsumo.service.CategoriaInsumoService;
import com.miraquetelavo.lavanderia.common.exception.ConflictoException;
import com.miraquetelavo.lavanderia.common.exception.RecursoNoEncontradoException;
import com.miraquetelavo.lavanderia.insumo.dto.InsumoRequest;
import com.miraquetelavo.lavanderia.insumo.service.InsumoService;
import com.miraquetelavo.lavanderia.proveedor.dto.ProveedorRequest;
import com.miraquetelavo.lavanderia.proveedor.dto.ProveedorResponse;
import com.miraquetelavo.lavanderia.proveedor.service.ProveedorService;
import com.miraquetelavo.lavanderia.proveedorinsumo.dto.AsociarInsumoRequest;
import com.miraquetelavo.lavanderia.proveedorinsumo.dto.ProveedorInsumoResponse;
import com.miraquetelavo.lavanderia.proveedorinsumo.dto.ProveedorResumenResponse;
import com.miraquetelavo.lavanderia.proveedorinsumo.entity.ProveedorInsumoId;
import com.miraquetelavo.lavanderia.proveedorinsumo.repository.ProveedorInsumoRepository;
import com.miraquetelavo.lavanderia.proveedorinsumo.service.ProveedorInsumoService;
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
import static org.springframework.http.HttpHeaders.LOCATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegracionPostgresql("proveedores")
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "lavanderia.jwt.secret=" + ProveedorPostgresqlTest.JWT,
        "spring.jpa.show-sql=false"
})
class ProveedorPostgresqlTest {

    static final String JWT = "dGVzdC1vbmx5LXZlcmlmaWNhY2lvbi1qd3QtbGF2YW5kZXJpYQ==";
    private static final PageRequest PAGINA = PageRequest.of(0, 1, Sort.by("id").descending());

    @Autowired private ProveedorService proveedores;
    @Autowired private ProveedorInsumoService relaciones;
    @Autowired private ProveedorInsumoRepository asociaciones;
    @Autowired private CategoriaInsumoService categorias;
    @Autowired private InsumoService insumos;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    @Autowired private RolRepository roles;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private JwtService jwt;

    @Test
    void administraDatosComercialesYReservaCuitInactivo() {
        var original = proveedores.registrar(new ProveedorRequest("  Distribuidora  ", "30-12345678-1",
                "  011-5555  ", "  Limpieza  "));
        assertThat(original).isEqualTo(new ProveedorResponse(original.id(), "Distribuidora", "30123456781",
                "011-5555", "Limpieza", true));
        assertThat(proveedores.obtener(original.id())).isEqualTo(original);
        assertThatThrownBy(() -> proveedores.registrar(solicitud("Otro", original.cuit())))
                .isInstanceOf(ConflictoException.class);

        var segundo = proveedores.registrar(solicitud(original.nombre(), "20123456786"));
        assertThatThrownBy(() -> proveedores.actualizar(original.id(), solicitud("Otro", segundo.cuit())))
                .isInstanceOf(ConflictoException.class);
        var editado = proveedores.actualizar(original.id(), solicitud("Distribuidora renovada", "20123456700"));
        assertThat(editado.cuit()).isEqualTo("20123456700");
        proveedores.desactivar(original.id());
        proveedores.desactivar(original.id());
        assertThatThrownBy(() -> proveedores.obtener(original.id())).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> proveedores.actualizar(original.id(), solicitud("Otro", editado.cuit())))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> proveedores.registrar(solicitud("Otro", editado.cuit())))
                .isInstanceOf(ConflictoException.class);
        assertThat(proveedores.activar(original.id())).isEqualTo(editado);
        assertThat(proveedores.activar(original.id())).isEqualTo(editado);
    }

    @Test
    void validaCamposTambienFueraDeHttp() {
        assertThatThrownBy(() -> proveedores.registrar(new ProveedorRequest(" ", "30123456781", "", null)))
                .isInstanceOfSatisfying(ConstraintViolationException.class,
                        ex -> assertThat(ex.getConstraintViolations()).hasSize(2));
        assertThatThrownBy(() -> proveedores.registrar(new ProveedorRequest("x".repeat(151), null,
                "1".repeat(21), "x".repeat(51))))
                .isInstanceOfSatisfying(ConstraintViolationException.class,
                        ex -> assertThat(ex.getConstraintViolations()).hasSize(4));
    }

    @Test
    void combinaFiltrosLiteralmenteYOrdenaPorId() {
        var primero = proveedor("Química % Industrial", 1);
        var segundo = proveedor("QUÍMICA % Comercial", 2);
        var tercero = proveedor("Química % Inactiva", 3);
        proveedor("Química ABC", 4);
        proveedores.desactivar(tercero.id());

        var pagina = proveedores.listarActivos("QUÍMICA %", null, "123", "LIMPIEZA", PAGINA);
        assertThat(pagina.getContent()).containsExactly(primero);
        assertThat(pagina.getTotalElements()).isEqualTo(2);
        assertThat(proveedores.listarActivos("Química %", null, null, null, PAGINA.next()).getContent())
                .containsExactly(segundo);
        var cuit = segundo.cuit().replaceFirst("^(..)(........)(.)$", "$1-$2-$3");
        assertThat(proveedores.listarActivos(null, cuit, null, null, PAGINA).getContent()).containsExactly(segundo);
        assertThat(proveedores.listarActivos("Química", null, "999", null, PAGINA)).isEmpty();
        assertThat(proveedores.listarActivos("Química", null, null, "otro rubro", PAGINA)).isEmpty();
    }

    @Test
    void paginaEnAmbasDireccionesYConservaLasFilasOcultas() {
        var primero = proveedor("Papelera", 5);
        var segundo = proveedor("Otra papelera", 6);
        var categoria = categoria();
        var papel = insumo(categoria);
        var bolsa = insumo(categoria);
        var papelPrimero = asociar(primero.id(), papel);
        var bolsaPrimero = asociar(primero.id(), bolsa);
        asociar(segundo.id(), papel);

        assertThat(relaciones.listarInsumos(primero.id(), PAGINA).getContent()).containsExactly(papelPrimero);
        assertThat(relaciones.listarInsumos(primero.id(), PAGINA.next()).getContent()).containsExactly(bolsaPrimero);
        var inversa = relaciones.listarProveedores(papel, PAGINA);
        assertThat(inversa.getTotalElements()).isEqualTo(2);
        assertThat(inversa.getContent()).extracting(ProveedorResumenResponse::id).containsExactly(primero.id());
        assertThat(relaciones.listarProveedores(papel, PAGINA.next()).getContent())
                .extracting(ProveedorResumenResponse::id).containsExactly(segundo.id());

        proveedores.desactivar(primero.id());
        assertThat(relaciones.listarInsumos(primero.id(), PAGINA)).isEmpty();
        assertThat(relaciones.listarProveedores(papel, PAGINA).getContent())
                .extracting(ProveedorResumenResponse::id).containsExactly(segundo.id());
        assertThat(existe(primero.id(), papel)).isTrue();
        proveedores.activar(primero.id());
        assertThat(relaciones.listarInsumos(primero.id(), PAGINA).getTotalElements()).isEqualTo(2);

        insumos.desactivar(papel);
        insumos.desactivar(bolsa);
        categorias.desactivar(categoria);
        assertThat(relaciones.listarProveedores(papel, PAGINA)).isEmpty();
        assertThat(relaciones.listarInsumos(primero.id(), PAGINA)).isEmpty();
        assertThat(existe(primero.id(), papel)).isTrue();

        proveedores.desactivar(primero.id());
        relaciones.desasociar(primero.id(), papel);
        relaciones.desasociar(primero.id(), papel);
        assertThat(existe(primero.id(), papel)).isFalse();
        assertThat(proveedores.activar(primero.id())).isEqualTo(primero);
        categorias.activar(categoria);
        assertThat(insumos.activar(papel).id()).isEqualTo(papel);
        assertThat(relaciones.listarProveedores(papel, PAGINA).getContent())
                .extracting(ProveedorResumenResponse::id).containsExactly(segundo.id());
    }

    @Test
    void distingueAusenciaInactividadYDuplicado() {
        var proveedor = proveedor("Proveedor con validaciones", 7).id();
        var categoria = categoria();
        var insumo = insumo(categoria);
        var ausente = Long.MAX_VALUE;
        assertThatThrownBy(() -> asociar(ausente, insumo)).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> asociar(proveedor, ausente)).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> relaciones.desasociar(ausente, insumo)).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> relaciones.desasociar(proveedor, ausente)).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> relaciones.listarInsumos(ausente, PAGINA))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> relaciones.listarProveedores(ausente, PAGINA))
                .isInstanceOf(RecursoNoEncontradoException.class);

        proveedores.desactivar(proveedor);
        assertThatThrownBy(() -> asociar(proveedor, insumo)).isInstanceOf(ConflictoException.class);
        proveedores.activar(proveedor);
        insumos.desactivar(insumo);
        assertThatThrownBy(() -> asociar(proveedor, insumo)).isInstanceOf(ConflictoException.class);
        insumos.activar(insumo);
        asociar(proveedor, insumo);
        assertThatThrownBy(() -> asociar(proveedor, insumo)).isInstanceOf(ConflictoException.class);

        // Simula datos previos inconsistentes para verificar el filtro y la validación de categoría por separado.
        jdbc.update("update categorias_insumo set activo = false where id_categoria_insumo = ?", categoria);
        assertThatThrownBy(() -> asociar(proveedor, insumo)).isInstanceOf(ConflictoException.class)
                .hasMessageContaining("categoría");
        assertThat(relaciones.listarInsumos(proveedor, PAGINA)).isEmpty();
        assertThat(relaciones.listarProveedores(insumo, PAGINA)).isEmpty();
        assertThat(existe(proveedor, insumo)).isTrue();
    }

    @Test
    void postgresqlImponeUnicidadClaveCompuestaYAmbasReferencias() {
        var proveedor = proveedor("Restricciones", 8);
        var insumo = insumo(categoria());
        asociar(proveedor.id(), insumo);
        assertThatThrownBy(() -> jdbc.update("""
                insert into proveedores (nombre_prov, cuit, telefono) values ('Duplicado SQL', ?, '123')
                """, proveedor.cuit())).isInstanceOfSatisfying(DataIntegrityViolationException.class,
                ex -> assertThat(esViolacionUnica(ex, "uk_proveedores_cuit")).isTrue());

        var insertar = "insert into proveedores_insumos (id_proveedor, id_insumo) values (?, ?)";
        assertThatThrownBy(() -> jdbc.update(insertar, proveedor.id(), insumo))
                .isInstanceOfSatisfying(DataIntegrityViolationException.class,
                        ex -> assertThat(esViolacionUnica(ex, "proveedores_insumos_pkey")).isTrue());
        assertThatThrownBy(() -> jdbc.update(insertar, Long.MAX_VALUE, insumo))
                .isInstanceOf(DataIntegrityViolationException.class).rootCause().hasMessageContaining("fk_proveedores_insumos_proveedor");
        assertThatThrownBy(() -> jdbc.update(insertar, proveedor.id(), Long.MAX_VALUE))
                .isInstanceOf(DataIntegrityViolationException.class).rootCause().hasMessageContaining("fk_proveedores_insumos_insumo");
        assertThat(new ProveedorInsumoId(proveedor.id(), insumo))
                .isEqualTo(new ProveedorInsumoId(proveedor.id(), insumo))
                .hasSameHashCodeAs(new ProveedorInsumoId(proveedor.id(), insumo))
                .isNotEqualTo(new ProveedorInsumoId(proveedor.id(), Long.MAX_VALUE));
    }

    @Test
    void compitenAltasYBajasDeLaMismaAsociacion() throws Exception {
        var proveedor = proveedor("Asociación concurrente", 9).id();
        var insumo = insumo(categoria());
        var altas = competir(() -> asociar(proveedor, insumo), () -> asociar(proveedor, insumo));
        assertThat(altas).filteredOn(ProveedorInsumoResponse.class::isInstance).hasSize(1);
        assertThat(altas).filteredOn(ConflictoException.class::isInstance).hasSize(1);
        assertThat(existe(proveedor, insumo)).isTrue();

        Callable<Boolean> baja = () -> {
            relaciones.desasociar(proveedor, insumo);
            return true;
        };
        assertThat(competir(baja, baja)).containsExactly(true, true);
        assertThat(existe(proveedor, insumo)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"proveedor", "insumo", "categoria"})
    void compiteAsociacionConBaja(String objetivo) throws Exception {
        var proveedor = proveedor("Baja concurrente", switch (objetivo) {
            case "proveedor" -> 10;
            case "insumo" -> 11;
            default -> 12;
        }).id();
        var categoria = categoria();
        var insumo = insumo(categoria);
        var resultados = competir(() -> asociar(proveedor, insumo), () -> {
            switch (objetivo) {
                case "proveedor" -> proveedores.desactivar(proveedor);
                case "insumo" -> insumos.desactivar(insumo);
                case "categoria" -> categorias.desactivar(categoria);
            }
            return true;
        });

        if ("categoria".equals(objetivo)) {
            assertThat(resultados.getFirst()).isInstanceOf(ProveedorInsumoResponse.class);
            assertThat(resultados.getLast()).isInstanceOf(ConflictoException.class);
            assertThat(relaciones.listarInsumos(proveedor, PAGINA)).hasSize(1);
        } else {
            assertThat(resultados.getLast()).isEqualTo(true);
            assertThat(resultados.getFirst()).isInstanceOfAny(ProveedorInsumoResponse.class, ConflictoException.class);
            assertThat(existe(proveedor, insumo)).isEqualTo(resultados.getFirst() instanceof ProveedorInsumoResponse);
            assertThat(relaciones.listarInsumos(proveedor, PAGINA)).isEmpty();
        }
    }

    @Test
    void compiteAsociacionConActualizacionDelProveedor() throws Exception {
        var proveedor = proveedor("Nombre anterior", 13);
        var insumo = insumo(categoria());
        var resultados = competir(() -> asociar(proveedor.id(), insumo),
                () -> proveedores.actualizar(proveedor.id(), solicitud("Nuevo nombre", proveedor.cuit())));
        assertThat(resultados.getFirst()).isInstanceOfSatisfying(ProveedorInsumoResponse.class,
                respuesta -> assertThat(respuesta.proveedorNombre()).isIn("Nombre anterior", "Nuevo nombre"));
        assertThat(relaciones.listarInsumos(proveedor.id(), PAGINA).getContent())
                .extracting(ProveedorInsumoResponse::proveedorNombre).containsExactly("Nuevo nombre");
    }

    @Test
    void elReinicioConservaLaBajaYLaAsociacion() {
        var proveedor = proveedor("Persistente", 14);
        var insumo = insumo(categoria());
        var asociacion = asociar(proveedor.id(), insumo);
        proveedores.desactivar(proveedor.id());

        try (var app = EsquemaPostgresql.reinicio(ProveedorPostgresqlTest.class).run(
                "--server.port=0", "--lavanderia.jwt.secret=" + JWT, "--spring.jpa.show-sql=false")) {
            var reiniciados = app.getBean(ProveedorService.class);
            var relacionesReinicio = app.getBean(ProveedorInsumoService.class);
            assertThatThrownBy(() -> reiniciados.obtener(proveedor.id())).isInstanceOf(RecursoNoEncontradoException.class);
            assertThatThrownBy(() -> reiniciados.registrar(solicitud("Copia", proveedor.cuit())))
                    .isInstanceOf(ConflictoException.class);
            assertThat(relacionesReinicio.listarInsumos(proveedor.id(), PAGINA)).isEmpty();
            assertThat(reiniciados.activar(proveedor.id())).isEqualTo(proveedor);
            assertThat(relacionesReinicio.listarInsumos(proveedor.id(), PAGINA).getContent()).containsExactly(asociacion);
        }
    }

    @Test
    void administraProveedoresYAsociacionesConJwtYPermisosReales() throws Exception {
        var admin = autorizar(CodigoRol.ADMINISTRADOR);
        var empleado = autorizar(CodigoRol.EMPLEADO);
        var insumo = insumo(categoria());
        var cuit = cuit(15);
        var body = """
                {"nombre":"Proveedor HTTP","cuit":"%s","telefono":"123456","rubro":"Limpieza"}
                """.formatted(cuit);
        var alta = mvc.perform(post("/api/proveedores").header(AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.cuit").value(cuit)).andReturn();
        var url = alta.getResponse().getHeader(LOCATION);
        assertThat(url).matches("http://localhost/api/proveedores/[0-9]+");
        var ruta = java.net.URI.create(url).getPath();
        var items = ruta + "/insumos";
        var inversa = "/api/insumos/" + insumo + "/proveedores";
        var asociacion = "{\"insumoId\":" + insumo + "}";

        for (var lectura : List.of("/api/proveedores", ruta, items, inversa)) {
            mvc.perform(get(lectura)).andExpect(status().isUnauthorized());
            mvc.perform(get(lectura).header(AUTHORIZATION, "Bearer invalido")).andExpect(status().isUnauthorized());
            mvc.perform(get(lectura).header(AUTHORIZATION, empleado)).andExpect(status().isOk());
        }
        for (var escritura : List.of(post("/api/proveedores"), put(ruta), delete(ruta), patch(ruta + "/activar"),
                post(items), delete(items + "/" + insumo))) {
            mvc.perform(escritura.header(AUTHORIZATION, empleado)).andExpect(status().isForbidden());
        }

        mvc.perform(get("/api/proveedores").param("cuit", cuit).param("size", "1").header(AUTHORIZATION, empleado))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements").value(1));
        mvc.perform(put(ruta).header(AUTHORIZATION, admin).contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("Proveedor HTTP", "Proveedor editado")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value("Proveedor editado"));
        mvc.perform(post(items).header(AUTHORIZATION, admin).contentType(MediaType.APPLICATION_JSON).content(asociacion))
                .andExpect(status().isCreated()).andExpect(header().doesNotExist(LOCATION))
                .andExpect(jsonPath("$.insumoId").value(insumo.intValue()));
        mvc.perform(post(items).header(AUTHORIZATION, admin).contentType(MediaType.APPLICATION_JSON).content(asociacion))
                .andExpect(status().isConflict()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(get(items).header(AUTHORIZATION, empleado))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements").value(1));
        mvc.perform(get(inversa).header(AUTHORIZATION, empleado))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].cuit").value(cuit));
        mvc.perform(delete(ruta).header(AUTHORIZATION, admin)).andExpect(status().isNoContent());
        mvc.perform(get(ruta).header(AUTHORIZATION, empleado)).andExpect(status().isNotFound());
        mvc.perform(patch(ruta + "/activar").header(AUTHORIZATION, admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(true));
        for (int intento = 0; intento < 2; intento++) {
            mvc.perform(delete(items + "/" + insumo).header(AUTHORIZATION, admin)).andExpect(status().isNoContent());
        }
        mvc.perform(delete(items + "/" + Long.MAX_VALUE).header(AUTHORIZATION, admin)).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "{\"cuit\":\"30123456781\",\"telefono\":\"123\"}",
            "{\"nombre\":\"Proveedor\",\"cuit\":\"30123456781\"}",
            "{\"nombre\":\"Proveedor\",\"cuit\":\"30123456789\",\"telefono\":\"123\"}",
            "{\"nombre\":\"Proveedor\",\"cuit\":\"30123456781\",\"telefono\":\"123\",\"activo\":false}"})
    void devuelveProblemDetailParaAltasInvalidas(String body) throws Exception {
        mvc.perform(post("/api/proveedores").header(AUTHORIZATION, autorizar(CodigoRol.ADMINISTRADOR))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    private ProveedorInsumoResponse asociar(Long proveedor, Long insumo) {
        return relaciones.asociar(proveedor, new AsociarInsumoRequest(insumo));
    }

    private boolean existe(Long proveedor, Long insumo) {
        return asociaciones.existsById(new ProveedorInsumoId(proveedor, insumo));
    }

    private Long categoria() {
        return categorias.registrar(new CategoriaInsumoRequest("Cat " + UUID.randomUUID())).id();
    }

    private Long insumo(Long categoria) {
        return insumos.registrar(new InsumoRequest("Insumo " + UUID.randomUUID(), categoria)).id();
    }

    private ProveedorResponse proveedor(String nombre, int secuencia) {
        return proveedores.registrar(solicitud(nombre, cuit(secuencia)));
    }

    private static ProveedorRequest solicitud(String nombre, String cuit) {
        return new ProveedorRequest(nombre, cuit, "011-123456", "Limpieza");
    }

    private static String cuit(int secuencia) {
        // Estos sufijos producen CUIT válidos para cada escenario sin compartir la clave entre pruebas.
        return List.of("",
                "30000000007", "30000000015", "30000000023", "30000000031", "30000000058",
                "30000000066", "30000000074", "30000000082", "30000000090", "30000000104",
                "30000000112", "30000000120", "30000000139", "30000000147", "30000000155").get(secuencia);
    }

    private String autorizar(CodigoRol codigo) {
        var rol = roles.findByNombre(codigo).orElseThrow();
        var nombre = UUID.randomUUID().toString();
        var usuario = usuarios.save(new Usuario(nombre, "hash-de-prueba", "Ana", nombre + "@example.com", rol));
        return "Bearer " + jwt.emitir(usuario.getId(), usuario.getVersionCredenciales().toString()).accessToken();
    }

    private static List<Object> competir(Callable<?> primera, Callable<?> segunda) throws Exception {
        var barrera = new CyclicBarrier(2);
        try (var ejecutor = Executors.newFixedThreadPool(2)) {
            var a = ejecutor.submit(() -> ejecutar(barrera, primera));
            var b = ejecutor.submit(() -> ejecutar(barrera, segunda));
            return List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
        }
    }

    private static Object ejecutar(CyclicBarrier barrera, Callable<?> operacion) throws Exception {
        barrera.await(10, TimeUnit.SECONDS);
        try {
            return operacion.call();
        } catch (ConflictoException ex) {
            return ex;
        }
    }
}
