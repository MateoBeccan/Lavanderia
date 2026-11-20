-- pgAdmin Query Tool. Auditoría de solo lectura desde la conexión administrativa a dev.
BEGIN READ ONLY;
SET LOCAL search_path = pg_catalog;
DO $$
BEGIN
    IF current_database() <> 'lavanderia_dev' OR inet_server_port() IS DISTINCT FROM 5432
       OR inet_server_addr() IS DISTINCT FROM '127.0.0.1'::inet THEN
        RAISE EXCEPTION 'Abrir Query Tool en lavanderia_dev, 127.0.0.1:5432';
    END IF;
END
$$;

SELECT current_database(), current_user, inet_server_addr(), inet_server_port(), version();
SELECT rolcanlogin, rolinherit, rolsuper, rolcreatedb, rolcreaterole, rolreplication, rolbypassrls
FROM pg_roles WHERE rolname = 'lavanderia_dev_app';

WITH RECURSIVE membresias AS (
    SELECT m.roleid, m.inherit_option AS heredable, m.set_option AS asumible
    FROM pg_auth_members m WHERE m.member = 'lavanderia_dev_app'::regrole
    UNION
    SELECT m.roleid, a.heredable AND m.inherit_option, a.asumible AND m.set_option
    FROM pg_auth_members m JOIN membresias a ON a.roleid = m.member
)
SELECT roleid::regrole AS rol, heredable, asumible FROM membresias;
SELECT count(*) AS miembros_del_rol FROM pg_auth_members WHERE roleid = 'lavanderia_dev_app'::regrole;
SELECT count(*) AS sesiones_del_rol FROM pg_stat_activity WHERE usename = 'lavanderia_dev_app';
SELECT count(*) AS dependencias_del_rol
FROM pg_shdepend WHERE refclassid = 'pg_authid'::regclass AND refobjid = 'lavanderia_dev_app'::regrole;

-- pg_database es un catálogo compartido. Esta consulta permanece conectada a dev.
SELECT datname, datdba::regrole AS propietario,
       EXISTS (SELECT FROM aclexplode(coalesce(datacl, acldefault('d', datdba))) a
               WHERE grantee = 0 AND privilege_type = 'CONNECT') AS public_connect,
       EXISTS (SELECT FROM aclexplode(coalesce(datacl, acldefault('d', datdba))) a
               WHERE grantee = 0 AND privilege_type = 'TEMPORARY') AS public_temp,
       has_database_privilege('lavanderia_dev_app', oid, 'CONNECT') AS app_connect_acl
FROM pg_database WHERE datname IN ('lavanderia_dev', 'lavanderia_db') ORDER BY datname;

SELECT rule_number, type, database, user_name, address, netmask, auth_method, error IS NOT NULL AS error
FROM pg_hba_file_rules ORDER BY rule_number;
SELECT relkind, count(*) FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public' GROUP BY relkind ORDER BY relkind;
COMMIT;
