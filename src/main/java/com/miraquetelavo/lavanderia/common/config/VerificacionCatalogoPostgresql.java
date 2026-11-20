package com.miraquetelavo.lavanderia.common.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Component;

@Component
@Profile("!test & !test-postgresql")
class VerificacionCatalogoPostgresql implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    VerificacionCatalogoPostgresql(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        var rolLimitado = jdbc.queryForObject("""
                select not (r.rolsuper or r.rolcreatedb or r.rolcreaterole
                            or r.rolbypassrls or r.rolreplication)
                    and not has_database_privilege(current_database(), 'CREATE, TEMP')
                    and not has_schema_privilege(current_schema(), 'CREATE')
                    and not exists (select from pg_auth_members where member = r.oid)
                    and not exists (
                        select from pg_class c join pg_namespace n on n.oid = c.relnamespace
                        where n.nspname = current_schema() and c.relowner = r.oid)
                from pg_roles r where r.rolname = current_user
                """, Boolean.class);
        if (!Boolean.TRUE.equals(rolLimitado)) {
            throw new IllegalStateException("PostgreSQL requiere un rol dedicado sin propiedad ni permisos de DDL.");
        }
        jdbc.query("""
                select usuario_id, clave, operacion, huella, ubicacion, cuerpo, creado_en
                from solicitudes_financieras_idempotentes limit 0
                """, (ResultSetExtractor<Void>) filas -> null);
        var plan = jdbc.queryForList("""
                explain insert into cajas (estado) values ('ABIERTA')
                on conflict (estado) where estado = 'ABIERTA' do nothing
                """, String.class);
        if (plan.stream().map(String::strip).noneMatch("Conflict Arbiter Indexes: ux_cajas_unica_abierta"::equals)) {
            throw new IllegalStateException("Falta el índice único parcial de Caja ux_cajas_unica_abierta.");
        }
        var preparados = jdbc.queryForObject("""
                select (select count(*) from roles where nombre_rol in ('ADMINISTRADOR', 'EMPLEADO')) = 2
                   and (select count(*) from estados_pago where codigo in ('PENDIENTE', 'PARCIAL', 'PAGADO')) = 3
                   and (select count(*) from estados_pedido where codigo in
                       ('PENDIENTE', 'EN_PROCESO', 'LISTO', 'ENTREGADO', 'CANCELADO')) = 5
                   and (select count(*) from metodos_pago where codigo = 'EFECTIVO' and activo) = 1
                   and (select count(*) from tipos_movimiento_caja where codigo in
                       ('INGRESO_MANUAL', 'EGRESO_MANUAL', 'COBRO_PEDIDO', 'PAGO_PROVEEDOR') and activo) = 4
                """, Boolean.class);
        if (!Boolean.TRUE.equals(preparados)) {
            throw new IllegalStateException("Faltan registros operativos en los catálogos base.");
        }
    }
}
