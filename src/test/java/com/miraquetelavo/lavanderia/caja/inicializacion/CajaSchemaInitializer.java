package com.miraquetelavo.lavanderia.caja.inicializacion;

import com.miraquetelavo.lavanderia.caja.entity.Caja;
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
public class CajaSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    public CajaSchemaInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // Serializa la actualización del esquema y evita promover el bloqueo durante ALTER TABLE.
        jdbc.execute("lock table cajas in access exclusive mode");
        jdbc.execute("""
                create unique index if not exists ux_cajas_unica_abierta
                on cajas (estado) where estado = 'ABIERTA'
                """);
        // EXPLAIN obliga a PostgreSQL a resolver el índice árbitro sin insertar una fila.
        var plan = jdbc.queryForList("""
                explain insert into cajas (estado) values ('ABIERTA')
                on conflict (estado) where estado = 'ABIERTA' do nothing
                """, String.class);
        if (plan.stream().map(String::strip)
                .noneMatch("Conflict Arbiter Indexes: ux_cajas_unica_abierta"::equals)) {
            throw new IllegalStateException("PostgreSQL no confirmó el índice único parcial de Caja.");
        }
        actualizarCheckImportes();
    }

    private void actualizarCheckImportes() {
        // Hibernate update no reemplaza el CHECK de una tabla ya creada.
        var restricciones = jdbc.queryForList("""
                select pg_get_constraintdef(oid) from pg_constraint
                where conrelid = 'cajas'::regclass and conname = 'ck_cajas_importes' and contype = 'c'
                """, String.class);
        if (restricciones.stream().anyMatch(definicion -> definicion.contains("monto_final_calc"))) {
            jdbc.execute("alter table cajas drop constraint ck_cajas_importes, "
                    + "add constraint ck_cajas_importes check (" + Caja.CHECK_IMPORTES + ")");
        } else if (restricciones.isEmpty()) {
            jdbc.execute("alter table cajas add constraint ck_cajas_importes check (" + Caja.CHECK_IMPORTES + ")");
        }
    }
}
