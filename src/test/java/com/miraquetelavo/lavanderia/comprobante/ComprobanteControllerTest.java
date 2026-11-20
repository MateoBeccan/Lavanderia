package com.miraquetelavo.lavanderia.comprobante;

import com.miraquetelavo.lavanderia.comprobante.controller.ComprobanteController;
import com.miraquetelavo.lavanderia.comprobante.dto.ComprobanteResponse;
import com.miraquetelavo.lavanderia.comprobante.dto.CrearComprobanteRequest;
import com.miraquetelavo.lavanderia.comprobante.service.ComprobanteService;
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
import java.time.LocalDate;
import java.util.List;

import static com.miraquetelavo.lavanderia.comprobante.dto.TipoOperacionComprobante.COBRO;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ComprobanteController.class, properties = "lavanderia.jwt.secret=${random.value}${random.value}")
@Import({SecurityConfig.class, JwtService.class})
class ComprobanteControllerTest {
    private static final String COBRO_RUTA = "/api/cobros/3/comprobante";
    private static final String PAGO_RUTA = "/api/pagos-proveedor/3/comprobante";
    private static final String DETALLE = "/api/comprobantes/7";
    private static final LocalDate FECHA = LocalDate.of(2026, 2, 28);

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockitoBean CustomUserDetailsService cuentas;
    @MockitoBean ComprobanteService servicio;

    @ParameterizedTest
    @ValueSource(strings = {"ADMINISTRADOR", "EMPLEADO"})
    void autorizaAmbosRoles(String rol) throws Exception {
        var respuesta = new ComprobanteResponse(7L, COBRO, 3L, null, 2L, null, FECHA, null,
                BigDecimal.TEN, "EFECTIVO", "Efectivo", 1L);
        when(servicio.crearDeCobro(3L, new CrearComprobanteRequest(FECHA, null))).thenReturn(respuesta);
        when(servicio.obtener(7L)).thenReturn(respuesta);
        var token = autorizar(rol);
        mvc.perform(post(COBRO_RUTA).header(AUTHORIZATION, token).contentType(APPLICATION_JSON)
                        .content("{\"fechaEmision\":\"2026-02-28\"}"))
                .andExpect(status().isCreated()).andExpect(header().string("Location", DETALLE))
                .andExpect(jsonPath("$.fechaEmision").value("2026-02-28"));
        mvc.perform(get(DETALLE).header(AUTHORIZATION, token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoOperacion").value("COBRO"));
    }

    @Test
    void protegeLecturaYAltasYDeniegaCambios() throws Exception {
        mvc.perform(post(COBRO_RUTA)).andExpect(status().isUnauthorized());
        mvc.perform(get(DETALLE).header(AUTHORIZATION, "Bearer invalido")).andExpect(status().isUnauthorized());
        mvc.perform(get(DETALLE).header(AUTHORIZATION, autorizar("SIN_PERMISO"))).andExpect(status().isForbidden());
        var token = autorizar("ADMINISTRADOR");
        mvc.perform(put(DETALLE).header(AUTHORIZATION, token)).andExpect(status().isForbidden());
        mvc.perform(patch(PAGO_RUTA).header(AUTHORIZATION, token)).andExpect(status().isForbidden());
        mvc.perform(delete(COBRO_RUTA).header(AUTHORIZATION, token)).andExpect(status().isForbidden());
        verifyNoInteractions(servicio);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "{}", "{\"fechaEmision\":\"2026-02-29\"}",
            "{\"fechaEmision\":\"2026-2-8\"}", "{\"fechaEmision\":[2026,2,28]}",
            "{\"fechaEmision\":\"2026-02-28T10:00:00\"}",
            "{\"fechaEmision\":\"2026-02-28\",\"cobroId\":1}"})
    void rechazaCuerpoInvalido(String cuerpo) throws Exception {
        mvc.perform(post(PAGO_RUTA).header(AUTHORIZATION, autorizar("EMPLEADO"))
                        .contentType(APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(servicio);
    }

    private String autorizar(String rol) {
        doAnswer(i -> new JwtAuthenticationToken(i.getArgument(0),
                List.of(new SimpleGrantedAuthority("ROLE_" + rol)))).when(cuentas).autenticar(any());
        return "Bearer " + jwt.emitir(1L, "1").accessToken();
    }
}
