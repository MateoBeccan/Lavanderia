package com.miraquetelavo.lavanderia.estadopedido.controller;

import com.miraquetelavo.lavanderia.common.exception.RecursoNoEncontradoException;
import com.miraquetelavo.lavanderia.estadopedido.dto.EstadoPedidoResponse;
import com.miraquetelavo.lavanderia.estadopedido.service.EstadoPedidoService;
import org.junit.jupiter.api.Test;
import com.miraquetelavo.lavanderia.security.CustomUserDetailsService;
import com.miraquetelavo.lavanderia.security.JwtService;
import com.miraquetelavo.lavanderia.security.SecurityConfig;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = EstadoPedidoController.class, properties = "lavanderia.jwt.secret=dGVzdC1vbmx5LXZlcmlmaWNhY2lvbi1qd3QtbGF2YW5kZXJpYQ==")
@Import({SecurityConfig.class, JwtService.class})
class EstadoPedidoControllerTest {

    @MockitoBean
    private CustomUserDetailsService cuentas;

    private static final String RUTA = "/api/estados-pedido";
    private static final EstadoPedidoResponse PENDIENTE =
            new EstadoPedidoResponse(7L, "PENDIENTE", "Pendiente", null, 1, true);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private EstadoPedidoService service;

    @Test
    @WithMockUser(roles = "EMPLEADO")
    void devuelveUnListadoSimple() throws Exception {
        when(service.listarActivos()).thenReturn(List.of(PENDIENTE));

        mvc.perform(get(RUTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].codigo").value("PENDIENTE"))
                .andExpect(jsonPath("$[0].orden").value(1));
    }

    @Test
    @WithMockUser(roles = "EMPLEADO")
    void devuelveElEstadoPorId() throws Exception {
        when(service.obtener(7L)).thenReturn(PENDIENTE);

        mvc.perform(get(RUTA + "/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.nombre").value("Pendiente"))
                .andExpect(jsonPath("$.activo").value(true));
    }

    @Test
    @WithMockUser(roles = "EMPLEADO")
    void devuelve404ConProblemDetail() throws Exception {
        when(service.obtener(7L)).thenThrow(new RecursoNoEncontradoException("Estado no encontrado."));

        mvc.perform(get(RUTA + "/7"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Estado no encontrado."));
    }

    @Test
    @WithMockUser(roles = "EMPLEADO")
    void devuelve400ParaUnIdMalformado() throws Exception {
        mvc.perform(get(RUTA + "/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(service);
    }

    @Test
    void exigeAutenticacion() throws Exception {
        mvc.perform(get(RUTA).accept(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
}
