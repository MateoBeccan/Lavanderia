package com.miraquetelavo.lavanderia.catalogopago;

import com.miraquetelavo.lavanderia.common.exception.RecursoNoEncontradoException;
import com.miraquetelavo.lavanderia.estadopago.controller.EstadoPagoController;
import com.miraquetelavo.lavanderia.estadopago.dto.EstadoPagoResponse;
import com.miraquetelavo.lavanderia.estadopago.service.EstadoPagoService;
import com.miraquetelavo.lavanderia.metodopago.controller.MetodoPagoController;
import com.miraquetelavo.lavanderia.metodopago.dto.MetodoPagoResponse;
import com.miraquetelavo.lavanderia.metodopago.service.MetodoPagoService;
import com.miraquetelavo.lavanderia.security.CustomUserDetailsService;
import com.miraquetelavo.lavanderia.security.JwtService;
import com.miraquetelavo.lavanderia.security.SecurityConfig;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {EstadoPagoController.class, MetodoPagoController.class})
@Import({SecurityConfig.class, JwtService.class})
class CatalogosPagoControllerTest {

    private static final String ESTADOS = "/api/estados-pago";
    private static final String METODOS = "/api/metodos-pago";

    @DynamicPropertySource
    static void configurarJwt(DynamicPropertyRegistry properties) {
        byte[] clave = new byte[32];
        new SecureRandom().nextBytes(clave);
        String base64 = Base64.getEncoder().encodeToString(clave);
        properties.add("lavanderia.jwt.secret", () -> base64);
    }

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockitoBean CustomUserDetailsService cuentas;
    @MockitoBean EstadoPagoService estados;
    @MockitoBean MetodoPagoService metodos;

    @ParameterizedTest
    @ValueSource(strings = {"ADMINISTRADOR", "EMPLEADO"})
    void permiteLasCuatroConsultasConJwtValido(String rol) throws Exception {
        String bearer = autorizar(rol);
        var estado = new EstadoPagoResponse(7L, "PARCIAL", "Parcialmente pagado", null, 2, true);
        var metodo = new MetodoPagoResponse(9L, "TARJETA", "Tarjeta", null, true);
        when(estados.listarActivos()).thenReturn(List.of(estado));
        when(estados.obtener(7L)).thenReturn(estado);
        when(metodos.listarActivos()).thenReturn(List.of(metodo));
        when(metodos.obtener(9L)).thenReturn(metodo);

        mvc.perform(get(ESTADOS).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].orden").value(2));
        mvc.perform(get(ESTADOS + "/7").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.codigo").value("PARCIAL"));
        mvc.perform(get(METODOS).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(9));
        mvc.perform(get(METODOS + "/9").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.codigo").value("TARJETA"));
    }

    @ParameterizedTest
    @ValueSource(strings = {ESTADOS, ESTADOS + "/1", METODOS, METODOS + "/1"})
    void rechazaSolicitudesSinJwtConJwtInvalidoOSinRolPermitido(String ruta) throws Exception {
        mvc.perform(get(ruta)).andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(get(ruta).header(HttpHeaders.AUTHORIZATION, "Bearer invalido"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(ruta).header(HttpHeaders.AUTHORIZATION, autorizar("LECTOR")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(estados, metodos);
    }

    @ParameterizedTest
    @CsvSource({ESTADOS + ",abc", METODOS + ",abc", ESTADOS + ",9223372036854775808", METODOS + ",1.5"})
    void rechazaIdsMalformados(String ruta, String id) throws Exception {
        mvc.perform(get(ruta + "/" + id).header(HttpHeaders.AUTHORIZATION, autorizar("EMPLEADO")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(estados, metodos);
    }

    @ParameterizedTest
    @ValueSource(strings = {ESTADOS, METODOS})
    void responde404YRechazaEscrituras(String ruta) throws Exception {
        if (ruta.equals(ESTADOS)) {
            when(estados.obtener(99L)).thenThrow(new RecursoNoEncontradoException("Estado no encontrado."));
        } else {
            when(metodos.obtener(99L)).thenThrow(new RecursoNoEncontradoException("Método no encontrado."));
        }
        String bearer = autorizar("ADMINISTRADOR");
        mvc.perform(get(ruta + "/99").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(post(ruta).header(HttpHeaders.AUTHORIZATION, bearer)).andExpect(status().isForbidden());
        mvc.perform(put(ruta + "/99").header(HttpHeaders.AUTHORIZATION, bearer)).andExpect(status().isForbidden());
        mvc.perform(patch(ruta + "/99").header(HttpHeaders.AUTHORIZATION, bearer)).andExpect(status().isForbidden());
        mvc.perform(delete(ruta + "/99").header(HttpHeaders.AUTHORIZATION, bearer)).andExpect(status().isForbidden());
    }

    private String autorizar(String rol) {
        when(cuentas.autenticar(any())).thenAnswer(invocacion -> new JwtAuthenticationToken(
                invocacion.getArgument(0), List.of(new SimpleGrantedAuthority("ROLE_" + rol))));
        return "Bearer " + jwt.emitir(1L, "version-de-prueba").accessToken();
    }
}
