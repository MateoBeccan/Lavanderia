package com.miraquetelavo.lavanderia.caja;

import com.miraquetelavo.lavanderia.common.idempotencia.IdempotenciaFinanciera;
import com.miraquetelavo.lavanderia.caja.controller.CajaController;
import com.miraquetelavo.lavanderia.caja.dto.AbrirCajaRequest;
import com.miraquetelavo.lavanderia.caja.dto.CajaResponse;
import com.miraquetelavo.lavanderia.caja.entity.EstadoCaja;
import com.miraquetelavo.lavanderia.caja.service.CajaService;
import com.miraquetelavo.lavanderia.security.CustomUserDetailsService;
import com.miraquetelavo.lavanderia.security.JwtService;
import com.miraquetelavo.lavanderia.security.SecurityConfig;
import com.miraquetelavo.lavanderia.tipomovimientocaja.controller.TipoMovimientoCajaController;
import com.miraquetelavo.lavanderia.tipomovimientocaja.service.TipoMovimientoCajaService;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {CajaController.class, TipoMovimientoCajaController.class},
        properties = "lavanderia.jwt.secret=${random.value}${random.value}")
@Import({SecurityConfig.class, JwtService.class, IdempotenciaFinanciera.class})
class CajaControllerTest {

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockitoBean CustomUserDetailsService cuentas;
    @MockitoBean CajaService cajas;
    @MockitoBean JdbcTemplate jdbc;
    @MockitoBean TipoMovimientoCajaService tipos;

    @ParameterizedTest
    @ValueSource(strings = {"EMPLEADO", "ADMINISTRADOR"})
    void ambosRolesPuedenAbrirConUsuarioDelJwt(String rol) throws Exception {
        var monto = new BigDecimal("100.00");
        var abierta = new CajaResponse(12L, 7L, "Ana", null, null, LocalDateTime.of(2026, 11, 17, 10, 0),
                null, monto, null, null, null, null, EstadoCaja.ABIERTA);
        when(cajas.abrirCaja(new AbrirCajaRequest(monto), 7L)).thenReturn(abierta);

        mvc.perform(post("/api/cajas/abrir").header(HttpHeaders.AUTHORIZATION, token(rol))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"montoInicial":100.00,"usuarioAperturaId":123,"fechaApertura":"2020-01-01T00:00"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "http://localhost/api/cajas/12"))
                .andExpect(jsonPath("$.usuarioAperturaId").value(7))
                .andExpect(jsonPath("$.fechaApertura").value("2026-11-17T10:00:00"))
                .andExpect(jsonPath("$.estado").value("ABIERTA"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.01", "0.001", "100000000.00", "null"})
    void validaAmbosImportes(String monto) throws Exception {
        String admin = token("ADMINISTRADOR");
        mvc.perform(post("/api/cajas/abrir").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"montoInicial\":" + monto + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(patch("/api/cajas/1/cerrar").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"efectivoContado\":" + monto + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(cajas);
    }

    @Test
    void protegeHistorialCierreYCatalogoConLaCadenaRealDeSeguridad() throws Exception {
        mvc.perform(post("/api/cajas/abrir")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/tipos-movimiento-caja")).andExpect(status().isUnauthorized());
        var empleado = token("EMPLEADO");
        mvc.perform(get("/api/cajas").header(HttpHeaders.AUTHORIZATION, empleado))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/cajas/1/cerrar").header(HttpHeaders.AUTHORIZATION, empleado))
                .andExpect(status().isForbidden());
        var admin = token("ADMINISTRADOR");
        mvc.perform(post("/api/tipos-movimiento-caja").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/tipos-movimiento-caja/1").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/tipos-movimiento-caja/1").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/tipos-movimiento-caja/1").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isForbidden());
        verifyNoInteractions(cajas, tipos);
    }

    private String token(String rol) {
        doAnswer(invocacion -> new JwtAuthenticationToken(invocacion.getArgument(0),
                List.of(new SimpleGrantedAuthority("ROLE_" + rol)))).when(cuentas).autenticar(any());
        return "Bearer " + jwt.emitir(7L, UUID.randomUUID().toString()).accessToken();
    }
}
