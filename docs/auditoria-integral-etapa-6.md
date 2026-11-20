# Mirá que te lavo · Auditoría integral de la etapa 6.1

**Fecha:** 19 de noviembre de 2026, Buenos Aires.

**Versión revisada:** `main` en `1f4ece4b`, con los cambios locales de Reportes, navegación y cliente HTTP presentes al iniciar.

## 1. Resumen ejecutivo

**Dictamen: bloquear la salida a producción.** El servidor puede aplicar un movimiento manual pendiente a una jornada distinta de la prevista. Además, la configuración habitual permite que el arranque modifique el esquema con una cuenta PostgreSQL privilegiada. Ambos problemas requieren corrección y verificación en un entorno desechable antes de operar con dinero real.

Los flujos de cobros y pagos comparten transacción con sus movimientos y bloquean las filas que protegen el saldo. V032 está presente en el catálogo local. Su garantía cubre la repetición de solicitudes exitosas con la misma clave, identidad y contenido, y tiene límites cuando se pierde el estado en el navegador o el primer intento se revierte.

Este diagnóstico combina revisión de código, pruebas seguras y una consulta de metadatos en PostgreSQL con `BEGIN READ ONLY`. Los hallazgos **confirmados** cuentan con evidencia del código, configuración, catálogo o pruebas indicadas. Los **potenciales** requieren datos representativos, validación operacional o una prueba aislada. Cada aceptación es un criterio para la fase de corrección. Los nombres Java referidos pertenecen a `src/main/java/com/miraquetelavo/lavanderia` y los tests Java a su árbol equivalente en `src/test/java`.

## 2. Estado por módulo

| Módulo | Funcionalidad, permisos y consistencia | Validaciones, casos límite, usabilidad y pruebas |
| --- | --- | --- |
| Autenticación y usuarios | Login y perfil para ambos roles. Alta, consulta, edición, activación, baja y contraseña por API para administradores. La UI ofrece login y salida. Gestión de usuarios figura pendiente. | JWT con vencimiento y revocación por estado o versión al consultar la cuenta. Login devuelve mensaje genérico. Vitest, JWT unitario, Playwright simulado y `UsuarioPostgresqlTest` inventariado. Ver A-04 y M-01. |
| Dashboard | Ambos roles ven accesos filtrados y métricas de clientes activos, pedidos y caja. Usa tres consultas, con página de un registro para cada conteo. | Caja inexistente se presenta como cerrada operativamente. Estados de carga y error por tarjeta. Playwright en `auth.spec.ts`; faltan mediciones de latencia y comportamiento con volumen. |
| Clientes | Ambos roles consultan, crean y editan; baja administrativa. El listado se limita a activos. Formularios y estados de error compartidos. | DNI de ocho dígitos y único, baja lógica. Verificar flujos con clientes históricos inactivos y que los errores de validación sigan mostrando el campo correcto. Pruebas de servicio, MVC, metamodelo y navegador. |
| Servicios y prendas | Lectura operativa, escritura y reactivación administrativas. UI de catálogo reutilizada. | Nombre normalizado y único, baja lógica y reglas de referencias activas. Servicios valida importe y modalidad. Pruebas MVC, unitarias, navegador y PostgreSQL para Prendas. |
| Pedidos y estados | Ambos roles crean, editan, consultan, transicionan y cancelan según el estado. Historial de transiciones por usuario. | El backend calcula importes y valida prendas, servicios, cantidades y restricciones sobre pedidos cobrados. El formulario advierte ante envío incierto. Pruebas de transiciones, detalles, MVC, Playwright e integración inventariada. Ver M-02. |
| Cobros | Ambos roles registran cobros, consultan saldo e historial. El flujo enlaza el comprobante. | Bloqueos Caja → Pedido, validación del pendiente, estado del pedido y método. El estado de pago y movimiento se actualizan junto con el cobro. Pruebas de servicio, Zod, recuperación, navegador y PostgreSQL inventariado. Ver A-01 y A-02. |
| Caja y arqueos | Ambos roles pueden abrir y registrar movimientos. Administrador lista jornadas y cierra con arqueo. Ambos consultan por ID, saldo y movimientos. | Índice parcial para una sola abierta; cierre por fila bloqueada y validación de efectivo y límites. UI permite reintento manual con advertencia si cambia la caja. Pruebas de cálculo, MVC, navegador y PostgreSQL inventariado. Ver C-01. |
| Proveedores | Ambos roles consultan. Administración da de alta, modifica, desactiva y reactiva. UI incluye filtros y búsqueda paginada. | Validación de CUIT cuando se informa y manejo de duplicados. Revisar búsqueda y enlaces a compras históricas con proveedor inactivo. Pruebas de CUIT, Playwright e integración inventariada. |
| Insumos y categorías | Lectura para ambos, ABM y reactivación administrativa. Insumos usa el filtro de categoría. | Nombres únicos sin distinción de mayúsculas por índices de expresión. Servicios validan actividad y relaciones. MVC, servicio, Zod y navegador; PostgreSQL inventariado. |
| Proveedor e insumo | Ambos roles pueden consultar la relación; administración vincula y desvincula. La ficha del proveedor permite gestionarla. | PK compuesta, FK en ambos lados y consultas paginadas de vínculos vigentes. Conviene comprobar visibilidad tras desactivar y reactivar catálogos y consistencia con compras históricas. Cobertura a través de Proveedores e integración inventariada. |
| Compras | Ambos roles listan, crean, editan y consultan. Detalles, resumen, saldo, pagos y comprobantes se conectan desde la ficha. | Valida proveedor, insumos y categoría activa, calcula importes en servidor y protege la edición frente a pagos. Pruebas de importes, MVC, formulario, navegador e integración inventariada. Ver M-02. |
| Pagos a proveedores | Ambos roles registran pagos y consultan saldo, historial y ficha. El registro enlaza el comprobante. | Bloqueos Caja → Compra, validación del saldo y movimiento asociado. Pruebas MVC, formulario, esquema, navegador y PostgreSQL inventariado. Ver A-01 y A-02. |
| Comprobantes | Ambos roles emiten comprobantes internos desde cobros y pagos y consultan historial. | Restricciones de emisión por única operación y a lo sumo un comprobante por cobro o pago. Hay comprobación de observaciones y manejo de conflicto. Pruebas MVC, servicio, esquema, Playwright y PostgreSQL inventariado. |
| Reportes administrativos | Administrador consulta pedidos, movimientos y compras con filtros, resumen y páginas. API y UI limitan el acceso por rol. | Valida fechas, códigos y tamaño hasta 100. Tabla y agregados comparten snapshot por respuesta. Casos de rango amplio, cambios entre páginas y costo de agregados quedan abiertos. Tests MVC, servicio, Vitest, navegador e integración inventariada. Ver M-03. |

## 3. Hallazgos críticos

### C-01 · Movimiento manual puede registrarse en otra jornada

**Severidad:** crítica. **Estado:** confirmado por trazado del código.

- **Ubicación y evidencia.** `AccionesCaja.tsx:79` guarda `cajaId` en el intento de UI. `frontend/src/features/caja/api/escrituras.ts:45` envía únicamente `intento.body` y la clave. `MovimientoCajaController.java:71` construye la huella con destino nulo. `RegistroMovimientoCaja.java:46–55,87–95` elige y bloquea la caja abierta al ejecutar. `ResultadoCaja.tsx:35,114–117` advierte el cambio y permite confirmar el reintento. La respuesta se acepta si coincide el tipo, sin comparar la caja de destino.
- **Impacto.** Si el primer envío no llegó o se revirtió y la caja A ya cerró, el reintento confirmado puede crear el movimiento en la caja B. También existe una ventana entre ver la confirmación y ejecutar el primer POST. El resultado afecta los dos arqueos y la atribución al turno.
- **Propuesta.** Incorporar el identificador esperado a una versión acordada del contrato, incluirlo en la huella y comprobarlo bajo el bloqueo de caja antes de escribir. Preservar el replay de un movimiento ya confirmado y planificar compatibilidad para claves V032 existentes. En la UI, ofrecer conciliación al detectar cambio de jornada hasta que el servidor pueda validar el destino.
- **Aceptación.** En entorno desechable, si A confirmó y se perdió la respuesta, reintentar tras abrir B devuelve la misma ubicación y el mismo movimiento de A. Si A no confirmó, el intento de A recibe conflicto y B queda intacta. Cubrir cambio de jornada antes del primer envío, ingreso, egreso y solicitudes simultáneas.

### C-02 · El arranque habitual puede cambiar PostgreSQL con una cuenta privilegiada

**Severidad:** crítica para salida a producción. **Estado:** confirmado en configuración y código.

- **Ubicación y evidencia.** `application.properties:6–18` apunta a `lavanderia_db` con usuario `postgres` y una contraseña suministrada mediante `${DB_PASSWORD}`, sin valor por defecto. También habilita `ddl-auto=update`, la recreación silenciosa de unicidad y el trazado SQL, y mantiene Flyway deshabilitado. `CajaSchemaInitializer.java:14–49` toma un bloqueo exclusivo y puede crear o reemplazar restricciones. Hay inicializadores de esquema para Insumos y Comprobantes y carga de catálogos ligada al modo de DDL.
- **Impacto.** Un reinicio o cambio de entidades puede bloquear tablas o modificar restricciones fuera de una ventana de mantenimiento. La cuenta compartida amplía el alcance de una exposición de credenciales.
- **Propuesta.** Inventariar los consumidores de la cuenta privilegiada y rotar su contraseña por un canal autorizado si se confirma una exposición. Separar roles de aplicación y mantenimiento, externalizar secretos, definir perfil productivo sin mutaciones de esquema al iniciar y separar la verificación de catálogos del aprovisionamiento. Preparar una línea base que reconozca V032 ya aplicada.
- **Aceptación.** La aplicación arranca dos veces con un rol sin DDL, esquema verificado y catálogos instalados. La huella del esquema se conserva. Un esquema incompleto falla de forma explícita, y el artefacto y el entorno efectivo carecen de contraseñas incrustadas y el datasource efectivo usa la cuenta de aplicación.

## 4. Hallazgos altos

### A-01 · Recarga y cierre de pestaña pierden la identidad del intento financiero

**Severidad:** alta. **Estado:** confirmado.

- **Ubicación y evidencia.** `frontend/src/shared/api/operacionFinanciera.ts:7–48` conserva el cuerpo y el UUID en memoria. `sessionStorage` guarda un marcador de conciliación por usuario. `useOperacionCaja.ts:39–75,136–143` guarda caja o apertura y permite cerrar el aviso. Los flujos de cobro y pago conservan un identificador del recurso, con lógica semejante de verificación. `IdempotenciaFinanciera.java:49–79` sólo recupera resultados desde un POST con la misma clave.
- **Impacto.** Tras recargar se puede revisar el historial, pero eventos de igual monto, operador o momento pueden confundirse. Una nueva solicitud recibe otro UUID. Cerrar la pestaña o bloquear el almacenamiento puede quitar incluso el marcador.
- **Propuesta.** Evaluar consulta autenticada y de solo lectura por UUID, ligada al usuario autenticado y sin exposición de cuerpos ajenos. Guardar UUID y referencia mínima antes del envío, sin payload ni token. Respuesta confirmada con referencia recuperable. Distinguir en curso de ausencia de confirmación cuando sea posible, manteniendo el intento incierto si una petición original todavía puede entrar. Definir retención, auditoría y recuperación supervisada.
- **Aceptación.** Perder la respuesta, recargar, ingresar con la misma cuenta y recuperar el identificador confirmado. Otra cuenta queda sin acceso. Consultar durante una transacción en curso o tras rollback deja clara la incertidumbre y no crea ningún asiento.

### A-02 · Protección idempotente opcional y limitada al usuario y UUID

**Severidad:** alta. **Estado:** confirmado.

- **Ubicación y evidencia.** `IdempotenciaFinanciera.java:35–52,61–79` permite ejecutar sin cabecera por defecto. V032 usa la PK `(usuario_id, clave)`, con operación y hash comparados en la repetición. Una clave nueva constituye otra operación. Solamente se registran resultados exitosos. El cierre por PATCH queda fuera y usa estado cerrado como guarda.
- **Impacto.** Un cliente sin UUID o una repetición desde otra cuenta puede generar otra operación si el saldo y el estado permiten integrarla. Una política que purgue resultados demasiado pronto acorta la protección. El cierre se puede conciliar por ID, pero una pérdida de respuesta no tiene un replay equivalente.
- **Propuesta.** Inventariar consumidores y evaluar obligatoriedad después de verificar compatibilidad. Definir horizonte de replay y retención del JSON, correlación operativa por UUID y un protocolo de conciliación para cierres.
- **Aceptación.** Los cinco POST pasan una matriz por clave ausente e inválida, distinto usuario y UUID, misma huella, huella cambiada, rollback y pérdida de respuesta tras COMMIT. Un cierre incierto se verifica por ID con sus importes originales.

### A-03 · Las pruebas con Spring real confían en el puerto para aislarse

**Severidad:** alta. **Estado:** confirmado para Playwright.

- **Ubicación y evidencia.** `frontend/playwright.config.ts:7–94` exige bandera y puerto de proxy por módulo. `caja.spring.spec.ts`, `cobros.spring.spec.ts`, `pagos-proveedor.spring.spec.ts` y otros realizan escrituras. El puerto no identifica la base utilizada por ese Spring. `E2E_PREVIEW_EXISTING` puede reutilizar un preview externo al proceso de prueba. Incluso el smoke de login escribe el último acceso en `AuthService.java:30–37`.
- **Impacto.** Se puede iniciar Spring en el puerto previsto con el datasource habitual y escribir registros sintéticos en la base principal.
- **Propuesta.** Un lanzador debe controlar clúster, base y rol desechables y comprobar su identidad desde el backend antes de habilitar la suite. El rol de prueba debe carecer de acceso a la principal. Mantener el proxy sin backend para simulaciones.
- **Aceptación.** Con un backend que declara un destino distinto, la suite aborta antes del login incluso si el puerto es correcto. Una base desechable verificada permite correr todo el flujo sin acceso a la principal.

### A-04 · Login público sin límite de intentos demostrado

**Severidad:** alta si se expone fuera de una red controlada. **Estado:** potencial en producción, ausencia en el código revisado confirmada.

- **Ubicación y evidencia.** `SecurityConfig.java:52–54,136–147` publica login y usa PBKDF2. `AuthService.java:30–37` autentica y registra acceso. En el repositorio no se encontró limitación de intentos de aplicación ni configuración de un proxy productivo que la imponga.
- **Impacto.** Adivinación de contraseñas y agotamiento de CPU por autenticaciones repetidas.
- **Propuesta.** Definir límites por origen y cuenta, respuesta genérica y telemetría de intentos, teniendo en cuenta el proxy de confianza y usuarios detrás de la misma red.
- **Aceptación.** Un ensayo controlado de carga en staging confirma límites, recuperación y observabilidad. El comportamiento comercial de los dos roles se conserva.

### A-05 · Versiones con avisos publicados y escaneo pendiente del backend

**Severidad:** alta como condición de liberación. **Estado:** versión afectada confirmada por aviso; exposición concreta pendiente.

- **Ubicación y evidencia.** `pom.xml:9–14` hereda Spring Boot 4.1.2. Su BOM local declara Spring Security 7.1.2, Spring Framework 7.0.10, Tomcat 11.0.27, Hibernate 7.4.13.Final, Flyway 12.4.0 y JDBC PostgreSQL 42.7.14. Spring Security 7.1.2 figura dentro del rango de CVE-2026-59273, CVE-2026-59329, CVE-2026-59330, CVE-2026-59331 y CVE-2026-59333. La configuración revisada usa MVC, JWT y matchers por ruta y rol. En el código no se identificaron los usos específicos de matchers EL, autenticación reactiva, WebAuthn, RSocket o restricciones IP que describen estos avisos. El servidor local PostgreSQL 17.6 también queda por detrás de la actualización de seguridad 17.12. [Aviso CVE-2026-59273](https://spring.io/security/cve-2026-59273/), [CVE-2026-59329](https://spring.io/security/cve-2026-59329/), [CVE-2026-59330](https://spring.io/security/cve-2026-59330/), [CVE-2026-59331](https://spring.io/security/cve-2026-59331/), [CVE-2026-59333](https://spring.io/security/cve-2026-59333/), [actualización de PostgreSQL](https://www.postgresql.org/about/news/postgresql-187-1712-1616-1520-1425-and-19-rc-2-released-3442/).
- **Impacto.** La coincidencia de versión obliga a revisar alcance y mitigación. Los transitivos restantes, herramientas de build e imagen de despliegue necesitan comprobación adicional.
- **Contraste puntual adicional.** El lockfile selecciona Vite 8.3.8 y Rolldown 1.2.19, posteriores a las correcciones requeridas para GHSA-p4f5-76q4-ffq7 en esa rama. El JDBC 42.7.14 del BOM incluye los parches publicados para CVE-2026-107314 y CVE-2026-107315. [Aviso de Vite](https://github.com/vitejs/vite/security/advisories/GHSA-p4f5-76q4-ffq7), [avisos de pgJDBC](https://jdbc.postgresql.org/security/).
- **Escaneo npm.** Tras recibir autorización para consultar el registro oficial, `npm audit` finalizó con código 0 e informó cero vulnerabilidades conocidas entre las 384 dependencias contabilizadas.
- **Propuesta.** Escanear el árbol Maven resuelto y la imagen final. Repetir el escaneo npm al cambiar el lockfile o al preparar la liberación. Evaluar una versión compatible de Boot que incorpore Spring Security corregido. Spring indica 7.1.3 para la rama abierta de estos avisos. Ensayar el parche menor de PostgreSQL en una copia y revisar los pasos posteriores a la actualización.
- **Aceptación.** Escaneos reproducibles con fecha y paquetes exactos, evaluación documentada de aplicabilidad y corrección o mitigación aprobada para cada alerta relevante.

## 5. Hallazgos medios

### M-01 · Administración de usuarios sin interfaz operativa

**Severidad:** media. **Estado:** confirmado.

- **Ubicación y evidencia.** `navigation.ts:79–82` marca pendiente la gestión. `AppRouter.tsx:239` monta vistas pendientes. `UsuarioController.java` sí ofrece operaciones administrativas protegidas.
- **Impacto.** Altas, bajas y cambios de contraseña requieren intervención técnica. La baja tardía de un empleado prolonga su acceso.
- **Propuesta.** Acordar la operación por UI administrativa o por un procedimiento controlado y trazable.
- **Aceptación.** Un administrador puede alta, cambio de clave y baja de una cuenta, y el JWT anterior deja de funcionar cuando corresponde. El empleado recibe 403 en las rutas administrativas.

### M-02 · Confirmación incierta en altas de pedidos y compras

**Severidad:** media. **Estado:** confirmado para el contrato; duplicado en operación todavía potencial.

- **Ubicación y evidencia.** `PedidoForm.tsx:60,79–99,249–275` maneja la incertidumbre localmente. Los POST de `PedidoController.java` y `CompraController.java` crean el recurso sin el mecanismo V032. `CompraForm.tsx` tiene bloqueo de formulario y tratamiento de errores.
- **Impacto.** Una respuesta perdida puede requerir buscar manualmente la alta antes de registrar otra. Un duplicado desplaza trabajo operativo y puede dar origen a otro circuito financiero.
- **Propuesta.** Definir criterios de conciliación de altas, mostrar el recurso esperado cuando se pueda identificar y evaluar correlación de creación en una fase posterior.
- **Aceptación.** Con pérdida de respuesta luego de confirmar un alta, el operador puede identificar el original y evitar una duplicación. Validar ambos flujos con datos sintéticos.

### M-03 · Coste de reportes y paginación variable con escrituras concurrentes

**Severidad:** media. **Estado:** potencial para latencia; diseño confirmado.

- **Ubicación y evidencia.** `ReporteRepository.java:27–99,131–139` calcula agregados, agrupaciones y página por consulta, con orden y offset. `ReporteServiceImpl.java:25,65–103` limita tamaño, admite rango abierto y usa `REPEATABLE_READ` por petición. `useReporte.ts:15–20` cachea por filtros y página.
- **Impacto.** Cada página puede repetir agregados caros. El resumen y filas son coherentes dentro de su respuesta; distintas páginas pueden observar snapshots diferentes. El índice de movimientos empieza por caja, mientras el reporte puede filtrar fecha global.
- **Propuesta.** Medir con `EXPLAIN (ANALYZE, BUFFERS)` en copia representativa y datos sintéticos. Incluir rangos abiertos y páginas profundas. Decidir índice, límites y estrategia de navegación según resultados.
- **Aceptación.** Planes, filas leídas, uso de búferes y p95 quedan registrados para cada reporte y el comportamiento al paginar durante cambios se documenta y prueba.

### M-04 · Restauración y migración reproducibles pendientes de formalización

**Severidad:** media, bloqueante cuando no existe recuperación operativa aprobada. **Estado:** potencial fuera del repositorio.

- **Ubicación y evidencia.** El registro operativo privado documenta respaldo, restauración aislada y comparación de firmas para la intervención de V032. Convivencia actual de Hibernate, inicializadores, SQL versionado y Flyway vacío. Haría falta demostrar un proceso recurrente que cubra una instalación limpia y una restauración desde respaldo.
- **Impacto.** Un backup puede existir sin estar disponible con las credenciales, frecuencia o tiempos requeridos cuando haya una caída. Un arranque vacío puede quedar sin invariantes específicas de PostgreSQL.
- **Propuesta.** Documentar RPO y RTO, responsables, backups cifrados fuera del host, retención, verificación y restauración periódica. Ensayar una línea base y la continuidad de V032 desde una copia.
- **Aceptación.** Un operador distinto restaura una copia en entorno desechable, verifica integridad y reconcilia una jornada dentro del RPO y RTO acordados.

### M-05 · Autorización por rol para recursos compartidos

**Severidad:** media. **Estado:** comportamiento confirmado, riesgo comercial potencial.

- **Ubicación y evidencia.** `SecurityConfig.java:54–122` permite que ambos roles identifiquen pedidos, compras, caja y sus recursos asociados según ruta. Limita usuarios y reportes al administrador y restringe el listado y cierre de caja. El detalle, saldo y movimientos de cajas por ID se comparten entre roles, incluso si el listado histórico es administrativo.
- **Impacto.** Si se espera separación futura por jornada, sucursal o titular, el alcance actual excedería esa expectativa. Para una operación de una sola sucursal, este acceso puede ser intencional.
- **Propuesta.** Confirmar la matriz comercial actual por objeto y documentar el alcance. Mantener permisos actuales hasta que el dueño del proceso apruebe un cambio.
- **Aceptación.** Tests positivos y negativos para ambos roles, recursos propios y compartidos, cajas cerradas, IDs inexistentes y subrecursos. Las expectativas quedan firmadas por Operaciones.

### M-06 · Los pedidos admiten listas de detalle sin límite de renglones

**Severidad:** media. **Estado:** ausencia de tope confirmada en el flujo revisado; impacto de capacidad potencial.

- **Ubicación y evidencia.** `CrearPedidoRequest.java:15` y `ActualizarPedidoRequest.java:14` exigen detalles válidos y una lista no vacía. `PedidoServiceImpl.java:197–230` carga catálogos por lotes y procesa todos los renglones dentro de la transacción. No se encontró un máximo de renglones en esos DTO o en el servicio.
- **Impacto.** Solicitudes grandes aumentan el costo de validación, memoria y persistencia, y pueden prolongar bloqueos durante la edición.
- **Propuesta.** Medir el tamaño habitual y máximo de un pedido legítimo. Acordar límites compatibles de renglones y bytes, con mensaje accionable en la UI.
- **Aceptación.** Un pedido en el límite se guarda correctamente. Uno por encima se rechaza antes de modificar datos y el formulario conserva el borrador.

### M-07 · Espera intermitente al abrir el historial de Caja

**Severidad:** media. **Estado:** fallo intermitente de navegador confirmado; causa pendiente.

- **Ubicación y evidencia.** `frontend/e2e/auth.spec.ts:223–230` navega a las rutas restringidas como empleado. Durante dos corridas la aserción venció a los cinco segundos en Caja con el indicador «Cargando…». En la repetición focalizada pasó. La traza de la corrida serial completa muestra solicitudes de chunks de Caja sin respuesta registrada antes del fallo. `AppRouter.tsx:190–196` carga Caja con Suspense, y `CajaRoutes.tsx:160–174` verifica el rol en la página de historial.
- **Impacto.** Respuesta demorada al acceder en frío y señal de regresión intermitente en CI. La prueba no registró datos del historial ni confirmó una falla en la autorización.
- **Propuesta.** Medir navegación en frío bajo condiciones reproducibles, revisar el orden de carga y de la guarda del historial y verificar un estado recuperable ante fallos de los chunks.
- **Aceptación.** Navegaciones repetidas, con carga en frío y red degradada controlada, muestran la restricción y no consultan el historial con rol empleado. El administrador puede abrir la vista y obtiene respuesta recuperable si falla la carga.

## 6. Mejoras de baja prioridad

### B-01 · Componentes de confirmación y avisos repetidos

**Severidad:** baja. **Estado:** confirmado.

- **Ubicación y evidencia.** `frontend/src/shared/components/forms/useUnsavedChanges.ts:5–18` centraliza parte del bloqueo. `CatalogoForm.tsx:44–59` y `PedidoForm.tsx:79–94` repiten lógica con matices propios. Caja mantiene un almacén de estado parecido al de `operacionFinanciera.ts`.
- **Impacto.** Mantenimiento duplicado y mayor probabilidad de mensajes de salida o conciliación distintos entre módulos.
- **Propuesta.** Unificar de forma dirigida tras fijar el contrato de recuperación. Conservar controles específicos de envío pendiente, foco y retorno por teclado.
- **Aceptación.** Probar navegación interna y recarga con formulario limpio, sucio, guardado, enviando e incierto en cada flujo afectado.

### B-02 · Coste de consultas y refrescos sin umbral medido

**Severidad:** baja. **Estado:** potencial.

- **Ubicación y evidencia.** Los repositorios de Pedidos, Compras, Movimientos y Comprobantes declaran `@EntityGraph` o proyecciones para asociaciones de listados. `UsuarioServiceImpl.java:74` usa `findAll(pagina)` y su mapeo lee el rol. `useOperacionCobro.ts:42–55`, `useOperacionPago.ts:66–70` y `useOperacionCaja.ts:83` invalidan familias de consultas completas. `AppProviders.tsx:7` define 30 s de frescura y reintentos de consulta desactivados.
- **Impacto.** Puede haber consultas adicionales según el plan de carga de roles, refetches simultáneos y rendimiento irregular cuando varias vistas están activas. El volumen real de consultas y renders sigue pendiente de medición.
- **Propuesta.** Medir SQL por pantalla, latencia al abrir Dashboard y renderizados durante captura de detalle. Reducir invalidaciones o cambiar el fetch sólo si las métricas confirman costo excesivo.
- **Aceptación.** Un perfil con el mismo volumen y guion de uso muestra el costo antes y después y mantiene saldos, fichas e historiales actualizados.

### B-03 · La búsqueda del listado de clientes tiene alcance de página

**Severidad:** baja. **Estado:** confirmado y cubierto por una prueba de comportamiento actual.

- **Ubicación y evidencia.** `ClientesPage.tsx:85–100,196–204` filtra sólo los resultados cargados y lo indica en la etiqueta. `ClienteController.java:39–42` recibe paginación. `clientes.spec.ts:52` comprueba que se busca en la página visible.
- **Impacto.** Con un volumen mayor, encontrar a una persona por DNI, teléfono o nombre desde el listado puede requerir avanzar página por página.
- **Propuesta.** Validar el tiempo de búsqueda con empleados y considerar un filtro paginado global si el recorrido es frecuente.
- **Aceptación.** Una búsqueda global acordada devuelve el cliente de otra página desde el primer resultado, conserva el filtro y distingue cero coincidencias de un fallo.

## 7. Seguridad

- **Sesión.** `JwtService` usa HS256, clave Base64 de al menos 32 bytes, emisor y expiración. La vigencia habitual es una hora. `CustomUserDetailsService` consulta estado, versión y rol en cada petición autenticada. La UI guarda el token en memoria, limpia el caché al terminar sesión y reacciona a 401. Una recarga requiere volver a ingresar. La salida en UI descarta la sesión local, por lo que un token ya emitido conserva su vigencia hasta expirar o hasta un cambio de credenciales o estado.
- **Perímetro HTTP.** La lista explícita de métodos y rutas termina en `denyAll`. CORS admite orígenes configurados, prohíbe comodines e incluye `Authorization` e `Idempotency-Key`. El modelo stateless con Bearer explica la desactivación de CSRF. Validar HTTPS, cabeceras y política de orígenes en el entorno productivo efectivo.
- **Entradas, errores y secretos.** Bean Validation y validaciones de servicio complementan los formularios Zod. `ApiExceptionHandler.java:30–103` usa `ProblemDetail`, diferencia 401, 403, 404 y 409 y devuelve un 500 genérico con detalle en logs del servidor. Verificar redacción de excepciones, tokens y datos personales en proxy y colector. El JWT exige configuración válida y el bootstrap de usuario está desactivado por defecto. El hallazgo C-02 aborda el uso de una cuenta PostgreSQL privilegiada y los permisos de escritura durante el arranque.

## 8. Integridad financiera

| Escenario | Comportamiento hallado y validación pendiente |
| --- | --- |
| Pedido → Cobro → Caja | `CobroServiceImpl.java:36–62` bloquea caja y pedido, calcula pendiente, guarda cobro y movimiento y actualiza el estado de pago. `RegistroMovimientoCaja` exige una transacción activa. El historial de pedido registra transiciones y operador. |
| Compra → Pago → Caja | `PagoProveedorServiceImpl.java:36–61` aplica el mismo orden de bloqueo con Compra y valida pendiente. El pago y su movimiento tienen relación única. La edición de la compra se serializa sobre la compra y verifica pagos. |
| Cierre y transacciones simultáneas | Movimientos y cierre dependen del bloqueo de la fila de caja. El cierre recalcula efectivo y saldo antes de persistir el arqueo. El índice parcial soporta aperturas simultáneas. Los egresos en efectivo validan disponibilidad; egresos por otros métodos siguen su regla y límites actuales. |
| COMMIT con respuesta perdida | V032 graba resultado y escritura de negocio en la misma transacción. Un replay válido devuelve `201`, `Location` y cuerpo persistido antes de intentar otra escritura. Si una transacción se revierte, el UUID queda libre para un intento nuevo. |
| UUID reutilizado o en curso | La exclusión advisory de alcance transaccional se toma antes de Caja y del recurso. En curso se responde 409 con `Retry-After`; contenido u operación distintos después de un éxito dan 409 por clave reutilizada. Comparación incluye destino cuando el flujo lo informa, monto decimal normalizado, método y descripción. |
| Cambio de jornada | Cobro y pago retienen la identidad del pedido o compra. Si nunca confirmaron, vuelven a seleccionar la caja abierta al ejecutar. Validar con Operaciones si el turno forma parte de su intención. Para un movimiento manual, existe un destino explícito en pantalla y el servidor no lo impone, C-01. |
| Precisión y trazabilidad | Entidades financieras usan `BigDecimal` y columnas `numeric`. DTO y reglas de dominio validan escalas. Los importes se deciden en servidor; la UI usa números JS para entrada, presentación o cálculos preliminares. Probar centavos, límites de columna, operaciones simultáneas al máximo del saldo y conciliación de sumatorias con datos sintéticos. |

El catálogo confirma restricciones de importe, claves foráneas y unicidad en tablas financieras. Persisten invariantes entre tablas que dependen de servicios, entre ellas igualdad del movimiento con su cobro o pago y estado de pago derivado. Validar sobre una copia la reconciliación de cardinalidad, importe, tipo de movimiento, saldo y arqueo, así como el rollback ante un fallo posterior al primer insert.

## 9. PostgreSQL y aislamiento de pruebas

- **Catálogo actual.** PostgreSQL 17.6. Consulta read-only de `lavanderia_db` confirmó 25 tablas en `public`, 32 FK validadas, índice parcial `ux_cajas_unica_abierta`, unicidad de catálogos por expresión y PK compuesta de V032 con CHECK validado, siete columnas NOT NULL e historial Flyway con cero filas. El registro operativo privado confirma el COMMIT y la verificación del respaldo.
- **Coherencia JPA.** Los 23 nombres de tabla declarados por las entidades coinciden con el catálogo. Las dos tablas adicionales son V032 y el historial de Flyway. Se contrastaron relaciones y restricciones principales de Caja, Movimiento, Cobro, Pago, Pedido, Compra, Comprobante, catálogos y Proveedor–Insumo. Hay estructuras con SQL fuera de Hibernate, como el índice parcial de caja, los índices de nombre de Insumos y V032. Queda pendiente una comparación exhaustiva de columnas, defaults, constraints e índices contra un esquema limpio y una conciliación de datos.
- **Paginación e índices.** Se observan índices por pedido/fecha, compra/fecha, caja/fecha, cliente/fecha, proveedor/fecha y relaciones de detalles. Las pantallas paginan en servidor y los reportes aplican orden estable con desempate por ID. Inventariar máximos de página fuera de reportes, filtros admitidos, ordenamientos y consultas costosas antes de ajustar índices.
- **JUnit.** Las 14 clases PostgreSQL se registran con `@IntegracionPostgresql`. `IntegracionPostgresql.java:16–22` exige la variable de entorno. `EsquemaPostgresql.java:26–110` acepta exclusivamente un JDBC sin parámetros con base `lavanderia_test_*`, comprueba `current_database()`, crea un esquema aleatorio por clase e impone sus propiedades de conexión con precedencia alta. Los reinicios usan ese esquema. La limpieza se dirige al esquema validado al cerrar el contexto. Para finanzas, conservar además un clúster y rol desechables y sin privilegios en la principal.
- **V032 en integración.** Las cuatro clases con `@Sql(BEFORE_TEST_CLASS)` son `CajaPostgresqlTest:53`, `CobroPostgresqlTest:67`, `MovimientoCajaPostgresqlTest:52` y `PagoProveedorPostgresqlTest:84`. Todas pasan por el soporte anterior. Esta ejecución suprimió la variable de opt-in, porque el encargo excluye ejecutar migraciones. Las pruebas de slice MVC que importan idempotencia sustituyen `JdbcTemplate`; el test de aplicación genera DDL en memoria, desactiva metadatos JDBC y usa un gestor de transacciones simulado.
- **Playwright.** Los proyectos reales son opt-in por bandera y destino. El puerto es una guarda operacional insuficiente, A-03. Para esta etapa se seleccionaron archivos simulados con Vite local y proxy dirigido explícitamente a `127.0.0.1:1`, sin iniciar Spring.

## 10. Frontend y usabilidad

- La arquitectura por funcionalidades separa rutas lazy, API, hooks, componentes y esquemas. Login usa etiquetas, mensajes de validación y control de visibilidad de contraseña. Dashboard ofrece accesos por rol, estados independientes y ruta primaria de pedido. Los módulos operativos reutilizan tablas con `caption`, región desplazable con foco, acciones de paginación, formularios y estados vacíos.
- El cliente HTTP conserva detalle y estado, distingue 401 y 403, y representa la red caída como estado 0. Las rutas tratan 404 según el recurso, incluida la ausencia normal de caja. Los 409 financieros incluyen tratamiento para clave en curso o reutilizada y conciliación según el flujo. La UX depende de que el backend distinga con precisión los conflictos.
- La protección de cambios sin guardar existe en los formularios principales y tiene variantes por módulo. Los resultados de Playwright incluyen los escenarios simulados de móvil y axe de las suites. Quedan pendientes pruebas manuales con lector de pantalla, Safari/Firefox y el recorrido del empleado en el entorno real, con cambio de turno, sesión vencida y posible pérdida de respuesta.
- El build generó entrada JS de 372,83 kB (115,79 kB gzip), CSS de 58,38 kB (10,11 kB gzip), un chunk compartido de 203,91 kB (63,52 kB gzip) y rutas con carga diferida. Son tamaños de archivos generados. Validar transferencia inicial y caché desde navegador, con la topología y compresión productivas.

## 11. Resultados de pruebas

| Verificación | Resultado | Alcance o motivo |
| --- | --- | --- |
| TypeScript | Aprobada | `npm exec --offline -- tsc --noEmit`. |
| ESLint | Aprobada | `npm run lint`. |
| Prettier | Aprobada | `npm run format:check`. |
| Build producción | Aprobada | `npm run build -- --outDir ../target/etapa61-dist`. Build local de Vite 8.3.8. |
| Vitest | Aprobada | 19 archivos y 167 pruebas aprobadas. |
| JUnit y MVC | Aprobada | 302 aprobadas y 0 fallidas. Maven offline con agente de Mockito explícito, URL JDBC a puerto sin servidor, SQL init, Flyway y bootstrap deshabilitados. El primer arranque quedó detenido al adjuntar el agente dinámicamente. |
| Integraciones JUnit/PostgreSQL | Omitidas | 135 casos de 14 clases opt-in, incluidas las cuatro que ejecutarían V032. |
| Playwright simulado | 88 aprobadas, 1 fallida | Corrida serial de 89 casos en 12 archivos, Chromium y Vite local con proxy sin backend. Falló la espera del acceso restringido en Caja para empleado, M-07. En la primera corrida con dos workers hubo cuatro timeouts de interfaz. Los cuatro casos pasaron en la repetición focalizada, cuyo teardown de Vite agotó el tiempo. |
| Playwright con Spring | No ejecutado | 14 escenarios en 12 archivos, incluyendo el login con persistencia de último acceso. Sin una instancia desechable atestada para estos escenarios. |
| Dependencias npm | Aprobada | `npm audit` contra el registro oficial autorizado. Código de salida 0, 384 dependencias contabilizadas y 0 vulnerabilidades conocidas reportadas. La consulta usó el almacén de certificados del sistema con validación TLS activa. |
| Dependencias Maven completas | No ejecutado | Inventario parcial vía BOM y contraste manual con avisos de Spring. Falta escaneo automatizado de transitivos y artefacto final. |
| Restauración, carga, usuarios reales y conciliación | No ejecutadas en esta etapa | Se revisó evidencia histórica de restauración de V032. Los ensayos nuevos requieren copia representativa con aislamiento y datos autorizados. |

## 12. Riesgos pendientes para producción

| Frente | Evidencia necesaria antes de operar |
| --- | --- |
| Ejecución y configuración | Artefactos reproducibles, perfil verificado, inventario de variables, JWT de entropía suficiente y procedimientos de rotación. Verificar `VITE_API_BASE_URL`, origen y reglas de proxy o CORS. Definir fallback SPA sin desviar rutas API. |
| Red y PostgreSQL | HTTPS y cabeceras en el punto de entrada, conexión PostgreSQL protegida según la topología, rol mínimo y red privada. Capacidad y timeouts del pool coherentes con el servidor. |
| Cambios de base | Baseline comprobada en copia restaurada y base limpia. Roles separados, catálogo y V032 verificados al inicio, procedimiento de aprobación, aplicación y reversión. |
| Logs y observabilidad | SQL debug desactivado, redacción de secretos, identificadores de correlación, política de acceso y retención. Alertas sobre 5xx, fallos de autenticación, 409 financieros, conexiones, bloqueos y degradación. No se encontró una configuración de observabilidad productiva en el repo. |
| Recuperación y mantenimiento | Respaldo cifrado y restauración recurrente, RPO/RTO aprobados, criterio para PITR, ensayo de reinicio, plan de mantenimiento y procedimiento de conciliación de caja tras una interrupción. |
| Cadena de suministro | Inventario y resultados de seguridad de frontend, backend, PostgreSQL y sus imágenes finales. Registrar la versión del JDK y parches de plataforma utilizados. |

## 13. Plan de corrección priorizado

1. **P0 · Proteger datos y jornada.** Resolver C-01 y C-02. Versionar el contrato de destino y su compatibilidad, y validar el perfil productivo con cuenta de mínimos privilegios.
2. **P0 · Asegurar los entornos de prueba.** Resolver A-03 y acordar cómo verificar V032 en una base desechable controlada. Hacer que las guardas fallen antes de autenticar o escribir.
3. **P1 · Cerrar recuperación y seguridad.** Resolver A-01, A-02, A-04 y A-05. Probar corte de red antes y después del COMMIT, reinicio, UUID repetido y diferente, solapamiento con cierre y operación entre turnos.
4. **P1 · Acreditar operación.** Resolver M-04 y cerrar la matriz de roles M-05 con Operaciones. Ensayar restauración y arranque del artefacto, confirmar HTTPS y telemetría, y validar conciliación de los dos circuitos.
5. **P2 · Completar experiencia y capacidad.** Atender M-01, M-02 y M-07. Medir M-03, M-06, B-02 y B-03. Resolver B-01 cuando estén definidos los comportamientos de confirmación y recuperación.

**Criterio de liberación.** Cerrar P0 y P1 con evidencia en un entorno representativo y desechable, reconciliar estados, saldos y movimientos, y registrar la aceptación de los riesgos residuales por el responsable operativo.

## 14. Seguimiento de la etapa 6.2

El informe conserva los 17 hallazgos de la línea base 6.1. C-01 y C-02 son críticos, A-01 a A-05 son altos, M-01 a M-07 son medios y B-01 a B-03 son bajos. El plan de §13 asigna P0 a C-01, C-02 y al aislamiento de A-03. El dictamen de liberación sigue bloqueado.

| Hallazgo | Corrección de código | Adopción principal |
| --- | --- | --- |
| C-01 · Jornada de Caja | El cliente omitía el ID guardado en la UI y el servicio elegía la caja abierta durante cada ejecución. Ahora ambos POST manuales exigen `cajaId` como entero JSON positivo. El servidor compara la huella y recupera un éxito antes de la escritura. Para un intento nuevo bloquea esa misma fila, verifica el estado y rechaza con `409 CAJA_JORNADA_CAMBIADA` si quedó obsoleta. | Requiere actualización coordinada de Spring, React y demás consumidores. Conciliar intentos abiertos de la versión anterior. Los datos históricos y V032 son compatibles. |
| C-02 · Arranque y credenciales | Las propiedades anteriores daban al arranque el datasource privilegiado y habilitaban Hibernate e inicializadores de escritura. El arranque normal exige configuración externa y usa Hibernate sin DDL, SQL init deshabilitado y Flyway apagado. La verificación de catálogo y permisos es de lectura. | Pendientes la separación de cuentas, la revisión de ACL efectivas, la verificación de catálogos y la adopción bajo la ventana autorizada. La rotación por exposición aplica si se confirma un incidente. |
| A-03 · Aislamiento | JUnit valida perfil, datasource y clúster antes de Hibernate o `@Sql`. Playwright exige una instancia atestada con nonce por ejecución y la comprueba también a través de su proxy antes del login. El smoke de login requiere su propia bandera. | Los lanzadores de prueba se mantienen fuera del artefacto operativo. |
| M-07 · Historial en frío | La traza de 6.1 mostró carga diferida incompleta antes de evaluar el rol. La guarda de EMPLEADO ahora ocurre en el router, antes de importar el módulo de Caja. Se agregó una prueba de acceso directo que intercepta el chunk y comprueba la ausencia de consultas al listado. | Verificar cargas y observabilidad del historial autorizado en el entorno de puesta en servicio. |

El cambio financiero abarca `RegistrarMovimientoManualRequest`, `MovimientoCajaController`, `RegistroMovimientoCaja`, el mapeo de errores, `IdempotenciaFinanciera` y el cliente y panel de resultado de Caja. El orden de exclusión es clave → caja. `CajaController` y el cierre conservan sus rutas y el bloqueo por ID. Para las huellas V032 anteriores a la corrección se verifica también el ID de la instantánea ya confirmada. No se reescriben filas, V032 ni saldos. Cobros y PagosProveedor mantienen su contrato.

El cambio de arranque abarca las propiedades comunes, `dev` y `prod`, `VerificacionCatalogoPostgresql` y el traslado de los inicializadores de esquema y catálogos a las fuentes de prueba. Las guardas de integración viven en `EsquemaPostgresql`, `IntegracionPostgresql`, `SpringE2e` y la configuración de Playwright. La revisión posterior del commit histórico confirmó que la contraseña JDBC se resolvía mediante la variable de entorno `DB_PASSWORD`, sin valor por defecto. Ese commit no contiene una contraseña literal ni acredita una exposición de la credencial. El procedimiento de rotación, el inventario de permisos, los GRANT y los pasos de despliegue están documentados. [Configuración de producción](seguridad-configuracion-produccion.md)

La protección V032 sigue ligada a usuario y UUID. Un rollback libera la clave. Un cliente que envía una nueva intención explícita con otros datos tras el rollback queda sujeto al destino que declare y al control de estado de esa caja. Los clientes sin cabecera conservan el comportamiento opcional de V032. La recarga sigue exigiendo conciliación cuando se perdió el payload, A-01. El reporte de 6.1 conserva las demás prioridades.

### Evidencia en el entorno aislado

| Verificación | Resultado |
| --- | --- |
| Backend JUnit, MVC y PostgreSQL 17.6 | 473 aprobadas, 0 fallos y 0 omitidas en la ejecución final con un clúster desechable de loopback y locale ICU `es-AR`. La corrida anterior tuvo un timeout de conexión en el reinicio de Insumos. La repetición focalizada y la corrida final lo superaron. |
| Caja, concurrencia e idempotencia | En JUnit, tras revertir un intento de A, cerrarla y abrir B, el reintento obsoleto fue rechazado. A quedó en 120,00 con un solo ingreso y B en 70,00 sin movimientos. El replay confirmado recuperó el mismo ID, cuerpo y `Location` tras reiniciar Spring. El replay V032 histórico funcionó para su caja y rechazó el ID de otra. Dos egresos de 70,00 con efectivo inicial de 100,00 dejaron un asiento y 30,00. Una segunda solicitud simultánea con la misma clave recibió `IDEMPOTENCIA_EN_CURSO`. |
| Caja real en Playwright | Pasó apertura, pérdida de respuesta después del commit, reintento de la misma clave y cuerpo, egreso, arqueo, consulta histórica y cambio de caja. A mantuvo saldo de 120,25, dos movimientos y diferencia de arqueo de −2,05. B mantuvo 70,00 y sin movimientos después del rechazo y de la recuperación del ingreso de A. |
| Perfil operativo | Dos arranques con perfil `prod` y el rol no propietario de la plantilla de permisos. SHA-256 del volcado de esquema idéntico antes y después. Se comprobaron SELECT, INSERT y UPDATE sobre Caja, SELECT e INSERT sobre V032, y la ausencia de UPDATE y DELETE sobre movimientos y V032. |
| Artefacto Spring | Empaquetado local e inspección del JAR aprobados. Los tres archivos de configuración carecen de credenciales literales y el JAR excluye el aprovisionamiento de prueba. El primer empaquetado falló por dependencias faltantes fuera de línea y el siguiente por la cadena TLS. Se completó con el almacén de certificados de Windows y TLS validado. |
| Frontend | TypeScript, ESLint, Prettier y build aprobados. Vitest aprobó 169 pruebas, incluida la validación de la caja que devuelve un `201` y la conservación del envío manual. |
| Reportes real en Playwright | Pasó el escenario que registra datos sintéticos con el contrato actualizado y consulta los tres reportes con Spring y PostgreSQL aislados. |
| Playwright simulado | La corrida de Auth, Caja, Cobros, PagosProveedor y Reportes terminó con 44 aprobadas y 1 fallida. El selector del enlace de Caja en el escenario de Pagos esperaba un texto que el Dashboard ya no muestra. Se ajustó el selector y pasó la repetición focalizada. Las suites reales de otros módulos quedan fuera de la ejecución de esta etapa. |
| Historial de Caja | Las primeras trazas de esta etapa también mostraron cargas detenidas, esta vez en el módulo del layout antes de evaluar la ruta. Los mismos recursos estáticos respondieron HTTP 200 al consultarlos por separado. El origen de esa demora adicional no quedó demostrado. La guarda quedó fuera del módulo diferido de Caja. La prueba pasó tres veces con el chunk de Caja interceptado, sin solicitarlo ni consultar el historial. Playwright también rechazó un destino Spring fuera de la configuración aislada antes de ejecutar la suite. |
| Dependencias Maven | OSV-Scanner v2.7.1 comparó 140 dependencias efectivamente resueltas, incluidas las de prueba, con la copia local del feed Maven. Reportó cero coincidencias. La descarga del binario y del feed usó solicitudes públicas a GitHub y Google Cloud Storage. El árbol de dependencias, el código y los secretos quedaron locales. Los avisos del proveedor de A-05 siguen sujetos a contraste y mitigación. [Modo sin conexión de OSV](https://github.com/google/osv-scanner/blob/main/docs/offline-mode.md) |

La base principal quedó fuera de la verificación. Se requiere la adopción autorizada y la comprobación de los permisos efectivos, el catálogo y el flujo operativo del entorno principal antes de considerar cerrada la salida a producción.
