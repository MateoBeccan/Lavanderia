package com.miraquetelavo.lavanderia.pedido;

import com.miraquetelavo.lavanderia.common.exception.ConflictoException;
import com.miraquetelavo.lavanderia.common.exception.RecursoNoEncontradoException;
import com.miraquetelavo.lavanderia.common.exception.SolicitudInvalidaException;
import com.miraquetelavo.lavanderia.historialestadopedido.dto.HistorialEstadoPedidoResponse;
import com.miraquetelavo.lavanderia.historialestadopedido.service.HistorialEstadoPedidoService;
import com.miraquetelavo.lavanderia.pedido.controller.PedidoController;
import com.miraquetelavo.lavanderia.pedido.dto.CambiarEstadoPedidoRequest;
import com.miraquetelavo.lavanderia.pedido.dto.CancelarPedidoRequest;
import com.miraquetelavo.lavanderia.pedido.dto.PedidoResponse;
import com.miraquetelavo.lavanderia.pedido.service.PedidoService;
import com.miraquetelavo.lavanderia.security.CustomUserDetailsService;
import com.miraquetelavo.lavanderia.security.JwtService;
import com.miraquetelavo.lavanderia.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = PedidoController.class, properties =
        "lavanderia.jwt.secret=dGVzdC1vbmx5LXZlcmlmaWNhY2lvbi1qd3QtbGF2YW5kZXJpYQ==")
@Import({SecurityConfig.class, JwtService.class})
class PedidoControllerTest {
    private static final String RUTA = "/api/pedidos";
    private static final String JSON = """
            {"clienteId":2,"fechaEntregaEstimada":"2027-01-01",
             "detalles":[{"prendaId":3,"servicioId":4,"cantidad":1}]}
            """;

    @Autowired MockMvc mvc;
    @Autowired JwtService tokens;
    @MockitoBean CustomUserDetailsService cuentas;
    @MockitoBean PedidoService servicio;
    @MockitoBean HistorialEstadoPedidoService historial;

    @ParameterizedTest
    @ValueSource(strings = {"ADMINISTRADOR", "EMPLEADO"})
    void permiteTransicionesCancelacionEHistorialConAmbosRoles(String rol) throws Exception {
        var autenticado = jwt().jwt(j -> j.subject("11")).authorities(new SimpleGrantedAuthority("ROLE_" + rol));
        var estado = new CambiarEstadoPedidoRequest("EN_PROCESO", "Lavado");
        var cancelacion = new CancelarPedidoRequest("Solicitud del cliente");
        var respuesta = new PedidoResponse(7L, 2L, "Ana", "Pérez", 9L, "Leo",
                new PedidoResponse.Estado(2L, "EN_PROCESO", "En proceso"), null, null, null, List.of());
        when(servicio.cambiarEstado(7L, estado, 11L)).thenReturn(respuesta);
        when(servicio.cancelar(7L, cancelacion, 11L)).thenReturn(new PedidoResponse(7L, 2L, "Ana", "Pérez", 9L, "Leo",
                new PedidoResponse.Estado(5L, "CANCELADO", "Cancelado"), null, null, null, List.of()));
        when(historial.listar(7L)).thenReturn(List.of(new HistorialEstadoPedidoResponse(
                1L, null, null, "PENDIENTE", "Pendiente", 9L, "Leo",
                LocalDateTime.of(2026, 11, 17, 12, 0), "Pedido registrado")));

        mvc.perform(patch(RUTA + "/7/estado").with(autenticado).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigoEstado\":\"EN_PROCESO\",\"observacion\":\"Lavado\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estadoPedido.codigo").value("EN_PROCESO"));
        mvc.perform(patch(RUTA + "/7/cancelar").with(autenticado).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"observacion\":\"Solicitud del cliente\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estadoPedido.codigo").value("CANCELADO"));
        mvc.perform(get(RUTA + "/7/historial").with(autenticado))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].usuarioId").value(9))
                .andExpect(jsonPath("$[0].estadoAnteriorCodigo").doesNotExist());
        verify(servicio).cambiarEstado(7L, estado, 11L);
        verify(servicio).cancelar(7L, cancelacion, 11L);
    }

    @Test
    void protegeLasRutasDeEstadoYElHistorial() throws Exception {
        var otro = jwt().authorities(new SimpleGrantedAuthority("ROLE_OTRO"));
        var empleado = jwt().authorities(new SimpleGrantedAuthority("ROLE_EMPLEADO"));
        for (var ruta : List.of("/estado", "/cancelar")) {
            mvc.perform(patch(RUTA + "/7" + ruta)).andExpect(status().isUnauthorized());
            mvc.perform(patch(RUTA + "/7" + ruta).with(otro)).andExpect(status().isForbidden());
        }
        var ruta = RUTA + "/7/historial";
        mvc.perform(get(ruta)).andExpect(status().isUnauthorized());
        mvc.perform(get(ruta).with(otro)).andExpect(status().isForbidden());
        mvc.perform(put(ruta + "/1").with(empleado)).andExpect(status().isForbidden());
        mvc.perform(delete(ruta + "/1").with(empleado)).andExpect(status().isForbidden());
        verifyNoInteractions(servicio, historial);
    }

    @Test
    void devuelveProblemDetailParaErroresDeEstado() throws Exception {
        var empleado = jwt().jwt(j -> j.subject("11")).authorities(new SimpleGrantedAuthority("ROLE_EMPLEADO"));
        when(servicio.cambiarEstado(eq(7L), any(), eq(11L)))
                .thenThrow(new SolicitudInvalidaException("Estado desconocido."));
        when(servicio.cancelar(eq(7L), any(), eq(11L))).thenThrow(new ConflictoException("Pedido no cancelable.", null));
        when(historial.listar(7L)).thenThrow(new RecursoNoEncontradoException("Pedido inexistente."));
        mvc.perform(patch(RUTA + "/7/estado").with(empleado).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigoEstado\":\"OTRO\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        mvc.perform(patch(RUTA + "/7/estado").with(empleado).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        mvc.perform(patch(RUTA + "/7/cancelar").with(empleado).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"observacion\":\"  \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        mvc.perform(patch(RUTA + "/7/cancelar").with(empleado).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"observacion\":\"Motivo\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
        mvc.perform(get(RUTA + "/7/historial").with(empleado))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
        verify(servicio).cambiarEstado(7L, new CambiarEstadoPedidoRequest("OTRO", null), 11L);
        verify(servicio).cancelar(7L, new CancelarPedidoRequest("Motivo"), 11L);
        verifyNoMoreInteractions(servicio);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMINISTRADOR", "EMPLEADO"})
    void tomaElResponsableDelJwtBearer(String rol) throws Exception {
        var token = tokens.emitir(11L, "version").accessToken();
        when(cuentas.autenticar(any())).thenAnswer(invocacion ->
                new JwtAuthenticationToken(invocacion.getArgument(0), List.of(new SimpleGrantedAuthority("ROLE_" + rol))));
        when(servicio.registrar(any(), eq(11L))).thenReturn(
                new PedidoResponse(7L, 2L, "Ana", "Pérez", 11L, "Leo", null, null, null, null, List.of()));

        mvc.perform(post(RUTA).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(JSON))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/pedidos/7"))
                .andExpect(jsonPath("$.usuarioId").value(11))
                .andExpect(jsonPath("$.password").doesNotExist());
        verify(servicio).registrar(any(), eq(11L));
    }

    @Test
    void distingue401Y403() throws Exception {
        mvc.perform(get(RUTA)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        mvc.perform(get(RUTA).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OTRO"))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
        verifyNoInteractions(servicio);
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "[null]", "[{\"prendaId\":3,\"servicioId\":4,\"cantidad\":0}]"})
    void rechazaDetallesInvalidos(String detalles) throws Exception {
        mvc.perform(post(RUTA).with(jwt().jwt(j -> j.subject("11")).authorities(new SimpleGrantedAuthority("ROLE_EMPLEADO")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clienteId\":2,\"fechaEntregaEstimada\":\"2027-01-01\",\"detalles\":" + detalles + "}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores").isArray());
        verifyNoInteractions(servicio);
    }

    @Test
    void traduceAusenciaYConflicto() throws Exception {
        var empleado = jwt().authorities(new SimpleGrantedAuthority("ROLE_EMPLEADO"));
        when(servicio.obtener(7L)).thenThrow(new RecursoNoEncontradoException("Pedido inexistente."));
        when(servicio.actualizar(eq(7L), any())).thenThrow(new ConflictoException("Pedido cerrado.", null));
        mvc.perform(get(RUTA + "/7").with(empleado))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
        mvc.perform(put(RUTA + "/7").with(empleado).contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.replace("\"clienteId\":2,", "")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
    }
}
