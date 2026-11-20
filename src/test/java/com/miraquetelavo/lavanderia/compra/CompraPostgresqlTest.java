package com.miraquetelavo.lavanderia.compra;

import com.miraquetelavo.lavanderia.soporte.EsquemaPostgresql;
import com.miraquetelavo.lavanderia.soporte.IntegracionPostgresql;
import com.miraquetelavo.lavanderia.categoriainsumo.entity.CategoriaInsumo;
import com.miraquetelavo.lavanderia.categoriainsumo.repository.CategoriaInsumoRepository;
import com.miraquetelavo.lavanderia.common.exception.ConflictoException;
import com.miraquetelavo.lavanderia.compra.dto.*;
import com.miraquetelavo.lavanderia.compra.service.CompraService;
import com.miraquetelavo.lavanderia.detallecompra.dto.ActualizarDetalleCompraRequest;
import com.miraquetelavo.lavanderia.detallecompra.dto.DetalleCompraRequest;
import com.miraquetelavo.lavanderia.insumo.entity.Insumo;
import com.miraquetelavo.lavanderia.insumo.repository.InsumoRepository;
import com.miraquetelavo.lavanderia.insumo.service.InsumoService;
import com.miraquetelavo.lavanderia.proveedor.entity.Proveedor;
import com.miraquetelavo.lavanderia.proveedor.repository.ProveedorRepository;
import com.miraquetelavo.lavanderia.proveedor.service.ProveedorService;
import com.miraquetelavo.lavanderia.proveedorinsumo.dto.AsociarInsumoRequest;
import com.miraquetelavo.lavanderia.proveedorinsumo.service.ProveedorInsumoService;
import com.miraquetelavo.lavanderia.rol.entity.CodigoRol;
import com.miraquetelavo.lavanderia.rol.repository.RolRepository;
import com.miraquetelavo.lavanderia.security.JwtService;
import com.miraquetelavo.lavanderia.usuario.entity.Usuario;
import com.miraquetelavo.lavanderia.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@IntegracionPostgresql("compras")
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "lavanderia.jwt.secret=" + CompraPostgresqlTest.JWT,
        "spring.jpa.show-sql=false"
})
class CompraPostgresqlTest {
    static final String JWT = "dGVzdC1vbmx5LXZlcmlmaWNhY2lvbi1qd3QtbGF2YW5kZXJpYQ==";
    @Autowired private CompraService compras;
    @Autowired private ProveedorRepository proveedores;
    @Autowired private ProveedorService proveedorService;
    @Autowired private CategoriaInsumoRepository categorias;
    @Autowired private InsumoRepository insumos;
    @Autowired private InsumoService insumoService;
    @Autowired private ProveedorInsumoService asociaciones;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private RolRepository roles;
    @Autowired private JwtService jwt;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    @Autowired private Clock relojPedidos;
    @Autowired private PlatformTransactionManager transacciones;

    private Long categoriaId;
    private Long proveedorId;
    private Long primero;
    private Long segundo;
    private Usuario empleado;
    private String autorizacion;

    @BeforeEach
    void prepararCatalogos() {
        var categoria = categorias.save(new CategoriaInsumo("Compra " + UUID.randomUUID()));
        categoriaId = categoria.getId();
        proveedorId = proveedor();
        primero = insumos.save(new Insumo("Primero " + UUID.randomUUID(), categoria)).getId();
        segundo = insumos.save(new Insumo("Segundo " + UUID.randomUUID(), categoria)).getId();
        asociaciones.asociar(proveedorId, new AsociarInsumoRequest(primero));
        asociaciones.asociar(proveedorId, new AsociarInsumoRequest(segundo));
        empleado = usuario(CodigoRol.EMPLEADO);
        autorizacion = token(empleado);
    }

    @Test
    void registraConJwtCalculaImportesYConsultaHistorialSinCaja() throws Exception {
        long movimientos = contar("movimientos_caja");
        LocalDateTime antes = LocalDateTime.now(relojPedidos).minusSeconds(1);
        mvc.perform(post("/api/compras")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/compras").header(AUTHORIZATION, "Bearer invalido")).andExpect(status().isUnauthorized());

        var json = cuerpo(proveedorId, "  FAC-01  ", linea(primero, "0.50", "1.01") + "," + linea(segundo, "2", "3.20"));
        var alta = enviar(post("/api/compras"), json)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.usuarioId").value(empleado.getId().intValue()))
                .andExpect(jsonPath("$.estadoPagoCodigo").value("PENDIENTE"))
                .andExpect(jsonPath("$.numeroComprobante").value("FAC-01"))
                .andExpect(jsonPath("$.detalles[0].subtotal").value(0.51))
                .andExpect(jsonPath("$.totalCompra").value(6.91)).andReturn();
        var ubicacion = alta.getResponse().getHeader("Location");
        var id = Long.valueOf(ubicacion.substring("/api/compras/".length()));
        assertThat(compras.obtenerPorId(id).fechaCompra()).isBetween(antes, LocalDateTime.now(relojPedidos));
        assertThat(contar("movimientos_caja")).isEqualTo(movimientos);

        var admin = token(usuario(CodigoRol.ADMINISTRADOR));
        mvc.perform(get(ubicacion).header(AUTHORIZATION, admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.detalles.length()").value(2));
        mvc.perform(post("/api/compras").header(AUTHORIZATION, admin).contentType(APPLICATION_JSON)
                        .content(cuerpo(proveedorId, "FAC-01", linea(primero, "1", "3"))))
                .andExpect(status().isCreated());
        var hoy = LocalDate.now(relojPedidos).toString();
        mvc.perform(get("/api/compras").header(AUTHORIZATION, autorizacion)
                        .param("proveedorId", proveedorId.toString()).param("fechaDesde", hoy).param("fechaHasta", hoy)
                        .param("estadoPagoCodigo", "pendiente").param("numeroComprobante", "FAC-01").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(id.intValue() + 1))
                .andExpect(jsonPath("$.content[0].detalles").doesNotExist());
        var ayer = LocalDate.now(relojPedidos).minusDays(1).toString();
        var manana = LocalDate.now(relojPedidos).plusDays(1).toString();
        for (var filtro : List.of("proveedorId", "fechaDesde", "fechaHasta", "estadoPagoCodigo", "numeroComprobante")) {
            var valor = switch (filtro) {
                case "proveedorId" -> Long.toString(Long.MAX_VALUE);
                case "fechaDesde" -> manana;
                case "fechaHasta" -> ayer;
                case "estadoPagoCodigo" -> "PAGADO";
                case "numeroComprobante" -> "OTRO";
                default -> throw new AssertionError(filtro);
            };
            var consulta = get("/api/compras").header(AUTHORIZATION, autorizacion).param(filtro, valor);
            if (!filtro.equals("proveedorId")) {
                consulta.param("proveedorId", proveedorId.toString());
            }
            mvc.perform(consulta)
                    .andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements").value(0));
        }
        proveedorService.desactivar(proveedorId);
        mvc.perform(get("/api/proveedores/{id}/compras", proveedorId).header(AUTHORIZATION, autorizacion))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements").value(2));
        mvc.perform(get("/api/proveedores/{id}/compras", Long.MAX_VALUE).header(AUTHORIZATION, autorizacion))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/compras/{id}", Long.MAX_VALUE).header(AUTHORIZATION, autorizacion))
                .andExpect(status().isNotFound());
        mvc.perform(delete(ubicacion).header(AUTHORIZATION, admin)).andExpect(status().isForbidden());
    }

    @Test
    void actualizaAgregadoYCambiaProveedorConservandoAutorFechaYPrecio() throws Exception {
        var compra = crear("10.05");
        var detalle = compra.detalles().getFirst();
        var nuevoProveedor = proveedor();
        asociaciones.asociar(nuevoProveedor, new AsociarInsumoRequest(primero));
        asociaciones.asociar(nuevoProveedor, new AsociarInsumoRequest(segundo));
        var mantener = """
                {"detalleId":%d,"insumoId":%d,"cantidad":2}
                """.formatted(detalle.id(), primero);
        enviar(put("/api/compras/{id}", compra.id()), cuerpo(nuevoProveedor, "EDIT", mantener + "," + linea(segundo, "1", "4")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalCompra").value(24.10));
        var modificada = compras.obtenerPorId(compra.id());
        assertThat(modificada.usuarioId()).isEqualTo(compra.usuarioId());
        assertThat(modificada.fechaCompra()).isEqualTo(compra.fechaCompra());
        assertThat(modificada.proveedorId()).isEqualTo(nuevoProveedor);
        assertThat(modificada.detalles().getFirst().precioCompra()).isEqualByComparingTo("10.05");
        var omitido = modificada.detalles().getLast().id();
        var actualizar = new ActualizarDetalleCompraRequest(detalle.id(), primero, BigDecimal.ONE, new BigDecimal("5.75"));
        var finalizada = compras.actualizarCompra(compra.id(), new ActualizarCompraRequest(nuevoProveedor, null, List.of(actualizar)));
        assertThat(finalizada.detalles()).hasSize(1);
        assertThat(finalizada.totalCompra()).isEqualByComparingTo("5.75");
        assertThat(jdbc.queryForObject("select count(*) from detalles_compra where id_detalle_compra = ?", Long.class, omitido))
                .isZero();
        var reemplazo = new ActualizarDetalleCompraRequest(detalle.id(), segundo, BigDecimal.ONE, new BigDecimal("7.50"));
        var reemplazada = compras.actualizarCompra(compra.id(), new ActualizarCompraRequest(nuevoProveedor, null, List.of(reemplazo)));
        assertThat(reemplazada.detalles().getFirst().id()).isEqualTo(detalle.id());
        assertThat(reemplazada.detalles().getFirst().insumoId()).isEqualTo(segundo);
        assertThat(reemplazada.totalCompra()).isEqualByComparingTo("7.50");
    }

    @Test
    void rechazaCatalogosInvalidosYConservaHistorialTrasDesasociar() throws Exception {
        var compra = crear("1");
        var linea = linea(primero, "1", "1");
        enviar(post("/api/compras"), cuerpo(Long.MAX_VALUE, "", linea)).andExpect(status().isNotFound());
        enviar(post("/api/compras"), cuerpo(proveedorId, "", linea(Long.MAX_VALUE, "1", "1")))
                .andExpect(status().isNotFound());
        asociaciones.desasociar(proveedorId, primero);
        enviar(post("/api/compras"), cuerpo(proveedorId, "", linea)).andExpect(status().isConflict());
        assertThat(compras.obtenerPorId(compra.id())).isEqualTo(compra);
        insumoService.desactivar(primero);
        enviar(post("/api/compras"), cuerpo(proveedorId, "", linea)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("insumo")));
        // El servicio de categorías impide este estado. SQL permite preparar el caso defensivo.
        jdbc.update("update categorias_insumo set activo = false where id_categoria_insumo = ?", categoriaId);
        enviar(post("/api/compras"), cuerpo(proveedorId, "", linea(segundo, "1", "1"))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("categoría")));
        proveedorService.desactivar(proveedorId);
        enviar(post("/api/compras"), cuerpo(proveedorId, "", linea(segundo, "1", "1"))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("proveedor")));
    }

    @Test
    void rechazaDetallesInvalidosYRevierteUnPutParcial() throws Exception {
        var compra = crear("3");
        var otra = crear("4");
        var endpoint = put("/api/compras/{id}", compra.id());
        var original = compra.detalles().getFirst();
        var ajeno = otra.detalles().getFirst();
        var existente = """
                {"detalleId":%d,"insumoId":%d,"cantidad":2}
                """;
        enviar(endpoint, cuerpo(proveedorId, "", "")).andExpect(status().isBadRequest());
        enviar(endpoint, cuerpo(proveedorId, "", existente.formatted(ajeno.id(), primero))).andExpect(status().isBadRequest());
        enviar(endpoint, cuerpo(proveedorId, "", existente.formatted(original.id(), segundo))).andExpect(status().isBadRequest());
        enviar(endpoint, cuerpo(proveedorId, "", existente.formatted(original.id(), primero) + "," +
                existente.formatted(original.id(), segundo))).andExpect(status().isBadRequest());
        enviar(endpoint, cuerpo(proveedorId, "", existente.formatted(original.id(), primero) + "," +
                "{\"insumoId\":" + segundo + ",\"cantidad\":1}")).andExpect(status().isBadRequest());
        enviar(endpoint, cuerpo(proveedorId, "", existente.formatted(original.id(), primero) + "," +
                linea(segundo, "2", "99999999.99"))).andExpect(status().isBadRequest());
        assertThat(compras.obtenerPorId(compra.id())).isEqualTo(compra);
        assertThat(compras.obtenerPorId(otra.id())).isEqualTo(otra);
    }

    @Test
    void rechazaCamposDelServidorYComprasSinDetallesOConInsumoDuplicado() throws Exception {
        var json = cuerpo(proveedorId, "", linea(primero, "1", "1"));
        for (var campo : List.of("usuarioId", "fechaCompra", "totalCompra", "estadoPagoId")) {
            enviar(post("/api/compras"), "{\"" + campo + "\":1," + json.substring(1))
                    .andExpect(status().isBadRequest());
        }
        enviar(post("/api/compras"), json.replace("\"cantidad\"", "\"subtotal\":8,\"cantidad\""))
                .andExpect(status().isBadRequest());
        enviar(post("/api/compras"), cuerpo(proveedorId, "", "")).andExpect(status().isBadRequest());
        enviar(post("/api/compras"), cuerpo(proveedorId, "", linea(primero, "1", "1") + "," + linea(primero, "2", "4")))
                .andExpect(status().isBadRequest());
        enviar(post("/api/compras"), "{").andExpect(status().isBadRequest());
    }

    @Test
    void protegeIntegridadFinancieraYReferencialEnPostgresql() throws Exception {
        var compra = crear("3");
        var id = compra.id();
        var detalleId = compra.detalles().getFirst().id();
        for (var cambio : List.of("total_compra = 0", "id_usuario = -1", "id_estado_pago = -1", "id_proveedor = -1")) {
            assertThatThrownBy(() -> jdbc.update("update compras set " + cambio + " where id_compra = ?", id))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
        for (var cambio : List.of("cantidad = 0", "precio_compra = 0", "subtotal = 0", "subtotal = 2",
                "id_insumo = -1", "id_compra = -1")) {
            assertThatThrownBy(() -> jdbc.update("update detalles_compra set " + cambio + " where id_detalle_compra = ?", detalleId))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
        jdbc.update("""
                update compras set id_estado_pago = (select id_estado_pago from estados_pago where codigo = 'PAGADO')
                where id_compra = ?
                """, id);
        enviar(put("/api/compras/{id}", id), cuerpo(proveedorId, "", linea(primero, "1", "2")))
                .andExpect(status().isConflict());
    }

    @ParameterizedTest
    @ValueSource(strings = {"proveedor", "insumo", "asociacion", "categoria"})
    void registroConcurrenteEsperaAlCatalogoYValidaElEstadoConfirmado(String cambio) throws Exception {
        long antes = contar("compras");
        try (var ejecutor = Executors.newSingleThreadExecutor()) {
            var inicio = new CountDownLatch(1);
            var registro = new TransactionTemplate(transacciones).execute(status -> {
                switch (cambio) {
                    case "proveedor" -> proveedorService.desactivar(proveedorId);
                    case "insumo" -> insumoService.desactivar(primero);
                    case "asociacion" -> asociaciones.desasociar(proveedorId, primero);
                    case "categoria" -> jdbc.update("update categorias_insumo set activo = false where id_categoria_insumo = ?",
                            categoriaId);
                    default -> throw new AssertionError(cambio);
                }
                var futuro = ejecutor.submit(() -> {
                    inicio.countDown();
                    return crear("1");
                });
                esperarBloqueo(inicio, futuro);
                return futuro;
            });
            assertThatThrownBy(() -> registro.get(10, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(ConflictoException.class);
        }
        assertThat(contar("compras")).isEqualTo(antes);
    }

    @Test
    void dosModificacionesSerializadasReleenElPrecioConfirmado() throws Exception {
        var compra = crear("3");
        var id = compra.detalles().getFirst().id();
        try (var ejecutor = Executors.newSingleThreadExecutor()) {
            var inicio = new CountDownLatch(1);
            var segundoPut = new TransactionTemplate(transacciones).execute(status -> {
                compras.actualizarCompra(compra.id(), new ActualizarCompraRequest(proveedorId, "PRIMERO",
                        List.of(new ActualizarDetalleCompraRequest(id, primero, BigDecimal.ONE, new BigDecimal("9.50")))));
                var futuro = ejecutor.submit(() -> {
                    inicio.countDown();
                    return compras.actualizarCompra(compra.id(), new ActualizarCompraRequest(proveedorId, "SEGUNDO",
                            List.of(new ActualizarDetalleCompraRequest(id, primero, new BigDecimal("2"), null))));
                });
                esperarBloqueo(inicio, futuro);
                return futuro;
            });
            assertThat(segundoPut.get(10, TimeUnit.SECONDS).totalCompra()).isEqualByComparingTo("19.00");
        }
        assertThat(compras.obtenerPorId(compra.id()).numeroComprobante()).isEqualTo("SEGUNDO");
    }

    @Test
    void desasociacionEsperaAlPutYDespuesConservaElHistorial() throws Exception {
        var compra = crear("4");
        var detalleId = compra.detalles().getFirst().id();
        var cambio = new ActualizarCompraRequest(proveedorId, null,
                List.of(new ActualizarDetalleCompraRequest(detalleId, primero, new BigDecimal("2"), null)));
        try (var ejecutor = Executors.newSingleThreadExecutor()) {
            var inicio = new CountDownLatch(1);
            var desasociacion = new TransactionTemplate(transacciones).execute(status -> {
                compras.actualizarCompra(compra.id(), cambio);
                var futuro = ejecutor.submit(() -> {
                    inicio.countDown();
                    asociaciones.desasociar(proveedorId, primero);
                });
                esperarBloqueo(inicio, futuro);
                return futuro;
            });
            desasociacion.get(10, TimeUnit.SECONDS);
        }
        assertThat(compras.obtenerPorId(compra.id()).totalCompra()).isEqualByComparingTo("8.00");
        assertThatThrownBy(() -> compras.actualizarCompra(compra.id(), cambio)).isInstanceOf(ConflictoException.class);
    }

    @Test
    void recuperaLaCompraEnUnNuevoArranque() {
        var compra = crear("3.60");
        try (var contexto = EsquemaPostgresql.reinicio(CompraPostgresqlTest.class).run(
                "--server.port=0", "--spring.jpa.show-sql=false", "--lavanderia.jwt.secret=" + JWT)) {
            assertThat(contexto.getBean(CompraService.class).obtenerPorId(compra.id())).isEqualTo(compra);
        }
    }

    private static void esperarBloqueo(CountDownLatch inicio, Future<?> futuro) {
        try {
            assertThat(inicio.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> futuro.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError(ex);
        }
    }

    private CompraResponse crear(String precio) {
        var detalle = new DetalleCompraRequest(primero, BigDecimal.ONE, new BigDecimal(precio));
        return compras.crearCompra(new CrearCompraRequest(proveedorId, null, List.of(detalle)), empleado.getId());
    }

    private Long proveedor() {
        return proveedores.save(new Proveedor("Proveedor de pruebas", UUID.randomUUID().toString().substring(0, 12),
                "5550100", null)).getId();
    }

    private Usuario usuario(CodigoRol codigo) {
        var nombre = "comp" + UUID.randomUUID().toString().substring(0, 12);
        return usuarios.save(new Usuario(nombre, "hash-de-prueba", "Ana", nombre + "@example.com",
                roles.findByNombre(codigo).orElseThrow()));
    }

    private String token(Usuario usuario) {
        return "Bearer " + jwt.emitir(usuario.getId(), usuario.getVersionCredenciales().toString()).accessToken();
    }

    private long contar(String tabla) {
        return jdbc.queryForObject("select count(*) from " + tabla, Long.class);
    }

    private ResultActions enviar(MockHttpServletRequestBuilder request, String json) throws Exception {
        return mvc.perform(request.header(AUTHORIZATION, autorizacion).contentType(APPLICATION_JSON).content(json));
    }

    private static String cuerpo(Long proveedorId, String numero, String detalles) {
        return """
                {"proveedorId":%d,"numeroComprobante":"%s","detalles":[%s]}
                """.formatted(proveedorId, numero, detalles);
    }

    private static String linea(Long id, String cantidad, String precio) {
        return """
                {"insumoId":%d,"cantidad":%s,"precioCompra":%s}
                """.formatted(id, cantidad, precio);
    }
}
