-- PostgreSQL 17, ejecutar con psql -X y una conexión explícita a lavanderia_db.
-- Sin variables, el archivo lista únicamente los 24 candidatos aprobados y public.
-- Antes de la fase transaccional, verificar el respaldo con una restauración
-- llamada lavanderia_db en otra instancia. Revisar la cascada en esa copia
-- y detener aplicación, pruebas y otros clientes antes de intervenir la base habitual.
-- Usar rol DBA (superusuario) y pasar inventario (JSON de la instancia),
-- cluster (system_identifier), servidor (IP de la conexión) y puerto con -v.
-- La operación abarca los 24 esquemas en una sola transacción y termina en ROLLBACK.
-- Solo tras autorización explícita, aplicar con COMMIT en una copia revisada.
\set ON_ERROR_STOP on
\set patron '^(caja|catalogos_pago|cobro|estado_pedido|movimientos_caja|pedido|prenda|usuarios)_test_(c56c28de|c85ba68f|cb081197)$'

SELECT current_database() AS base, inet_server_addr() AS servidor,
       inet_server_port() AS puerto, system_identifier AS cluster
FROM pg_control_system();

SELECT n.nspname AS esquema,
       n.oid AS oid_esquema,
       pg_get_userbyid(n.nspowner) AS dueno,
       count(*) FILTER (WHERE c.relkind IN ('r', 'p')) AS tablas,
       array_agg(c.relname ORDER BY c.relname) FILTER (WHERE c.relkind IN ('r', 'p')) AS nombres_tablas,
       array_agg(c.relname ORDER BY c.relname) FILTER (WHERE c.relkind IN ('v', 'm')) AS vistas
FROM pg_namespace n
LEFT JOIN pg_class c ON c.relnamespace = n.oid
WHERE n.nspname = 'public' OR n.nspname ~ :'patron'
GROUP BY n.oid
ORDER BY n.nspname;

\if :{?inventario}
BEGIN;
SET LOCAL lock_timeout = '3s';
SET LOCAL statement_timeout = '30s';
SET LOCAL search_path = pg_catalog, pg_temp;
SET LOCAL event_triggers = on;
SET LOCAL session_replication_role = origin;

CREATE TEMP TABLE limpieza_objetivo ON COMMIT DROP AS
SELECT n.oid, n.nspname::text AS nombre, false AS cascada_verificada
FROM pg_namespace n
JOIN jsonb_to_recordset(:'inventario'::jsonb) AS i(nombre text, oid oid, dueno text)
  ON n.nspname = i.nombre AND n.oid = i.oid AND pg_get_userbyid(n.nspowner) = i.dueno
WHERE current_database() = 'lavanderia_db'
  AND inet_server_addr() = :'servidor'::inet
  AND inet_server_port() = :'puerto'::integer
  AND (SELECT system_identifier FROM pg_control_system()) = :'cluster'::bigint
  AND n.nspname <> 'public'
  AND n.nspname ~ :'patron'
  AND jsonb_array_length(:'inventario'::jsonb) = 24;

DO $validar$
BEGIN
    IF (SELECT count(*) FROM pg_temp.limpieza_objetivo) <> 24
       OR (SELECT count(DISTINCT oid) FROM pg_temp.limpieza_objetivo) <> 24 THEN
        RAISE EXCEPTION 'La instancia o los 24 nombres, OID y propietarios no coinciden con el inventario aprobado';
    END IF;
    IF EXISTS (
        SELECT FROM pg_temp.limpieza_objetivo o
        WHERE (SELECT count(*) FROM pg_class c
               WHERE c.relnamespace = o.oid AND c.relkind IN ('r', 'p')) <> 15
    ) OR (SELECT count(*) FROM pg_tables WHERE schemaname = 'public') <> 24 THEN
        RAISE EXCEPTION 'Cambió el conteo de tablas del inventario';
    END IF;
    IF EXISTS (SELECT FROM pg_prepared_xacts WHERE database = current_database()) THEN
        RAISE EXCEPTION 'Hay transacciones preparadas en la base';
    END IF;
    IF EXISTS (
        SELECT 1 FROM pg_stat_activity
        WHERE datname = current_database() AND pid <> pg_backend_pid()
          AND backend_type IN ('client backend', 'logical replication worker')
    ) THEN
        RAISE EXCEPTION 'Hay otras sesiones en la base. Detener la actividad y volver a revisar';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_event_trigger WHERE evtenabled <> 'D') THEN
        RAISE EXCEPTION 'Hay event triggers activos. Revisar sus efectos con el DBA';
    END IF;
END
$validar$;

-- Solo los OID de TOAST y sus índices internos asociados a tablas confirmadas.
CREATE TEMP TABLE limpieza_toast ON COMMIT DROP AS
SELECT c.reltoastrelid AS oid
FROM pg_class c JOIN pg_temp.limpieza_objetivo o ON o.oid = c.relnamespace
WHERE c.reltoastrelid <> 0
UNION
SELECT i.indexrelid
FROM pg_index i
JOIN pg_class c ON c.reltoastrelid = i.indrelid
JOIN pg_temp.limpieza_objetivo o ON o.oid = c.relnamespace;

-- Algunos objetos dependientes no informan schema_name en sql_drop.
-- Autorizar sus OID solo cuando la relación que los contiene es candidata.
CREATE TEMP TABLE limpieza_objetos_asociados ON COMMIT DROP AS
WITH relaciones AS (
    SELECT c.oid FROM pg_class c JOIN pg_temp.limpieza_objetivo o ON o.oid = c.relnamespace
)
SELECT 'pg_attrdef'::regclass::oid AS clase, a.oid
FROM pg_attrdef a JOIN relaciones r ON r.oid = a.adrelid
UNION ALL
SELECT 'pg_trigger'::regclass::oid, t.oid FROM pg_trigger t JOIN relaciones r ON r.oid = t.tgrelid
UNION ALL
SELECT 'pg_rewrite'::regclass::oid, w.oid FROM pg_rewrite w JOIN relaciones r ON r.oid = w.ev_class
UNION ALL
SELECT 'pg_policy'::regclass::oid, p.oid FROM pg_policy p JOIN relaciones r ON r.oid = p.polrelid;

CREATE FUNCTION pg_temp.limpieza_verificar_cascada() RETURNS event_trigger
LANGUAGE plpgsql
AS $verificar$
DECLARE
    objeto record;
BEGIN
    FOR objeto IN SELECT * FROM pg_event_trigger_dropped_objects()
    LOOP
        IF objeto.classid = 'pg_namespace'::regclass THEN
            UPDATE pg_temp.limpieza_objetivo SET cascada_verificada = true WHERE oid = objeto.objid;
            IF FOUND THEN
                CONTINUE;
            END IF;
        ELSIF EXISTS (SELECT FROM pg_temp.limpieza_objetivo WHERE nombre = objeto.schema_name)
           OR (objeto.schema_name IS NULL AND EXISTS (
               SELECT FROM pg_temp.limpieza_objetos_asociados WHERE clase = objeto.classid AND oid = objeto.objid
           ))
           OR (objeto.classid = 'pg_class'::regclass AND objeto.schema_name = 'pg_toast'
               AND EXISTS (SELECT FROM pg_temp.limpieza_toast WHERE oid = objeto.objid)) THEN
            CONTINUE;
        END IF;
        RAISE EXCEPTION 'La cascada alcanza un objeto fuera del esquema aprobado: %',
            objeto.object_identity;
    END LOOP;
END
$verificar$;

CREATE EVENT TRIGGER lavanderia_verificar_limpieza_historica
ON sql_drop WHEN TAG IN ('DROP SCHEMA')
EXECUTE FUNCTION pg_temp.limpieza_verificar_cascada();

SELECT format('DROP SCHEMA %s CASCADE', string_agg(format('%I', nombre), ', ' ORDER BY nombre))
FROM pg_temp.limpieza_objetivo
\gexec

DO $confirmar$
BEGIN
    IF EXISTS (SELECT FROM pg_temp.limpieza_objetivo WHERE NOT cascada_verificada)
       OR EXISTS (SELECT FROM pg_namespace n JOIN pg_temp.limpieza_objetivo o ON n.oid = o.oid)
       OR (SELECT count(*) FROM pg_tables WHERE schemaname = 'public') <> 24 THEN
        RAISE EXCEPTION 'La cascada no se verificó para todos los esquemas o cambió el conteo de public';
    END IF;
END
$confirmar$;

SELECT count(*) FILTER (WHERE cascada_verificada) AS esquemas_verificados,
       (SELECT count(*) FROM pg_tables WHERE schemaname = 'public') AS tablas_public
FROM pg_temp.limpieza_objetivo;

DROP EVENT TRIGGER lavanderia_verificar_limpieza_historica;
DROP FUNCTION pg_temp.limpieza_verificar_cascada();

-- Mantener ROLLBACK durante la revisión. Una excepción deja la transacción abortada.
ROLLBACK;
\endif
