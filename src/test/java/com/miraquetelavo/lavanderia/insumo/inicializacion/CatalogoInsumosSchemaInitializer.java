package com.miraquetelavo.lavanderia.insumo.inicializacion;

import com.miraquetelavo.lavanderia.categoriainsumo.entity.CategoriaInsumo;
import com.miraquetelavo.lavanderia.insumo.entity.Insumo;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@DependsOnDatabaseInitialization
@Profile("test-postgresql")
public class CatalogoInsumosSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    public CatalogoInsumosSchemaInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // Serializa la instalación si dos instancias actualizan el mismo esquema.
        jdbc.execute("lock table categorias_insumo, insumos in share row exclusive mode");
        jdbc.execute("create unique index if not exists " + CategoriaInsumo.UK_NOMBRE
                + " on categorias_insumo (lower(nombre_cat))");
        jdbc.execute("create unique index if not exists " + Insumo.UK_NOMBRE
                + " on insumos (lower(nombre_insumo))");
    }
}
