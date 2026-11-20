package com.miraquetelavo.lavanderia.comprobante.inicializacion;

import com.miraquetelavo.lavanderia.comprobante.entity.Comprobante;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@DependsOnDatabaseInitialization
@Profile("test-postgresql")
public class ComprobanteSchemaInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // Hibernate puede omitir los CHECK cuando actualiza una tabla existente.
        jdbc.execute("lock table comprobantes in share row exclusive mode");
        conservarNombreUnico("comprobantes_id_cobro_key", Comprobante.UK_COBRO);
        conservarNombreUnico("comprobantes_id_pago_key", Comprobante.UK_PAGO);
        var restricciones = jdbc.queryForList("""
                select contype = 'c' and convalidated and
                       regexp_replace(lower(pg_get_expr(conbin, conrelid)), '[[:space:]()]', '', 'g') = ?
                from pg_constraint
                where conrelid = 'comprobantes'::regclass and conname = ?
                """, Boolean.class, "id_cobroisnull<>id_pagoisnull", Comprobante.CK_OPERACION);
        if (restricciones.isEmpty()) {
            jdbc.execute("alter table comprobantes add constraint " + Comprobante.CK_OPERACION
                    + " check (" + Comprobante.CHECK_OPERACION + ")");
        } else if (!Boolean.TRUE.equals(restricciones.getFirst())) {
            throw new IllegalStateException("El CHECK de exclusividad de comprobantes no está validado o es incorrecto.");
        }
    }

    private void conservarNombreUnico(String generado, String esperado) {
        // create-only puede declarar la unicidad del OneToOne en línea, con nombre de PostgreSQL.
        if (Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists (select from pg_constraint where conrelid = 'comprobantes'::regclass
                    and conname = ? and contype = 'u')
                """, Boolean.class, generado))) {
            jdbc.execute("alter table comprobantes rename constraint " + generado + " to " + esperado);
        }
    }
}
