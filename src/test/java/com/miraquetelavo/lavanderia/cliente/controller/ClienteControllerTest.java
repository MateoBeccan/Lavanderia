package com.miraquetelavo.lavanderia.cliente.controller;

import com.miraquetelavo.lavanderia.cliente.dto.ClienteRequest;
import com.miraquetelavo.lavanderia.cliente.dto.ClienteResponse;
import com.miraquetelavo.lavanderia.cliente.service.ClienteService;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ClienteController.class, properties = "lavanderia.jwt.secret=dGVzdC1vbmx5LXZlcmlmaWNhY2lvbi1qd3QtbGF2YW5kZXJpYQ==")
@Import({SecurityConfig.class, JwtService.class})
class ClienteControllerTest {

    @MockitoBean
    private CustomUserDetailsService cuentas;

    private static final String RUTA = "/api/clientes";
    private static final String JSON_VALIDO = """
            {"nombre":"Ana","apellido":"Pérez","dni":"01234567"}
            """;
    private static final ClienteRequest SOLICITUD = new ClienteRequest("Ana", "Pérez", "01234567", null, null);
    private static final ClienteResponse CLIENTE = new ClienteResponse(
            7L, "Ana", "Pérez", "01234567", null, null, true, LocalDateTime.of(2026, 1, 1, 10, 0));

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ClienteService service;

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void registraYDevuelveLocation() throws Exception {
        when(service.registrar(SOLICITUD)).thenReturn(CLIENTE);

        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/clientes/7"))
                .andExpect(jsonPath("$.dni").value("01234567"))
                .andExpect(jsonPath("$.activo").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void listaConMetadatosDePaginacion() throws Exception {
        var pageable = PageRequest.of(0, 20, Sort.by("id"));
        when(service.listarActivos(pageable)).thenReturn(new PageImpl<>(List.of(CLIENTE), pageable, 1));

        mvc.perform(get(RUTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(7))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void consultaYActualiza() throws Exception {
        when(service.obtener(7L)).thenReturn(CLIENTE);
        when(service.actualizar(7L, SOLICITUD)).thenReturn(CLIENTE);

        mvc.perform(get(RUTA + "/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));

        mvc.perform(put(RUTA + "/7").contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void devuelve400ConErroresDeCampo() throws Exception {
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":" ", "email":"incorrecto"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errores.length()").value(4));

        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "123456", "123456789", "01.234.567", "1234 567", "1234567a"})
    @WithMockUser(roles = "ADMINISTRADOR")
    void rechazaDniInvalidoAlActualizar(String dni) throws Exception {
        mvc.perform(put(RUTA + "/7").contentType(MediaType.APPLICATION_JSON)
                        .content(JSON_VALIDO.replace("01234567", dni)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("dni"));
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void devuelve409ParaDniDuplicado() throws Exception {
        when(service.registrar(SOLICITUD)).thenThrow(new ConflictoException("Ya existe un cliente con ese DNI.", null));
        when(service.actualizar(7L, SOLICITUD))
                .thenThrow(new ConflictoException("Ya existe un cliente con ese DNI.", null));

        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Ya existe un cliente con ese DNI."));

        mvc.perform(put(RUTA + "/7").contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ya existe un cliente con ese DNI."));
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
    void devuelve404ParaClienteNoEncontrado() throws Exception {
        when(service.obtener(7L)).thenThrow(new RecursoNoEncontradoException("No se encontró el cliente."));

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
