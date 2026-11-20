-- pgAdmin Query Tool. Abrir otra conexión con el login real lavanderia_dev_app.
-- El cliente de prueba es sintético. El ROLLBACK y el ID explícito evitan filas y consumo de secuencias.
BEGIN;
SET LOCAL search_path = pg_catalog;
DO $$
DECLARE
    cliente_prueba constant bigint := -620002;
BEGIN
    IF current_database() <> 'lavanderia_dev'
       OR current_user <> 'lavanderia_dev_app' OR session_user <> current_user
       OR inet_server_port() IS DISTINCT FROM 5432 OR inet_server_addr() IS DISTINCT FROM '127.0.0.1'::inet
       OR EXISTS (SELECT FROM pg_roles WHERE rolname = current_user
                  AND (rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls))
       OR EXISTS (SELECT FROM pg_auth_members WHERE member = current_user::regrole OR roleid = current_user::regrole)
       OR NOT has_schema_privilege('public', 'USAGE')
       OR has_database_privilege(current_database(), 'CREATE,TEMP')
       OR EXISTS (SELECT FROM pg_namespace WHERE nspname NOT LIKE 'pg_%' AND has_schema_privilege(oid, 'CREATE'))
       OR EXISTS (SELECT FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                  WHERE n.nspname = 'public' AND pg_has_role(c.relowner, 'USAGE')) THEN
        RAISE EXCEPTION 'El destino, la identidad o los privilegios de la sesión dev son incorrectos';
    END IF;

    IF EXISTS (SELECT FROM pg_trigger WHERE tgrelid = 'public.clientes'::regclass AND NOT tgisinternal)
       OR EXISTS (SELECT FROM pg_rewrite WHERE ev_class = 'public.clientes'::regclass)
       OR EXISTS (SELECT FROM public.clientes WHERE id = cliente_prueba) THEN
        RAISE EXCEPTION 'La tabla de prueba tiene reglas, triggers de usuario o un ID ocupado';
    END IF;

    IF EXISTS (
        SELECT FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p')
          AND (has_table_privilege(c.oid, 'TRUNCATE')
               OR (c.relname <> 'flyway_schema_history' AND NOT has_table_privilege(c.oid, 'SELECT'))
               OR (c.relname IN ('movimientos_caja', 'cobros', 'pagos', 'solicitudes_financieras_idempotentes')
                   AND (NOT has_table_privilege(c.oid, 'INSERT') OR has_any_column_privilege(c.oid, 'UPDATE')
                        OR has_table_privilege(c.oid, 'DELETE'))))
    ) OR EXISTS (
        SELECT FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        JOIN pg_attribute a ON a.attrelid = c.oid AND a.attidentity <> '' AND NOT a.attisdropped
        WHERE n.nspname = 'public'
          AND NOT (
            has_sequence_privilege(pg_get_serial_sequence(format('%I.%I', n.nspname, c.relname), a.attname), 'USAGE')
                = has_table_privilege(c.oid, 'INSERT')
            AND NOT has_sequence_privilege(pg_get_serial_sequence(format('%I.%I', n.nspname, c.relname), a.attname),
                                           'UPDATE,SELECT')
          )
    ) THEN
        RAISE EXCEPTION 'Las ACL de tablas o secuencias no coinciden con el criterio de desarrollo';
    END IF;

    INSERT INTO public.clientes (id, activo, nombre, apellido, fecha_creacion)
    VALUES (cliente_prueba, true, 'Prueba', 'Sintética', localtimestamp);
    UPDATE public.clientes SET activo = false WHERE id = cliente_prueba;
    IF NOT EXISTS (SELECT FROM public.clientes WHERE id = cliente_prueba AND NOT activo) THEN
        RAISE EXCEPTION 'Falló la escritura o lectura del cliente sintético';
    END IF;

    BEGIN
        CREATE TABLE public.dev_prueba_ddl (id integer);
        RAISE EXCEPTION 'CREATE TABLE estaba permitido';
    EXCEPTION WHEN insufficient_privilege THEN NULL;
    END;
    BEGIN
        ALTER TABLE public.clientes ADD COLUMN dev_prueba_ddl integer;
        RAISE EXCEPTION 'ALTER TABLE estaba permitido';
    EXCEPTION WHEN insufficient_privilege THEN NULL;
    END;
    BEGIN
        DROP TABLE public.clientes RESTRICT;
        RAISE EXCEPTION 'DROP TABLE estaba permitido';
    EXCEPTION WHEN insufficient_privilege THEN NULL;
    END;

    RAISE NOTICE 'Escritura y lectura sintéticas comprobadas. CREATE, ALTER y DROP rechazados por permisos.';
END
$$;
ROLLBACK;

BEGIN READ ONLY;
DO $$
BEGIN
    IF EXISTS (SELECT FROM public.clientes WHERE id = -620002) THEN
        RAISE EXCEPTION 'Quedó el cliente de verificación después del ROLLBACK';
    END IF;
END
$$;
SELECT current_database(), current_user, inet_server_addr(), inet_server_port(), current_schema();
SELECT c.relname AS tabla,
       has_table_privilege(c.oid, 'SELECT') AS lectura,
       has_table_privilege(c.oid, 'INSERT') AS insercion,
       has_table_privilege(c.oid, 'UPDATE') AS actualizacion,
       has_table_privilege(c.oid, 'DELETE') AS borrado,
       has_table_privilege(c.oid, 'TRUNCATE') AS truncado
FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p') ORDER BY 1;
SELECT c.relname AS secuencia, has_sequence_privilege(c.oid, 'USAGE') AS uso,
       has_sequence_privilege(c.oid, 'SELECT') AS lectura,
       has_sequence_privilege(c.oid, 'UPDATE') AS actualizacion
FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public' AND c.relkind = 'S' ORDER BY 1;
SELECT count(*) AS roles FROM public.roles;
SELECT count(*) AS metodos_activos FROM public.metodos_pago WHERE activo;
SELECT count(*) AS tipos_activos FROM public.tipos_movimiento_caja WHERE activo;
SELECT usuario_id, clave, operacion, huella, ubicacion, cuerpo, creado_en
FROM public.solicitudes_financieras_idempotentes LIMIT 0;
COMMIT;
