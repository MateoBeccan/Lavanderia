-- Plantilla operativa para psql. Revisar en una copia antes de autorizar su aplicación.
-- Para desarrollo, ingresar exclusivamente por dev-permisos.psql.
-- Invocar con -v app_db=... -v app_schema=... -v app_role=... y ON_ERROR_STOP.
-- El rol LOGIN dedicado debe existir, sin membresías y con contraseña entregada por separado.
\set ON_ERROR_STOP on
SELECT current_database() = :'app_db' AS destino_correcto \gset
\if :destino_correcto
\else
    \echo La conexión apunta a otra base. Operación cancelada.
    SELECT 1 / 0;
\endif

BEGIN;
SET LOCAL search_path TO pg_catalog, :"app_schema";

GRANT CONNECT ON DATABASE :"app_db" TO :"app_role";
GRANT USAGE ON SCHEMA :"app_schema" TO :"app_role";

GRANT SELECT ON roles, usuarios, clientes, prendas, servicios, estados_pedido, estados_pago,
    pedidos, detalles_pedido, historial_estados_pedido, metodos_pago, tipos_movimiento_caja,
    cajas, movimientos_caja, cobros, proveedores, categorias_insumo, insumos, proveedores_insumos,
    compras, detalles_compra, pagos, comprobantes, solicitudes_financieras_idempotentes TO :"app_role";

GRANT INSERT, UPDATE ON usuarios, clientes, prendas, servicios, pedidos, cajas, proveedores,
    categorias_insumo, insumos, compras, detalles_pedido, detalles_compra TO :"app_role";
GRANT INSERT ON historial_estados_pedido, movimientos_caja, cobros, proveedores_insumos,
    pagos, comprobantes, solicitudes_financieras_idempotentes TO :"app_role";
GRANT DELETE ON detalles_pedido, detalles_compra, proveedores_insumos TO :"app_role";
-- El bloqueo de la fila de rol al administrar cuentas exige UPDATE sobre alguna columna.
GRANT UPDATE (descripcion) ON roles TO :"app_role";

SELECT format('GRANT USAGE ON SEQUENCE %I.%I TO %I', ns.nspname, seq.relname, :'app_role')
FROM pg_class seq
JOIN pg_namespace ns ON ns.oid = seq.relnamespace
JOIN pg_depend dep ON dep.classid = 'pg_class'::regclass AND dep.objid = seq.oid AND dep.objsubid = 0
JOIN pg_class tabla ON dep.refclassid = 'pg_class'::regclass AND dep.refobjid = tabla.oid
    AND dep.refobjsubid > 0 AND tabla.relnamespace = ns.oid
WHERE ns.nspname = :'app_schema' AND seq.relkind = 'S' AND dep.deptype IN ('a', 'i')
  AND tabla.relname IN ('usuarios', 'clientes', 'prendas', 'servicios', 'pedidos', 'cajas',
    'proveedores', 'categorias_insumo', 'insumos', 'compras', 'detalles_pedido', 'detalles_compra',
    'historial_estados_pedido', 'movimientos_caja', 'cobros', 'pagos', 'comprobantes')
\gexec

COMMIT;
