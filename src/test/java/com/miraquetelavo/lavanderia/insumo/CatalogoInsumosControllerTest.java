package com.miraquetelavo.lavanderia.insumo;

import com.miraquetelavo.lavanderia.categoriainsumo.controller.CategoriaInsumoController;
import com.miraquetelavo.lavanderia.categoriainsumo.dto.CategoriaInsumoRequest;
import com.miraquetelavo.lavanderia.categoriainsumo.dto.CategoriaInsumoResponse;
import com.miraquetelavo.lavanderia.categoriainsumo.service.CategoriaInsumoService;
import com.miraquetelavo.lavanderia.common.exception.ConflictoException;
import com.miraquetelavo.lavanderia.common.exception.RecursoNoEncontradoException;
import com.miraquetelavo.lavanderia.insumo.controller.InsumoController;
import com.miraquetelavo.lavanderia.insumo.dto.InsumoRequest;
import com.miraquetelavo.lavanderia.insumo.dto.InsumoResponse;
import com.miraquetelavo.lavanderia.insumo.service.InsumoService;
import com.miraquetelavo.lavanderia.security.CustomUserDetailsService;
import com.miraquetelavo.lavanderia.security.JwtService;
import com.miraquetelavo.lavanderia.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
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

@WebMvcTest(controllers = {CategoriaInsumoController.class, InsumoController.class},
        properties = "lavanderia.jwt.secret=" + CatalogoInsumosPostgresqlTest.JWT)
@Import({SecurityConfig.class, JwtService.class})
class CatalogoInsumosControllerTest {

    private static final String CATEGORIAS = "/api/categorias-insumo";
    private static final String INSUMOS = "/api/insumos";
    private static final String JSON_CATEGORIA = """
            {"nombre":"  Detergentes  "}
            """;
    private static final String JSON_INSUMO = """
            {"nombre":"  Detergente líquido  ","categoriaId":7}
            """;
    private static final CategoriaInsumoRequest SOLICITUD_CATEGORIA = new CategoriaInsumoRequest("Detergentes");
    private static final InsumoRequest SOLICITUD_INSUMO = new InsumoRequest("Detergente líquido", 7L);
    private static final CategoriaInsumoResponse CATEGORIA = new CategoriaInsumoResponse(7L, "Detergentes", true);
    private static final InsumoResponse INSUMO = new InsumoResponse(7L, "Detergente líquido", 7L, "Detergentes", true);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtService jwt;

    @MockitoBean
    private CustomUserDetailsService cuentas;

    @MockitoBean
    private CategoriaInsumoService categorias;

    @MockitoBean
    private InsumoService insumos;

    @Test
    void administradorPuedeCrearEditarDesactivarYReactivar() throws Exception {
        var token = autorizar("ADMINISTRADOR");
        when(categorias.registrar(SOLICITUD_CATEGORIA)).thenReturn(CATEGORIA);
        when(insumos.registrar(SOLICITUD_INSUMO)).thenReturn(INSUMO);
        when(categorias.actualizar(7L, SOLICITUD_CATEGORIA)).thenReturn(CATEGORIA);
        when(insumos.actualizar(7L, SOLICITUD_INSUMO)).thenReturn(INSUMO);
        when(categorias.activar(7L)).thenReturn(CATEGORIA);
        when(insumos.activar(7L)).thenReturn(INSUMO);

        for (var ruta : List.of(CATEGORIAS, INSUMOS)) {
            var json = ruta.equals(CATEGORIAS) ? JSON_CATEGORIA : JSON_INSUMO;
            mvc.perform(post(ruta).header(AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().isCreated()).andExpect(header().string(LOCATION, "http://localhost" + ruta + "/7"))
                    .andExpect(jsonPath("$.activo").value(true));
            mvc.perform(put(ruta + "/7").header(AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(7));
            mvc.perform(delete(ruta + "/7").header(AUTHORIZATION, token)).andExpect(status().isNoContent());
            mvc.perform(patch(ruta + "/7/activar").header(AUTHORIZATION, token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(true));
        }
        verify(categorias).desactivar(7L);
        verify(insumos).desactivar(7L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMINISTRADOR", "EMPLEADO"})
    void permiteLecturasYDevuelveMetadatos(String rol) throws Exception {
        var token = autorizar(rol);
        var pagina = PageRequest.of(0, 2);
        when(categorias.listarActivos()).thenReturn(List.of(CATEGORIA));
        when(categorias.obtener(7L)).thenReturn(CATEGORIA);
        when(insumos.listarActivos(7L, pagina)).thenReturn(new PageImpl<>(List.of(INSUMO), pagina, 1));
        when(insumos.obtener(7L)).thenReturn(INSUMO);

        mvc.perform(get(CATEGORIAS).header(AUTHORIZATION, token)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Detergentes"));
        mvc.perform(get(CATEGORIAS + "/7").header(AUTHORIZATION, token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));
        mvc.perform(get(INSUMOS).param("categoriaId", "7").param("size", "2").header(AUTHORIZATION, token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].categoriaId").value(7))
                .andExpect(jsonPath("$.page.totalElements").value(1));
        mvc.perform(get(INSUMOS + "/7").header(AUTHORIZATION, token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.categoriaNombre").value("Detergentes"));
    }

    @ParameterizedTest
    @ValueSource(strings = {CATEGORIAS, INSUMOS})
    void rechazaEscriturasDeEmpleadoYLecturasSinJwtValido(String ruta) throws Exception {
        var token = autorizar("EMPLEADO");
        mvc.perform(get(ruta)).andExpect(status().isUnauthorized());
        mvc.perform(get(ruta).header(AUTHORIZATION, "Bearer inválido")).andExpect(status().isUnauthorized());
        mvc.perform(post(ruta).header(AUTHORIZATION, token)).andExpect(status().isForbidden());
        mvc.perform(put(ruta + "/7").header(AUTHORIZATION, token)).andExpect(status().isForbidden());
        mvc.perform(delete(ruta + "/7").header(AUTHORIZATION, token)).andExpect(status().isForbidden());
        mvc.perform(patch(ruta + "/7/activar").header(AUTHORIZATION, token)).andExpect(status().isForbidden());
        verifyNoInteractions(categorias, insumos);
    }

    @Test
    void validaNombreIdYCuerpo() throws Exception {
        var token = autorizar("ADMINISTRADOR");
        mvc.perform(post(CATEGORIAS).header(AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"  \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores[0].campo").value("nombre"));
        mvc.perform(post(INSUMOS).header(AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\" \",\"categoriaId\":0}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores.length()").value(2));
        mvc.perform(get(INSUMOS).param("categoriaId", "abc").header(AUTHORIZATION, token))
                .andExpect(status().isBadRequest());
        mvc.perform(post(INSUMOS).header(AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(categorias, insumos);
    }

    @Test
    void devuelveProblemasParaRecursosAusentesYConflictos() throws Exception {
        var token = autorizar("ADMINISTRADOR");
        when(insumos.obtener(9L)).thenThrow(new RecursoNoEncontradoException("Insumo ausente."));
        doThrow(new ConflictoException("Categoría con insumos activos.", null)).when(categorias).desactivar(7L);
        when(insumos.registrar(SOLICITUD_INSUMO)).thenThrow(new ConflictoException("Nombre duplicado.", null));

        mvc.perform(get(INSUMOS + "/9").header(AUTHORIZATION, token)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Insumo ausente."));
        mvc.perform(delete(CATEGORIAS + "/7").header(AUTHORIZATION, token)).andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(post(INSUMOS).header(AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON).content(JSON_INSUMO))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value("Nombre duplicado."));
    }

    private String autorizar(String rol) {
        when(cuentas.autenticar(any())).thenAnswer(invocacion -> new JwtAuthenticationToken(
                invocacion.getArgument(0), List.of(new SimpleGrantedAuthority("ROLE_" + rol))));
        return "Bearer " + jwt.emitir(1L, "version-de-prueba").accessToken();
    }
}
