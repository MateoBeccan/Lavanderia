# Idempotencia financiera · V032 y etapa 6.2

Los usuarios registran cobros y pagos que ya ocurrieron fuera del sistema. Los cinco POST de esta etapa admiten una cabecera `Idempotency-Key` y conservan sus permisos y el contrato de éxito `201 Created` con `Location`.

## Auditoría y alcance

| Operación | Entrada y salida | Protección previa y riesgo del reenvío |
| --- | --- | --- |
| `POST /api/pedidos/{id}/cobros` | `RegistrarCobroRequest` con `monto` y `metodoPagoId` → `CobroResponse` | Bloqueo pesimista Caja → Pedido, validación de pedido cancelado, método activo, estado de pago y saldo pendiente. El vínculo a MovimientoCaja es único por cobro. Un reenvío podía crear otro cobro y otro movimiento si todavía quedaba saldo. |
| `POST /api/compras/{id}/pagos` | `RegistrarPagoProveedorRequest` con `monto` y `metodoPagoId` → `PagoProveedorResponse` | Bloqueo pesimista Caja → Compra, validación de saldo pendiente, estado de pago, método activo y límites de caja y efectivo. El vínculo a MovimientoCaja es único por pago. Un reenvío podía crear otro pago y otro movimiento si lo permitían los saldos. |
| `POST /api/cajas/abrir` | `AbrirCajaRequest` con `montoInicial` → `CajaResponse` | El índice parcial `ux_cajas_unica_abierta` y el `INSERT … ON CONFLICT` garantizan una única caja abierta. Un reenvío podía devolver conflicto mientras siguiera abierta o abrir otra jornada tras el cierre. |
| `POST /api/movimientos-caja/ingresos-manuales` y `…/egresos-manuales` | `RegistrarMovimientoManualRequest` con `cajaId`, `monto`, `metodoPagoId` y `descripcion` → `MovimientoCajaResponse` | Bloqueo pesimista de la fila del ID indicado y verificación de que siga abierta. Se validan el método y tipo activos y los saldos aplicables. |
| `PATCH /api/cajas/{id}/cerrar` | `CerrarCajaRequest` con `efectivoContado` → `CajaResponse` | Solo ADMINISTRADOR. Bloquea la caja por ID, verifica que siga abierta y persiste el arqueo una vez. Con un cuerpo válido, otro cierre al mismo ID devuelve `409`. Tras perder la respuesta se recupera el cierre con `GET /api/cajas/{id}` y se verifica quién cerró y qué arqueo se aplicó. El GET puede mostrar el cierre de otro administrador si ganó la carrera. |

Los cinco POST admiten ADMINISTRADOR y EMPLEADO. La identidad procede del sujeto del JWT que Spring Security valida contra la cuenta activa, sus credenciales vigentes y su rol. Los importes conservan sus límites y precisión de hasta dos decimales. Cobros, pagos y movimientos exigen importe positivo. Apertura admite cero. Cobro y pago rechazan campos desconocidos. Apertura y movimientos conservan su manejo previo de campos no reconocidos.

## Contrato HTTP

Enviá exactamente una cabecera con un UUID de 36 caracteres en formato hexadecimal `8-4-4-4-12`. Se aceptan mayúsculas y minúsculas. React debe usar `crypto.randomUUID()`. El servidor usa el par **ID de usuario autenticado y UUID**. Dentro de ese par, el tipo de operación forma parte de la comparación. Un mismo UUID enviado desde otra cuenta tiene su propio historial y jamás devuelve el cuerpo guardado por la primera.

| Respuesta | Significado |
| --- | --- |
| `201 Created` | Primer éxito confirmado o recuperación de un éxito previo. Se devuelven el cuerpo y el `Location` originales, aunque hayan cambiado el saldo, la caja abierta o los nombres de los catálogos. |
| `400 Bad Request` | Cabecera inválida o repetida, cabecera ausente en modo obligatorio, o error de validación del contrato existente. La validación de entrada ocurre antes de consultar un resultado previo. |
| `401 Unauthorized` / `403 Forbidden` | Fallo de autenticación o de permisos, respectivamente. Se comprueba la autorización en cada intento. |
| `404 Not Found` | El recurso requerido por una primera ejecución no está disponible según las reglas existentes. |
| `409 Conflict` con `codigo=IDEMPOTENCIA_CLAVE_REUTILIZADA` | Esa cuenta ya confirmó otra operación o usó otro contenido con el UUID. |
| `409 Conflict` con `codigo=IDEMPOTENCIA_EN_CURSO` y `Retry-After: 1` | Otra transacción tiene el bloqueo de la clave. Esperá al menos un segundo y reenviá la misma solicitud. Si el contenido era distinto, el siguiente intento tras la confirmación devolverá el conflicto de reutilización. |
| `409 Conflict` con `codigo=CAJA_JORNADA_CAMBIADA` | El ID del movimiento manual ya no identifica una caja abierta. Se rechaza sin movimiento ni cambio de saldo. |
| Otro `409` | Conflicto del dominio, por ejemplo saldo insuficiente, caja cerrada, o conflicto de concurrencia de base de datos. Conserva el formato de error existente. |
| `5xx`, timeout o desconexión | El cliente puede desconocer si hubo commit. Repetí con la misma clave, cuenta, ruta y datos. Si hubo commit, el servidor recupera el éxito. Si hubo rollback, la operación puede ejecutarse contra el estado vigente. |

La comparación usa el tipo de operación, el ID de ruta cuando corresponde, el `cajaId` en movimientos manuales, el importe decimal, el método de pago y la descripción de los movimientos manuales. `20` y `20.00` son equivalentes. Para la descripción se aplica el mismo `strip()` del negocio. El orden y formato de las propiedades en JSON son irrelevantes. Los errores y conflictos del dominio se vuelven a evaluar en cada ejecución y liberan la clave al abortar. Los POST antiguos sin cabecera conservan su comportamiento por defecto y pueden duplicarse al reenviarse. `lavanderia.idempotencia.financiera.requerida=true` exige la cabecera en los cinco POST. CORS admite la cabecera desde los orígenes configurados y expone `Location` y `Retry-After`.

## Transacción y persistencia

La clave primaria de `solicitudes_financieras_idempotentes` garantiza un resultado por cuenta y UUID. Una tabla común permite detectar reutilización entre operaciones, conservar el resultado de una apertura que ya se cerró y responder con el estado original aunque cambien las entidades. Restricciones por entidad cubrirían solo cada tabla y obligarían a reconstruir la respuesta desde el estado actual.

La transacción Spring usa `READ COMMITTED`. Adquiere primero un bloqueo asesor transaccional de PostgreSQL, calculado sobre el esquema, el usuario y el UUID. Se pide sin espera y se libera al confirmar o revertir. Después se consulta la fila por la clave primaria exacta. Las operaciones nuevas toman sus bloqueos de negocio en el orden original y guardan la instantánea antes de confirmar. Los servicios existentes participan en la misma transacción JDBC/JPA. El índice único sigue resolviendo aperturas con claves diferentes. Una colisión del hash del bloqueo puede provocar un `409` transitorio para otra clave. La consulta y la restricción de unicidad siempre usan usuario y UUID completos. [Bloqueos asesores en PostgreSQL 17](https://www.postgresql.org/docs/17/functions-admin.html#FUNCTIONS-ADVISORY-LOCKS) · [Propagación de transacciones Spring](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html)

Se persisten el ID del actor, la clave, el tipo de operación, la huella SHA-256 del contenido normalizado, la fecha y la instantánea del éxito con su ubicación. La tabla solo contiene resultados confirmados. El movimiento o la apertura conserva su propio usuario registrante. La instantánea comparte la sensibilidad y las necesidades de acceso y respaldo de los datos financieros. Se conserva sin caducidad en esta etapa. Cambios futuros al formato de la huella o a los DTO deben conservar la capacidad de recuperar las respuestas anteriores.

El alcance es la operación HTTP. Dos UUID distintos, dos cuentas diferentes o un cliente antiguo sin cabecera pueden representar el mismo hecho comercial y se evalúan por separado. Tras un rollback de un movimiento manual, un reintento con el mismo cuerpo solo puede operar sobre la caja indicada. Si esa caja cerró o no existe, se devuelve el conflicto de jornada incluso si hay otra abierta. El replay de un éxito ocurre antes de bloquear la caja. Las operaciones confirmadas con la huella manual histórica de destino vacío se recuperan si coinciden importe, método y descripción y la instantánea guardada contiene el mismo `cajaId`. Las filas antiguas se conservan. Un cliente antiguo debe enviar el identificador real para recuperar un resultado.

## Migración y puesta en servicio

Prepará la prevalidación de la base de destino y obtené la autorización de mantenimiento antes de aplicar V032.

La migración aditiva es [V032__idempotencia_financiera.sql](../src/main/resources/db/migration/V032__idempotencia_financiera.sql). Hibernate opera con `ddl-auto=none` en ejecución normal. La etapa 6.2 conserva V032 y no agrega una migración de esquema. Flyway está instalado y sigue deshabilitado. El SQL se ejecuta explícitamente, dentro de una transacción y con el esquema esperado en el `search_path`. En un entorno que adopte Flyway a futuro, se debe registrar la migración en su historial conforme al esquema ya desplegado.

Se verificó el SHA-256 registrado del respaldo del 18 de noviembre de 2026 y se restauró su esquema `public` en un clúster local PostgreSQL 17.6. Tras la migración, `public` pasó de 24 a 25 tablas y la tabla nueva quedó vacía. Las huellas de los volcados de estructura y datos históricos coincidieron antes y después, excluyendo la nueva tabla de la comparación posterior. Aplicar la migración a la base principal requiere autorización.

Los clientes pueden usar la protección una vez aplicada la migración y desplegada esta versión en todas las instancias que reciben los cinco POST. Si hay un proxy, debe reenviar `Idempotency-Key`. La aplicación necesita permisos de lectura e inserción en la tabla. Activá el modo obligatorio cuando todos los clientes de esos POST ya envíen la cabecera.

## Verificación histórica de la etapa 3.2A

La suite backend completa ejecutó **464 pruebas, 0 fallos y 0 omitidas** en PostgreSQL 17.6 aislado con ICU `es-AR`. La primera corrida usó locale `C` y falló en el filtro de proveedores con acentos. La repetición con el locale indicado en el README incluyó esa prueba.

En cobros y pagos, una prueba retiene la primera transacción abierta mientras una segunda solicitud HTTP obtiene el `409` con `Retry-After`. Después del commit, el reintento obtiene el mismo cuerpo y ubicación. Las pruebas verifican un solo registro, un solo movimiento y el saldo esperado. También cubren el rollback con la fila ya insertada, la ausencia de filas huérfanas, la recuperación tras perder la respuesta y la recuperación desde un contexto Spring nuevo. La suite comprueba el alcance por usuario, el modo obligatorio, las cabeceras inválidas, JWT y CORS. Para caja y movimientos se verifican la repetición tras cerrar la jornada y la reutilización de clave entre ingreso y egreso.

## Implementación en React

1. Al confirmar un cobro, congelá el ID de pedido, el importe y el método y generá un UUID. Guardá el UUID junto con el intento en memoria. Usá una referencia o un bloqueo síncrono para impedir dos envíos antes de que React actualice el botón.
2. Enviá `POST /api/pedidos/{id}/cobros` con el JWT, `Idempotency-Key` y el cuerpo congelado. Ante timeout, desconexión o `IDEMPOTENCIA_EN_CURSO`, mostrá «Resultado pendiente de confirmar» y ofrecé recuperar el mismo intento. Renovar autenticación es compatible con el reintento desde la misma cuenta. Una clave nueva identifica un cobro distinto.
3. Usá el `201` para mostrar la confirmación del registro y refrescá el pedido, su historial de cobros y su saldo. Invalidá también las consultas de caja abierta, saldo y movimientos de la caja indicada en la respuesta. Para pagos a proveedores, refrescá compra, pagos y saldo de compra. Para apertura o movimiento manual, consultá el estado vigente de caja porque una respuesta recuperada puede describir una jornada que ya cerró.
4. Mostrá los conflictos definitivos y permití corregir y confirmar una nueva intención según el caso. Mientras se desconozca el resultado, conservá el intento anterior y evitá enviar otro UUID para ese cobro. Si la interfaz ofrece recuperación tras recargar, conservá como mínimo el UUID y la referencia del intento solo si existe una forma de reconstruir exactamente su ruta y contenido. Evitá persistir importes, descripciones, respuestas financieras y tokens en almacenamiento local solo para implementar esta función.

Para resultados de la etapa 6.2 y adopción coordinada, consultá el [seguimiento de auditoría](auditoria-integral-etapa-6.md#14-seguimiento-de-la-etapa-62) y el [procedimiento de producción](seguridad-configuracion-produccion.md).
