package com.miraquetelavo.lavanderia.compra;

import com.miraquetelavo.lavanderia.compra.controller.CompraController;
import com.miraquetelavo.lavanderia.compra.dto.CompraResponse;
import com.miraquetelavo.lavanderia.compra.service.CompraService;
import com.miraquetelavo.lavanderia.security.CustomUserDetailsService;
import com.miraquetelavo.lavanderia.security.JwtService;
import com.miraquetelavo.lavanderia.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpHeaders.LOCATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = CompraController.class, properties = "lavanderia.jwt.secret=" + CompraPostgresqlTest.JWT)
@Import({SecurityConfig.class, JwtService.class})
class CompraControllerTest {
    private static final String ALTA = """
            {"proveedorId":3,"detalles":[{"insumoId":5,"cantidad":1,"precioCompra":2}]}
            """;

    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwt;
    @MockitoBean private CustomUserDetailsService cuentas;
    @MockitoBean private CompraService compras;

    @ParameterizedTest
    @ValueSource(strings = {"ADMINISTRADOR", "EMPLEADO"})
    void autorizaAltaYUsaElIdDelToken(String rol) throws Exception {
        var compra = new CompraResponse(9L, 3L, "Proveedor", 17L, "Ana", LocalDateTime.of(2026, 1, 1, 10, 0),
                null, new BigDecimal("2.00"), "PENDIENTE", "Pendiente", List.of());
        when(compras.crearCompra(any(), eq(17L))).thenReturn(compra);

        mvc.perform(post("/api/compras").header(AUTHORIZATION, autorizar(rol)).contentType(APPLICATION_JSON).content(ALTA))
                .andExpect(status().isCreated()).andExpect(header().string(LOCATION, "/api/compras/9"))
                .andExpect(jsonPath("$.usuarioId").value(17));
    }

    @Test
    void rechazaSinJwtYSiLaAutoridadCareceDePermiso() throws Exception {
        mvc.perform(get("/api/compras")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/proveedores/3/compras").header(AUTHORIZATION, autorizar("SIN_PERMISO")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(compras);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-0.25", "1.000", "100000000"})
    void rechazaCantidadOPrecioInvalidoAntesDeInvocarServicio(String invalido) throws Exception {
        var token = autorizar("EMPLEADO");
        for (var json : List.of(ALTA.replace("\"cantidad\":1", "\"cantidad\":" + invalido),
                ALTA.replace("\"precioCompra\":2", "\"precioCompra\":" + invalido))) {
            mvc.perform(post("/api/compras").header(AUTHORIZATION, token).contentType(APPLICATION_JSON).content(json))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        }
        verifyNoInteractions(compras);
    }

    private String autorizar(String rol) {
        when(cuentas.autenticar(any())).thenAnswer(invocacion ->
                new JwtAuthenticationToken(invocacion.getArgument(0), List.of(new SimpleGrantedAuthority("ROLE_" + rol))));
        return "Bearer " + jwt.emitir(17L, "prueba").accessToken();
    }
}
