package com.miraquetelavo.lavanderia.servicio.controller;

import com.miraquetelavo.lavanderia.servicio.dto.ServicioRequest;
import com.miraquetelavo.lavanderia.servicio.dto.ServicioResponse;
import com.miraquetelavo.lavanderia.servicio.service.ServicioService;
import com.miraquetelavo.lavanderia.servicio.entity.ModalidadCobro;
import com.miraquetelavo.lavanderia.common.exception.ConflictoException;
import com.miraquetelavo.lavanderia.common.exception.RecursoNoEncontradoException;
import org.junit.jupiter.api.Test;
import com.miraquetelavo.lavanderia.security.CustomUserDetailsService;
import com.miraquetelavo.lavanderia.security.JwtService;
import com.miraquetelavo.lavanderia.security.SecurityConfig;
import org.springframework.context.annotation.Import;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ServicioController.class, properties = "lavanderia.jwt.secret=dGVzdC1vbmx5LXZlcmlmaWNhY2lvbi1qd3QtbGF2YW5kZXJpYQ==")
@Import({SecurityConfig.class, JwtService.class})
class ServicioControllerTest {

    @MockitoBean
    private CustomUserDetailsService cuentas;

    private static final String RUTA = "/api/servicios";
    private static final String JSON_VALIDO = """
            {"nombre":"Lavado","precio":1200.50,"modalidadCobro":"KILO"}
            """;
    private static final ServicioRequest SOLICITUD =
            new ServicioRequest("Lavado", new BigDecimal("1200.50"), ModalidadCobro.KILO);
    private static final ServicioResponse SERVICIO =
            new ServicioResponse(7L, "Lavado", SOLICITUD.precio(), ModalidadCobro.KILO, true);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ServicioService service;

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void registraYDevuelveLocation() throws Exception {
        when(service.registrar(SOLICITUD)).thenReturn(SERVICIO);

        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/servicios/7"))
                .andExpect(jsonPath("$.modalidadCobro").value("KILO"))
                .andExpect(jsonPath("$.activo").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void listaConMetadatosDePaginacion() throws Exception {
        var pageable = PageRequest.of(0, 20, Sort.by("id"));
        when(service.listarActivos(pageable)).thenReturn(new PageImpl<>(List.of(SERVICIO), pageable, 1));

        mvc.perform(get(RUTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(7))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void consultaYActualiza() throws Exception {
        when(service.obtener(7L)).thenReturn(SERVICIO);
        when(service.actualizar(7L, SOLICITUD)).thenReturn(SERVICIO);

        mvc.perform(get(RUTA + "/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));

        mvc.perform(put(RUTA + "/7").contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "\"\"", "\"   \""})
    @WithMockUser(roles = "ADMINISTRADOR")
    void rechazaNombreInvalido(String nombre) throws Exception {
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                        .content(JSON_VALIDO.replace("\"Lavado\"", nombre)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errores[0].campo").value("nombre"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "-1", "0", "10.001", "100000000.00"})
    @WithMockUser(roles = "ADMINISTRADOR")
    void rechazaPrecioInvalidoAlActualizar(String precio) throws Exception {
        mvc.perform(put(RUTA + "/7").contentType(MediaType.APPLICATION_JSON)
                        .content(JSON_VALIDO.replace("1200.50", precio)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("precio"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"HORA\"", "0", "\"0\"", "null"})
    @WithMockUser(roles = "ADMINISTRADOR")
    void rechazaModalidadInvalida(String modalidad) throws Exception {
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                        .content(JSON_VALIDO.replace("\"KILO\"", modalidad)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void devuelve409ParaNombreDuplicado() throws Exception {
        when(service.registrar(SOLICITUD)).thenThrow(new ConflictoException("Ya existe un servicio con ese nombre.", null));
        when(service.actualizar(7L, SOLICITUD))
                .thenThrow(new ConflictoException("Ya existe un servicio con ese nombre.", null));

        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Ya existe un servicio con ese nombre."));

        mvc.perform(put(RUTA + "/7").contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ya existe un servicio con ese nombre."));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void devuelve400ParaJsonMalformado() throws Exception {
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Se requiere un cuerpo JSON válido."));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void devuelve404ParaServicioNoEncontrado() throws Exception {
        when(service.obtener(7L)).thenThrow(new RecursoNoEncontradoException("No se encontró el servicio."));

        mvc.perform(get(RUTA + "/7"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void desactivaYDevuelve204() throws Exception {
        mvc.perform(delete(RUTA + "/7"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(service).desactivar(7L);
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void reactivaYDevuelveElServicioActivo() throws Exception {
        when(service.activar(7L)).thenReturn(SERVICIO);

        mvc.perform(patch(RUTA + "/7/activar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(true));
    }

    @Test
    @WithMockUser(roles = "EMPLEADO")
    void reservaLaBajaAlAdministrador() throws Exception {
        mvc.perform(delete(RUTA + "/7")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void exigeAutenticacion() throws Exception {
        mvc.perform(get(RUTA).accept(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
}
