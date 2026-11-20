# PostgreSQL de desarrollo en IntelliJ

## Aprovisionamiento aplicado

`lavanderia_dev` fue aprovisionada el 20 de noviembre de 2026. Las instrucciones de preparación de esta página quedan como registro y guía para una instalación vacía. El SQL de estructura se protege contra una segunda aplicación sobre una base ya preparada.

- Se agregaron cinco reglas HBA exclusivas para `lavanderia_dev_app`. Las ocho reglas originales conservaron su orden y contenido. Se comprobó una conexión administrativa nueva y el login dev por IPv4 e IPv6. En ambos casos el intento del login dev hacia `lavanderia_db` fue rechazado por HBA antes de ejecutar SQL. Se conservó un respaldo administrativo del HBA previo.
- Se aplicaron las ACL de dev, la estructura, los catálogos y los GRANT mínimos. El catálogo de PostgreSQL confirmó 25 tablas, 22 secuencias, 32 FK validadas, PK y CHECK de V032 e índice único parcial de Caja.
- `dev-verificar.sql` pasó con una conexión real del rol. Se verificaron el INSERT, UPDATE y SELECT de un cliente sintético y el rechazo de CREATE, ALTER y DROP. Una segunda transacción comprobó el DELETE del vínculo proveedor-insumo. Ambas revirtieron sus datos y las cuatro secuencias observadas conservaron su estado. Los catálogos quedaron cargados. Las tablas de negocio consultadas, V032 y el historial Flyway quedaron vacíos.
- Spring Boot arrancó en loopback con el perfil `dev`, Hibernate sin DDL, Flyway deshabilitado y el datasource verificado en `pg_stat_activity`. Se repitió el arranque desde terminal con las mismas variables guardadas en la configuración local de IntelliJ, incluida su variable JWT existente. El GET de verificación devolvió 401 sin credenciales HTTP. Se detuvieron las instancias al terminar y se conservó la configuración de IntelliJ.

## Configuración y aislamiento

El perfil dev resuelve el datasource desde las tres variables `LAVANDERIA_DEV_DB_*`. Los perfiles predeterminado y prod usan otro conjunto de variables. La configuración común usa `ddl-auto=none`, Flyway e inicialización SQL deshabilitados y trazado SQL apagado.

Antes de aprovisionar un entorno, revisá atributos del rol, membresías, sesiones, ACL de base y esquema, y orden de autenticación. En un clúster compartido, los permisos de PUBLIC y las reglas generales de HBA pueden dar acceso fuera de dev. Para el rol de desarrollo, comprobá aislamiento antes de instalar el esquema. El esquema de dev contiene 25 PK, 32 FK, 20 CHECK, 25 índices explícitos y 22 secuencias de identidad. Los GRANT prevén USAGE para las 17 secuencias de tablas con altas desde la aplicación.

## Preparación administrativa

El paso 0 es de solo lectura. Los pasos 1 a 5 requieren autorización explícita. Abrí una conexión administrativa en pgAdmin a `127.0.0.1:5432`, seleccioná **Databases → lavanderia_dev** y abrí **Query Tool**. Usá **Open File** (Ctrl+O) y **Execute Script** (F5) para cada archivo SQL completo, sin texto seleccionado. Revisá **Messages** y el estado de la transacción antes de avanzar. Si hay un error, ejecutá `ROLLBACK` en esa sesión y revisá la causa. Las guardas se ejecutan dentro de su transacción y dependen de ejecutar el archivo completo. [Query Tool de pgAdmin](https://www.pgadmin.org/docs/pgadmin4/latest/query_tool.html)

0. **Repetir la auditoría.** Ejecutá [dev-auditar.sql](dev-auditar.sql). Verificá servidor, base, esquema vacío y ausencia de sesiones, membresías o dependencias. Revisá el orden de HBA y `CONNECT` de `PUBLIC`. El inventario del catálogo compartido se ejecuta desde dev.

1. **Limitar el login al destino dev.** En el clúster compartido, los permisos de `PUBLIC` pueden conceder CONNECT a otras bases. Pedile al administrador del servidor que revise el archivo indicado por `SHOW hba_file`, haga una copia de la configuración y coloque estas reglas específicas del rol antes de las reglas generales. Conservá una sesión administrativa abierta y revisá el orden y los errores en `pg_hba_file_rules`. En Windows los cambios de HBA afectan conexiones nuevas. Si la política impide editar la autenticación del clúster compartido, usá un servicio PostgreSQL separado para dev y adaptá los destinos y guardas. [Autenticación de PostgreSQL 17](https://www.postgresql.org/docs/17/auth-pg-hba-conf.html)

   ```text
   local all            lavanderia_dev_app                 reject
   host  lavanderia_dev  lavanderia_dev_app  127.0.0.1/32  scram-sha-256
   host  lavanderia_dev  lavanderia_dev_app  ::1/128       scram-sha-256
   host  all            lavanderia_dev_app  0.0.0.0/0     reject
   host  all            lavanderia_dev_app  ::/0          reject
   ```

   Verificá que la primera coincidencia para ese rol sea la regla específica de dev por TCP y el rechazo en cualquier otra base o socket local. El rechazo de HBA limita conexiones nuevas incluso si el catálogo todavía informa `CONNECT` heredado de `PUBLIC` en la principal. Revisá que el rol siga sin sesiones anteriores. Para verificar el rechazo por TCP, usá la base de mantenimiento `postgres` como destino de la conexión de prueba. El error esperado menciona un rechazo de HBA, antes de la autenticación. La prueba positiva de conexión se hace en dev.

2. **Corregir el rol existente.** Con autorización para modificar `lavanderia_dev_app`, abrí **Query Tool** sobre `lavanderia_dev`, con conexión administrativa a `127.0.0.1:5432`. Revisá y ejecutá [dev-preparar.sql](dev-preparar.sql). El script vuelve a verificar sesiones y dependencias, quita los privilegios elevados y revoca los accesos públicos relevantes de la base dev. Conserva el verificador actual. Si la contraseña disponible se desconoce, la asignación de una nueva contraseña SCRAM es una intervención aparte y autorizada. Gestioná el valor por el canal de secretos y evitá pegarlo en Query Tool.
3. **Cargar estructura.** En el mismo Query Tool, abrí y revisá [dev-estructura.sql](dev-estructura.sql). Ejecutá el archivo completo en la base vacía. Se generó con `pg_restore --schema-only --no-owner --no-privileges` desde una exportación verificada de un entorno de pruebas. Se cotejaron 23 tablas de entidades y dos adicionales, V032 y el historial vacío de Flyway. La revisión incluyó la PK de V032 y el índice parcial de Caja. Se ajustó la creación de `public` para el esquema existente y se agregó una guarda transaccional de destino y esquema vacío. La única migración disponible en el repositorio es V032 y esta estructura ya la incluye. [Opciones de restauración de PostgreSQL 17](https://www.postgresql.org/docs/17/app-pgrestore.html)
4. **Aprovisionar los catálogos.** Ejecutá [dev-catalogos.sql](dev-catalogos.sql) como mantenedor en dev. El primer arranque y las consultas de catálogo se pueden verificar sin crear una cuenta de negocio. Para habilitar el login web, seguí el procedimiento de administrador inicial una vez instalados los permisos.
5. **Asignar los permisos de ejecución.** Seleccioná `lavanderia_dev` en el explorador de pgAdmin y abrí **Tools → PSQL Tool** con la conexión administrativa. Ejecutá [dev-permisos.psql](dev-permisos.psql), que comprueba el destino, el rol y los prerrequisitos, y fija la base, el esquema y el rol antes de llamar a la [plantilla compartida de GRANT](permisos-postgresql-aplicacion.sql). La plantilla conserva su uso parametrizado para otros procedimientos. Para dev, usá la entrada fija. La plantilla concede SELECT de dominio, las escrituras acotadas por tabla y USAGE en las secuencias de inserción. Los movimientos y V032 quedan con SELECT e INSERT. Los objetos permanecen bajo propiedad administrativa. [PSQL Tool de pgAdmin](https://www.pgadmin.org/docs/pgadmin4/latest/psql_tool.html) · [Privilegios de PostgreSQL 17](https://www.postgresql.org/docs/17/ddl-priv.html)

   ```text
   \i 'C:/lavanderia/docs/dev-permisos.psql'
   ```

   Si la instalación de pgAdmin carece de PSQL Tool, ejecutá con el cliente PostgreSQL 17 y la misma cuenta administrativa.

   ```powershell
   & 'C:/Program Files/PostgreSQL/17/bin/psql.exe' -X -h 127.0.0.1 -p 5432 -U postgres -d lavanderia_dev -f 'C:/lavanderia/docs/dev-permisos.psql'
   ```

## Verificar y arrancar IntelliJ

1. Una vez autorizado el ensayo, registrá una conexión separada en pgAdmin a `127.0.0.1:5432/lavanderia_dev` con el login real `lavanderia_dev_app` y su contraseña. En **Query Tool**, ejecutá [dev-verificar.sql](dev-verificar.sql) completo. Su primer bloque incluye escritura y pruebas de rechazo de CREATE, ALTER y DROP dentro de una transacción con ROLLBACK. El ID explícito evita consumir la secuencia. Debés ver el aviso de éxito de las pruebas y el ROLLBACK. Su segundo bloque es de solo lectura y comprueba que la fila sintética desapareció, lista las ACL efectivas de tablas y secuencias y consulta catálogos y V032. Si la herramienta solo muestra los resultados de la última consulta, abrí otra pestaña para inspeccionar cada SELECT. [Query Tool de pgAdmin](https://www.pgadmin.org/docs/pgadmin4/latest/query_tool.html)
2. En **Run → Edit Configurations → LavanderiaApplication → Environment variables**, configurá estos valores. Inyectá ambos secretos desde un entorno o archivo local fuera del repositorio. El JWT de desarrollo debe ser una clave aleatoria independiente, con al menos 32 bytes antes de codificarla en Base64. [Variables de entorno en IntelliJ IDEA](https://www.jetbrains.com/help/idea/program-arguments-and-environment-variables.html)

   | Variable | Valor |
   | --- | --- |
   | `SPRING_PROFILES_ACTIVE` | `dev` |
   | `LAVANDERIA_DEV_DB_URL` | `jdbc:postgresql://127.0.0.1:5432/lavanderia_dev?currentSchema=public&ApplicationName=lavanderia-dev-idea` |
   | `LAVANDERIA_DEV_DB_USER` | `lavanderia_dev_app` |
   | `LAVANDERIA_DEV_DB_PASSWORD` | Secreto PostgreSQL dev |
   | `LAVANDERIA_JWT_SECRET` | Secreto JWT exclusivo de dev |

3. Quitá de esa configuración cualquier override de datasource, Hibernate, SQL init o Flyway. Ejecutá la aplicación y comprobá que el log informe el perfil `dev` y complete el arranque. Con el proceso activo, esta consulta administrativa, ejecutada en dev, permite cotejar el destino, el usuario y el cliente sin operaciones financieras. Contrastá el resultado con las consultas de solo lectura del paso 1.

   ```sql
   SELECT datname, usename, application_name, client_addr
   FROM pg_stat_activity
   WHERE application_name = 'lavanderia-dev-idea';
   ```

Mantené credenciales, JWT, datos sintéticos y scripts operativos separados de producción. El aislamiento propuesto limita el login de la aplicación. Para separar también al administrador, la configuración y los recursos del servidor, usá un clúster dev aparte. Para cambios futuros, exportá y revisá estructura sin datos con una cuenta de mantenimiento y repetí la verificación de ACL. Las pruebas automatizadas de integración usan su propio clúster desechable y sus variables `LAVANDERIA_TEST_JDBC_*`.

## Administrador inicial de desarrollo

`InicializadorUsuarios` solo se carga en `test-postgresql`. Para dev, usá el comando local `CrearAdministradorDev` después de aprovisionar los roles y los permisos. Es una clase de herramientas en el classpath de pruebas. Arranca un servidor temporal en loopback con perfil `dev`, comprueba la identidad de la conexión y reutiliza `UsuarioServiceImpl.inicializarAdministrador`. Ese servicio bloquea el rol administrador y realiza el alta solo si la tabla de usuarios está vacía. Usa el mismo bean `PasswordEncoder` PBKDF2 que valida el login. PostgreSQL protege la unicidad de username y email normalizados.

En IntelliJ podés duplicar la configuración de `LavanderiaApplication` para conservar su entorno dev, elegir como clase principal `com.miraquetelavo.lavanderia.soporte.CrearAdministradorDev` y usar el classpath de pruebas del módulo. Agregá únicamente `LAVANDERIA_BOOTSTRAP_PASSWORD` con tu contraseña elegida de 12 a 128 caracteres. Usá una configuración local sin compartir. Como alternativa, con las variables de conexión dev y JWT ya cargadas en PowerShell, ejecutá lo siguiente desde la raíz.

```powershell
$env:LAVANDERIA_BOOTSTRAP_PASSWORD = [Net.NetworkCredential]::new('', (Read-Host 'Contraseña de admin dev' -AsSecureString)).Password
try {
    mvn.cmd spring-boot:test-run '-Dspring-boot.run.main-class=com.miraquetelavo.lavanderia.soporte.CrearAdministradorDev'
} finally {
    Remove-Item Env:LAVANDERIA_BOOTSTRAP_PASSWORD -ErrorAction SilentlyContinue
}
```

La cuenta es `admin`, `admin.dev@example.com`, activa y con rol `ADMINISTRADOR`. El comando verifica `POST /api/auth/login` y `GET /api/auth/me` y termina. El login registra el último acceso. Una repetición puede verificar la cuenta existente con su contraseña actual. Si hay otra cuenta en la tabla, el comando deja intacta la base de usuarios y exige revisar el caso. Retirá la variable temporal de IntelliJ al terminar. La configuración habitual de `LavanderiaApplication` queda disponible para iniciar el backend y entrar desde el frontend.
