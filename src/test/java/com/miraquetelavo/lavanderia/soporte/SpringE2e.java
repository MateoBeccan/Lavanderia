package com.miraquetelavo.lavanderia.soporte;

import com.miraquetelavo.lavanderia.LavanderiaApplication;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationListener;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class SpringE2e {
    public static void main(String[] args) throws Exception {
        String nonce = System.getenv("E2E_ISOLATION_NONCE");
        if (nonce == null || !nonce.matches("[0-9a-f]{32,64}")) {
            throw new IllegalArgumentException("Falta E2E_ISOLATION_NONCE aleatorio para esta ejecución.");
        }
        if (args.length != 1 || !args[0].matches("18[0-9]{3}")) {
            throw new IllegalArgumentException("Indicá el puerto HTTP local asignado a la suite Playwright.");
        }
        var esquemaCreado = new AtomicReference<EsquemaPostgresql>();
        var listo = new AtomicBoolean();
        try {
            var app = new SpringApplicationBuilder(LavanderiaApplication.class).profiles("test-postgresql")
                    .listeners((ApplicationListener<ApplicationReadyEvent>) evento -> listo.set(true))
                    .initializers(contexto -> {
                        EsquemaPostgresql.exigirPerfil(contexto);
                        var esquema = new EsquemaPostgresql("caja");
                        esquemaCreado.set(esquema);
                        esquema.configurar(contexto);
                        var registro = (GenericApplicationContext) contexto;
                        registro.registerBean("inicializarV032E2e", ApplicationRunner.class, () -> argumentos -> {
                            new ResourceDatabasePopulator(new ClassPathResource(
                                    "db/migration/V032__idempotencia_financiera.sql"))
                                    .execute(contexto.getBean(DataSource.class));
                        });
                        registro.registerBean("identidadAisladaE2e", FilterRegistrationBean.class, () -> {
                            var filtro = new FilterRegistrationBean<>((req, res, chain) -> {
                                var solicitud = (HttpServletRequest) req;
                                var respuesta = (HttpServletResponse) res;
                                if (listo.get() && "GET".equals(solicitud.getMethod())) {
                                    respuesta.setHeader("Cache-Control", "no-store");
                                    respuesta.setContentType("text/plain");
                                    respuesta.getWriter().print(nonce);
                                } else {
                                    respuesta.setStatus(503);
                                }
                            });
                            filtro.addUrlPatterns("/api/__e2e_isolated");
                            filtro.setOrder(Ordered.HIGHEST_PRECEDENCE);
                            return filtro;
                        });
                    }).run("--server.address=127.0.0.1", "--server.port=" + args[0],
                            "--lavanderia.bootstrap.enabled=true");
            Runtime.getRuntime().addShutdownHook(Thread.ofPlatform().unstarted(() -> {
                app.close();
                try {
                    esquemaCreado.get().eliminar();
                } catch (Exception ex) {
                    throw new IllegalStateException("No se pudo limpiar el esquema desechable de Playwright.", ex);
                }
            }));
        } catch (RuntimeException ex) {
            var esquema = esquemaCreado.get();
            if (esquema != null) {
                try {
                    esquema.eliminar();
                } catch (Exception limpieza) {
                    ex.addSuppressed(limpieza);
                }
            }
            throw ex;
        }
    }
}
