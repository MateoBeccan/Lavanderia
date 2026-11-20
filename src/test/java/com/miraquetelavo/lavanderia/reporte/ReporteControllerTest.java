package com.miraquetelavo.lavanderia.reporte;

import com.miraquetelavo.lavanderia.reporte.controller.ReporteController;
import com.miraquetelavo.lavanderia.reporte.controller.ReportePdfController;
import com.miraquetelavo.lavanderia.reporte.pdf.ExportacionReportePdf;
import com.miraquetelavo.lavanderia.reporte.repository.ReporteRepository;
import com.miraquetelavo.lavanderia.reporte.service.impl.ReporteServiceImpl;
import com.miraquetelavo.lavanderia.estadopago.repository.EstadoPagoRepository;
import com.miraquetelavo.lavanderia.estadopedido.repository.EstadoPedidoRepository;
import com.miraquetelavo.lavanderia.metodopago.repository.MetodoPagoRepository;
import com.miraquetelavo.lavanderia.tipomovimientocaja.repository.TipoMovimientoCajaRepository;
import com.miraquetelavo.lavanderia.security.CustomUserDetailsService;
import com.miraquetelavo.lavanderia.security.JwtService;
import com.miraquetelavo.lavanderia.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {ReporteController.class, ReportePdfController.class},
        properties = "lavanderia.jwt.secret=${random.value}${random.value}")
@Import({SecurityConfig.class, JwtService.class, ReporteServiceImpl.class})
class ReporteControllerTest {
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockitoBean CustomUserDetailsService cuentas;
    @MockitoBean ReporteRepository reportes;
    @MockitoBean EstadoPedidoRepository estadosPedido;
    @MockitoBean EstadoPagoRepository estadosPago;
    @MockitoBean TipoMovimientoCajaRepository tipos;
    @MockitoBean MetodoPagoRepository metodos;
    @MockitoBean ExportacionReportePdf exportacion;

    @ParameterizedTest
    @ValueSource(strings = {"pedidos", "movimientos-caja", "compras"})
    void protegeCadaReporte(String reporte) throws Exception {
        var ruta = "/api/reportes/" + reporte;
        mvc.perform(get(ruta)).andExpect(status().isUnauthorized());
        mvc.perform(get(ruta).header(AUTHORIZATION, "Bearer invalido")).andExpect(status().isUnauthorized());
        mvc.perform(get(ruta).header(AUTHORIZATION, token("EMPLEADO"))).andExpect(status().isForbidden());
        mvc.perform(post(ruta).header(AUTHORIZATION, token("ADMINISTRADOR"))).andExpect(status().isForbidden());
        verifyNoInteractions(reportes);
    }

    @ParameterizedTest
    @ValueSource(strings = {"pedidos", "movimientos-caja", "compras"})
    void proteccionDeLasRutasPdf(String reporte) throws Exception {
        var ruta = "/api/reportes/" + reporte + "/pdf";
        mvc.perform(get(ruta)).andExpect(status().isUnauthorized());
        mvc.perform(get(ruta).header(AUTHORIZATION, "Bearer invalido")).andExpect(status().isUnauthorized());
        mvc.perform(get(ruta).header(AUTHORIZATION, token("EMPLEADO"))).andExpect(status().isForbidden());
        mvc.perform(post(ruta).header(AUTHORIZATION, token("ADMINISTRADOR"))).andExpect(status().isForbidden());
        verifyNoInteractions(exportacion);
    }

    @Test
    void cargaOcupadaDevuelve429ConDetalle() throws Exception {
        when(exportacion.compras(null, null, null, null)).thenThrow(
                new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Reintentá en unos segundos."));

        mvc.perform(get("/api/reportes/compras/pdf")
                        .header(AUTHORIZATION, token("ADMINISTRADOR")).accept("application/pdf", "application/problem+json"))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").value("Reintentá en unos segundos."));
    }

    @ParameterizedTest
    @CsvSource({
            "fechaDesde,2026-11-31", "fechaHasta,2026-2-01", "fechaDesde,0000-11-01",
            "fechaHasta,+999999999-12-31", "page,-1", "page,no", "size,0", "size,101",
            "clienteId,no", "clienteId,0", "estadoPedidoCodigo,INEXISTENTE", "estadoPagoCodigo,INEXISTENTE",
            "fechaDesde,''", "fechaHasta,+02026-11-01", "page,''", "size,''", "clienteId,''"
    })
    void devuelveProblemDetailParaFiltrosInvalidos(String campo, String valor) throws Exception {
        mvc.perform(get("/api/reportes/pedidos").header(AUTHORIZATION, token("ADMINISTRADOR")).param(campo, valor))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(reportes);
    }

    private String token(String rol) {
        doAnswer(i -> new JwtAuthenticationToken(i.getArgument(0),
                List.of(new SimpleGrantedAuthority("ROLE_" + rol)))).when(cuentas).autenticar(any());
        return "Bearer " + jwt.emitir(1L, "1").accessToken();
    }
}
