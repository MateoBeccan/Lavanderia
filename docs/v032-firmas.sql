-- Huellas de las tablas preexistentes. Ejecutar con psql -X -qAt -F '|' y ON_ERROR_STOP=1.
BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY;
SET LOCAL TIME ZONE 'UTC';
SET LOCAL DateStyle = 'ISO, YMD';
SET LOCAL extra_float_digits = 3;

SELECT format($sql$
    SELECT %L, count(*), encode(sha256(convert_to(
        coalesce(string_agg(huella, '' ORDER BY huella COLLATE "C"), ''), 'UTF8')), 'hex')
    FROM (SELECT encode(sha256(convert_to(to_jsonb(fila)::text, 'UTF8')), 'hex') AS huella
          FROM %I.%I fila) AS filas
    $sql$, tablename, schemaname, tablename)
FROM pg_tables
WHERE schemaname = 'public' AND tablename <> 'solicitudes_financieras_idempotentes'
ORDER BY tablename COLLATE "C"
\gexec

SELECT 'secuencia.' || sequencename, last_value, increment_by
FROM pg_sequences WHERE schemaname = 'public'
ORDER BY sequencename COLLATE "C";
COMMIT;
