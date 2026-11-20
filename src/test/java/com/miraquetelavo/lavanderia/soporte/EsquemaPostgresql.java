package com.miraquetelavo.lavanderia.soporte;

import com.miraquetelavo.lavanderia.LavanderiaApplication;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.Profiles;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

import java.sql.Connection;
import java.util.Objects;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class EsquemaPostgresql {
    private static final Pattern URL_PRUEBAS = Pattern.compile(
            "jdbc:postgresql://127\\.0\\.0\\.1:([1-9][0-9]{3,4})/(lavanderia_test_[a-z0-9_]{1,47})");
    private static final String PREFIJOS = "caja|catalogos_pago|cobro|comprobantes|compras|estado_pedido|"
            + "insumos|movimientos_caja|pagos_proveedor|pedido|prenda|proveedores|reportes|usuarios";
    private static final Pattern NOMBRE_PRUEBAS = Pattern.compile("(?:" + PREFIJOS + ")_test_[0-9a-f]{32}");
    private static final Map<Class<?>, EsquemaPostgresql> ESQUEMAS = new ConcurrentHashMap<>();

    private final String url;
    private final int puerto;
    private final String base;
    private final String usuario;
    private final String clave;
    private final String nombre;

    EsquemaPostgresql(String prefijo) {
        url = Objects.requireNonNullElse(System.getenv("LAVANDERIA_TEST_JDBC_URL"), "");
        var destino = URL_PRUEBAS.matcher(url);
        if (!destino.matches()) {
            throw new IllegalArgumentException("Se requiere una URL JDBC loopback con puerto explícito y base lavanderia_test_*.");
        }
        puerto = Integer.parseInt(destino.group(1));
        if (puerto == 5432 || puerto > 65535) {
            throw new IllegalArgumentException("Se requiere un puerto PostgreSQL desechable distinto de 5432.");
        }
        base = destino.group(2);
        usuario = System.getenv("LAVANDERIA_TEST_JDBC_USER");
        if (usuario == null || !usuario.matches("lavanderia_test_[a-z0-9_]+")) {
            throw new IllegalArgumentException("Falta LAVANDERIA_TEST_JDBC_USER.");
        }
        clave = System.getenv("LAVANDERIA_TEST_JDBC_PASSWORD");
        if (clave == null || clave.isBlank()) {
            throw new IllegalArgumentException("Falta LAVANDERIA_TEST_JDBC_PASSWORD.");
        }
        nombre = prefijo + "_test_" + UUID.randomUUID().toString().replace("-", "");
        validarNombre();

        // CREATE sin IF NOT EXISTS deja fuera de la limpieza cualquier esquema preexistente.
        try (var conexion = conectar(); var sql = conexion.createStatement()) {
            sql.execute("CREATE SCHEMA " + nombre);
        } catch (SQLException ex) {
            throw new IllegalStateException("No se pudo crear el esquema de pruebas " + nombre, ex);
        }
    }

    private void validarNombre() {
        if (!NOMBRE_PRUEBAS.matcher(nombre).matches()) {
            throw new IllegalStateException("Nombre de esquema de pruebas inválido: " + nombre);
        }
    }

    private Connection conectar() throws SQLException {
        var conexion = origen().getConnection();
        try (var sql = conexion.createStatement();
             var resultado = sql.executeQuery("""
                     SELECT current_database(), host(inet_server_addr()), inet_server_port(), current_user,
                            shobj_description(d.oid, 'pg_database'),
                            r.rolsuper OR r.rolcreaterole OR r.rolcreatedb,
                            EXISTS (SELECT FROM pg_database WHERE datname = 'lavanderia_db')
                     FROM pg_database d JOIN pg_roles r ON r.rolname = current_user
                     WHERE d.datname = current_database()
                     """)) {
            if (!resultado.next() || !base.equals(resultado.getString(1))
                    || !"127.0.0.1".equals(resultado.getString(2)) || puerto != resultado.getInt(3)
                    || !usuario.equals(resultado.getString(4))
                    || !"lavanderia:pruebas-desechables".equals(resultado.getString(5))
                    || resultado.getBoolean(6) || resultado.getBoolean(7)) {
                throw new SQLException("El clúster o rol no cumple las condiciones de aislamiento.");
            }
        } catch (SQLException ex) {
            try {
                conexion.close();
            } catch (SQLException cierre) {
                ex.addSuppressed(cierre);
            }
            throw ex;
        }
        return conexion;
    }

    private PGSimpleDataSource origen() {
        var origen = new PGSimpleDataSource();
        origen.setUrl(url);
        origen.setUser(usuario);
        origen.setPassword(clave);
        origen.setApplicationName(nombre);
        origen.setCurrentSchema(nombre);
        return origen;
    }

    void configurar(ConfigurableApplicationContext contexto) {
        exigirPerfil(contexto);
        boolean vacio;
        try (var conexion = conectar(); var sql = conexion.createStatement();
             var filas = sql.executeQuery("""
                     SELECT NOT EXISTS (SELECT FROM pg_tables WHERE schemaname = current_schema())
                     """)) {
            filas.next();
            vacio = filas.getBoolean(1);
        } catch (SQLException ex) {
            throw new IllegalStateException("No se pudo verificar el esquema de pruebas.", ex);
        }
        contexto.getEnvironment().getPropertySources().addFirst(new MapPropertySource("postgresqlIntegracion", Map.of(
                "spring.jpa.hibernate.ddl-auto", vacio ? "create-only" : "none",
                "spring.jpa.properties.hibernate.default_schema", nombre,
                "spring.flyway.enabled", "false",
                "spring.sql.init.mode", "never")));
        // La conexión usada por Hibernate, @Sql y los servicios es la que acabamos de validar.
        ((GenericApplicationContext) contexto).registerBean(HikariDataSource.class, () -> {
            var pool = new HikariDataSource();
            pool.setDataSource(origen());
            pool.setMinimumIdle(0);
            return pool;
        });
    }

    static void exigirPerfil(ConfigurableApplicationContext contexto) {
        var entorno = contexto.getEnvironment();
        if (!entorno.acceptsProfiles(Profiles.of("test-postgresql & !prod & !dev"))) {
            throw new IllegalStateException("Se requiere el perfil aislado test-postgresql.");
        }
    }

    void eliminar() throws SQLException {
        validarNombre();
        try (var conexion = conectar();
             var sesiones = conexion.prepareStatement("""
                     SELECT 1 FROM pg_stat_activity
                     WHERE datname = current_database() AND application_name = ? AND pid <> pg_backend_pid()
                     """);
             var sql = conexion.createStatement()) {
            sesiones.setString(1, nombre);
            try (var resultado = sesiones.executeQuery()) {
                if (resultado.next()) {
                    throw new SQLException("Todavía hay conexiones abiertas del esquema " + nombre);
                }
            }
            sql.execute("SET lock_timeout = '5s'");
            sql.execute("DROP SCHEMA " + nombre + " CASCADE");
        }
    }

    public static SpringApplicationBuilder reinicio(Class<?> clase) {
        var esquema = ESQUEMAS.get(clase);
        if (esquema == null) {
            throw new IllegalStateException("No existe un esquema activo para " + clase.getName());
        }
        return new SpringApplicationBuilder(LavanderiaApplication.class).profiles("test-postgresql").initializers(esquema::configurar);
    }

    public static final class Configuracion implements ContextCustomizerFactory {
        @Override
        public ContextCustomizer createContextCustomizer(Class<?> clase, List<ContextConfigurationAttributes> atributos) {
            var integracion = clase.getAnnotation(IntegracionPostgresql.class);
            return integracion == null ? null : new ContextoPorClase(clase, integracion.value());
        }
    }

    // La clase forma parte de la clave de caché de Spring. DirtiesContext puede recrear
    // el contexto durante la clase sin perder los datos que comprueba la prueba siguiente.
    private record ContextoPorClase(Class<?> clase, String prefijo) implements ContextCustomizer {
        @Override
        public void customizeContext(ConfigurableApplicationContext contexto, MergedContextConfiguration configuracion) {
            exigirPerfil(contexto);
            ESQUEMAS.computeIfAbsent(clase, ignorada -> new EsquemaPostgresql(prefijo)).configurar(contexto);
        }
    }

    public static final class Limpieza extends AbstractTestExecutionListener {
        @Override
        public int getOrder() {
            // Spring invoca afterTestClass en orden inverso.
            return Ordered.HIGHEST_PRECEDENCE;
        }

        @Override
        public void afterTestClass(TestContext contexto) throws SQLException {
            var esquema = ESQUEMAS.remove(contexto.getTestClass());
            if (esquema != null) {
                contexto.markApplicationContextDirty(DirtiesContext.HierarchyMode.CURRENT_LEVEL);
                esquema.eliminar();
            }
        }
    }
}
