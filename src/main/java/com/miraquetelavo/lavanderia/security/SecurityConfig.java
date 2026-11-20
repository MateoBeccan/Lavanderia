package com.miraquetelavo.lavanderia.security;

import com.miraquetelavo.lavanderia.common.idempotencia.IdempotenciaFinanciera;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.util.Arrays;
import java.util.List;

import static org.springframework.http.HttpMethod.*;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    private static final String ADMIN = "ADMINISTRADOR";
    private static final String EMPLEADO = "EMPLEADO";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwt, CustomUserDetailsService cuentas,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        AuthenticationEntryPoint noAutenticado = (request, response, ex) -> {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            resolver.resolveException(request, response, null, ex);
        };
        AccessDeniedHandler sinPermiso = (request, response, ex) ->
                resolver.resolveException(request, response, null, ex);
        return http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .exceptionHandling(errores -> errores.authenticationEntryPoint(noAutenticado)
                        .accessDeniedHandler(sinPermiso))
                .oauth2ResourceServer(oauth -> oauth.authenticationEntryPoint(noAutenticado)
                        .accessDeniedHandler(sinPermiso)
                        .jwt(tokens -> tokens.decoder(jwt.decoder()).jwtAuthenticationConverter(cuentas::autenticar)))
                .authorizeHttpRequests(reglas -> reglas
                        .requestMatchers(POST, "/api/auth/login").permitAll()
                        .requestMatchers(GET, "/api/auth/me").hasAnyRole(ADMIN, EMPLEADO)
                        .requestMatchers(GET, "/api/reportes/**").hasRole(ADMIN)
                        .requestMatchers(GET, "/api/cajas").hasRole(ADMIN)
                        .requestMatchers(PATCH, "/api/cajas/{id}/cerrar").hasRole(ADMIN)
                        .requestMatchers(POST, "/api/cajas/abrir").hasAnyRole(ADMIN, EMPLEADO)
                        .requestMatchers(POST, "/api/movimientos-caja/ingresos-manuales",
                                "/api/movimientos-caja/egresos-manuales").hasAnyRole(ADMIN, EMPLEADO)
                        .requestMatchers(GET, "/api/cajas/{id}/movimientos", "/api/cajas/{id}/saldo",
                                "/api/movimientos-caja/{id}").hasAnyRole(ADMIN, EMPLEADO)
                        .requestMatchers(GET, "/api/cajas/abierta", "/api/cajas/{id}",
                                "/api/tipos-movimiento-caja", "/api/tipos-movimiento-caja/{id}")
                                .hasAnyRole(ADMIN, EMPLEADO)
                        .requestMatchers(GET, "/api/estados-pago", "/api/estados-pago/{id}",
                                "/api/metodos-pago", "/api/metodos-pago/{id}").hasAnyRole(ADMIN, EMPLEADO)
                        .requestMatchers(GET, "/api/pedidos", "/api/pedidos/{id}", "/api/pedidos/{id}/historial")
                                .hasAnyRole(ADMIN, EMPLEADO)
                        .requestMatchers(GET, "/api/pedidos/{id}/cobros", "/api/pedidos/{id}/saldo", "/api/cobros/{id}")
                                .hasAnyRole(ADMIN, EMPLEADO)
                        .requestMatchers(POST, "/api/pedidos/{id}/cobros").hasAnyRole(ADMIN, EMPLEADO)
                        .requestMatchers(PATCH, "/api/pedidos/{id}/estado", "/api/pedidos/{id}/cancelar")
                                .hasAnyRole(ADMIN, EMPLEADO)
                        .requestMatchers(POST, "/api/pedidos").hasAnyRole(ADMIN, EMPLEADO)
                        .requestMatchers(PUT, "/api/pedidos/{id}").hasAnyRole(ADMIN, EMPLEADO)
                        .requestMatchers(GET, "/api/compras", "/api/compras/{id}", "/api/proveedores/{id}/compras")
                                .hasAnyRole(ADMIN, EMPLEADO)
                        .requestMatchers(GET, "/api/compras/{id}/pagos", "/api/compras/{id}/saldo",
                                "/api/pagos-proveedor/{id}").hasAnyRole(ADMIN, EMPLEADO)
                        .requestMatchers(POST, "/api/compras/{id}/pagos").hasAnyRole(ADMIN, EMPLEADO)
                        .requestMatchers(POST, "/api/cobros/{id}/comprobante", "/api/pagos-proveedor/{id}/comprobante")
                                .hasAnyRole(ADMIN, EMPLEADO)
                        .requestMatchers(GET, "/api/comprobantes/{id}", "/api/cobros/{id}/comprobante",
                                "/api/pagos-proveedor/{id}/comprobante", "/api/pedidos/{id}/comprobantes",
                                "/api/compras/{id}/comprobantes").hasAnyRole(ADMIN, EMPLEADO)
                        .requestMatchers(POST, "/api/compras").hasAnyRole(ADMIN, EMPLEADO)
                        .requestMatchers(PUT, "/api/compras/{id}").hasAnyRole(ADMIN, EMPLEADO)
                        .requestMatchers(GET, "/api/usuarios", "/api/usuarios/{id}").hasRole(ADMIN)
                        .requestMatchers(POST, "/api/usuarios").hasRole(ADMIN)
                        .requestMatchers(PUT, "/api/usuarios/{id}").hasRole(ADMIN)
                        .requestMatchers(DELETE, "/api/usuarios/{id}").hasRole(ADMIN)
                        .requestMatchers(PATCH, "/api/usuarios/{id}/activar", "/api/usuarios/{id}/password").hasRole(ADMIN)
                        .requestMatchers(GET, "/api/clientes", "/api/clientes/{id}",
                                "/api/servicios", "/api/servicios/{id}", "/api/prendas", "/api/prendas/{id}",
                                "/api/estados-pedido", "/api/estados-pedido/{id}").hasAnyRole(ADMIN, EMPLEADO)
                        .requestMatchers(POST, "/api/clientes").hasAnyRole(ADMIN, EMPLEADO)
                        .requestMatchers(PUT, "/api/clientes/{id}").hasAnyRole(ADMIN, EMPLEADO)
                        .requestMatchers(POST, "/api/servicios", "/api/prendas").hasRole(ADMIN)
                        .requestMatchers(PUT, "/api/servicios/{id}", "/api/prendas/{id}").hasRole(ADMIN)
                        .requestMatchers(DELETE, "/api/clientes/{id}", "/api/servicios/{id}", "/api/prendas/{id}").hasRole(ADMIN)
                        .requestMatchers(PATCH, "/api/servicios/{id}/activar", "/api/prendas/{id}/activar").hasRole(ADMIN)
                        .requestMatchers(GET, "/api/categorias-insumo", "/api/categorias-insumo/{id}",
                                "/api/insumos", "/api/insumos/{id}").hasAnyRole(ADMIN, EMPLEADO)
                        .requestMatchers(POST, "/api/categorias-insumo", "/api/insumos").hasRole(ADMIN)
                        .requestMatchers(PUT, "/api/categorias-insumo/{id}", "/api/insumos/{id}").hasRole(ADMIN)
                        .requestMatchers(DELETE, "/api/categorias-insumo/{id}", "/api/insumos/{id}").hasRole(ADMIN)
                        .requestMatchers(PATCH, "/api/categorias-insumo/{id}/activar",
                                "/api/insumos/{id}/activar").hasRole(ADMIN)
                        .requestMatchers(GET, "/api/proveedores", "/api/proveedores/{id}",
                                "/api/proveedores/{id}/insumos", "/api/insumos/{id}/proveedores")
                                .hasAnyRole(ADMIN, EMPLEADO)
                        .requestMatchers(POST, "/api/proveedores", "/api/proveedores/{id}/insumos").hasRole(ADMIN)
                        .requestMatchers(PUT, "/api/proveedores/{id}").hasRole(ADMIN)
                        .requestMatchers(DELETE, "/api/proveedores/{id}",
                                "/api/proveedores/{id}/insumos/{insumoId}").hasRole(ADMIN)
                        .requestMatchers(PATCH, "/api/proveedores/{id}/activar").hasRole(ADMIN)
                        .anyRequest().denyAll())
                .build();
    }

    @Bean
    AuthenticationManager authenticationManager(CustomUserDetailsService cuentas, PasswordEncoder encoder) {
        var proveedor = new DaoAuthenticationProvider(cuentas);
        proveedor.setPasswordEncoder(encoder);
        proveedor.setAlwaysPerformAdditionalChecksOnUser(true);
        return new ProviderManager(proveedor);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${lavanderia.cors.origins:}") String origenes) {
        var cors = new CorsConfiguration();
        var lista = Arrays.stream(origenes.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
        if (lista.stream().anyMatch(s -> s.contains("*"))) {
            throw new IllegalStateException("CORS requiere orígenes explícitos.");
        }
        cors.setAllowedOrigins(lista);
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        cors.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, IdempotenciaFinanciera.CABECERA));
        cors.setExposedHeaders(List.of(HttpHeaders.LOCATION, HttpHeaders.RETRY_AFTER));
        cors.setAllowCredentials(false);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }
}
