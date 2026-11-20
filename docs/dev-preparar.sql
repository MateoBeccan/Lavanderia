-- pgAdmin Query Tool. Ejecutar solo con autorización, conectado a lavanderia_dev.
-- Paso 1. Corregir el rol existente antes de entregar credenciales al proceso dev.
-- Ejecutar como bloque separado del aprovisionamiento de estructura.
BEGIN;
SET LOCAL search_path = pg_catalog;
DO $$
BEGIN
    IF current_database() <> 'lavanderia_dev' OR inet_server_port() IS DISTINCT FROM 5432
       OR inet_server_addr() IS DISTINCT FROM '127.0.0.1'::inet
       OR NOT (SELECT rolsuper FROM pg_roles WHERE rolname = current_user) THEN
        RAISE EXCEPTION 'Se requiere una sesión administrativa en lavanderia_dev, 127.0.0.1:5432';
    END IF;
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'lavanderia_dev_app' AND rolcanlogin) THEN
        RAISE EXCEPTION 'Falta el rol de login. Revisar el alta o habilitación por separado';
    END IF;
    IF EXISTS (SELECT FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
               WHERE n.nspname = 'public') THEN
        RAISE EXCEPTION 'El esquema public dejó de estar vacío. Revisar antes de cambiar las ACL';
    END IF;
    IF EXISTS (SELECT FROM pg_stat_activity WHERE usename = 'lavanderia_dev_app')
       OR EXISTS (SELECT FROM pg_auth_members
                  WHERE member = 'lavanderia_dev_app'::regrole OR roleid = 'lavanderia_dev_app'::regrole)
       OR EXISTS (SELECT FROM pg_shdepend
                  WHERE refclassid = 'pg_authid'::regclass AND refobjid = 'lavanderia_dev_app'::regrole) THEN
        RAISE EXCEPTION 'El rol tiene sesiones, membresías o dependencias. Revisar antes de modificarlo';
    END IF;
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'lavanderia_dev_app'
               AND (rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls)) THEN
        ALTER ROLE lavanderia_dev_app NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
    END IF;
    REVOKE CONNECT, CREATE, TEMPORARY ON DATABASE lavanderia_dev FROM PUBLIC;
    REVOKE CREATE, TEMPORARY ON DATABASE lavanderia_dev FROM lavanderia_dev_app;
    REVOKE CREATE ON SCHEMA public FROM PUBLIC, lavanderia_dev_app;
END
$$;
COMMIT;
