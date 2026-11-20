package com.miraquetelavo.lavanderia.pedido.inicializacion;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(1)
@DependsOnDatabaseInitialization
@Profile("test-postgresql")
public class PedidoEstadoPagoInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    public PedidoEstadoPagoInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        var tieneFk = jdbc.queryForObject("""
                select exists (select from pg_constraint
                    where conrelid = 'pedidos'::regclass and confrelid = 'estados_pago'::regclass
                    and conname = 'fk_pedidos_estado_pago' and contype = 'f' and convalidated)
                """, Boolean.class);
        if (!Boolean.TRUE.equals(tieneFk)) {
            throw new IllegalStateException("Falta la clave foránea validada fk_pedidos_estado_pago.");
        }
        if (esObligatorio()) {
            return;
        }

        jdbc.execute("lock table pedidos in access exclusive mode");
        if (esObligatorio()) {
            return;
        }
        jdbc.update("""
                with importes as (
                    select p.id_pedido, p.total_pedido, coalesce(sum(c.monto_cobrado), 0) as cobrado
                    from pedidos p left join cobros c using (id_pedido)
                    where p.id_estado_pago is null
                    group by p.id_pedido
                )
                update pedidos p set id_estado_pago = e.id_estado_pago
                from importes i join estados_pago e on e.codigo = case
                    when i.cobrado = 0 then 'PENDIENTE'
                    when i.cobrado < i.total_pedido then 'PARCIAL'
                    when i.cobrado = i.total_pedido then 'PAGADO'
                end
                where p.id_pedido = i.id_pedido and p.id_estado_pago is null
                """);
        if (Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists (select from pedidos where id_estado_pago is null)", Boolean.class))) {
            throw new IllegalStateException("Quedaron pedidos sin estado de pago. Revisar catálogo e importes históricos.");
        }
        jdbc.execute("alter table pedidos alter column id_estado_pago set not null");
    }

    private boolean esObligatorio() {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select attnotnull from pg_attribute
                where attrelid = 'pedidos'::regclass and attname = 'id_estado_pago' and not attisdropped
                """, Boolean.class));
    }
}
