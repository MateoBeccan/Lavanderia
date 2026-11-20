package com.miraquetelavo.lavanderia;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import static org.assertj.core.api.Assertions.assertThat;

class ConfiguracionPostgresqlTest {
    @Test
    void elArranqueNormalConProduccionODesarrolloNoEjecutaDdlNiCompletaCredenciales() throws Exception {
        var normal = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"));
        assertThat(normal).containsEntry("spring.datasource.url", "${LAVANDERIA_DB_URL}")
                .containsEntry("spring.datasource.username", "${LAVANDERIA_DB_USER}")
                .containsEntry("spring.datasource.password", "${LAVANDERIA_DB_PASSWORD}");

        for (String perfil : new String[]{"dev", "prod"}) {
            var propiedades = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"));
            propiedades.putAll(PropertiesLoaderUtils.loadProperties(new ClassPathResource(
                    "application-" + perfil + ".properties")));
            String prefijo = perfil.equals("dev") ? "LAVANDERIA_DEV_DB_" : "LAVANDERIA_DB_";
            assertThat(propiedades).containsEntry("spring.jpa.hibernate.ddl-auto", "none")
                    .containsEntry("spring.flyway.enabled", "false")
                    .containsEntry("spring.sql.init.mode", "never")
                    .containsEntry("spring.datasource.url", "${" + prefijo + "URL}")
                    .containsEntry("spring.datasource.username", "${" + prefijo + "USER}")
                    .containsEntry("spring.datasource.password", "${" + prefijo + "PASSWORD}")
                    .doesNotContainKeys("jakarta.persistence.schema-generation.database.action",
                            "spring.jpa.properties.jakarta.persistence.schema-generation.database.action",
                            "spring.jpa.properties.hibernate.hbm2ddl.auto");
        }
    }
}
