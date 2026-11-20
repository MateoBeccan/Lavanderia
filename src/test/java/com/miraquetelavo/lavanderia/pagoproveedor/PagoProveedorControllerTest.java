package com.miraquetelavo.lavanderia.pagoproveedor;

import com.miraquetelavo.lavanderia.common.idempotencia.IdempotenciaFinanciera;
import com.miraquetelavo.lavanderia.pagoproveedor.controller.PagoProveedorController;
import com.miraquetelavo.lavanderia.pagoproveedor.dto.PagoProveedorResponse;
import com.miraquetelavo.lavanderia.pagoproveedor.dto.RegistrarPagoProveedorRequest;
import com.miraquetelavo.lavanderia.pagoproveedor.service.PagoProveedorService;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = PagoProveedorController.class, properties = {
        "lavanderia.jwt.secret=${random.value}${random.value}",
        "lavanderia.cors.origins=http://localhost:5173"
})
@Import({SecurityConfig.class, JwtService.class, IdempotenciaFinanciera.class})
class PagoProveedorControllerTest {
    private static final String RUTA = "/api/compras/3/pagos";
    private static final String CUERPO = """
            {"monto":20.00,"metodoPagoId":2}
            """;

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockitoBean CustomUserDetailsService cuentas;
    @MockitoBean PagoProveedorService pagos;
    @MockitoBean JdbcTemplate jdbc;

    @ParameterizedTest
    @ValueSource(strings = {"ADMINISTRADOR", "EMPLEADO"})
    void autorizaAmbosRolesYPasaLaIdentidadDelJwt(String rol) throws Exception {
        var monto = new BigDecimal("20.00");
        var solicitud = new RegistrarPagoProveedorRequest(monto, 2L);
        var pago = new PagoProveedorResponse(4L, 3L, 6L, "Proveedor", 7L, 8L, "TRANSFERENCIA",
                "Transferencia", 17L, "Ana", LocalDateTime.of(2026, 1, 1, 10, 0), monto);
        when(pagos.registrarPago(3L, solicitud, 17L)).thenReturn(pago);
        when(pagos.obtenerPorId(4L)).thenReturn(pago);
        var token = autorizar(rol);

        mvc.perform(post(RUTA).header(AUTHORIZATION, token).contentType(APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/pagos-proveedor/4"))
                .andExpect(jsonPath("$.usuarioId").value(17));
        mvc.perform(get("/api/pagos-proveedor/4").header(AUTHORIZATION, token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.compraId").value(3));
    }

    @Test
    void requiereJwtYPermisoYRestringeLosMetodosDeEscritura() throws Exception {
        mvc.perform(post(RUTA)).andExpect(status().isUnauthorized());
        mvc.perform(get(RUTA).header(AUTHORIZATION, "Bearer invalido")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/compras/3/saldo").header(AUTHORIZATION, autorizar("SIN_PERMISO")))
                .andExpect(status().isForbidden());
        var token = autorizar("ADMINISTRADOR");
        mvc.perform(put("/api/pagos-proveedor/4").header(AUTHORIZATION, token)).andExpect(status().isForbidden());
        mvc.perform(patch("/api/pagos-proveedor/4").header(AUTHORIZATION, token)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/pagos-proveedor/4").header(AUTHORIZATION, token)).andExpect(status().isForbidden());
        verifyNoInteractions(pagos);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "0", "-0.01", "1.001", "100000000"})
    void rechazaImportesInvalidos(String importe) throws Exception {
        rechazar(CUERPO.replace("20.00", importe));
    }

    @ParameterizedTest
    @ValueSource(strings = {"compraId", "cajaId", "usuarioId", "movimientoCajaId", "fechaPago",
            "estadoPagoId", "tipoMovimientoId"})
    void rechazaCamposAdministradosPorElBackend(String campo) throws Exception {
        rechazar(CUERPO.strip().replace("}", ",\"" + campo + "\":1}"));
    }

    @Test
    void rechazaMetodoInvalidoYCuerpoMalformado() throws Exception {
        rechazar("{\"monto\":20,\"metodoPagoId\":0}");
        rechazar("{\"monto\":20}");
        rechazar("{");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "1-1-1-1-1", "clave", "99999999-9999-9999-9999-999999999999, otra"})
    void rechazaCabeceraInvalidaSinEjecutar(String clave) throws Exception {
        mvc.perform(post(RUTA).header(AUTHORIZATION, autorizar("EMPLEADO"))
                        .header("Idempotency-Key", clave).contentType(APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(pagos, jdbc);
    }

    @Test
    void exigePermisosYRechazaCabecerasRepetidas() throws Exception {
        var clave = "99999999-9999-9999-9999-999999999999";
        mvc.perform(post(RUTA).header("Idempotency-Key", clave).contentType(APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(RUTA).header(AUTHORIZATION, autorizar("SIN_PERMISO"))
                        .header("Idempotency-Key", clave).contentType(APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isForbidden());
        mvc.perform(post(RUTA).header(AUTHORIZATION, autorizar("EMPLEADO"))
                        .header("Idempotency-Key", clave, clave).contentType(APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(pagos, jdbc);
    }

    @Test
    void aceptaLaCabeceraIdempotenteDesdeElOrigenConfigurado() throws Exception {
        mvc.perform(options(RUTA).header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization,content-type,idempotency-key"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Headers",
                        org.hamcrest.Matchers.containsString("idempotency-key")));
        verifyNoInteractions(pagos, jdbc);
    }

    private void rechazar(String cuerpo) throws Exception {
        mvc.perform(post(RUTA).header(AUTHORIZATION, autorizar("EMPLEADO"))
                        .contentType(APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(pagos);
    }

    private String autorizar(String rol) {
        doAnswer(invocacion ->
                new JwtAuthenticationToken(invocacion.getArgument(0), List.of(new SimpleGrantedAuthority("ROLE_" + rol)))).when(cuentas).autenticar(any());
        return "Bearer " + jwt.emitir(17L, "prueba").accessToken();
    }
}
