# Módulo Cliente

Para iniciar la aplicación y autenticar las peticiones, seguí la sección de seguridad.
Hibernate genera o actualiza la tabla `clientes` al iniciar con la configuración local del proyecto.

| Método | Ruta | Éxito |
| --- | --- | --- |
| POST | `/api/clientes` | 201 con el cliente y el encabezado `Location` |
| GET | `/api/clientes` | 200 con `content` y metadatos en `page` |
| GET | `/api/clientes/{id}` | 200 con el cliente activo |
| PUT | `/api/clientes/{id}` | 200 con el cliente actualizado |
| DELETE | `/api/clientes/{id}` | 204 tras la baja lógica, incluso si ya estaba inactivo |

El listado incluye clientes activos. Admite `page` (desde 0), `size` (20 por defecto) y
`sort` (por defecto `id,asc`). Ejemplo de consulta: `/api/clientes?page=0&size=10&sort=apellido,asc`.
GET por ID y PUT devuelven 404 para clientes inactivos. Las tres operaciones por ID devuelven
404 si el ID es inexistente.

En POST y PUT, nombre y apellido son obligatorios y admiten hasta 100 caracteres cada uno.
El teléfono es opcional y admite hasta 30. El email es opcional y admite hasta 254 con formato válido.
El DNI es obligatorio en POST y PUT. Enviá una cadena de 7 u 8 dígitos sin puntos ni espacios.
Un DNI repetido devuelve 409. La unicidad también incluye clientes inactivos.
Los registros previos pueden conservar `dni: null`. Si el cliente está activo, podés completarlo con PUT.
PUT reemplaza los datos de contacto, por lo que un teléfono o email omitido se guarda como nulo.
Los errores de validación devuelven 400 en formato Problem Details.

```json
{
  "nombre": "Ana",
  "apellido": "Pérez",
  "dni": "12345678",
  "telefono": "+54 11 1234 5678",
  "email": "ana.perez@example.com"
}
```

## Pruebas

Ejecutá `.\mvnw.cmd clean compile` y `.\mvnw.cmd test`. La suite verifica la lógica del servicio,
el contrato HTTP con MockMvc y el registro de la entidad en el metamodelo JPA. El contexto de
pruebas arranca con el dialecto de PostgreSQL y con la conexión y generación de esquema
deshabilitadas para esta comprobación.

# Módulo Servicio

| Método | Ruta | Éxito |
| --- | --- | --- |
| POST | `/api/servicios` | 201 con el servicio activo y el encabezado `Location` |
| GET | `/api/servicios` | 200 con servicios activos en `content` y metadatos en `page` |
| GET | `/api/servicios/{id}` | 200 con el servicio activo |
| PUT | `/api/servicios/{id}` | 200 con los datos actualizados y el mismo ID |
| DELETE | `/api/servicios/{id}` | 204, incluso si ya estaba inactivo |
| PATCH | `/api/servicios/{id}/activar` | 200 con el servicio activo, incluso si ya lo estaba |

El listado admite `page` (desde 0), `size` (20 por defecto) y `sort` (por defecto `id,asc`).
Las operaciones por ID devuelven 404 si el ID no existe. GET y PUT también devuelven
404 si el servicio está inactivo. La baja conserva el registro y su nombre.
Enviá el token Bearer según la sección de seguridad.

POST y PUT requieren nombre, precio y modalidad de cobro. Se quitan los espacios
de los extremos del nombre, que admite hasta 100 caracteres. El precio debe ser
positivo y admite hasta ocho dígitos enteros y dos decimales. Las modalidades
válidas son `UNIDAD`, `KILO` y `PRENDA`. El estado cambia mediante DELETE y PATCH.
Los errores de entrada devuelven 400 y los nombres duplicados devuelven 409,
en ambos casos con Problem Details.

Ejemplo de POST para `/api/servicios`.

```json
{
  "nombre": "Lavado por kilo",
  "precio": 1200.50,
  "modalidadCobro": "KILO"
}
```

Ejemplo de PUT para `/api/servicios/7`.

```json
{
  "nombre": "Lavado delicado por prenda",
  "precio": 1850.00,
  "modalidadCobro": "PRENDA"
}
```

Al crear la tabla, Hibernate solicita una columna generada `nombre_normalizado`
con `lower(nombre_servicio)` y la restricción única `uk_servicios_nombre`.
Las consultas usan el mismo `lower()` de PostgreSQL, con la configuración regional
de la base, e incluyen los registros inactivos.

Si `servicios` ya existe con un esquema distinto o con duplicados, revisá la tabla
antes de habilitar el módulo. Con la estrategia actual `RECREATE_QUIETLY`, Hibernate
puede continuar el arranque tras un fallo al recrear la restricción. Comprobá
la expresión de la columna y la restricción en el catálogo de PostgreSQL.

```sql
SELECT column_name, is_generated, generation_expression
FROM information_schema.columns
WHERE table_schema = current_schema()
  AND table_name = 'servicios'
  AND column_name = 'nombre_normalizado';

SELECT conname, pg_get_constraintdef(oid)
FROM pg_constraint
WHERE conrelid = 'servicios'::regclass
  AND conname = 'uk_servicios_nombre';
```

# Seguridad, roles y usuarios

El arranque requiere PostgreSQL previamente aprovisionado, credenciales de aplicación y una clave JWT. Configurá el perfil apropiado y cargá los secretos en el entorno del proceso. En producción, conservá la misma clave JWT en todas las instancias. El procedimiento de alta inicial y los permisos están en la [guía de configuración de producción](docs/seguridad-configuracion-produccion.md).

| Variable | Configuración |
| --- | --- |
| `LAVANDERIA_DB_URL`, `LAVANDERIA_DB_USER`, `LAVANDERIA_DB_PASSWORD` | Datasource obligatorio para perfil predeterminado o `prod`. |
| `LAVANDERIA_DEV_DB_URL`, `LAVANDERIA_DEV_DB_USER`, `LAVANDERIA_DEV_DB_PASSWORD` | Datasource de desarrollo con perfil `dev`. |
| `LAVANDERIA_JWT_SECRET` | Obligatoria. Base64 de al menos 32 bytes aleatorios. |
| `LAVANDERIA_JWT_TTL_SECONDS` | Vigencia positiva, 3600 segundos por defecto. |
| `LAVANDERIA_CORS_ORIGINS` | Orígenes completos separados por comas. |

### Arranque local desde IntelliJ IDEA

Abrí **Run → Edit Configurations** y elegí `LavanderiaApplication`. En **Environment variables**, configurá `SPRING_PROFILES_ACTIVE=dev` e inyectá `LAVANDERIA_DEV_DB_URL`, `LAVANDERIA_DEV_DB_USER`, `LAVANDERIA_DEV_DB_PASSWORD` y `LAVANDERIA_JWT_SECRET` desde tu entorno local o un archivo fuera del repositorio. La URL debe tener el formato `jdbc:postgresql://<host>:<puerto>/<base_dev>`. Usá una base de desarrollo previamente aprovisionada y su rol de aplicación dedicado. [Configuración de variables en IntelliJ IDEA](https://www.jetbrains.com/help/idea/program-arguments-and-environment-variables.html)

Si el log muestra el perfil `default` y el error `'url' must start with "jdbc"`, revisá el perfil y la URL efectiva del proceso que lanza IntelliJ. El perfil predeterminado lee `LAVANDERIA_DB_URL`. Una variable ausente puede dejar el marcador sin resolver y producir ese error antes de que Spring intente conectarse.

Para preparar y verificar `lavanderia_dev` y su rol dedicado, seguí el [procedimiento de desarrollo](docs/desarrollo-postgresql.md).

### Autenticación y permisos

`POST /api/auth/login` recibe JSON. Usá el `accessToken` de la respuesta en
`Authorization: Bearer <accessToken>` para las demás operaciones.

```json
{"username":"ana.lavanderia","password":"<contraseña de la cuenta>"}
```

La respuesta contiene `accessToken`, `tokenType` y `expiresIn` en segundos.
`GET /api/auth/me` devuelve `id`, `username`, `nombre`, `email` y `rol`.
La API usa OAuth2 Resource Server con JWT HS256 y no utiliza sesiones ni cookies de autenticación.
CSRF está deshabilitado porque el cliente debe enviar el Bearer explícitamente en el encabezado
Authorization. Usá HTTPS fuera del entorno local. CORS autoriza preflight desde los orígenes
configurados, con solicitudes sin credenciales de navegador.

El backend consulta el rol y el estado actual de la cuenta en cada solicitud Bearer. La baja
o el cambio de contraseña revocan los tokens vigentes incluso si luego se reactiva la cuenta.
La cuenta guarda un hash PBKDF2 y registra el último login exitoso en UTC.
Los fallos de autenticación devuelven 401 y las operaciones fuera del rol devuelven 403,
con cuerpos Problem Details. Cualquier ruta o método fuera de la tabla queda denegado.

| Operación | ADMINISTRADOR | EMPLEADO |
| --- | --- | --- |
| `GET /api/auth/me` | Sí | Sí |
| Administración de usuarios | Sí | |
| Clientes, GET, POST y PUT | Sí | Sí |
| Clientes, DELETE por ID | Sí | |
| Servicios y prendas, GET | Sí | Sí |
| Servicios y prendas, POST, PUT, DELETE y PATCH de activación | Sí | |
| Estados de pedido, GET | Sí | Sí |
| Pedidos, GET, POST, PUT, cambio de estado y cancelación | Sí | Sí |

### API de usuarios

| Método | Ruta | Éxito |
| --- | --- | --- |
| POST | `/api/usuarios` | 201 con `Location` y datos de la cuenta activa |
| GET | `/api/usuarios` | 200 con página de cuentas activas e inactivas |
| GET | `/api/usuarios/{id}` | 200 con la cuenta |
| PUT | `/api/usuarios/{id}` | 200 con username, nombre, email y rol actualizados |
| DELETE | `/api/usuarios/{id}` | 204 tras la baja lógica |
| PATCH | `/api/usuarios/{id}/activar` | 200 con la cuenta activa |
| PATCH | `/api/usuarios/{id}/password` | 204 tras cambiar la contraseña. Cuerpo JSON `{"password":"<nueva contraseña>"}`. |

Ejemplo de alta de empleado.

```json
{
  "username": "ana.lavanderia",
  "password": "<contraseña nueva de al menos 12 caracteres>",
  "nombre": "Ana Pérez",
  "email": "ana.perez@example.com",
  "rol": "EMPLEADO"
}
```

Los usernames y emails se comparan sin distinguir mayúsculas y quedan reservados durante la baja.
Cada usuario referencia a un único rol mediante `usuarios.id_rol`. El administrador no puede
desactivarse ni degradarse a sí mismo. Las operaciones que pueden cambiar el número de
administradores activos se serializan mediante el bloqueo de la fila del rol ADMINISTRADOR.

### Pruebas sobre PostgreSQL

Usá un clúster local PostgreSQL 17 desechable en un puerto distinto de 5432 y sin la base `lavanderia_db`. Creá una base con nombre `lavanderia_test_*`, locale ICU `es-AR` y comentario `lavanderia:pruebas-desechables`. Prepará un rol con prefijo `lavanderia_test_` y permiso para crear esquemas en esa base. Inyectá su contraseña mediante `LAVANDERIA_TEST_JDBC_PASSWORD`.

```powershell
$env:LAVANDERIA_TEST_JDBC_URL = 'jdbc:postgresql://127.0.0.1:55462/lavanderia_test_local'
$env:LAVANDERIA_TEST_JDBC_USER = 'lavanderia_test_runner'
.\mvnw.cmd test
```

La anotación de integración activa `test-postgresql` y exige coincidencia de host, puerto, base, marcador y rol antes de crear un esquema por clase. Un único datasource explícito sirve a Hibernate y a `@Sql`. Sin `LAVANDERIA_TEST_JDBC_URL` se omiten las integraciones opt-in. Para una ejecución focalizada, agregá `-Dtest=MovimientoCajaPostgresqlTest`.

Para revisar esquemas históricos, consultá el [procedimiento SQL](docs/limpieza-esquemas-pruebas.sql).

### API de pedidos

| Método | Ruta | Éxito |
| --- | --- | --- |
| POST | `/api/pedidos` | 201 con `Location` y el pedido completo |
| GET | `/api/pedidos` | 200 con una página de resúmenes |
| GET | `/api/pedidos/{id}` | 200 con el pedido completo |
| PUT | `/api/pedidos/{id}` | 200 con el pedido actualizado |
| PATCH | `/api/pedidos/{id}/estado` | 200 con el pedido en el nuevo estado |
| PATCH | `/api/pedidos/{id}/cancelar` | 200 con el pedido cancelado |
| GET | `/api/pedidos/{id}/historial` | 200 con la lista de cambios |

El listado admite `clienteId`, `estado` (código, por ejemplo `PENDIENTE`), `desde` y
`hasta` como fechas de ingreso inclusivas en formato ISO. Usá `page` desde cero,
`size` y `sort` con `id`, `fechaIngreso`, `fechaEntregaEstimada` o `total`.
El orden por ID desempata cuando se ordena por otro campo.

POST registra un pedido en PENDIENTE con el usuario autenticado. La entrega estimada
debe ser igual o posterior al día del negocio, configurable con
`lavanderia.pedidos.zona-horaria` (por defecto, `America/Argentina/Buenos_Aires`).

```json
{
  "clienteId": 12,
  "fechaEntregaEstimada": "2027-01-05",
  "detalles": [
    {"prendaId": 3, "servicioId": 4, "cantidad": 2, "estadoPrenda": "Con manchas"}
  ]
}
```

PUT reemplaza el conjunto de líneas. Incluí el `detalleId` devuelto por la API para
conservar o editar una línea. Las líneas sin ID son nuevas y las líneas omitidas se
eliminan. Una línea con la misma prenda y servicio mantiene su precio unitario,
incluso al cambiar la cantidad. Una línea nueva o con cambio de prenda o servicio
toma el precio vigente. ENTREGADO y CANCELADO devuelven 409 al editar.

```json
{
  "fechaEntregaEstimada": "2027-01-06",
  "detalles": [
    {"detalleId": 51, "prendaId": 3, "servicioId": 4, "cantidad": 3, "observaciones": "Revisar puños"},
    {"prendaId": 5, "servicioId": 6, "cantidad": 1.25}
  ]
}
```

UNIDAD y PRENDA requieren cantidades enteras positivas. KILO admite hasta dos decimales.
El backend multiplica cantidad por precio y suma los subtotales con `BigDecimal`.
Si un importe excede `DECIMAL(10,2)` o requiere fracciones de centavo, devuelve 400.

Cada escritura abarca el pedido y sus detalles en una transacción. PUT bloquea la fila
del pedido antes de leer sus líneas, de modo que cada reemplazo se valida sobre el último
estado confirmado. Dos reemplazos válidos sucesivos se aplican en orden de adquisición del
bloqueo. Durante alta y edición, los bloqueos compartidos de prendas y servicios protegen
la disponibilidad y el precio leídos hasta el commit. Si una edición del catálogo se
confirma primero, el pedido lee esos valores al adquirir el bloqueo.

### Estados e historial

Las notas de evolución que mencionan Hibernate en modo de actualización describen el aprovisionamiento histórico. Para el arranque actual y los cambios controlados de esquema, seguí la [guía de producción](docs/seguridad-configuracion-produccion.md).

Cada alta nueva registra un asiento con estado anterior nulo, estado nuevo PENDIENTE,
el responsable del JWT, la fecha de ingreso y la observación «Pedido registrado».
Los cambios posteriores registran el estado anterior y el nuevo, el responsable del JWT y
la fecha del servidor. La API ordena los asientos por fecha de cambio e ID ascendentes.

| Estado actual | Destinos permitidos |
| --- | --- |
| PENDIENTE | EN_PROCESO, CANCELADO |
| EN_PROCESO | LISTO, CANCELADO |
| LISTO | ENTREGADO |
| ENTREGADO | Ninguno |
| CANCELADO | Ninguno |

`PATCH /api/pedidos/{id}/estado` recibe el código de destino y una observación opcional.
Se recortan los espacios exteriores del código y se normaliza a mayúsculas.

```json
{"codigoEstado":"EN_PROCESO","observacion":"Se inició el lavado"}
```

`PATCH /api/pedidos/{id}/cancelar` recibe el motivo. El motivo también es obligatorio
si se solicita el destino CANCELADO mediante `/estado`. La cancelación conserva el
pedido, sus detalles e importes. Cuando se implementen los cobros, habrá que validar
los pagos registrados antes de cancelar.

```json
{"observacion":"El cliente solicitó cancelar el trabajo"}
```

El módulo responde con `ProblemDetail`. Un destino inexistente o inactivo o un cuerpo
inválido devuelve 400. Un pedido inexistente devuelve 404. Una transición incompatible,
incluido un intento de repetir el estado, devuelve 409. Las rutas requieren JWT y
admiten los roles ADMINISTRADOR y EMPLEADO.

El pedido y su asiento se confirman en la misma transacción. Los cambios de estado
y las ediciones de detalles adquieren el mismo bloqueo por fila del pedido. La API
solo permite consultar los asientos y las claves foráneas impiden borrar sus referencias
en cascada.

Un pedido existente sin asientos devuelve `[]`. Si un pedido anterior al módulo
recibe un cambio, su primer asiento refleja esa transición real. Un historial que
empieza con estado anterior informado carece de asiento inicial.

Con la [base de integración configurada](#pruebas-sobre-postgresql), ejecutá `.\mvnw.cmd test '-Dtest=PedidoPostgresqlTest'`.

# Catálogos de pago

**EstadoPago** (`estados_pago`) se lista por `orden` y luego por ID.

| Código | Nombre | Orden | Significado |
| --- | --- | --- | --- |
| `PENDIENTE` | Pendiente | 1 | El total está pendiente. |
| `PARCIAL` | Parcialmente pagado | 2 | Se pagó una parte del total. |
| `PAGADO` | Pagado | 3 | Se pagó el total y el saldo pendiente es cero. |

**MetodoPago** (`metodos_pago`) presenta los métodos iniciales en este orden comercial.

| Código | Nombre |
| --- | --- |
| `EFECTIVO` | Efectivo |
| `TRANSFERENCIA` | Transferencia bancaria |
| `TARJETA` | Tarjeta |
| `OTROS` | Otros |

Los IDs los genera PostgreSQL. Usá los códigos estables para identificar estados y métodos.
`EFECTIVO` identifica dinero físico para las futuras operaciones de caja.

| Método | Ruta | Resultado |
| --- | --- | --- |
| GET | `/api/estados-pago` | Lista de estados activos |
| GET | `/api/estados-pago/{id}` | Estado activo por ID |
| GET | `/api/metodos-pago` | Lista de métodos activos |
| GET | `/api/metodos-pago/{id}` | Método activo por ID |

Las cuatro rutas requieren `Authorization: Bearer <JWT>` de un `ADMINISTRADOR` o
`EMPLEADO`. Las consultas exitosas devuelven 200. Los IDs inválidos devuelven 400 y
los registros inexistentes o inactivos devuelven 404. La autenticación inválida o
ausente devuelve 401 y un rol sin permiso recibe 403. Los errores usan `ProblemDetail`.

Con `ddl-auto=update`, Hibernate gestiona las tablas y restricciones antes de ejecutar los
inicializadores. Cada catálogo se carga en una transacción mediante
`INSERT ... ON CONFLICT (codigo) DO NOTHING`, que preserva las filas existentes y permite
ejecuciones concurrentes del inicializador. PostgreSQL exige códigos únicos en ambas
tablas, nombres únicos en `metodos_pago` y un orden no negativo en `estados_pago`.
Si el nombre de un método inicial faltante pertenece a otro código, falla la carga de
MétodoPago y se revierte su transacción. Con `ddl-auto=none`, se omiten los inicializadores.

Con la [base de integración configurada](#pruebas-sobre-postgresql), ejecutá `.\mvnw.cmd test '-Dtest=CatalogosPagoPostgresqlTest'`.

## Caja y tipos de movimiento

El [contrato de idempotencia financiera](docs/idempotencia-financiera.md) cubre apertura, movimientos manuales, cobros y pagos a proveedores. Incluye el uso de `Idempotency-Key` y la guía para React.

Una caja representa una jornada financiera. La apertura registra el monto inicial de efectivo,
el usuario activo del JWT y la fecha local del servidor. El cierre registra al administrador,
la fecha de cierre y el arqueo. Ambos usan el reloj de negocio de Pedidos, cuya zona
predeterminada es `America/Argentina/Buenos_Aires` (`lavanderia.pedidos.zona-horaria`).

| Método | Ruta | Roles | Respuesta |
|---|---|---|---|
| POST | `/api/cajas/abrir` | Administrador, empleado | 201 con `Location: /api/cajas/{id}` y detalle |
| GET | `/api/cajas/abierta` | Administrador, empleado | 200 con detalle o 404 |
| GET | `/api/cajas/{id}` | Administrador, empleado | 200 con detalle o 404 |
| GET | `/api/cajas` | Administrador | 200 con página de resúmenes |
| PATCH | `/api/cajas/{id}/cerrar` | Administrador | 200 con detalle, 404 si falta o 409 si ya cerró |
| GET | `/api/tipos-movimiento-caja` | Administrador, empleado | 200 con tipos activos |
| GET | `/api/tipos-movimiento-caja/{id}` | Administrador, empleado | 200 con detalle o 404 si falta o está inactivo |

El historial admite `page` (desde 0), `size` (20 por defecto), `estado` (`ABIERTA` o `CERRADA`),
`desde` y `hasta` (días inclusivos de apertura en formato `YYYY-MM-DD`). Se ordena siempre por
fecha de apertura descendente y, ante empate, ID descendente.

Ejemplo de solicitud de apertura.

```json
{"montoInicial": 1000.00}
```

Ejemplo de solicitud de cierre.

```json
{"efectivoContado": 975.50}
```

Ejemplo de respuesta de cierre.

```json
{
  "id": 12,
  "usuarioAperturaId": 7,
  "usuarioAperturaNombre": "Ana",
  "usuarioCierreId": 3,
  "usuarioCierreNombre": "Lucía",
  "fechaApertura": "2026-11-17T09:00:00",
  "fechaCierre": "2026-11-17T18:00:00",
  "montoInicial": 1000.00,
  "montoFinalCalculado": 1000.00,
  "efectivoEsperado": 1000.00,
  "efectivoContado": 975.50,
  "diferenciaArqueo": -24.50,
  "estado": "CERRADA"
}
```

Los campos de cierre son nulos durante la apertura. Los importes ingresados admiten valores
desde cero hasta 99.999.999,99 con un máximo de dos decimales. Al cerrar, la aplicación
consolida los movimientos confirmados y guarda el saldo general y el efectivo esperado.
La diferencia firmada se calcula como efectivo contado menos efectivo esperado y se conserva
por separado. Para una jornada sin movimientos, ambos saldos equivalen al monto inicial.

### Integridad y concurrencia en PostgreSQL

`cajas` referencia a `usuarios` mediante dos claves foráneas. Sus restricciones comprueban el
monto inicial y los efectivos no negativos, la fórmula del arqueo y la coherencia de los campos con el estado.
`numeric(10,2)` acota los saldos y `numeric(11,2)` almacena la diferencia firmada.
El índice de fecha de apertura e ID respalda el orden del historial.

Con `ddl-auto=update`, Hibernate crea las tablas y el inicializador ejecuta este SQL después
de que el esquema esté disponible.

```sql
CREATE UNIQUE INDEX IF NOT EXISTS ux_cajas_unica_abierta
    ON cajas (estado) WHERE estado = 'ABIERTA';
```

El inicializador serializa la actualización con un bloqueo exclusivo de tabla y verifica que PostgreSQL
reconoce el índice como único árbitro de la apertura. Si falla la verificación, aborta el
arranque. El `INSERT ... ON CONFLICT (estado) WHERE estado = 'ABIERTA' DO NOTHING` exige que
PostgreSQL pueda resolver el índice en cada apertura y devuelve 409 cuando ya hay una caja
abierta. El inicializador también sustituye el CHECK legado de importes en una sola sentencia,
para admitir saldo general firmado y conservar los controles de efectivo y arqueo.
Una apertura que coincide con un cierre pendiente puede esperar su confirmación.

Con `ddl-auto=none`, el esquema, sus restricciones, el índice parcial y los tipos iniciales
se provisionan fuera de la aplicación. Los inicializadores quedan deshabilitados.

### Catálogo y pruebas

`tipos_movimiento_caja` tiene claves únicas separadas para código y nombre. El inicializador
inserta `INGRESO_MANUAL`, `EGRESO_MANUAL`, `COBRO_PEDIDO` y `PAGO_PROVEEDOR` con órdenes 1 a 4.
El inicializador serializa las escrituras sobre la tabla para cubrir también la clave única
de nombre. `ON CONFLICT (codigo) DO NOTHING` conserva las filas existentes al reiniciar.
La API del catálogo es de solo lectura.

Ejemplo de un elemento del catálogo.

```json
{
  "id": 1,
  "codigo": "INGRESO_MANUAL",
  "nombre": "Ingreso manual",
  "descripcion": "Ingreso de dinero registrado manualmente.",
  "orden": 1,
  "activo": true
}
```

Con la [base de integración configurada](#pruebas-sobre-postgresql), ejecutá `.\mvnw.cmd test '-Dtest=CajaPostgresqlTest'`.

## Movimientos de caja

Las operaciones requieren un JWT de administrador o empleado. El cierre sigue reservado a administradores.

| Método | Ruta | Respuesta |
|---|---|---|
| POST | `/api/movimientos-caja/ingresos-manuales` | 201 con el movimiento y su `Location` |
| POST | `/api/movimientos-caja/egresos-manuales` | 201 con el movimiento y su `Location` |
| GET | `/api/movimientos-caja/{id}` | Detalle del movimiento o 404 |
| GET | `/api/cajas/{id}/movimientos` | Historial paginado o 404 si falta la caja |
| GET | `/api/cajas/{id}/saldo` | Saldos, datos de la jornada y arqueo de una caja cerrada |

Ejemplo de ingreso manual. Usá el ID obtenido del catálogo de métodos de pago.

```json
{"monto": 15000.00, "metodoPagoId": 2, "descripcion": "Ingreso manual de fondos"}
```

Ejemplo de egreso manual.

```json
{"monto": 2500.00, "metodoPagoId": 1, "descripcion": "Compra menor de materiales"}
```

La aplicación asigna caja, tipo, usuario y fecha. La descripción manual es obligatoria y admite
hasta 250 caracteres. El importe es positivo y admite hasta dos decimales. La ausencia de caja
abierta, un egreso de efectivo superior al disponible y un saldo fuera de rango devuelven 409.

El listado admite `page`, `size`, `tipo` (código del tipo), `metodoPagoId`, `desde` y `hasta`
(días inclusivos en formato `YYYY-MM-DD`). Su orden es fecha y hora descendente, seguido del ID
descendente. El historial se conserva después del cierre.

### Saldos y bloqueo

- Saldo general = monto inicial + ingresos − egresos, incluidos todos los métodos de pago.
- Efectivo disponible = monto inicial + ingresos en efectivo − egresos en efectivo. Se identifica
  el efectivo por el código estable `EFECTIVO`.
- Diferencia de arqueo = efectivo contado − efectivo esperado. El cierre guarda el saldo general
  y el efectivo disponible como valores definitivos. El saldo general puede ser negativo.

Los códigos `INGRESO_MANUAL` y `COBRO_PEDIDO` suman. `EGRESO_MANUAL` y `PAGO_PROVEEDOR` restan.
La consulta agrega los cuatro totales y lee el estado de caja en una sola sentencia.
Las escrituras y el cierre usan el mismo bloqueo pesimista de fila y calculan tras adquirirlo,
con aislamiento `READ COMMITTED`. Si el cierre obtiene primero el bloqueo, el movimiento se
rechaza al confirmar que la caja cerró. Si lo obtiene el movimiento, el cierre incluye ese importe.

`movimientos_caja` tiene cuatro claves foráneas sin borrado en cascada y un índice por caja, fecha
e ID. PostgreSQL exige un importe positivo `numeric(10,2)` y limita la descripción a 250 caracteres,
con un CHECK que descarta texto en blanco cuando existe. La aplicación protege el rango de los
saldos consolidados `numeric(10,2)` y limita los acumulados a 16 dígitos enteros y dos decimales.

El registro automático futuro deberá vincular el asiento al documento origen en la misma
transacción de negocio y reutilizar el bloqueo y la validación de la escritura manual.

### Verificación PostgreSQL

Con la [base de integración configurada](#pruebas-sobre-postgresql), ejecutá `.\mvnw.cmd test '-Dtest=MovimientoCajaPostgresqlTest'`.

## Cobros de pedidos

Los roles `ADMINISTRADOR` y `EMPLEADO` pueden registrar y consultar cobros con su JWT.

| Método | Ruta | Resultado |
| --- | --- | --- |
| POST | `/api/pedidos/{id}/cobros` | `201 Created`, con `Location: /api/cobros/{id}` |
| GET | `/api/pedidos/{id}/cobros?page=0&size=20` | Historial paginado, por fecha e ID descendentes |
| GET | `/api/pedidos/{id}/saldo` | Total, acumulado, saldo pendiente y estado financiero |
| GET | `/api/cobros/{id}` | Cobro individual |

El registro acepta importe y método de pago. El importe debe ser positivo, con hasta dos
decimales y ocho dígitos enteros. La API rechaza campos adicionales. La identidad sale
del JWT y el backend determina la caja, el instante y el tipo de movimiento.

```json
{"monto":8000.00,"metodoPagoId":2}
```

Ejemplo de respuesta para un pedido de 10.000 después del primer cobro.

```json
{
  "id": 41,
  "pedidoId": 25,
  "movimientoCajaId": 84,
  "cajaId": 3,
  "metodoPagoCodigo": "TRANSFERENCIA",
  "metodoPagoNombre": "Transferencia",
  "usuarioId": 7,
  "usuarioNombre": "Ana",
  "fechaCobro": "2026-11-17T10:30:00",
  "montoCobrado": 8000.00
}
```

```json
{
  "pedidoId": 25,
  "totalPedido": 10000.00,
  "totalCobrado": 8000.00,
  "saldoPendiente": 2000.00,
  "estadoPagoCodigo": "PARCIAL",
  "estadoPagoNombre": "Parcial"
}
```

El saldo es el total del pedido menos la suma de sus cobros. El estado financiero
empieza en `PENDIENTE`, pasa a `PARCIAL` al cobrar parte y a `PAGADO` al completar
el total. Un pedido entregado puede conservar saldo. Cobrar no cambia el estado
operativo. Con un cobro confirmado, el PUT completo y ambas rutas de cancelación
responden `409 Conflict`. El límite del PUT protege los importes y precios históricos.
También devuelven `409` los cobros sobre un pedido cancelado, sin saldo, por encima
del saldo o sin caja abierta. Un método inexistente devuelve `404` y uno inactivo
devuelve `400`. Los cobros confirmados son inmutables en la API.

### Transacción e integridad

El cobro bloquea en este orden la fila de Caja y la fila de Pedido, bajo
`READ COMMITTED`. El cierre y los movimientos manuales toman el bloqueo de Caja.
El PUT y las transiciones operativas toman el bloqueo de Pedido y no solicitan el
de Caja. Los bloqueos se conservan hasta terminar la transacción. La consulta de
saldo reúne total, catálogo y suma en una sola sentencia y verifica su coherencia.

Cada cobro persiste un movimiento `COBRO_PEDIDO` con el mismo importe e instante.
El calculador de caja existente lo incluye en el saldo general. Solo un cobro en
efectivo se incluye en el efectivo físico. El movimiento, el cobro y el cambio de
estado financiero se confirman juntos.

PostgreSQL exige las FK `cobros.id_pedido`, `cobros.id_mov_caja` y
`pedidos.id_estado_pago`. El movimiento es único por cobro, el importe es
`NUMERIC(10,2) NOT NULL` con un CHECK positivo y el historial cuenta con un índice
sobre pedido, fecha descendente e ID descendente. Las FK no eliminan registros en
cascada. La transacción protege el saldo y el estado, el uso de una caja abierta,
el tipo de movimiento y la coincidencia de importe e instante entre ambas tablas.
Esas reglas entre tablas requieren pasar por el servicio cuando se escribe.

### Evolución del esquema y pruebas

Con `ddl-auto=update`, Hibernate agrega la columna inicialmente nullable, su FK y
la tabla de cobros. Después de sembrar el catálogo, el inicializador verifica la FK
y completa solo los pedidos sin estado con el código que surge de los cobros
existentes. Para un pedido sin cobros asigna `PENDIENTE`. Comprueba que no queden
valores nulos antes de aplicar `NOT NULL`. En los siguientes arranques omite el
backfill cuando la columna ya es obligatoria. Para actualizar una instalación,
detener las instancias anteriores antes de arrancar la versión con Cobro.

Si Hibernate falla al agregar la columna o su FK, revisar el error de arranque.
En una instalación que requiera intervención manual y aún carezca de la columna,
el DDL aditivo de esa fase es:

```sql
ALTER TABLE pedidos ADD COLUMN id_estado_pago bigint;
ALTER TABLE pedidos ADD CONSTRAINT fk_pedidos_estado_pago
    FOREIGN KEY (id_estado_pago) REFERENCES estados_pago (id_estado_pago);
```

Con la tabla `cobros` y el catálogo disponibles, volver a arrancar con `update`
para completar la transición. Un backfill fallido revierte su transacción y se
debe resolver antes de completar el arranque. Si se usa `ddl-auto=none`, el esquema
ya debe contar con todas las estructuras y restricciones definitivas.

Con la [base de integración configurada](#pruebas-sobre-postgresql), ejecutá `.\mvnw.cmd test '-Dtest=CobroPostgresqlTest'`.

## Categorías de insumo e insumos

`/api/categorias-insumo` y `/api/insumos` requieren JWT. ADMINISTRADOR y
EMPLEADO pueden consultar. Las escrituras requieren ADMINISTRADOR.

| Método y sufijo sobre ambas rutas | Resultado |
| --- | --- |
| `POST` | 201 con encabezado `Location` y el recurso activo |
| `GET` | 200 con los recursos activos |
| `GET /{id}` | 200 si está activo, 404 si está inactivo o ausente |
| `PUT /{id}` | 200 con los datos actualizados, 404 si está inactivo o ausente |
| `DELETE /{id}` | 204 con baja lógica idempotente, 404 si está ausente |
| `PATCH /{id}/activar` | 200 con reactivación idempotente, 404 si está ausente |

El listado de categorías devuelve un arreglo ordenado por ID. El de insumos
devuelve `content` y `page`, usa `page` y `size` (20 por defecto), mantiene el
orden ascendente por ID y permite filtrar con `?categoriaId=7`.

Ejemplo de cuerpo para una categoría

```json
{"nombre":"Detergentes"}
```

Ejemplo de cuerpo para un insumo

```json
{"nombre":"Detergente líquido","categoriaId":7}
```

Ejemplo de respuesta de insumo

```json
{"id":12,"nombre":"Detergente líquido","categoriaId":7,"categoriaNombre":"Detergentes","activo":true}
```

Los nombres se recortan, son obligatorios y admiten hasta 50 caracteres para
categorías y 100 para insumos. El ID de categoría debe ser positivo.
`ProblemDetail` devuelve 400 para entradas inválidas y 409 para nombres duplicados,
intentos de dar de baja categorías con insumos activos y reactivaciones o
traslados hacia categorías inactivas. El alta de un insumo requiere una categoría
activa y devuelve 404 si está inactiva o ausente.

Hibernate crea `categorias_insumo` e `insumos` con `ddl-auto=update`. Cada insumo
tiene una relación JPA `ManyToOne` diferida y obligatoria con su categoría. La
FK `fk_insumos_categoria` preserva la referencia y el índice
`ix_insumos_categoria_activo` cubre búsquedas por categoría y estado. Las
consultas de insumos cargan la categoría junto con los resultados.

El inicializador, siguiendo el patrón de Caja, instala índices únicos completos
`uk_categorias_insumo_nombre` y `uk_insumos_nombre` sobre `lower(nombre_cat)` y
`lower(nombre_insumo)`. PostgreSQL arbitra las escrituras concurrentes y reserva
los nombres de registros inactivos. Los índices por expresión evitan duplicar
las columnas de nombre. Con `ddl-auto=none`, el esquema debe incluir ambos índices.

Las escrituras sobre un insumo existente toman primero su bloqueo exclusivo.
El alta, la reactivación y el cambio de categoría toman un bloqueo compartido
sobre la categoría de destino hasta que termina la transacción. La baja de
categoría toma un bloqueo exclusivo sobre esa fila y comprueba por existencia
si quedan insumos activos. Así, la baja se serializa con las operaciones que
incorporan insumos activos sin intentar bloquear filas de insumos desde la
operación de categoría.

Con la [base de integración configurada](#pruebas-sobre-postgresql), ejecutá `.\mvnw.cmd test '-Dtest=CatalogoInsumosPostgresqlTest'`.

## Proveedores e insumos suministrados

Todas las rutas requieren JWT mediante `Authorization: Bearer <token>`.

| Método | Ruta | Resultado | Roles |
| --- | --- | --- | --- |
| POST | `/api/proveedores` | 201 con `Location` al proveedor creado | ADMINISTRADOR |
| GET | `/api/proveedores` | 200 con proveedores activos paginados | ADMINISTRADOR, EMPLEADO |
| GET | `/api/proveedores/{id}` | 200 con el proveedor activo | ADMINISTRADOR, EMPLEADO |
| PUT | `/api/proveedores/{id}` | 200 con el proveedor activo actualizado | ADMINISTRADOR |
| DELETE | `/api/proveedores/{id}` | 204 al dar de baja, incluso si ya estaba inactivo | ADMINISTRADOR |
| PATCH | `/api/proveedores/{id}/activar` | 200 con el proveedor activo, incluso si ya lo estaba | ADMINISTRADOR |
| POST | `/api/proveedores/{id}/insumos` | 201 con la asociación | ADMINISTRADOR |
| GET | `/api/proveedores/{id}/insumos` | 200 con los insumos asociados, paginados | ADMINISTRADOR, EMPLEADO |
| DELETE | `/api/proveedores/{id}/insumos/{insumoId}` | 204 si ambas referencias existen | ADMINISTRADOR |
| GET | `/api/insumos/{id}/proveedores` | 200 con los proveedores asociados, paginados | ADMINISTRADOR, EMPLEADO |

Ejemplo de alta o actualización

```json
{"nombre":"Distribuidora del Sur","cuit":"30-12345678-1","telefono":"011-5555-1234","rubro":"Limpieza"}
```

Ejemplo de respuesta de proveedor

```json
{"id":12,"nombre":"Distribuidora del Sur","cuit":"30123456781","telefono":"011-5555-1234","rubro":"Limpieza","activo":true}
```

Ejemplo de solicitud y respuesta al asociar el insumo 5 al proveedor 12

```json
{"insumoId":5}
```

```json
{"proveedorId":12,"proveedorNombre":"Distribuidora del Sur","insumoId":5,"insumoNombre":"Detergente líquido","categoriaId":2,"categoriaNombre":"Detergentes"}
```

Ejemplo de respuesta de `GET /api/insumos/5/proveedores?page=0&size=20`

```json
{
  "content": [{"id":12,"nombre":"Distribuidora del Sur","cuit":"30123456781","rubro":"Limpieza"}],
  "page": {"size":20,"number":0,"totalElements":1,"totalPages":1}
}
```

Los listados usan páginas desde cero, con tamaño 20 por defecto y orden fijo por ID ascendente.
`GET /api/proveedores?nombre=sur&telefono=5555&rubro=limpieza&cuit=30-12345678-1`
combina filtros con AND. Nombre, teléfono y rubro admiten coincidencias parciales literales
sin distinguir mayúsculas. El CUIT se busca por coincidencia exacta después de normalizarlo.

Nombre y teléfono son obligatorios y admiten hasta 150 y 20 caracteres, respectivamente.
Rubro es opcional y admite hasta 50 caracteres. El nombre comercial puede repetirse.
El CUIT admite 11 dígitos o guiones en el formato `XX-XXXXXXXX-X`. Se valida el
verificador mediante módulo 11, sin consulta fiscal externa, y se guardan los 11 dígitos.
El CUIT de un proveedor dado de baja sigue reservado. El campo `activo` se administra
mediante baja y reactivación.

Los listados de asociaciones muestran filas con proveedor, insumo y categoría activos.
Si el recurso de la ruta existe pero está inactivo, la página es vacía. Una baja
lógica oculta las asociaciones y las conserva para cuando los tres registros vuelvan a
estar activos. Desasociar elimina solo la fila de vínculo y también se permite con
referencias inactivas. Repetir la desasociación devuelve 204 si ambos extremos existen.

`ProblemDetail` devuelve 400 para solicitudes inválidas, 404 si falta alguno de los
recursos requeridos o si se consulta o edita un proveedor inactivo por ID, y 409 para
CUIT duplicado, asociación duplicada, intento de asociación con registros inactivos
o conflicto concurrente. Un JWT ausente o inválido devuelve 401. Un JWT válido sin
el rol requerido devuelve 403.

Hibernate crea `proveedores` y `proveedores_insumos` con `ddl-auto=update`.
`uk_proveedores_cuit` protege la unicidad en toda la tabla. La entidad
`ProveedorInsumo` usa `@EmbeddedId` y dos `@MapsId` con relaciones obligatorias
y diferidas. Las FKs de PostgreSQL protegen ambas referencias. Hibernate genera
la PK en el orden `(id_insumo, id_proveedor)`, que ya cubre la búsqueda por insumo.
El índice `ix_proveedores_insumos_proveedor` cubre la búsqueda por proveedor.
Las consultas paginadas proyectan los DTO en SQL con joins.

Las escrituras del proveedor bloquean su fila de forma exclusiva. La asociación
bloquea en orden Proveedor → Insumo → CategoriaInsumo, con bloqueo exclusivo sobre
los dos primeros y compartido sobre la categoría. La desasociación toma los dos
primeros bloqueos en el mismo orden. Se conserva el orden existente de Insumo →
CategoriaInsumo. La baja de categoría bloquea la categoría y comprueba si quedan
insumos activos, sin bloquear sus filas. La PK arbitra el alta del vínculo con
`INSERT ... ON CONFLICT DO NOTHING`, que se traduce a 409 si el vínculo ya existe.

Con la [base de integración configurada](#pruebas-sobre-postgresql), ejecutá `.\mvnw.cmd test '-Dtest=ProveedorPostgresqlTest'`.

## Compras de insumos

Las rutas requieren JWT de un usuario activo con rol `ADMINISTRADOR` o `EMPLEADO`.

| Método | Ruta | Resultado |
| --- | --- | --- |
| POST | `/api/compras` | Registra una compra. Devuelve 201, `Location` y el detalle completo. |
| GET | `/api/compras` | Devuelve el historial paginado en orden descendente por fecha e ID. |
| GET | `/api/compras/{id}` | Devuelve la compra completa con sus detalles. |
| PUT | `/api/compras/{id}` | Modifica una compra pendiente y recalcula el total. |
| GET | `/api/proveedores/{id}/compras` | Devuelve el historial paginado del proveedor, incluso si está inactivo. |

El historial admite `page`, `size` y los filtros `proveedorId`, `fechaDesde` y `fechaHasta`
(fecha ISO, ambos días inclusive en el reloj de negocio), `estadoPagoCodigo` y
`numeroComprobante` (coincidencia exacta después de quitar espacios exteriores).
El comprobante es opcional y admite hasta 50 caracteres. Puede repetirse.

Ejemplo de alta con dos insumos asociados al proveedor.

```json
{
  "proveedorId": 1,
  "numeroComprobante": "FAC-0001-00001234",
  "detalles": [
    {"insumoId": 5, "cantidad": 0.50, "precioCompra": 1.01},
    {"insumoId": 8, "cantidad": 4.00, "precioCompra": 2300.00}
  ]
}
```

El backend asigna el usuario a partir del JWT, registra la fecha con el reloj de negocio e
inicia la compra en `PENDIENTE`. Calcula cada subtotal como cantidad por precio unitario,
con redondeo comercial `HALF_UP` a dos decimales. En el ejemplo, los subtotales son
`0.51` y `9200.00`, y el total es `9200.51`. Los datos ingresados deben ser positivos y tener
hasta dos decimales. Se rechazan subtotales que redondean a cero y valores fuera de
`NUMERIC(10,2)`, cuyo máximo es `99999999.99`.

Ejemplo de PUT que conserva el precio pactado del detalle 14, cambia su cantidad y agrega
una línea. Los detalles omitidos se eliminan del agregado.

```json
{
  "proveedorId": 1,
  "numeroComprobante": "FAC-0001-00001234",
  "detalles": [
    {"detalleId": 14, "insumoId": 5, "cantidad": 2.50},
    {"insumoId": 8, "cantidad": 1.00, "precioCompra": 2400.00}
  ]
}
```

Cada detalle nuevo o con insumo reemplazado requiere su propio precio explícito. La compra
conserva el usuario y la fecha de alta. Se requiere al menos un detalle y se admiten un solo
detalle por insumo y un solo uso de cada `detalleId` en el PUT. Si el negocio necesita
registrar lotes del mismo insumo a precios distintos, deberá ampliar esa regla. El alta de la
obligación comercial prescinde de caja abierta y de movimientos de caja, pagos o stock.

### Concurrencia e integridad

El PUT toma el bloqueo pesimista de la compra antes de leer sus detalles. El alta y el PUT
bloquean el proveedor y los insumos (por ID ascendente) para escritura, y las categorías
(por ID ascendente) para lectura. El alta y la baja de asociaciones toman los mismos bloqueos
de proveedor e insumo antes de escribir. La compra comprueba la asociación una vez adquiridos
los bloqueos y conserva esos bloqueos hasta el commit. Los módulos de catálogo operan sin
adquirir el bloqueo de compra. El PUT comprueba que no existan pagos confirmados bajo el mismo bloqueo de Compra.

PostgreSQL protege las PK, las FK obligatorias, los importes `NUMERIC(10,2)`, su positividad
y la igualdad de cada subtotal con el producto redondeado. Los índices cubren las referencias,
el orden del historial y el comprobante. El servicio garantiza que haya detalles, que sean
únicos por insumo dentro de cada compra, que su suma coincida con el total y que el catálogo y
las asociaciones estén vigentes en la escritura. Cada detalle referencia directamente al
insumo, de modo que la desasociación posterior conserva el historial. El esquema se genera
con `ddl-auto=update`.

### Pruebas sobre PostgreSQL

Con la [base de integración configurada](#pruebas-sobre-postgresql), ejecutá `.\mvnw.cmd test '-Dtest=CompraPostgresqlTest'`.

La suite de compras usa MockMvc con los filtros JWT activos y verifica las escrituras, la
concurrencia, las restricciones SQL y la lectura desde un nuevo contexto de aplicación.

## Pagos a proveedores

Los cuatro endpoints requieren un JWT válido de un usuario activo con rol Administrador o Empleado.

| Método y ruta | Resultado |
| --- | --- |
| `POST /api/compras/{id}/pagos` | Registra un pago y devuelve `201 Created` con `Location: /api/pagos-proveedor/{id}`. |
| `GET /api/compras/{id}/pagos` | Devuelve `content` y metadatos `page`. Admite `page` y `size` con tamaño predeterminado 20. El orden fijo es `fechaPago DESC, id DESC`. |
| `GET /api/compras/{id}/saldo` | Devuelve total, acumulado, saldo pendiente y estado de pago. |
| `GET /api/pagos-proveedor/{id}` | Devuelve los datos del pago y sus referencias a compra, proveedor, movimiento, caja, método y usuario. |

Ejemplo de solicitud para la compra 35.

```json
{"monto": 20000.00, "metodoPagoId": 2}
```

Ejemplo de respuesta del alta.

```json
{
  "id": 12,
  "compraId": 35,
  "proveedorId": 9,
  "proveedorNombre": "Insumos del Sur",
  "movimientoCajaId": 57,
  "cajaId": 4,
  "metodoPagoCodigo": "TRANSFERENCIA",
  "metodoPagoNombre": "Transferencia",
  "usuarioId": 7,
  "usuarioNombre": "Ana",
  "fechaPago": "2026-11-17T15:20:00",
  "montoPagado": 20000.00
}
```

Ejemplo de saldo después del pago.

```json
{
  "compraId": 35,
  "totalCompra": 50000.00,
  "totalPagado": 20000.00,
  "saldoPendiente": 30000.00,
  "estadoPagoCodigo": "PARCIAL",
  "estadoPagoNombre": "Parcialmente pagado"
}
```

El importe debe ser positivo, ajustarse a `NUMERIC(10,2)` y caber en el saldo pendiente.
La base suma los pagos con `COALESCE(SUM(...), 0)`. La consulta del saldo lee en una
sentencia el total de la compra, la suma y el catálogo, y devuelve `409` si el estado
persistido es inconsistente. La compra empieza en `PENDIENTE`, pasa a `PARCIAL` con
un abono que deja saldo y llega a `PAGADO` cuando la suma iguala el total. Una deuda
existente puede pagarse después de la baja lógica del proveedor. Desde el primer
pago confirmado, el `PUT` de la compra devuelve `409` y conserva el documento histórico.

El registro usa una transacción `READ_COMMITTED` con bloqueos pesimistas en orden
Caja → Compra. Cobro, los movimientos manuales y el cierre se serializan con el pago por
la fila de Caja. El PUT usa la fila de Compra y comprueba los pagos después de adquirir
el bloqueo. El movimiento `PAGO_PROVEEDOR` es un egreso. Todos los métodos reducen
el saldo general, que admite negativos dentro del rango configurado. Solo `EFECTIVO`
reduce el efectivo físico y se rechaza si ese importe supera la disponibilidad. El
arqueo usa los mismos totales de caja.

PostgreSQL crea `pagos` mediante `ddl-auto=update` con PK, campos obligatorios,
FK a `compras` y `movimientos_caja`, unicidad de `id_mov_caja`, chequeo de importe
positivo e índice `(id_compra, fecha_pago DESC, id_pago DESC)`. El servicio compone
el importe y el instante del pago a partir de su movimiento y confirma ambos junto con
el estado de la compra. Esa transacción también garantiza el límite por saldo y la
correspondencia con el tipo de egreso. La API solo admite alta y lectura de pagos.

Los importes inválidos, métodos inactivos y cuerpos malformados devuelven `400`.
Los recursos inexistentes devuelven `404`. Caja cerrada o ausente, saldo o efectivo
insuficiente, compra pagada y conflictos de concurrencia devuelven `409`. Las respuestas
de error usan `ProblemDetail`.

Como en Cobro, un reintento HTTP puede registrar otro pago si todavía existe saldo.
Una mejora compartida para ambos módulos puede usar claves de idempotencia persistidas
en la misma transacción y devolver el resultado original al repetir la misma solicitud.

### Pruebas de PagoProveedor

Con la [base de integración configurada](#pruebas-sobre-postgresql), ejecutá `.\mvnw.cmd test '-Dtest=PagoProveedorPostgresqlTest'`.

Si el entorno restringe la escritura de locks en el repositorio local de Maven, agregá
`'-Daether.syncContext.named.factory=noop'` al ejecutar en modo offline con la caché ya
preparada.

Al volver a iniciar la aplicación con `ddl-auto=update`, Hibernate puede emitir las
advertencias existentes para las columnas generadas de Prenda, Servicio y Usuario.
La verificación de PagoProveedor consulta las restricciones y el índice del esquema
PostgreSQL y vuelve a leer un pago confirmado desde un contexto de aplicación nuevo.

## Comprobantes

Un comprobante registra la fecha de emisión y, opcionalmente, observaciones sobre
un cobro o un pago a proveedor ya confirmado. Cada operación admite un comprobante
opcional. El importe, el método de pago y el movimiento de caja se leen de la
operación original al consultar. El alta registra solamente el comprobante.

Todas las rutas requieren un JWT válido de un usuario activo con rol
`ADMINISTRADOR` o `EMPLEADO`.

| Método | Ruta | Resultado |
| --- | --- | --- |
| POST | `/api/cobros/{id}/comprobante` | Registra el comprobante del cobro |
| POST | `/api/pagos-proveedor/{id}/comprobante` | Registra el comprobante del pago |
| GET | `/api/comprobantes/{id}` | Devuelve el detalle |
| GET | `/api/cobros/{id}/comprobante` | Devuelve el detalle por cobro |
| GET | `/api/pagos-proveedor/{id}/comprobante` | Devuelve el detalle por pago |
| GET | `/api/pedidos/{id}/comprobantes` | Devuelve resúmenes paginados por pedido |
| GET | `/api/compras/{id}/comprobantes` | Devuelve resúmenes paginados por compra |

Los dos POST aceptan el mismo cuerpo. La fecha es obligatoria, tiene formato ISO
`yyyy-MM-dd` y debe ser igual o anterior al día actual según el reloj de negocio.
Se admite una fecha anterior al cobro o al pago. Las observaciones se recortan
antes de aplicar el límite de 200 caracteres. Si quedan vacías, se guardan como
`null`. Usá este campo para anotaciones operativas sin datos sensibles.

```json
{
  "fechaEmision": "2026-11-17",
  "observaciones": "Comprobante entregado al cliente"
}
```

El alta devuelve `201 Created` con `Location: /api/comprobantes/42` y un cuerpo
como el siguiente. Para `PAGO_PROVEEDOR` se completan `pagoProveedorId` y
`compraId` y los identificadores del cobro y del pedido son nulos.

```json
{
  "id": 42,
  "tipoOperacion": "COBRO",
  "cobroId": 12,
  "pagoProveedorId": null,
  "pedidoId": 9,
  "compraId": null,
  "fechaEmision": "2026-11-17",
  "observaciones": "Comprobante entregado al cliente",
  "montoOperacion": 1500.00,
  "metodoPagoCodigo": "EFECTIVO",
  "metodoPagoNombre": "Efectivo",
  "movimientoCajaId": 60
}
```

Los listados aceptan `page` desde 0 y `size` con valor predeterminado 20. Devuelven
`content` y `page`, con orden fijo por fecha de emisión descendente y luego por
ID descendente. Un pedido o compra existente sin comprobantes devuelve una
página vacía. El historial sigue disponible para pedidos entregados y compras
de proveedores desactivados.

Los comprobantes son inmutables. La API rechaza PUT, PATCH y DELETE. Los errores
usan `ProblemDetail` con 400 para un cuerpo o datos inválidos, 401/403 para
autenticación o permisos, 404 para recursos ausentes y 409 si la operación ya
tiene un comprobante, incluso cuando dos solicitudes intentan crearlo a la vez.

PostgreSQL mantiene dos claves foráneas sin borrado en cascada, dos restricciones
`UNIQUE` y un `CHECK` que exige exactamente una relación. Con `ddl-auto=update` el
inicializador comprueba el `CHECK` al arrancar y lo instala si falta. PostgreSQL
valida las filas existentes al instalarlo. Si el `CHECK` existente es incorrecto
o no está validado, el arranque falla. Con la actualización automática
deshabilitada, el esquema administrado debe incluir estas restricciones.

### Pruebas de Comprobante

Con la [base de integración configurada](#pruebas-sobre-postgresql), ejecutá `.\mvnw.cmd test '-Dtest=ComprobantePostgresqlTest'`.

### Columnas generadas y evolución del esquema

Prenda, Servicio y Usuario mapean las columnas normalizadas como `text` y
declaran la expresión `lower(...)` mediante `@GeneratedColumn`. PostgreSQL
genera y almacena los valores. La corrección del mapeo se puede desplegar
sobre las columnas existentes, que ya tienen el tipo, la expresión y la
unicidad esperados.

La adopción de Flyway requiere preparar una línea base versionada y probarla
en una base nueva y en una restauración. La tabla
`public.flyway_schema_history` existía y estaba vacía en la auditoría. V032 se
aplica por procedimiento controlado. El arranque normal usa `ddl-auto=none`,
Flyway deshabilitado y catálogo previamente aprovisionado. La generación de
esquema al iniciar queda limitada a las integraciones aisladas.

Antes de adoptar Flyway, aprobá una línea base que cubra objetos, índices por
expresión, restricciones y datos de referencia y que reconozca V032 donde ya
esté aplicada. Seguí la [guía de producción](docs/seguridad-configuracion-produccion.md).

## Reportes administrativos y financieros

Las tres consultas requieren `Authorization: Bearer <JWT>` con rol `ADMINISTRADOR`.
Un JWT ausente o inválido devuelve 401 y un usuario autenticado sin permiso recibe 403.
Los filtros inválidos devuelven 400 con `ProblemDetail`.

| Consulta GET | Fecha de referencia | Filtros adicionales | Resumen |
| --- | --- | --- | --- |
| `/api/reportes/pedidos` | `fechaIngreso` | `clienteId`, `estadoPedidoCodigo`, `estadoPagoCodigo` | `cantidadPedidos`, `totalGeneral` |
| `/api/reportes/movimientos-caja` | `fechaHora` | `tipoMovimientoCodigo`, `metodoPagoCodigo` | `cantidadMovimientos`, `totalIngresos`, `totalEgresos`, `saldoNeto` |
| `/api/reportes/compras` | `fechaCompra` | `proveedorId`, `estadoPagoCodigo` | `cantidadCompras`, `totalComprado` |

Los filtros se combinan con AND. `fechaDesde` y `fechaHasta` son opcionales e inclusivas
en formato `yyyy-MM-dd`, con años de 0001 a 9999. Se aplica cada límite provisto.
El límite inferior es la medianoche de `fechaDesde` y el superior exclusivo es la
medianoche del día siguiente a `fechaHasta`. Se consultan los timestamps locales
guardados con el reloj de negocio, cuya zona predeterminada es Buenos Aires.

Los códigos se comparan de forma exacta con los catálogos persistidos, incluidos sus
registros inactivos. Entre los códigos disponibles están los estados de pedido
`PENDIENTE`, `EN_PROCESO`, `LISTO`, `ENTREGADO` y `CANCELADO`, los estados de
pago `PENDIENTE`, `PARCIAL` y `PAGADO`, y los métodos `EFECTIVO`,
`TRANSFERENCIA`, `TARJETA` y `OTROS`.

`page` comienza en 0 y `size` admite de 1 a 100. Los valores predeterminados son
0 y 20. El orden fijo es por fecha descendente y después por ID descendente.
`datos` contiene la página solicitada. `resumen`, `totalElementos` y
`totalPaginas` describen el conjunto filtrado completo. Una consulta sin
coincidencias devuelve HTTP 200 con `datos: []`, conteos cero e importes `0.00`.

- **Pedidos.** `totalGeneral` suma los importes nominales registrados, incluidos
  los pedidos cancelados que cumplan los filtros. El ingreso efectivamente cobrado
  se consulta en movimientos de caja con `tipoMovimientoCodigo=COBRO_PEDIDO`.
- **Movimientos.** `INGRESO_MANUAL` y `COBRO_PEDIDO` aportan a `totalIngresos`.
  `EGRESO_MANUAL` y `PAGO_PROVEEDOR` aportan a `totalEgresos`. Ambos totales son
  magnitudes positivas o cero. `saldoNeto = totalIngresos - totalEgresos` mide el
  flujo confirmado del período, incluyendo cajas cerradas. El saldo de apertura y
  el arqueo se consultan en el módulo de Caja. Cada movimiento aporta una sola vez.
- **Compras.** `totalComprado` suma el importe nominal, incluidas compras pendientes
  y de proveedores inactivos. El dinero efectivamente pagado se consulta en
  movimientos de caja con `tipoMovimientoCodigo=PAGO_PROVEEDOR`.

Ejemplos de consultas con filtros combinados

```text
GET /api/reportes/pedidos?fechaDesde=2026-11-01&fechaHasta=2026-11-30&estadoPagoCodigo=PARCIAL
GET /api/reportes/compras?fechaDesde=2026-11-01&proveedorId=5&estadoPagoCodigo=PENDIENTE
GET /api/reportes/movimientos-caja?tipoMovimientoCodigo=EGRESO_MANUAL&metodoPagoCodigo=EFECTIVO&size=1
```

Respuesta de ejemplo para la consulta de movimientos

```json
{
  "datos": [
    {
      "movimientoId": 12,
      "cajaId": 4,
      "fechaHora": "2026-11-30T23:59:59.999999",
      "tipoMovimientoCodigo": "EGRESO_MANUAL",
      "tipoMovimientoNombre": "Egreso manual",
      "metodoPagoCodigo": "EFECTIVO",
      "metodoPagoNombre": "Efectivo",
      "usuarioId": 1,
      "usuarioNombre": "Ana",
      "descripcion": "Gasto de caja",
      "monto": 35.25,
      "naturaleza": "EGRESO"
    }
  ],
  "resumen": {
    "cantidadMovimientos": 2,
    "totalIngresos": 0.00,
    "totalEgresos": 55.25,
    "saldoNeto": -55.25
  },
  "pagina": 0,
  "tamanio": 1,
  "totalElementos": 2,
  "totalPaginas": 2
}
```

El repositorio usa proyecciones de columnas, parámetros enlazados y agregaciones
`COUNT`, `SUM` y `COALESCE` en PostgreSQL. Los importes agregados se leen como
`BigDecimal` a dos decimales. El servicio ejecuta el resumen, la validación de
catálogos y el detalle dentro de una transacción local de solo lectura con
`REPEATABLE_READ`, de modo que comparten un snapshot aunque se confirmen
escrituras concurrentes.

Las comparaciones de fecha permiten aprovechar los índices existentes
`ix_pedidos_fecha` e `ix_compras_fecha`; también existen índices compuestos que
empiezan por cliente, estado operativo de pedido o proveedor. El índice de
movimientos empieza por `id_caja`, por lo que no ofrece el orden global por fecha.
De esta etapa no surgen cambios de esquema. PostgreSQL puede elegir un escaneo y
ordenamiento para un conjunto amplio de movimientos o una consulta sin rango.

Con la [base de integración configurada](#pruebas-sobre-postgresql), ejecutá `.\mvnw.cmd test '-Dtest=ReportePostgresqlTest'`.

Si la caché local de Maven requiere evitar el bloqueo, agregá
`'-Daether.syncContext.named.factory=noop'`.
