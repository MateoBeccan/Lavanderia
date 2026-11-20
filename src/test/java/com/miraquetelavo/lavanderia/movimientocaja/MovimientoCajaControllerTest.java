package com.miraquetelavo.lavanderia.movimientocaja;

import com.miraquetelavo.lavanderia.common.idempotencia.IdempotenciaFinanciera;
import com.miraquetelavo.lavanderia.movimientocaja.controller.MovimientoCajaController;
import com.miraquetelavo.lavanderia.movimientocaja.service.MovimientoCajaService;
import com.miraquetelavo.lavanderia.security.CustomUserDetailsService;
import com.miraquetelavo.lavanderia.security.JwtService;
import com.miraquetelavo.lavanderia.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = MovimientoCajaController.class,
        properties = "lavanderia.jwt.secret=${random.value}${random.value}")
@Import({SecurityConfig.class, JwtService.class, IdempotenciaFinanciera.class})
class MovimientoCajaControllerTest {

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockitoBean CustomUserDetailsService cuentas;
    @MockitoBean MovimientoCajaService movimientos;
    @MockitoBean JdbcTemplate jdbc;

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"cajaId\":1,\"monto\":0,\"metodoPagoId\":1,\"descripcion\":\"Compra\"}",
            "{\"cajaId\":1,\"monto\":-1,\"metodoPagoId\":1,\"descripcion\":\"Compra\"}",
            "{\"cajaId\":1,\"monto\":0.001,\"metodoPagoId\":1,\"descripcion\":\"Compra\"}",
            "{\"cajaId\":1,\"monto\":100000000,\"metodoPagoId\":1,\"descripcion\":\"Compra\"}",
            "{\"cajaId\":1,\"monto\":1,\"metodoPagoId\":1,\"descripcion\":\"  \"}",
            "{\"cajaId\":1,\"monto\":1,\"metodoPagoId\":0,\"descripcion\":\"Compra\"}",
            "{\"monto\":1,\"metodoPagoId\":1,\"descripcion\":\"Compra\"}",
            "{\"cajaId\":0,\"monto\":1,\"metodoPagoId\":1,\"descripcion\":\"Compra\"}",
            "{\"cajaId\":-1,\"monto\":1,\"metodoPagoId\":1,\"descripcion\":\"Compra\"}",
            "{\"cajaId\":1.5,\"monto\":1,\"metodoPagoId\":1,\"descripcion\":\"Compra\"}",
            "{\"cajaId\":\"1\",\"monto\":1,\"metodoPagoId\":1,\"descripcion\":\"Compra\"}"
    })
    void rechazaUnaSolicitudInvalida(String cuerpo) throws Exception {
        mvc.perform(post("/api/movimientos-caja/egresos-manuales").header(HttpHeaders.AUTHORIZATION, token())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(movimientos);
    }

    @Test
    void exigeJwtYBloqueaMutacionesYRegistroAutomatico() throws Exception {
        mvc.perform(get("/api/cajas/1/saldo")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/cajas/1/movimientos")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/movimientos-caja/ingresos-manuales")).andExpect(status().isUnauthorized());
        String token = token();
        mvc.perform(put("/api/movimientos-caja/1").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/movimientos-caja/1").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/movimientos-caja/1").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/movimientos-caja/cobros").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden());
        verifyNoInteractions(movimientos);
    }

    private String token() {
        when(cuentas.autenticar(any())).thenAnswer(invocacion -> new JwtAuthenticationToken(invocacion.getArgument(0),
                List.of(new SimpleGrantedAuthority("ROLE_EMPLEADO"))));
        return "Bearer " + jwt.emitir(7L, UUID.randomUUID().toString()).accessToken();
    }
}
