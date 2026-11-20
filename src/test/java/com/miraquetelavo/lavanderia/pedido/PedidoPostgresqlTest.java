package com.miraquetelavo.lavanderia.pedido;

import com.miraquetelavo.lavanderia.soporte.EsquemaPostgresql;
import com.miraquetelavo.lavanderia.soporte.IntegracionPostgresql;
import com.miraquetelavo.lavanderia.cliente.entity.Cliente;
import com.miraquetelavo.lavanderia.cliente.repository.ClienteRepository;
import com.miraquetelavo.lavanderia.common.exception.ConflictoException;
import com.miraquetelavo.lavanderia.common.exception.RecursoNoEncontradoException;
import com.miraquetelavo.lavanderia.common.exception.SolicitudInvalidaException;
import com.miraquetelavo.lavanderia.detallepedido.dto.DetallePedidoRequest;
import com.miraquetelavo.lavanderia.historialestadopedido.dto.HistorialEstadoPedidoResponse;
import com.miraquetelavo.lavanderia.historialestadopedido.repository.HistorialEstadoPedidoRepository;
import com.miraquetelavo.lavanderia.historialestadopedido.service.HistorialEstadoPedidoService;
import com.miraquetelavo.lavanderia.pedido.dto.ActualizarPedidoRequest;
import com.miraquetelavo.lavanderia.pedido.dto.CambiarEstadoPedidoRequest;
import com.miraquetelavo.lavanderia.pedido.dto.CancelarPedidoRequest;
import com.miraquetelavo.lavanderia.pedido.dto.CrearPedidoRequest;
import com.miraquetelavo.lavanderia.pedido.dto.PedidoResponse;
import com.miraquetelavo.lavanderia.pedido.service.PedidoService;
import com.miraquetelavo.lavanderia.prenda.entity.Prenda;
import com.miraquetelavo.lavanderia.prenda.repository.PrendaRepository;
import com.miraquetelavo.lavanderia.rol.entity.CodigoRol;
import com.miraquetelavo.lavanderia.rol.repository.RolRepository;
import com.miraquetelavo.lavanderia.security.JwtService;
import com.miraquetelavo.lavanderia.servicio.entity.ModalidadCobro;
import com.miraquetelavo.lavanderia.servicio.entity.Servicio;
import com.miraquetelavo.lavanderia.servicio.repository.ServicioRepository;
import com.miraquetelavo.lavanderia.usuario.entity.Usuario;
import com.miraquetelavo.lavanderia.usuario.repository.UsuarioRepository;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@IntegracionPostgresql("pedido")
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "lavanderia.jwt.secret=dGVzdC1vbmx5LXZlcmlmaWNhY2lvbi1qd3QtbGF2YW5kZXJpYQ==",
        "spring.jpa.show-sql=false"
})
class PedidoPostgresqlTest {
    @Autowired PedidoService pedidos;
    @Autowired ClienteRepository clientes;
    @Autowired UsuarioRepository usuarios;
    @Autowired PrendaRepository prendas;
    @Autowired ServicioRepository servicios;
    @Autowired RolRepository roles;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transacciones;
    @Autowired Clock reloj;
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @Autowired HistorialEstadoPedidoService historial;
    @Autowired HistorialEstadoPedidoRepository asientos;

    private Cliente cliente;
    private Usuario usuario;
    private Prenda prenda;
    private Servicio unidad;
    private Servicio kilo;
    private LocalDate hoy;

    @BeforeEach
    void prepararCatalogos() {
        var sufijo = UUID.randomUUID().toString();
        cliente = clientes.save(new Cliente("Ana", "Pérez", null, null, null));
        prenda = prendas.save(new Prenda("Camisa " + sufijo));
        unidad = servicios.save(new Servicio("Unidad " + sufijo, new BigDecimal("13.00"), ModalidadCobro.UNIDAD));
        kilo = servicios.save(new Servicio("Kilo " + sufijo, new BigDecimal("90.28"), ModalidadCobro.KILO));
        var rol = roles.findByNombre(CodigoRol.EMPLEADO).orElseThrow();
        usuario = usuarios.save(new Usuario(sufijo, "hash-de-prueba", "Leo", sufijo + "@example.com", rol));
        hoy = LocalDate.now(reloj);
    }

    @Test
    void registraAltaYTransicionesEnOrdenConElResponsableYLaFechaDelServidor() throws Exception {
        var original = crear(linea(null, unidad, "2"));
        var administrador = nuevoAdministrador();
        var bearer = "Bearer " + jwt.emitir(administrador.getId(), administrador.getVersionCredenciales().toString()).accessToken();
        mvc.perform(patch("/api/pedidos/{id}/estado", original.id()).header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigoEstado\":\" en_proceso \",\"observacion\":\"En lavado\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estadoPedido.codigo").value("EN_PROCESO"));
        cambiar(original.id(), "LISTO");
        var entregado = cambiar(original.id(), "ENTREGADO");

        assertThat(entregado.detalles()).isEqualTo(original.detalles());
        assertThat(entregado.total()).isEqualTo(original.total());
        assertThat(entregado.fechaIngreso()).isEqualTo(original.fechaIngreso());
        assertThat(entregado.fechaEntregaEstimada()).isEqualTo(original.fechaEntregaEstimada());
        assertThat(entregado.clienteId()).isEqualTo(original.clienteId());
        assertThat(entregado.usuarioId()).isEqualTo(original.usuarioId());
        var entradas = historial.listar(original.id());
        assertThat(entradas).extracting(HistorialEstadoPedidoResponse::estadoNuevoCodigo)
                .containsExactly("PENDIENTE", "EN_PROCESO", "LISTO", "ENTREGADO");
        assertThat(entradas).extracting(HistorialEstadoPedidoResponse::estadoAnteriorCodigo)
                .containsExactly(null, "PENDIENTE", "EN_PROCESO", "LISTO");
        assertThat(entradas).isSortedAccordingTo(Comparator.comparing(HistorialEstadoPedidoResponse::fechaCambio)
                .thenComparing(HistorialEstadoPedidoResponse::id));
        assertThat(entradas.getFirst().estadoAnteriorNombre()).isNull();
        assertThat(entradas.getFirst().fechaCambio()).isEqualTo(original.fechaIngreso());
        assertThat(entradas.getFirst().observacion()).isEqualTo("Pedido registrado");
        assertThat(entradas).extracting(HistorialEstadoPedidoResponse::usuarioId)
                .containsExactly(usuario.getId(), administrador.getId(), usuario.getId(), usuario.getId());
        assertThat(entradas.get(1).observacion()).isEqualTo("En lavado");
        assertThatThrownBy(() -> cambiar(original.id(), "PENDIENTE")).isInstanceOf(ConflictoException.class);
        assertThat(historial.listar(original.id())).isEqualTo(entradas);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cancelaDesdePendienteOEnProcesoConservandoDetalles(boolean enProceso) {
        var original = crear(linea(null, unidad, "1"));
        if (enProceso) {
            cambiar(original.id(), "EN_PROCESO");
        }
        var cancelado = cancelar(original.id());
        assertThat(cancelado.estadoPedido().codigo()).isEqualTo("CANCELADO");
        assertThat(cancelado.detalles()).isEqualTo(original.detalles());
        assertThat(historial.listar(original.id()).getLast().observacion()).isEqualTo("Solicitud del cliente");
        assertThatThrownBy(() -> cambiar(original.id(), "PENDIENTE")).isInstanceOf(ConflictoException.class);
        assertThatThrownBy(() -> cancelar(original.id())).isInstanceOf(ConflictoException.class);
    }

    @Test
    void validaDestinoExistenciaYMotivoSinRegistrarCambiosFallidos() {
        var original = crear(linea(null, unidad, "1"));
        var id = original.id();
        assertThatThrownBy(() -> cambiar(Long.MAX_VALUE, "EN_PROCESO")).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> historial.listar(Long.MAX_VALUE)).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> cambiar(id, "DESCONOCIDO")).isInstanceOf(SolicitudInvalidaException.class);
        assertThatThrownBy(() -> cambiar(id, "CANCELADO")).isInstanceOf(SolicitudInvalidaException.class);
        assertThatThrownBy(() -> pedidos.cancelar(id, new CancelarPedidoRequest("x".repeat(251)), usuario.getId()))
                .isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> pedidos.cancelar(id, new CancelarPedidoRequest("motivo"), Long.MAX_VALUE))
                .isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> cambiar(id, "PENDIENTE")).isInstanceOf(ConflictoException.class);
        assertThatThrownBy(() -> cambiar(id, "ENTREGADO")).isInstanceOf(ConflictoException.class);
        transacciones.executeWithoutResult(t -> {
            jdbc.update("update estados_pedido set activo = false where codigo = 'EN_PROCESO'");
            assertThatThrownBy(() -> cambiar(id, "EN_PROCESO")).isInstanceOf(SolicitudInvalidaException.class);
            t.setRollbackOnly();
        });
        assertThat(historial.listar(id)).hasSize(1);
        cambiar(id, "EN_PROCESO");
        cambiar(id, "LISTO");
        assertThatThrownBy(() -> cancelar(id)).isInstanceOf(ConflictoException.class);
        assertThat(historial.listar(id)).hasSize(3);
    }

    @Test
    void identificaPedidosAnterioresSinInventarUnAsientoInicial() {
        var id = jdbc.queryForObject("""
                insert into pedidos (id_cliente, id_usuario, id_estado_pedido, id_estado_pago,
                                     fecha_ingreso, fecha_entrega_est, total_pedido)
                select ?, ?, operativo.id_estado_pedido, pago.id_estado_pago, ?, ?, 13.00
                from estados_pedido operativo join estados_pago pago using (codigo) where codigo = 'PENDIENTE'
                returning id_pedido
                """, Long.class, cliente.getId(), usuario.getId(), hoy.minusDays(1).atStartOfDay(), hoy);
        jdbc.update("""
                insert into detalles_pedido (id_pedido, id_prenda, id_servicio, cantidad, precio_unitario, subtotal)
                values (?, ?, ?, 1.00, 13.00, 13.00)
                """, id, prenda.getId(), unidad.getId());
        assertThat(historial.listar(id)).isEmpty();
        cambiar(id, "EN_PROCESO");
        assertThat(historial.listar(id)).singleElement().satisfies(entrada ->
                assertThat(entrada.estadoAnteriorCodigo()).isEqualTo("PENDIENTE"));
    }

    @Test
    void historialEsInmutableYPostgresConservaSusReferenciasEIndice() {
        var id = crear(linea(null, unidad, "1")).id();
        transacciones.executeWithoutResult(t -> {
            var asiento = asientos.findByPedido_IdOrderByFechaCambioAscIdAsc(id).getFirst();
            ReflectionTestUtils.setField(asiento, "observacion", "Intento de modificación");
        });
        assertThat(historial.listar(id).getFirst().observacion()).isEqualTo("Pedido registrado");
        assertThat(jdbc.queryForList("""
                select conname from pg_constraint
                where conrelid = 'historial_estados_pedido'::regclass and contype = 'f'
                    and convalidated and confdeltype = 'a'
                """, String.class)).containsExactlyInAnyOrder("fk_historial_pedido", "fk_historial_estado_anterior",
                        "fk_historial_estado_nuevo", "fk_historial_usuario");
        assertThat(jdbc.queryForObject("""
                select indexdef from pg_indexes where schemaname = current_schema() and indexname = 'ix_historial_pedido_fecha'
                """, String.class)).contains("(id_pedido, fecha_cambio, id_historial_estado_pedido)");
    }

    @Test
    void fallaRealDeInsercionDeHistorialRevierteAltaYTransicion() {
        rechazarInsercionDeHistorial(() -> crear(linea(null, unidad, "1")));
        assertThat(jdbc.queryForObject("select count(*) from pedidos where id_cliente = ?", Long.class, cliente.getId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from detalles_pedido where id_prenda = ?", Long.class, prenda.getId())).isZero();

        var original = crear(linea(null, unidad, "1"));
        rechazarInsercionDeHistorial(() -> cambiar(original.id(), "EN_PROCESO"));
        assertThat(pedidos.obtener(original.id())).isEqualTo(original);
        assertThat(historial.listar(original.id())).hasSize(1);
    }

    @Test
    void dosTransicionesIdenticasConcurrentesGeneranUnSoloAsientoNuevo() throws Exception {
        var original = crear(linea(null, unidad, "1"));
        var barrera = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var primera = executor.submit(() -> ejecutarEnCarrera(barrera, () -> cambiar(original.id(), "EN_PROCESO")));
            var segunda = executor.submit(() -> ejecutarEnCarrera(barrera, () -> cambiar(original.id(), "EN_PROCESO")));
            var resultados = List.of(primera.get(15, TimeUnit.SECONDS), segunda.get(15, TimeUnit.SECONDS));
            assertThat(resultados).filteredOn(ConflictoException.class::isInstance).hasSize(1);
            assertThat(resultados).filteredOn(PedidoResponse.class::isInstance).hasSize(1);
        }
        assertThat(historial.listar(original.id())).extracting(HistorialEstadoPedidoResponse::estadoNuevoCodigo)
                .containsExactly("PENDIENTE", "EN_PROCESO");
    }

    @Test
    void edicionYCancelacionConcurrentesRespetanElOrdenDelBloqueo() throws Exception {
        var original = crear(linea(null, unidad, "1"));
        var barrera = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var cancelacion = executor.submit(() -> ejecutarEnCarrera(barrera, () -> cancelar(original.id())));
            var edicion = executor.submit(() -> ejecutarEnCarrera(barrera, () -> pedidos.actualizar(original.id(),
                    editar(linea(original.detalles().getFirst().id(), unidad, "2")))));
            assertThat(cancelacion.get(15, TimeUnit.SECONDS)).isInstanceOfSatisfying(PedidoResponse.class, cancelado -> {
                var finalizado = pedidos.obtener(original.id());
                assertThat(finalizado).isEqualTo(cancelado);
                assertThat(finalizado.estadoPedido().codigo()).isEqualTo("CANCELADO");
            });
            var resultadoEdicion = edicion.get(15, TimeUnit.SECONDS);
            var total = resultadoEdicion instanceof PedidoResponse editado ? editado.total() : original.total();
            assertThat(pedidos.obtener(original.id()).total()).isEqualByComparingTo(total);
        }
        assertThat(historial.listar(original.id())).extracting(HistorialEstadoPedidoResponse::estadoNuevoCodigo)
                .containsExactly("PENDIENTE", "CANCELADO");
    }

    @Test
    void conservaHistorialDespuesDeReiniciarSpringBoot() {
        var argumentos = new String[]{
                "--server.port=0",
                "--spring.jpa.show-sql=false",
                "--lavanderia.jwt.secret=dGVzdC1vbmx5LXZlcmlmaWNhY2lvbi1qd3QtbGF2YW5kZXJpYQ=="
        };
        Long id;
        List<HistorialEstadoPedidoResponse> confirmado;
        try (var app = EsquemaPostgresql.reinicio(PedidoPostgresqlTest.class).run(argumentos)) {
            var servicio = app.getBean(PedidoService.class);
            id = servicio.registrar(new CrearPedidoRequest(cliente.getId(), hoy, List.of(linea(null, unidad, "1"))),
                    usuario.getId()).id();
            servicio.cancelar(id, new CancelarPedidoRequest("Motivo"), usuario.getId());
            confirmado = app.getBean(HistorialEstadoPedidoService.class).listar(id);
        }
        try (var app = EsquemaPostgresql.reinicio(PedidoPostgresqlTest.class).run(argumentos)) {
            assertThat(app.getBean(HistorialEstadoPedidoService.class).listar(id)).isEqualTo(confirmado).hasSize(2);
            assertThat(app.getBean(PedidoService.class).obtener(id).estadoPedido().codigo()).isEqualTo("CANCELADO");
        }
    }

    private void rechazarInsercionDeHistorial(Runnable operacion) {
        assertThatThrownBy(() -> transacciones.executeWithoutResult(t -> {
            jdbc.execute("alter table historial_estados_pedido add constraint ck_rechazar_historial_test check (false) not valid");
            operacion.run();
        })).isInstanceOf(DataIntegrityViolationException.class);
    }

    private Object ejecutarEnCarrera(CyclicBarrier barrera, java.util.function.Supplier<PedidoResponse> operacion) throws Exception {
        barrera.await(5, TimeUnit.SECONDS);
        try {
            return operacion.get();
        } catch (ConflictoException ex) {
            return ex;
        }
    }

    private Usuario nuevoAdministrador() {
        var sufijo = UUID.randomUUID().toString();
        var rol = roles.findByNombre(CodigoRol.ADMINISTRADOR).orElseThrow();
        return usuarios.save(new Usuario(sufijo, "hash-de-prueba", "Pat", sufijo + "@example.com", rol));
    }

    private PedidoResponse cambiar(Long id, String codigo) {
        return pedidos.cambiarEstado(id, new CambiarEstadoPedidoRequest(codigo, null), usuario.getId());
    }

    private PedidoResponse cancelar(Long id) {
        return pedidos.cancelar(id, new CancelarPedidoRequest("Solicitud del cliente"), usuario.getId());
    }

    @Test
    void guardaVariosDetallesYPaginaPorClienteEstadoYFecha() {
        var primero = crear(linea(null, unidad, "2"), linea(null, kilo, "1.25"));
        var segundo = crear(linea(null, unidad, "1"));

        assertThat(primero.estadoPedido().codigo()).isEqualTo("PENDIENTE");
        assertThat(primero.usuarioId()).isEqualTo(usuario.getId());
        assertThat(primero.total()).isEqualByComparingTo("138.85");
        assertThat(primero.detalles().get(1).subtotal()).isEqualByComparingTo("112.85");
        assertThat(pedidos.obtener(primero.id())).isEqualTo(primero);
        var pagina = pedidos.listar(cliente.getId(), "pendiente", hoy, hoy, PageRequest.of(1, 1));
        assertThat(pagina.getTotalElements()).isEqualTo(2);
        assertThat(pagina.getContent()).singleElement().satisfies(p -> assertThat(p.id()).isEqualTo(segundo.id()));
        assertThat(pedidos.listar(cliente.getId(), "LISTO", hoy, hoy, PageRequest.ofSize(1))).isEmpty();
        assertThat(pedidos.listar(cliente.getId(), null, hoy.minusDays(1), hoy.minusDays(1), PageRequest.ofSize(1))).isEmpty();
    }

    @Test
    void registraConJwtContraLaBaseYDevuelveResumenPaginado() throws Exception {
        var bearer = "Bearer " + jwt.emitir(usuario.getId(), usuario.getVersionCredenciales().toString()).accessToken();
        var respuesta = mvc.perform(post("/api/pedidos").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"clienteId":%d,"fechaEntregaEstimada":"%s",
                                 "detalles":[{"prendaId":%d,"servicioId":%d,"cantidad":2}]}
                                """.formatted(cliente.getId(), hoy, prenda.getId(), unidad.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.usuarioId").value(usuario.getId().intValue()))
                .andExpect(jsonPath("$.detalles[0].subtotal").value(26.0))
                .andReturn();
        var ubicacion = respuesta.getResponse().getHeader("Location");
        assertThat(ubicacion).startsWith("http://localhost/api/pedidos/");
        mvc.perform(get(ubicacion).header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estadoPedido.codigo").value("PENDIENTE"));
        mvc.perform(get("/api/pedidos").header("Authorization", bearer)
                        .param("clienteId", cliente.getId().toString()).param("estado", "PENDIENTE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].total").value(26.0));
    }

    @Test
    void conservaPrecioAlCambiarCantidadYAplicaPrecioActualALineasNuevas() {
        var original = crear(linea(null, unidad, "2"), linea(null, unidad, "1"));
        var conservado = original.detalles().get(0).id();
        var borrado = original.detalles().get(1).id();
        transacciones.executeWithoutResult(estado -> {
            var servicio = servicios.findOneById(unidad.getId()).orElseThrow();
            servicio.actualizarDatos(servicio.getNombre(), new BigDecimal("20.00"), ModalidadCobro.UNIDAD);
        });
        assertThat(pedidos.obtener(original.id()).total()).isEqualByComparingTo("39.00");

        var actualizado = pedidos.actualizar(original.id(), editar(linea(null, unidad, "1"), linea(conservado, unidad, "3")));
        assertThat(actualizado.detalles().get(0).id()).isEqualTo(conservado);
        assertThat(actualizado.detalles().get(0).precioUnitario()).isEqualByComparingTo("13.00");
        assertThat(actualizado.detalles().get(1).precioUnitario()).isEqualByComparingTo("20.00");
        assertThat(actualizado.total()).isEqualByComparingTo("59.00");
        assertThat(actualizado.usuarioId()).isEqualTo(original.usuarioId());
        assertThat(actualizado.fechaIngreso()).isEqualTo(original.fechaIngreso());
        assertThat(jdbc.queryForObject("select count(*) from detalles_pedido where id_detalle_pedido = ?", Long.class, borrado))
                .isZero();
        assertThat(pedidos.obtener(original.id())).isEqualTo(actualizado);

        var cambiado = pedidos.actualizar(original.id(), editar(linea(conservado, kilo, "1.25")));
        assertThat(cambiado.detalles()).singleElement().satisfies(l -> {
            assertThat(l.id()).isEqualTo(conservado);
            assertThat(l.precioUnitario()).isEqualByComparingTo("90.28");
        });
    }

    @Test
    void revierteCambiosSiFallaUnaLineaPosterior() {
        var original = crear(linea(null, unidad, "1"));
        var valido = linea(original.detalles().getFirst().id(), unidad, "2");
        assertThatThrownBy(() -> pedidos.actualizar(original.id(), editar(valido, linea(null, unidad, "0.5"))))
                .isInstanceOf(SolicitudInvalidaException.class);
        assertThat(pedidos.obtener(original.id())).isEqualTo(original);
        assertThatThrownBy(() -> crear(linea(null, unidad, "1"), linea(null, kilo, "0.01")))
                .isInstanceOf(SolicitudInvalidaException.class);
        assertThat(jdbc.queryForObject("select count(*) from pedidos where id_cliente = ?", Long.class, cliente.getId()))
                .isEqualTo(1L);
    }

    @Test
    void revierteInsercionYBorradoDeDetallesYaEnviadosAPostgresql() {
        var original = crear(linea(null, unidad, "1"));
        transacciones.executeWithoutResult(t -> {
            var actualizado = pedidos.actualizar(original.id(), editar(linea(null, kilo, "1.25")));
            assertThat(actualizado.detalles().getFirst().id()).isNotEqualTo(original.detalles().getFirst().id());
            t.setRollbackOnly();
        });
        assertThat(pedidos.obtener(original.id())).isEqualTo(original);
    }

    @Test
    void validaFechaCantidadesEIdentidadDeDetalles() {
        assertThatThrownBy(() -> pedidos.registrar(
                new CrearPedidoRequest(cliente.getId(), hoy.minusDays(1), List.of(linea(null, unidad, "1"))), usuario.getId()))
                .isInstanceOf(SolicitudInvalidaException.class);
        assertThatThrownBy(this::crear).isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> crear(linea(null, unidad, "-1"))).isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> crear(linea(null, unidad, "0"))).isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> crear(linea(null, kilo, "0.001"))).isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> crear(linea(null, unidad, "1.5"))).isInstanceOf(SolicitudInvalidaException.class);
        var porPrenda = servicios.save(new Servicio("Por prenda " + UUID.randomUUID(), BigDecimal.ONE, ModalidadCobro.PRENDA));
        assertThatThrownBy(() -> crear(linea(null, porPrenda, "1.5"))).isInstanceOf(SolicitudInvalidaException.class);
        assertThatThrownBy(() -> crear(linea(1L, unidad, "1"))).isInstanceOf(SolicitudInvalidaException.class);
        var original = crear(linea(null, unidad, "1"));
        var otro = crear(linea(null, unidad, "1"));
        assertThatThrownBy(() -> pedidos.actualizar(original.id(), editar(linea(otro.detalles().getFirst().id(), unidad, "1"))))
                .isInstanceOf(SolicitudInvalidaException.class);
        var repetida = linea(original.detalles().getFirst().id(), unidad, "1");
        assertThatThrownBy(() -> pedidos.actualizar(original.id(), editar(repetida, repetida)))
                .isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void rechazaReferenciasInexistentesOInactivas() {
        var linea = linea(null, unidad, "1");
        var solicitud = new CrearPedidoRequest(cliente.getId(), hoy, List.of(linea));
        assertThatThrownBy(() -> pedidos.registrar(solicitud, Long.MAX_VALUE)).isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> pedidos.registrar(new CrearPedidoRequest(Long.MAX_VALUE, hoy, List.of(linea)), usuario.getId()))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> crear(new DetallePedidoRequest(null, Long.MAX_VALUE, unidad.getId(), BigDecimal.ONE, null, null)))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> crear(new DetallePedidoRequest(null, prenda.getId(), Long.MAX_VALUE, BigDecimal.ONE, null, null)))
                .isInstanceOf(RecursoNoEncontradoException.class);

        transacciones.executeWithoutResult(t -> servicios.findOneById(unidad.getId()).orElseThrow().desactivar());
        assertThatThrownBy(() -> crear(linea)).isInstanceOf(RecursoNoEncontradoException.class);
        transacciones.executeWithoutResult(t -> prendas.findOneById(prenda.getId()).orElseThrow().desactivar());
        assertThatThrownBy(() -> crear(linea(null, kilo, "1"))).isInstanceOf(RecursoNoEncontradoException.class);
        transacciones.executeWithoutResult(t -> clientes.findOneById(cliente.getId()).orElseThrow().desactivar());
        assertThatThrownBy(() -> crear(linea)).isInstanceOf(RecursoNoEncontradoException.class);
        transacciones.executeWithoutResult(t -> usuarios.findById(usuario.getId()).orElseThrow().desactivar());
        assertThatThrownBy(() -> pedidos.registrar(solicitud, usuario.getId())).isInstanceOf(BadCredentialsException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ENTREGADO", "CANCELADO"})
    void impideEditarPedidosCerrados(String codigo) {
        var original = crear(linea(null, unidad, "1"));
        jdbc.update("""
                update pedidos set id_estado_pedido = (select id_estado_pedido from estados_pedido where codigo = ?)
                where id_pedido = ?
                """, codigo, original.id());
        assertThatThrownBy(() -> pedidos.actualizar(original.id(), editar(linea(null, unidad, "2"))))
                .isInstanceOf(ConflictoException.class);
        assertThat(pedidos.obtener(original.id()).total()).isEqualByComparingTo("13.00");
    }

    @Test
    void postgresAplicaLasClavesForaneasDePedidoYDetalles() {
        var restricciones = jdbc.queryForList("""
                select conname from pg_constraint
                where conrelid in ('pedidos'::regclass, 'detalles_pedido'::regclass) and contype = 'f' and convalidated
                """, String.class);
        assertThat(restricciones).containsExactlyInAnyOrder("fk_pedidos_cliente", "fk_pedidos_usuario", "fk_pedidos_estado",
                "fk_pedidos_estado_pago", "fk_detalles_pedido", "fk_detalles_prenda", "fk_detalles_servicio");
        var pedido = crear(linea(null, unidad, "1"));
        assertThatThrownBy(() -> jdbc.update("update detalles_pedido set id_prenda = ? where id_pedido = ?", Long.MAX_VALUE, pedido.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void dosActualizacionesConcurrentesNoPuedenConservarLineasQueLaOtraElimino() throws Exception {
        var original = crear(linea(null, unidad, "1"), linea(null, unidad, "2"));
        var barrera = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var primera = executor.submit(() -> editarEnParalelo(original, 0, barrera));
            var segunda = executor.submit(() -> editarEnParalelo(original, 1, barrera));
            var resultados = List.of(primera.get(15, TimeUnit.SECONDS), segunda.get(15, TimeUnit.SECONDS));
            assertThat(resultados).filteredOn(SolicitudInvalidaException.class::isInstance).hasSize(1);
            assertThat(resultados).filteredOn(PedidoResponse.class::isInstance).containsExactly(pedidos.obtener(original.id()));
        }
    }

    private Object editarEnParalelo(PedidoResponse original, int posicion, CyclicBarrier barrera) throws Exception {
        var id = original.detalles().get(posicion).id();
        barrera.await(5, TimeUnit.SECONDS);
        try {
            return pedidos.actualizar(original.id(), editar(linea(id, unidad, "3")));
        } catch (SolicitudInvalidaException ex) {
            return ex;
        }
    }

    private PedidoResponse crear(DetallePedidoRequest... lineas) {
        return pedidos.registrar(new CrearPedidoRequest(cliente.getId(), hoy, List.of(lineas)), usuario.getId());
    }

    private ActualizarPedidoRequest editar(DetallePedidoRequest... lineas) {
        return new ActualizarPedidoRequest(hoy.plusDays(1), List.of(lineas));
    }

    private DetallePedidoRequest linea(Long id, Servicio servicio, String cantidad) {
        return new DetallePedidoRequest(id, prenda.getId(), servicio.getId(), new BigDecimal(cantidad), null, "Recepción");
    }
}
