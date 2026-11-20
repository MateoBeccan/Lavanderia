package com.miraquetelavo.lavanderia.comprobante;

import com.miraquetelavo.lavanderia.soporte.EsquemaPostgresql;
import com.miraquetelavo.lavanderia.soporte.IntegracionPostgresql;
import com.miraquetelavo.lavanderia.caja.dto.AbrirCajaRequest;
import com.miraquetelavo.lavanderia.caja.dto.CerrarCajaRequest;
import com.miraquetelavo.lavanderia.caja.entity.EstadoCaja;
import com.miraquetelavo.lavanderia.caja.service.CajaService;
import com.miraquetelavo.lavanderia.categoriainsumo.entity.CategoriaInsumo;
import com.miraquetelavo.lavanderia.categoriainsumo.repository.CategoriaInsumoRepository;
import com.miraquetelavo.lavanderia.cliente.entity.Cliente;
import com.miraquetelavo.lavanderia.cliente.repository.ClienteRepository;
import com.miraquetelavo.lavanderia.cobro.dto.CobroResponse;
import com.miraquetelavo.lavanderia.cobro.dto.RegistrarCobroRequest;
import com.miraquetelavo.lavanderia.cobro.service.CobroService;
import com.miraquetelavo.lavanderia.compra.dto.CrearCompraRequest;
import com.miraquetelavo.lavanderia.compra.service.CompraService;
import com.miraquetelavo.lavanderia.comprobante.dto.CrearComprobanteRequest;
import com.miraquetelavo.lavanderia.comprobante.service.ComprobanteService;
import com.miraquetelavo.lavanderia.detallecompra.dto.DetalleCompraRequest;
import com.miraquetelavo.lavanderia.detallepedido.dto.DetallePedidoRequest;
import com.miraquetelavo.lavanderia.insumo.entity.Insumo;
import com.miraquetelavo.lavanderia.insumo.repository.InsumoRepository;
import com.miraquetelavo.lavanderia.metodopago.repository.MetodoPagoRepository;
import com.miraquetelavo.lavanderia.movimientocaja.service.MovimientoCajaService;
import com.miraquetelavo.lavanderia.pagoproveedor.dto.PagoProveedorResponse;
import com.miraquetelavo.lavanderia.pagoproveedor.dto.RegistrarPagoProveedorRequest;
import com.miraquetelavo.lavanderia.pagoproveedor.service.PagoProveedorService;
import com.miraquetelavo.lavanderia.pedido.dto.CambiarEstadoPedidoRequest;
import com.miraquetelavo.lavanderia.pedido.dto.CrearPedidoRequest;
import com.miraquetelavo.lavanderia.pedido.service.PedidoService;
import com.miraquetelavo.lavanderia.prenda.entity.Prenda;
import com.miraquetelavo.lavanderia.prenda.repository.PrendaRepository;
import com.miraquetelavo.lavanderia.proveedor.entity.Proveedor;
import com.miraquetelavo.lavanderia.proveedor.repository.ProveedorRepository;
import com.miraquetelavo.lavanderia.proveedorinsumo.dto.AsociarInsumoRequest;
import com.miraquetelavo.lavanderia.proveedorinsumo.service.ProveedorInsumoService;
import com.miraquetelavo.lavanderia.rol.entity.CodigoRol;
import com.miraquetelavo.lavanderia.rol.repository.RolRepository;
import com.miraquetelavo.lavanderia.security.JwtService;
import com.miraquetelavo.lavanderia.servicio.entity.ModalidadCobro;
import com.miraquetelavo.lavanderia.servicio.entity.Servicio;
import com.miraquetelavo.lavanderia.servicio.repository.ServicioRepository;
import com.miraquetelavo.lavanderia.usuario.entity.Usuario;
import com.miraquetelavo.lavanderia.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@IntegracionPostgresql("comprobantes")
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "lavanderia.jwt.secret=${random.value}${random.value}",
        "spring.jpa.show-sql=false"
})
class ComprobantePostgresqlTest {
    @Autowired ComprobanteService comprobantes;
    @Autowired CobroService cobros;
    @Autowired PagoProveedorService pagos;
    @Autowired PedidoService pedidos;
    @Autowired CompraService compras;
    @Autowired CajaService cajas;
    @Autowired MovimientoCajaService movimientos;
    @Autowired ClienteRepository clientes;
    @Autowired PrendaRepository prendas;
    @Autowired ServicioRepository servicios;
    @Autowired ProveedorRepository proveedores;
    @Autowired CategoriaInsumoRepository categorias;
    @Autowired InsumoRepository insumos;
    @Autowired ProveedorInsumoService asociaciones;
    @Autowired UsuarioRepository usuarios;
    @Autowired RolRepository roles;
    @Autowired MetodoPagoRepository metodos;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @Autowired Clock reloj;

    private Usuario admin;
    private Proveedor proveedor;
    private long pedidoId;
    private long compraId;
    private long cajaId;
    private long efectivo;

    @BeforeEach
    void preparar() {
        admin = usuario(CodigoRol.ADMINISTRADOR);
        cajas.listarCajas(EstadoCaja.ABIERTA, null, null, Pageable.unpaged()).forEach(c -> {
            var efectivoDisponible = movimientos.consultarSaldo(c.id()).efectivoDisponible();
            cajas.cerrarCaja(c.id(), new CerrarCajaRequest(efectivoDisponible), admin.getId());
        });
        cajaId = cajas.abrirCaja(new AbrirCajaRequest(new BigDecimal("100")), admin.getId()).id();
        efectivo = metodos.findByCodigo("EFECTIVO").orElseThrow().getId();
        var sufijo = UUID.randomUUID().toString();
        proveedor = proveedores.save(new Proveedor("Proveedor " + sufijo, sufijo.substring(0, 20), "123", null));
        var categoria = categorias.save(new CategoriaInsumo("Categoría " + sufijo));
        var insumo = insumos.save(new Insumo("Insumo " + sufijo, categoria));
        asociaciones.asociar(proveedor.getId(), new AsociarInsumoRequest(insumo.getId()));
        compraId = compras.crearCompra(new CrearCompraRequest(proveedor.getId(), null,
                List.of(new DetalleCompraRequest(insumo.getId(), BigDecimal.ONE, new BigDecimal("100")))), admin.getId()).id();

        var cliente = clientes.save(new Cliente("Ana", "Pérez", null, null, null));
        var prenda = prendas.save(new Prenda("Camisa " + sufijo));
        var servicio = servicios.save(new Servicio("Lavado " + sufijo, new BigDecimal("100"), ModalidadCobro.UNIDAD));
        pedidoId = pedidos.registrar(new CrearPedidoRequest(cliente.getId(), hoy(), List.of(
                new DetallePedidoRequest(null, prenda.getId(), servicio.getId(), BigDecimal.ONE, null, null))), admin.getId()).id();
    }

    @Test
    void altaYConsultaDeAmbasOperacionesSinAlterarFinanzas() throws Exception {
        var cobro = cobrar("30");
        var pago = pagar("15");
        var saldoCaja = movimientos.consultarSaldo(cajaId);
        var saldoPedido = cobros.consultarSaldo(pedidoId);
        var saldoCompra = pagos.consultarSaldoCompra(compraId);
        var cantidadMovimientos = cantidadMovimientos();
        var token = token(admin);
        var empleado = usuario(CodigoRol.EMPLEADO);
        var tokenEmpleado = token(empleado);
        var rutaCobro = rutaCobro(cobro.id());
        var rutaPago = "/api/pagos-proveedor/" + pago.id() + "/comprobante";
        var fechaHistorica = hoy().minusDays(10);

        mvc.perform(get(rutaCobro).header(AUTHORIZATION, token)).andExpect(status().isNotFound());
        var altaCobro = mvc.perform(post(rutaCobro).header(AUTHORIZATION, token).contentType(APPLICATION_JSON)
                        .content("""
                                {"fechaEmision":"%s","observaciones":"  %s  "}
                                """.formatted(fechaHistorica, "x".repeat(200))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.tipoOperacion").value("COBRO"))
                .andExpect(jsonPath("$.pedidoId").value(pedidoId))
                .andExpect(jsonPath("$.cobroId").value(cobro.id()))
                .andExpect(jsonPath("$.pagoProveedorId").isEmpty())
                .andExpect(jsonPath("$.observaciones").value("x".repeat(200)))
                .andExpect(jsonPath("$.fechaEmision").value(fechaHistorica.toString()))
                .andExpect(jsonPath("$.montoOperacion").value(30))
                .andExpect(jsonPath("$.metodoPagoCodigo").value(cobro.metodoPagoCodigo()))
                .andExpect(jsonPath("$.metodoPagoNombre").value(cobro.metodoPagoNombre()))
                .andExpect(jsonPath("$.movimientoCajaId").value(cobro.movimientoCajaId())).andReturn();
        mvc.perform(get(altaCobro.getResponse().getHeader("Location")).header(AUTHORIZATION, tokenEmpleado))
                .andExpect(status().isOk());
        mvc.perform(get(rutaCobro).header(AUTHORIZATION, tokenEmpleado)).andExpect(status().isOk());

        var altaPago = mvc.perform(post(rutaPago).header(AUTHORIZATION, tokenEmpleado).contentType(APPLICATION_JSON)
                        .content(cuerpo(hoy())))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.tipoOperacion").value("PAGO_PROVEEDOR"))
                .andExpect(jsonPath("$.compraId").value(compraId))
                .andExpect(jsonPath("$.pagoProveedorId").value(pago.id()))
                .andExpect(jsonPath("$.pedidoId").isEmpty())
                .andExpect(jsonPath("$.observaciones").isEmpty())
                .andExpect(jsonPath("$.montoOperacion").value(15))
                .andExpect(jsonPath("$.movimientoCajaId").value(pago.movimientoCajaId())).andReturn();
        mvc.perform(get(altaPago.getResponse().getHeader("Location")).header(AUTHORIZATION, token))
                .andExpect(status().isOk());
        mvc.perform(get(rutaPago).header(AUTHORIZATION, token)).andExpect(status().isOk());

        for (var ruta : List.of(rutaCobro, rutaPago)) {
            mvc.perform(post(ruta).header(AUTHORIZATION, token).contentType(APPLICATION_JSON).content(cuerpo(hoy())))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value("La operación ya tiene un comprobante."));
        }
        empleado.desactivar();
        usuarios.save(empleado);
        mvc.perform(get(rutaCobro).header(AUTHORIZATION, tokenEmpleado)).andExpect(status().isUnauthorized());

        assertThat(cantidadMovimientos()).isEqualTo(cantidadMovimientos);
        assertThat(movimientos.consultarSaldo(cajaId)).isEqualTo(saldoCaja);
        assertThat(cobros.consultarSaldo(pedidoId)).isEqualTo(saldoPedido);
        assertThat(pagos.consultarSaldoCompra(compraId)).isEqualTo(saldoCompra);
        assertThat(cobros.obtener(cobro.id())).isEqualTo(cobro);
        assertThat(pagos.obtenerPorId(pago.id())).isEqualTo(pago);
    }

    @Test
    void paginaConOrdenEstableYConservaHistorialDePadresHistoricos() throws Exception {
        var fecha = hoy();
        var primero = comprobantes.crearDeCobro(cobrar("10").id(), new CrearComprobanteRequest(fecha, null));
        var segundo = comprobantes.crearDeCobro(cobrar("10").id(), new CrearComprobanteRequest(fecha, "   "));
        var tercero = comprobantes.crearDeCobro(cobrar("80").id(), new CrearComprobanteRequest(fecha.minusDays(1), null));
        for (var estado : List.of("EN_PROCESO", "LISTO", "ENTREGADO")) {
            pedidos.cambiarEstado(pedidoId, new CambiarEstadoPedidoRequest(estado, null), admin.getId());
        }
        var primerPago = comprobantes.crearDePagoProveedor(pagar("10").id(), new CrearComprobanteRequest(fecha, null));
        var segundoPago = comprobantes.crearDePagoProveedor(pagar("90").id(), new CrearComprobanteRequest(fecha, null));
        proveedor.desactivar();
        proveedores.save(proveedor);

        assertThat(segundo.observaciones()).isNull();
        mvc.perform(get("/api/pedidos/{id}/comprobantes", pedidoId).header(AUTHORIZATION, token(admin))
                        .param("size", "2").param("sort", "id,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].id").value(segundo.id()))
                .andExpect(jsonPath("$.content[1].id").value(primero.id()));
        assertThat(comprobantes.listarPorPedido(pedidoId, PageRequest.of(1, 2)).getContent())
                .extracting(c -> c.id()).containsExactly(tercero.id());
        mvc.perform(get("/api/compras/{id}/comprobantes", compraId).header(AUTHORIZATION, token(admin))
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(segundoPago.id()));
        assertThat(comprobantes.listarPorCompra(compraId, PageRequest.of(1, 1)).getContent())
                .extracting(c -> c.id()).containsExactly(primerPago.id());
    }

    @Test
    void confirmaRestriccionesRealesDePostgresql() {
        var cobro = cobrar("10");
        var pago = pagar("10");
        assertThat(jdbc.queryForList("""
                select conname from pg_constraint
                where conrelid = 'comprobantes'::regclass and convalidated and (contype <> 'f' or confdeltype = 'a')
                """, String.class)).contains("ck_comprobantes_operacion", "fk_comprobantes_cobro",
                "fk_comprobantes_pago", "uk_comprobantes_cobro", "uk_comprobantes_pago", "comprobantes_pkey");

        rechazaInsert(null, null, "23514", "ck_comprobantes_operacion");
        rechazaInsert(cobro.id(), pago.id(), "23514", "ck_comprobantes_operacion");
        rechazaInsert(Long.MAX_VALUE, null, "23503", "fk_comprobantes_cobro");
        rechazaInsert(null, Long.MAX_VALUE, "23503", "fk_comprobantes_pago");
        insertar(cobro.id(), null);
        insertar(null, pago.id());
        rechazaInsert(cobro.id(), null, "23505", "uk_comprobantes_cobro");
        rechazaInsert(null, pago.id(), "23505", "uk_comprobantes_pago");
    }

    @Test
    void dosAltasConcurrentesDevuelvenUn201YUn409() throws Exception {
        var cobro = cobrar("30");
        var token = token(admin);
        var barrera = new CyclicBarrier(2);
        try (var ejecutor = Executors.newFixedThreadPool(2)) {
            var altas = tx.execute(status -> {
                // El bloqueo permite las dos consultas de existencia y retiene ambas inserciones.
                jdbc.execute("lock table comprobantes in share mode");
                var primero = ejecutor.submit(() -> crearConcurrente(cobro.id(), token, barrera));
                var segundo = ejecutor.submit(() -> crearConcurrente(cobro.id(), token, barrera));
                esperarAmbasInserciones();
                return List.of(primero, segundo);
            });
            var primero = altas.getFirst();
            var segundo = altas.getLast();
            assertThat(List.of(primero.get(15, TimeUnit.SECONDS), segundo.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(jdbc.queryForObject("select count(*) from comprobantes where id_cobro = ?", Long.class, cobro.id()))
                .isEqualTo(1);
    }

    @Test
    void revierteElAltaSiLaTransaccionFallaDespuesDeGuardar() {
        var cobro = cobrar("10");
        var fallo = new IllegalStateException("Fallo posterior al alta");
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            comprobantes.crearDeCobro(cobro.id(), new CrearComprobanteRequest(hoy(), null));
            throw fallo;
        })).isSameAs(fallo);
        assertThat(comprobantes.listarPorPedido(pedidoId, PageRequest.of(0, 5))).isEmpty();
        assertThat(comprobantes.crearDeCobro(cobro.id(), new CrearComprobanteRequest(hoy(), null)).cobroId())
                .isEqualTo(cobro.id());
    }

    @Test
    void nuevoArranqueConservaElHistorialYReinstalaCheckFaltante() {
        var previo = comprobantes.crearDePagoProveedor(pagar("10").id(), new CrearComprobanteRequest(hoy(), null));
        jdbc.execute("alter table comprobantes drop constraint ck_comprobantes_operacion");

        try (var app = EsquemaPostgresql.reinicio(ComprobantePostgresqlTest.class).run(
                "--server.port=0", "--lavanderia.jwt.secret=${random.value}${random.value}",
                "--spring.jpa.show-sql=false")) {
            var servicio = app.getBean(ComprobanteService.class);
            assertThat(servicio.obtener(previo.id())).isEqualTo(previo);
            rechazaInsert(null, null, "23514", "ck_comprobantes_operacion");
        }
    }

    @Test
    void devuelve404AlConsultarRecursosInexistentes() throws Exception {
        for (var ruta : List.of("/api/comprobantes/{id}", "/api/cobros/{id}/comprobante",
                "/api/pagos-proveedor/{id}/comprobante", "/api/pedidos/{id}/comprobantes", "/api/compras/{id}/comprobantes")) {
            mvc.perform(get(ruta, Long.MAX_VALUE).header(AUTHORIZATION, token(admin)))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
        }
    }

    private int crearConcurrente(long id, String token, CyclicBarrier barrera) throws Exception {
        barrera.await(5, TimeUnit.SECONDS);
        return mvc.perform(post(rutaCobro(id)).header(AUTHORIZATION, token).contentType(APPLICATION_JSON)
                .content(cuerpo(hoy()))).andReturn().getResponse().getStatus();
    }

    private void esperarAmbasInserciones() {
        var limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        do {
            var bloqueadas = jdbc.queryForObject("""
                    select count(*) from pg_locks
                    where relation = 'comprobantes'::regclass
                        and mode = 'RowExclusiveLock' and not granted
                    """, Integer.class);
            if (bloqueadas == 2) {
                return;
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(20));
        } while (System.nanoTime() < limite);
        fail("Las dos altas no llegaron a competir por la inserción.");
    }

    private long cantidadMovimientos() {
        return jdbc.queryForObject("select count(*) from movimientos_caja", Long.class);
    }

    private void rechazaInsert(Long cobroId, Long pagoId, String codigo, String restriccion) {
        assertThatThrownBy(() -> insertar(cobroId, pagoId))
                .isInstanceOfSatisfying(DataIntegrityViolationException.class, ex ->
                        assertThat(ex.getMostSpecificCause()).isInstanceOfSatisfying(PSQLException.class, pg -> {
                            assertThat(pg.getSQLState()).isEqualTo(codigo);
                            assertThat(pg.getServerErrorMessage().getConstraint()).isEqualTo(restriccion);
                        }));
    }

    private void insertar(Long cobroId, Long pagoId) {
        jdbc.update("insert into comprobantes (id_cobro, id_pago, fecha_emision) values (?, ?, ?)", cobroId, pagoId, hoy());
    }

    private LocalDate hoy() {
        return LocalDate.now(reloj);
    }

    private CobroResponse cobrar(String monto) {
        return cobros.registrar(pedidoId, new RegistrarCobroRequest(new BigDecimal(monto), efectivo), admin.getId());
    }

    private PagoProveedorResponse pagar(String monto) {
        return pagos.registrarPago(compraId, new RegistrarPagoProveedorRequest(new BigDecimal(monto), efectivo), admin.getId());
    }

    private Usuario usuario(CodigoRol codigo) {
        var rol = roles.findByNombre(codigo).orElseThrow();
        var nombre = UUID.randomUUID().toString();
        return usuarios.save(new Usuario(nombre, "hash-de-prueba", "Ana", nombre + "@example.com", rol));
    }

    private String token(Usuario usuario) {
        return "Bearer " + jwt.emitir(usuario.getId(), usuario.getVersionCredenciales().toString()).accessToken();
    }

    private static String rutaCobro(long id) {
        return "/api/cobros/" + id + "/comprobante";
    }

    private static String cuerpo(LocalDate fecha) {
        return "{\"fechaEmision\":\"" + fecha + "\"}";
    }
}
