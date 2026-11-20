# Mira que te lavo · Frontend

SPA interna para administradores y empleados con inicio de sesión, panel, gestión de clientes, pedidos y catálogos de servicios y prendas. Spring Boot conserva las reglas de negocio y los permisos.

## Desarrollo local

Requisitos: Node 24 LTS con npm, Java 21, PostgreSQL y una cuenta activa de Spring Boot. Versiones exactas de las dependencias en [package-lock.json](package-lock.json).

1. Iniciá PostgreSQL con la base `lavanderia_db`. Abrí PowerShell en `C:\lavanderia` con `DB_PASSWORD` y `LAVANDERIA_JWT_SECRET` cargadas desde tu entorno. Si falta el primer usuario, seguí el procedimiento de bootstrap del [README del backend](../README.md#primer-administrador).
2. En esa terminal iniciá Spring Boot.

   ```powershell
   .\mvnw.cmd spring-boot:run
   ```

3. En una segunda terminal iniciá Vite.

   ```powershell
   cd C:\lavanderia\frontend
   npm ci
   npm run dev
   ```

4. Abrí http://localhost:5173 e ingresá con la cuenta interna. El proxy de desarrollo envía `/api` a `http://localhost:8080`.

Copiá [.env.example](.env.example) a `.env.local` si necesitás cambiar la configuración.

| Variable                       | Predeterminado          | Uso                                                                                                       |
| ------------------------------ | ----------------------- | --------------------------------------------------------------------------------------------------------- |
| `VITE_API_BASE_URL`            | `/api`                  | Prefijo API del navegador. Se incorpora durante el build.                                                 |
| `API_PROXY_TARGET`             | `http://localhost:8080` | Destino del proxy de Vite en desarrollo y preview.                                                        |
| `E2E_USERNAME`, `E2E_PASSWORD` | Sin valor               | Variables del proceso de Playwright para la prueba opcional contra Spring Boot. Usá una cuenta de prueba. |

En producción serví `dist` con fallback a `index.html` para las rutas SPA y con un proxy de `/api` hacia Spring Boot. Para API en otro origen, fijá `VITE_API_BASE_URL` a su URL con el prefijo `/api` y agregá el origen exacto de la SPA a `LAVANDERIA_CORS_ORIGINS`. Si el proxy cambia el host o el esquema que ve Spring, agregá también el origen público a esa lista. Usá HTTPS. Las variables `VITE_*` son públicas.

## Arquitectura

| Carpeta                  | Responsabilidad                                                                                        |
| ------------------------ | ------------------------------------------------------------------------------------------------------ |
| `src/app/providers`      | QueryClient, sesión y notificaciones Sonner.                                                           |
| `src/app/router`         | Rutas y catálogo de navegación por rol.                                                                |
| `src/app/layouts`        | AppLayout, Sidebar, Header, Breadcrumbs, UserMenu.                                                     |
| `src/features/auth`      | LoginPage, validación, contratos y sesión en memoria.                                                  |
| `src/features/dashboard` | Panel, claves de consulta y métricas respaldadas por la API.                                           |
| `src/features/clientes`  | Listado, detalle, alta, edición y desactivación lógica de clientes.                                    |
| `src/shared/api`         | fetch, errores, filtros, señales de cancelación y `PagedResponse<T>`.                                  |
| `src/shared/components`  | PageHeader, StatCard, Brand, estados de feedback, FormField, DataTable, Button, Input y ConfirmDialog. |
| `src/styles`             | Tokens, Tailwind y estilos de la interfaz.                                                             |

React y TypeScript estricto sobre Vite. React Router para navegación, TanStack Query para datos remotos, React Hook Form y Zod para formularios, TanStack Table para la tabla y Lucide para iconos. Los componentes de shadcn/ui se mantienen como código local, con primitivas Radix para diálogos y menús. Inter se sirve con los assets del build. ESLint y Prettier están configurados por proyecto.

Para incorporar un módulo, creá `src/features/<módulo>` cuando tenga código, mantené los DTO junto a la funcionalidad y usá claves de TanStack Query prefijadas por módulo e identificadas por filtros. Pasá la señal de TanStack al cliente HTTP. Agregá la entrada al catálogo de navegación y contrastá los permisos de cada acción con Spring Security. El [inventario de la API](AUDITORIA-API.md) enlaza los contratos fuente.

## Rutas y sesión

`/login`, `/` y las rutas de Clientes, Proveedores, Insumos, Categorías de insumos, Servicios, Prendas, Pedidos, Caja, Cobros, Compras, Pagos a proveedores, Comprobantes y Reportes están implementadas. `/reportes` requiere ADMINISTRADOR. `/administracion` sigue pendiente y también requiere ADMINISTRADOR. Ambos roles pueden entrar a los catálogos. Las escrituras de catálogos requieren ADMINISTRADOR.

La sesión se activa después de obtener el token y consultar `GET /api/auth/me`. El JWT vive exclusivamente en memoria y se elimina al cerrar sesión, vencer o recibir un 401 autenticado. Recargar o abrir otra pestaña exige volver a ingresar. El cierre de sesión es local porque la API carece de endpoint de logout. El 403 conserva la sesión y muestra acceso restringido. El perfil y la caché de consultas se descartan al cerrar o expirar la sesión. Las contraseñas se usan únicamente durante el envío del formulario.

El panel usa los totales paginados de clientes activos y pedidos registrados. Caja se consulta mediante `GET /api/cajas/abierta`. Un 404 representa ausencia de caja abierta. No se calculan métricas financieras.

## Clientes

| Ruta                                | Función                                                                           | Autorización  |
| ----------------------------------- | --------------------------------------------------------------------------------- | ------------- |
| `/clientes`                         | Listado de activos con paginación, tamaño, orden y búsqueda en la página visible. | Ambos roles   |
| `/clientes/nuevo`                   | Alta.                                                                             | Ambos roles   |
| `/clientes/:id`                     | Detalle y estado.                                                                 | Ambos roles   |
| `/clientes/:id/editar`              | Edición con precarga y confirmación al salir con cambios.                         | Ambos roles   |
| Acción **Desactivar** en el listado | Baja lógica con confirmación.                                                     | ADMINISTRADOR |

`features/clientes/api` consume `GET` y `POST /api/clientes` y `GET`, `PUT` y `DELETE /api/clientes/{id}`. `types/` describe los contratos, `schemas/` valida la entrada y `hooks/` concentra las claves y mutaciones de TanStack Query, incluida la invalidación del total activo que usa el panel. `pages/` contiene `ClientesPage` y las pantallas configuradas en `ClienteRoutes`. `components/` contiene el formulario compartido `ClienteForm`, `EstadoCliente` y la presentación de errores. La navegación usa el router de datos de React Router para proteger el formulario frente a cambios de ruta y al botón Atrás.

Nombre y apellido son obligatorios, con un máximo de 100 caracteres cada uno. El DNI es obligatorio, tiene siete u ocho dígitos sin separadores y es único incluso entre clientes inactivos. Teléfono y correo son opcionales, con máximos de 30 y 254 caracteres. El correo debe tener formato válido. DNI y datos de contacto se pueden editar. ID, estado y fecha de alta quedan fuera del formulario. Los errores de validación de Spring se muestran junto a su campo y un conflicto de DNI se asocia a ese campo.

La API permite ordenar y paginar activos, pero carece de filtros por campo y búsqueda global. La búsqueda se aplica a la página descargada, con 300 ms de espera y comparación sin distinguir mayúsculas ni tildes. Los inactivos quedan fuera del listado y del detalle público del módulo. La API carece de reactivación y de un listado de inactivos.

### Prueba manual

1. En una instancia de pruebas, ingresá como EMPLEADO y verificá el listado, cambio de página, tamaño, orden y búsqueda en la página visible.
2. Registrá un cliente de prueba y verificá el detalle. Intentá guardar un DNI con separadores y, luego, un DNI ya registrado para comprobar la validación local y el mensaje de Spring.
3. Abrí la edición, cambiá un dato y usá Atrás o Cancelar. Elegí permanecer, guardá y volvé al listado para comprobar el cambio.
4. Ingresá como ADMINISTRADOR, cancelá una primera confirmación de baja y confirmá la segunda. La fila debe desaparecer, el total debe bajar y la URL del detalle debe mostrar «Cliente no encontrado». EMPLEADO debe poder editar y carecer de la acción Desactivar.

## Insumos y categorías · Etapa 4.2

| Ruta                                                        | Función                                           | Permiso                                                           |
| ----------------------------------------------------------- | ------------------------------------------------- | ----------------------------------------------------------------- |
| `/insumos`, `/insumos/:id`                                  | Listado, filtro por categoría, paginación y ficha | ADMINISTRADOR, EMPLEADO                                           |
| `/insumos/categorias`, `/insumos/categorias/:id`            | Listado completo de categorías activas y ficha    | ADMINISTRADOR, EMPLEADO                                           |
| `/nuevo`, `/:id/editar` y `/reactivar` bajo ambos catálogos | Alta, edición y reactivación por ID               | ADMINISTRADOR                                                     |
| `/proveedores/:id`                                          | Insumos suministrados con paginación de servidor  | Ambos roles para lectura, ADMINISTRADOR para asociar y desasociar |

**Por página** admite 10, 20 y 50. Spring filtra por `categoriaId` y ordena por ID ascendente. **Nombre A–Z** ordena la página visible y el filtro se conserva en la URL al editar o volver desde la ficha. En la ficha del proveedor, el selector navega por páginas del catálogo activo, conserva el ID al cambiar de página y excluye los insumos ya asociados. Consultá los [contratos de esta etapa](AUDITORIA-API.md#contrato-de-insumos-y-categorías).

### Prueba manual

1. Con una instancia de pruebas y ADMINISTRADOR, abrí **Insumos → Categorías de insumos**. Creá una categoría de prueba, abrí su ficha, anotá el ID, editá el nombre y probá un alta del mismo nombre en mayúsculas.
2. Volvé a **Insumos** y creá un insumo en esa categoría. Probá el formulario vacío y editá el nombre o la categoría. Volvé al listado, filtrá por categoría y, con más de 10 resultados, comprobá la segunda página y el orden local.
3. Abrí la ficha de un proveedor activo. En **Insumos suministrados**, elegí **Asociar insumo**. Seleccioná una opción, cambiá de página y verificá que siga seleccionada. Confirmá. Verificá que aparezca en el listado y que se retire de las opciones disponibles.
4. Intentá desactivar la categoría con el insumo activo y verificá el conflicto. Anotá el ID del insumo y desactivalo. La relación deja de verse en la ficha del proveedor. Desactivá su categoría y probá reactivar primero el insumo. Tras el conflicto, reactivá la categoría y luego el insumo por ID. La relación vuelve a verse. Usá **Desasociar** y confirmá para retirarla.
5. Repetí la consulta con EMPLEADO. Comprobá el listado y las fichas, y abrí directamente una URL de edición para verificar el acceso restringido. En ancho móvil, recorré los controles y la tabla con Tab.

### Playwright con Spring Boot y PostgreSQL aislados

Prepará un clúster PostgreSQL 17.6 desechable en loopback (por ejemplo, `55542`) y restaurá una copia de solo estructura que incluya el esquema vigente y los índices únicos del catálogo. Iniciá Spring en `127.0.0.1:18097` conectado exclusivamente a la base desechable, con `ddl-auto=validate`, Flyway deshabilitado, JWT de pruebas y un administrador sintético. Verificá el catálogo JDBC y la versión en el log antes de habilitar las escrituras. Cargá `E2E_USERNAME` y `E2E_PASSWORD` con las credenciales del administrador aislado.

```powershell
$env:API_PROXY_TARGET = 'http://127.0.0.1:18097'
$env:E2E_INSUMOS_ISOLATED = '1'
$env:E2E_PORT = '5297'
npm run test:e2e -- e2e/insumos.spring.spec.ts --workers=1 --output=../target/insumos-playwright
```

La prueba usa datos sintéticos y comprueba el destino del proxy en el navegador antes de las escrituras desde React. Para la suite con respuestas interceptadas, ejecutá `npm run test:e2e -- e2e/insumos.spec.ts`.

Para Compras, `useProveedores` consulta proveedores activos y `opcionesInsumosSuministrados` expone consultas paginadas por proveedor con los IDs, nombres y categoría vigentes. Los datos comerciales disponibles en la ficha del proveedor son CUIT, teléfono y rubro. El precio de compra pertenece a cada detalle de compra.

## Proveedores · Etapa 4.1

| Ruta                                            | Funcionalidad                                                                       | Roles                   |
| ----------------------------------------------- | ----------------------------------------------------------------------------------- | ----------------------- |
| `/proveedores`                                  | Tabla de activos con filtros de servidor y paginación real                          | ADMINISTRADOR, EMPLEADO |
| `/proveedores/:id`                              | Ficha del proveedor activo e insumos suministrados                                  | ADMINISTRADOR, EMPLEADO |
| `/proveedores/nuevo`, `/proveedores/:id/editar` | Alta y edición con validación, errores por campo y protección de cambios pendientes | ADMINISTRADOR           |
| `/proveedores/reactivar`                        | Reactivación por ID con confirmación                                                | ADMINISTRADOR           |

El listado admite tamaño 10, 20 o 50. **Aplicar filtros** combina Nombre, CUIT, Teléfono y Rubro en Spring y vuelve a la primera página. Los filtros y la página se conservan al volver desde la ficha. Spring ordena por ID ascendente. **Nombre A–Z** reordena únicamente la página visible. El [contrato de proveedores](AUDITORIA-API.md#contrato-de-proveedores) detalla los DTO, validaciones, permisos y relaciones.

El alta y la edición admiten Nombre, CUIT, Teléfono y Rubro opcional. El ID lo asigna Spring y el estado se gestiona con las acciones de baja y reactivación. La baja conserva las compras y asociaciones, y oculta el proveedor del listado y del detalle. Guardá el ID de la ficha o usá el acceso **Reactivar** de la notificación de baja. La API requiere ese número para reactivar y no ofrece un listado de inactivos.

### Prueba manual

1. Ingresá como ADMINISTRADOR en una instancia de pruebas. Abrí **Proveedores** desde el menú. Aplicá filtros, cambiá el tamaño de página y verificá el orden local con varios resultados.
2. Registrá un proveedor con datos sintéticos. Probá primero un CUIT con verificador inválido y luego uno válido. Verificá la confirmación, el CUIT sin guiones y el estado **Activo** en la ficha.
3. Editá la ficha y elegí **Cancelar** tras cambiar un campo. Rechazá el descarte y comprobá que el dato siga en el formulario. Guardá y verificá el cambio en la ficha y en el listado.
4. Anotá el ID. Desactivá con confirmación y verificá que la ficha directa muestre **Proveedor no encontrado**. Intentá un alta con el mismo CUIT para comprobar el conflicto aun durante la baja. Elegí **Reactivar proveedor**, ingresá el ID, confirmá y verificá que el registro vuelva al listado con el mismo ID.
5. Ingresá como EMPLEADO. Verificá listado y detalle. Confirmá que las acciones administrativas estén ocultas y que las URL de alta, edición y reactivación muestren **Acceso restringido**.

### Playwright con Spring Boot aislado

Cada suite real exige el lanzador aislado. Consultá [aislamiento de Playwright con Spring](#aislamiento-de-playwright-con-spring).

```powershell
$env:API_PROXY_TARGET = 'http://127.0.0.1:18096'
$env:E2E_PROVEEDORES_ISOLATED = '1'
# Cargá E2E_USERNAME y E2E_PASSWORD del administrador sintético.
npm run test:e2e -- --workers=1 --output=../target/proveedores-playwright -g 'Proveedores con Spring Boot aislado'
```

La prueba real crea proveedores sintéticos y una cuenta de empleado en la instancia aislada. Verifica alta, edición, paginación, filtros, CUIT duplicado durante la baja, reactivación y el 403 de las cuatro escrituras bajo EMPLEADO. Para ejecutar solo las pruebas con respuestas interceptadas, usá `npm run test:e2e -- e2e/proveedores.spec.ts`. Vitest cubre el cálculo del dígito verificador, longitudes, campos obligatorios y el rango del ID.

Si Windows deja Playwright esperando al cerrar Vite, iniciá una vista previa por separado como en [el E2E de Pedidos](#e2e-de-pedidos-con-escritura). Usá el destino de Proveedores del bloque anterior y el mismo `E2E_PORT` en ambas terminales. La prueba comprueba que la vista previa lee los registros recién creados en Spring aislado antes de enviar escrituras desde el navegador.

## Servicios y prendas

| Ruta (base `/servicios` o `/prendas`) | Función                                                                              | Autorización  |
| ------------------------------------- | ------------------------------------------------------------------------------------ | ------------- |
| Base                                  | Listado de activos, orden y paginación de Spring, filtro de la página visible.       | Ambos roles   |
| `/:id`                                | Detalle del registro activo.                                                         | Ambos roles   |
| `/nuevo` y `/:id/editar`              | Alta y edición con errores por campo y confirmación al salir con cambios pendientes. | ADMINISTRADOR |
| `/reactivar`                          | Reactivación con ID conocido y confirmación.                                         | ADMINISTRADOR |
| Acción **Desactivar** en el listado   | Baja lógica con confirmación que muestra el ID.                                      | ADMINISTRADOR |

Cada módulo agrupa su cliente REST, tipos, esquema, consultas y configuración de pantallas bajo `src/features/servicios` o `src/features/prendas`. `src/shared/catalogos` comparte las vistas de listado, detalle, alta/edición y reactivación, el cliente de catálogos construido sobre el cliente HTTP de la sesión y la actualización de caché. Los endpoints y sus estados están en la [auditoría de la API](AUDITORIA-API.md#contrato-de-servicios-y-prendas).

Servicio requiere nombre (hasta 100 caracteres), precio positivo (hasta 8 dígitos enteros y 2 decimales, con coma o punto en el formulario) y modalidad `UNIDAD`, `KILO` o `PRENDA`. Prenda solo requiere nombre (hasta 50 caracteres). El servidor aplica su propia normalización. Si devuelve el `409` de nombre duplicado, el formulario lo muestra junto a **Nombre**, también si el registro anterior está inactivo o solo cambia el uso de mayúsculas.

El filtro **Filtrar página actual** consulta el nombre en las filas recibidas. Spring carece de búsqueda global, filtros de colección por campos o estado, listado de inactivos y consulta de inactivos por ID. Para reactivar, conservá el ID del detalle o de la confirmación de baja.

### Prueba manual

1. Ingresá a una instancia de pruebas como EMPLEADO. Abrí Servicios y Prendas desde el menú. Probá el orden por nombre, el tamaño de página, Anterior/Siguiente si hay suficientes registros, el filtro local y el detalle. Abrí directamente `/servicios/nuevo` y `/prendas/reactivar` para comprobar el acceso restringido.
2. Ingresá como ADMINISTRADOR. Creá un servicio y una prenda de prueba. Probá guardar con campos vacíos y con un precio inválido. Editá un dato, intentá salir y elegí permanecer antes de guardar.
3. Intentá registrar el mismo nombre con otras mayúsculas. Comprobá el error debajo de **Nombre**. Desactivá el registro, conservá su ID y verificá que desaparezca del listado y que el detalle indique que el registro no se encuentra. Repetí el intento de alta para comprobar que el nombre sigue reservado.
4. Abrí **Reactivar servicio** o **Reactivar prenda**, ingresá el ID y confirmá. Comprobá que la pantalla muestre el detalle y que el registro vuelva al listado. Probá también un ID inexistente.
5. En una ventana estrecha, recorré los controles con Tab y confirmá una acción con Enter. La tabla se puede desplazar dentro de su región.

### Consumo desde Pedidos

`useServiciosDisponibles` y `usePrendasDisponibles` consultan las páginas de activos. `SelectorCatalogo` recibe las opciones de la página visible y devuelve el ID numérico seleccionado. Mantiene la selección cuando el registro queda fuera de la página actual. Las altas, ediciones, bajas y reactivaciones invalidan las mismas consultas. PedidoForm envía `servicioId` y `prendaId`, ofrece paginación para las opciones y muestra el error de Spring si una opción deja de estar disponible. El precio del renglón, la cantidad según modalidad y la vigencia de las referencias los decide Spring Boot.

## Pedidos

| Ruta                  | Función                                                                                                                     | Autorización                                             |
| --------------------- | --------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------- |
| `/pedidos`            | Lista paginada en Spring con orden por ID, ingreso, entrega o total y filtros por ID de cliente, estado y rango de ingreso. | Ambos roles                                              |
| `/pedidos/nuevo`      | Alta con cliente activo, entrega estimada, renglones dinámicos y confirmación.                                              | Ambos roles                                              |
| `/pedidos/:id`        | Ficha, importes confirmados, saldo de solo lectura, historial y cambios de estado disponibles.                              | Ambos roles                                              |
| `/pedidos/:id/editar` | Edita la fecha de entrega y los renglones con precarga, revisión y protección de cambios sin guardar.                       | Ambos roles, si el pedido no es terminal ni tiene cobros |

Los selectores muestran diez activos por página ordenados por ID. Usá **Anterior** y **Siguiente** para recorrer clientes, prendas y servicios. La opción elegida conserva su ID y su nombre aunque navegues a otra página. El filtro del listado usa el ID de cliente exacto. La API carece de búsqueda global de pedidos o catálogos.

El formulario acepta coma o punto decimal para cantidades. Los servicios por kilo admiten hasta dos decimales y los demás requieren cantidades enteras. Spring comprueba referencias vigentes, calcula subtotales y total, y conserva el precio histórico de renglones con la misma prenda y servicio. La interfaz muestra los importes confirmados en pesos argentinos. La fecha mínima del formulario usa Buenos Aires, que es la zona predeterminada de Spring.

El alta comienza en **Pendiente**. El flujo operativo es **Pendiente → En proceso → Listo para entregar → Entregado**. La cancelación queda disponible antes de **Listo para entregar**, sin cobros y con motivo obligatorio. La ficha actualiza el historial tras cada transición. Si las condiciones cambian mientras el pedido está abierto, el servidor puede devolver un conflicto y la interfaz vuelve a consultar el pedido.

Durante el envío los controles se bloquean y la mutación se ejecuta una sola vez por confirmación. Ante una desconexión, un timeout de 30 s o una respuesta sin confirmación, verificá en el listado o la ficha antes de repetir. Una pestaña nueva requiere volver a ingresar por la sesión en memoria. El bloqueo local no cubre solicitudes emitidas desde distintas pestañas o dispositivos ni reemplaza la idempotencia de servidor.

### Prueba manual de Pedidos

1. Iniciá una instancia de pruebas y prepará un cliente activo, una prenda y un servicio. ADMINISTRADOR puede preparar los catálogos y ambos roles pueden operar pedidos.
2. Ingresá como EMPLEADO, abrí **Pedidos → Nuevo pedido**, seleccioná un cliente y una fecha de entrega. Agregá varios renglones. Probá omitir una prenda e ingresar una cantidad inválida para comprobar los mensajes por renglón.
3. Completá los renglones, quitá uno y elegí **Revisar pedido → Guardar pedido**. En la ficha verificá el precio unitario, total, estado inicial, saldo y el evento **Pedido registrado**.
4. Abrí **Editar pedido**. Cambiá una cantidad, intentá salir y elegí permanecer. Confirmá la edición y verificá el total actualizado. Cambiá el precio del servicio como ADMINISTRADOR y repetí la edición de cantidad para comprobar la conservación del precio histórico.
5. Avanzá el pedido hasta **Entregado** y verificá cada evento. Con otro pedido, probá **Cancelado** con y sin motivo. Comprobá que los estados terminales esconden las acciones de edición y cambio de estado.
6. Volvé al listado, filtrá por el ID de cliente de la ficha y por el estado final. Probá la paginación con más de 20 resultados. Repetí altas y transiciones con ADMINISTRADOR.

El [contrato auditado](AUDITORIA-API.md#contrato-de-pedidos) contiene los DTO, reglas y códigos HTTP. La ficha también permite consultar el historial y registrar un cobro cuando el saldo, el estado del pedido y la caja lo permiten.

### E2E de Pedidos con escritura

Cada suite real exige el lanzador aislado. Consultá [aislamiento de Playwright con Spring](#aislamiento-de-playwright-con-spring).

En `C:\lavanderia\frontend` cargá `E2E_USERNAME` y `E2E_PASSWORD` de ese administrador y ejecutá el siguiente comando.

```powershell
$env:API_PROXY_TARGET = 'http://127.0.0.1:18093'
$env:E2E_PEDIDOS_ISOLATED = '1'
npm run test:e2e -- --workers=1 -g 'Pedidos con Spring Boot aislado'
```

La configuración verificada en Windows usa un solo worker y puertos separados para Vite, Spring Boot y PostgreSQL. Para las suites reales, Playwright inicia su propia vista previa y verifica el proxy antes del login.

```powershell
$env:API_PROXY_TARGET = 'http://127.0.0.1:18093'
npx vite build --mode e2e
npx vite preview --mode e2e --host 127.0.0.1 --port 5183 --strictPort
```

En suites con Spring, Playwright administra su vista previa y comprueba el aislamiento antes de enviar credenciales. Cerrá el lanzador al terminar.

Los E2E reales cubren ambos roles, alta por kilo, edición con precio histórico, saldo, historial, entrega, cancelación y rechazo de transiciones inválidas. Las pruebas con respuestas interceptadas cubren además paginación, orden, filtros, validación por fila, opciones obsoletas, cambios sin guardar, doble envío, resultado incierto, errores 401/403/404/409, restricciones con cobros y accesibilidad móvil básica. Vitest cubre validación de cantidades, permisos, transiciones, importes, errores y el formulario dinámico.

## Validación

```powershell
npx tsc --noEmit
npm run lint
npm run format:check
npm test
npm run build
npx playwright install chromium
npm run test:e2e
```

Vitest y Testing Library cubren autenticación, cliente HTTP, esquemas de Clientes, Servicios y Prendas, y el selector de catálogo. Playwright verifica listados, formularios, permisos, carga, errores y accesibilidad mediante respuestas interceptadas en el navegador. Los tests existentes de login y navegación siguen incluidos.

Para el login real, seguí el [procedimiento de aislamiento](#aislamiento-de-playwright-con-spring) con `E2E_LOGIN_ISOLATED=1` y puerto `18053` y ejecutá `npm run test:e2e -- e2e/spring.spec.ts`. Revisá aparte con ambos roles el menú y la apertura directa de Reportes.

### E2E de Clientes con escritura

Cada suite real exige el lanzador aislado. Consultá [aislamiento de Playwright con Spring](#aislamiento-de-playwright-con-spring).

En la terminal del frontend apuntá a esa instancia. La prueba automatizada de escritura requiere habilitación explícita y este destino local.

```powershell
$env:API_PROXY_TARGET = 'http://127.0.0.1:18091'
$env:E2E_CLIENTES_ISOLATED = '1'
# Cargá E2E_USERNAME y E2E_PASSWORD del administrador de este clúster.
npm run test:e2e -- -g 'Clientes con Spring Boot aislado'
```

La prueba comprueba alta `201`, edición `200`, DNI duplicado `409`, baja `204` y detalle posterior `404` contra la API real. El registro de prueba queda inactivo en la base desechable. Ejecutá este flujo únicamente contra el clúster de pruebas.

### E2E de catálogos con escritura

Cada suite real exige el lanzador aislado. Consultá [aislamiento de Playwright con Spring](#aislamiento-de-playwright-con-spring).

En la terminal de frontend, cargá `E2E_USERNAME` y `E2E_PASSWORD` de ese administrador.

```powershell
$env:API_PROXY_TARGET = 'http://127.0.0.1:18092'
$env:E2E_CATALOGOS_ISOLATED = '1'
npm run test:e2e -- -g 'Catálogos con Spring Boot aislado'
```

Las pruebas crean registros y empleados únicos dentro de la base desechable. Comprueban alta, edición, conflicto con un nombre inactivo, baja, reactivación, 404, lectura con EMPLEADO y respuesta 403 de Spring para las cuatro escrituras administrativas. El flujo sin la variable de habilitación ejecuta las pruebas de navegador con respuestas interceptadas.

## Etapa 2

1. **Clientes**. Listado, detalle, alta, edición y baja administrativa implementados.
2. **Servicios y prendas**. Listado, detalle, alta, edición, baja y reactivación por ID implementados. Consultas paginadas y selector reutilizables desde Pedidos.
3. **Pedidos**. Listado, alta, edición y seguimiento implementados para ambos roles.

## Referencias

- [Vite](https://vite.dev/guide/), [shadcn/ui con Vite](https://ui.shadcn.com/docs/installation/vite)
- [React Router](https://reactrouter.com/start/declarative/installation), [TanStack Query](https://tanstack.com/query/latest/docs/framework/react/installation), [Vitest](https://vitest.dev/guide/)

## Caja y movimientos · Etapa 3.3

- `/caja` muestra el estado devuelto por Spring Boot, saldo general, efectivo y última consulta, con historial por caja. Los movimientos se distinguen por sentido y admiten filtros de tipo, método de pago y fechas, paginación de servidor y ficha de detalle.
- ADMINISTRADOR y EMPLEADO pueden consultar, abrir una caja si no hay otra abierta y registrar ingresos o egresos manuales en la caja actual. ADMINISTRADOR también puede navegar el historial global de cajas y cerrar una caja abierta registrando el arqueo.
- La apertura y los movimientos manuales requieren revisión y confirmación. Cada envío nuevo lleva un UUID. Después de un éxito se muestra el resultado de Spring y se refrescan Caja, Dashboard, saldos e historial. La primera página de movimientos muestra los más recientes. Los filtros y la paginación dan acceso al historial completo. [Auditoría de Caja](AUDITORIA-API.md#contrato-de-caja-y-movimientos--etapa-33)

### Prueba manual

1. Usá Spring Boot conectado a PostgreSQL desechable con V032 presente y configurá el proxy de Vite. Ingresá como EMPLEADO en `/caja` sin caja abierta. Ingresá 100,00, elegí **Revisar apertura**, abrí el diálogo y confirmá. Verificá el número de caja y que el Dashboard muestre **Abierta**.
2. Continuá con **Ingreso manual**, elegí Efectivo, ingresá 25,50 y un concepto de prueba. Revisá y confirmá. Verificá que saldo general y efectivo sean 125,50 y que el historial muestre un solo movimiento. Desde **Egreso manual**, probá 125,51 en efectivo para comprobar el límite y registrá 5,25 con otro concepto. Los dos saldos deben quedar en 120,25. Los cobros de pedidos se registran en Cobros y los pagos a proveedores tienen su propio flujo.
3. Verificá que EMPLEADO carezca de acceso al listado global de cajas y de la acción de cierre. Ingresá como ADMINISTRADOR, usá **Cierre y arqueo**, contá 118,20 y confirmá el cierre. El Dashboard debe mostrar **Sin caja abierta**. En **Historial de cajas**, verificá estado **Cerrada**, 120,25 esperado, 118,20 contado, diferencia −2,05 y ambos movimientos.

### Recuperar un envío de Caja

- Ante una respuesta perdida, volvé a Caja en la misma pestaña y con la misma cuenta. **Reintentar el mismo envío** conserva el UUID y el contenido original y respeta `Retry-After`. El movimiento conserva también el ID original de caja. Si esa jornada terminó y el envío no llegó a confirmarse, Spring devuelve un conflicto de jornada. Si ya estaba confirmado, devuelve el registro original. La interfaz actualiza saldos y movimientos después de resolver el envío.
- Tras recargar, la pestaña conserva un aviso por cuenta y, para un movimiento, el ID de la caja consultada. El UUID y los datos financieros viven en memoria. Actualizá Caja y revisá los movimientos. Si hace falta, consultá el historial con un administrador. Usá **Ya concilié el envío** solo cuando hayas determinado qué ocurrió. La misma revisión se requiere ante un UUID reutilizado. Cerrar la pestaña o cambiar de dispositivo puede eliminar el aviso.
- Si se interrumpe la respuesta de un cierre, usá **Consultar estado actualizado** y revisá la caja por ID. El arqueo se registra mediante el cierre. [Contrato financiero](../docs/idempotencia-financiera.md)

### Playwright con Spring Boot aislado

Iniciá el lanzador con el puerto `18094` siguiendo el [procedimiento de aislamiento](#aislamiento-de-playwright-con-spring). Usá un esquema nuevo para cada ejecución.

```powershell
$env:API_PROXY_TARGET = 'http://127.0.0.1:18094'
$env:E2E_CAJA_ISOLATED = '1'
# Cargá E2E_USERNAME y E2E_PASSWORD del administrador sintético.
npm run test:e2e -- --workers=1 e2e/caja.spring.spec.ts
```

Playwright comprueba la apertura desde React, la recuperación de un ingreso cuya respuesta se descarta después de registrar el movimiento, el egreso, la actualización del Dashboard e historial, los permisos y el cierre con arqueo. Las pruebas con respuestas interceptadas cubren además timeout, doble clic y conflictos de idempotencia.

## Cobros de pedidos · Etapa 3.2B

**Cobros** permite seleccionar un pedido y consultar su historial paginado. También podés abrir el módulo desde la ficha de Pedidos. ADMINISTRADOR y EMPLEADO pueden consultar y registrar importes parciales o completar el saldo cuando hay una caja abierta y el pedido admite cobros. El formulario muestra el saldo confirmado por Spring, los métodos de pago activos y una revisión antes de confirmar. Los importes se validan en centavos y se muestran en ARS. [Contrato de Cobros](AUDITORIA-API.md#contrato-de-cobros--etapa-32b)

Después del `201`, la pantalla muestra el cobro informado por Spring con enlaces al pedido, al detalle y al movimiento de caja. Se vuelven a consultar el saldo y el historial del pedido, la caja y los movimientos. El Dashboard comparte la consulta de caja abierta. Spring genera el ingreso vinculado dentro de la misma transacción del cobro.

### Recuperar un cobro pendiente

- Si el resultado queda pendiente o el servidor indica que todavía está procesando el envío, abrí **Revisar envío pendiente**. Podés consultar el pedido o usar **Reintentar el mismo envío** desde esa pantalla. El reintento usa el UUID, el pedido, el importe y el método originales. Cuando Spring devuelve `Retry-After`, el botón se habilita después de esa espera. Si venció el inicio de sesión, ingresá con la misma cuenta.
- La pestaña guarda en `sessionStorage` solo el ID del pedido pendiente, bajo una clave propia de la cuenta. El UUID y el importe permanecen en memoria. Tras recargar, usá **Actualizar saldo e historial**, revisá el historial y marcá **Ya verifiqué el historial** solo cuando hayas podido conciliar el envío anterior. Si todavía hay dudas, esperá y volvé a consultar. Este control no puede confirmar por sí solo un envío que sigue en ejecución. Cerrar la pestaña, cambiar de dispositivo o deshabilitar el almacenamiento del navegador puede eliminar el aviso. La idempotencia de Spring tiene alcance por cuenta y UUID.
- Una clave reutilizada con contenido diferente exige revisar el historial antes de habilitar una nueva operación. Los rechazos definitivos del negocio se muestran con el mensaje del servidor. El registro requiere una conexión segura o un origen local de desarrollo para generar el UUID. [Idempotencia financiera](../docs/idempotencia-financiera.md)

### Prueba manual con datos sintéticos

1. Ingresá como EMPLEADO en la instancia aislada. Abrí un pedido pendiente desde **Cobros** o **Pedidos** y verificá que sin caja abierta no se ofrezca el registro.
2. Prepará una caja de 100,00 en esa instancia y un pedido con total de 50,00. Abrí **Registrar cobro**, probá un importe mayor al saldo y luego elegí 12,53 en efectivo. Revisá los datos y confirmá una sola vez. El pedido debe quedar **Parcialmente pagado**, con saldo de 37,47.
3. Abrí el detalle y seguí el enlace a Caja. Verificá un movimiento por 12,53, saldo general y efectivo de 112,53. Revisá desde Cobros que el historial muestre una sola fila y que el detalle enlace al mismo movimiento.
4. Simulá una respuesta perdida en el entorno aislado. Usá **Reintentar el mismo envío** y comprobá que el número de cobro y movimiento permanezcan iguales. Repetí la verificación con ADMINISTRADOR.

### Pruebas automatizadas

```powershell
npm run lint
npm run format:check
npm run build
npm test
npm run test:e2e -- --workers=1
```

Cada suite real exige el lanzador aislado. Consultá [aislamiento de Playwright con Spring](#aislamiento-de-playwright-con-spring).

```powershell
$env:API_PROXY_TARGET = 'http://127.0.0.1:18095'
$env:E2E_COBROS_ISOLATED = '1'
# Cargá E2E_USERNAME y E2E_PASSWORD del administrador sintético.
npm run test:e2e -- --workers=1 e2e/cobros.spring.spec.ts
```

La prueba crea sus propios pedidos y caja. Descarta un `201` después del commit, recupera el cuerpo y la ubicación originales, verifica los saldos y el movimiento único, y prueba concurrencia con dos solicitudes del mismo administrador. Las pruebas interceptadas cubren también el tiempo de espera de `Retry-After`, la sesión vencida, el doble clic, la verificación después de recargar y la navegación móvil.

La etapa se verificó con TypeScript estricto, ESLint, Prettier, el build de Vite y 72 pruebas de Vitest. Pasaron 47 escenarios de Playwright, incluido el de Cobros con Spring Boot y PostgreSQL 17.6 aislado. Se omitieron seis escenarios de escritura que requieren sus propios entornos aislados. La prueba real confirmó que un cobro de 12,53 dejó 37,47 pendientes y elevó el saldo general y el efectivo de la caja a 112,53 con un único movimiento.

## Compras · Etapa 4.3

En **Abastecimiento → Compras**, ADMINISTRADOR y EMPLEADO pueden listar, registrar y consultar compras. **Editar compra** aparece cuando Spring informa que la compra está pendiente y sin pagos. La ficha muestra los importes confirmados, el saldo y el historial de pagos. Consultá los contratos y las limitaciones en [Auditoría de la API](AUDITORIA-API.md#etapa-43--compras-de-abastecimiento).

### Prueba manual

1. Abrí `/compras`, aplicá filtros de proveedor, fechas, comprobante y estado, y recorré las páginas. El servidor ordena por fecha e ID descendentes. El filtro por ID permite encontrar compras históricas de proveedores que hoy están inactivos.
2. Abrí **Nueva compra**, navegá las páginas de proveedores e insumos y elegí dos insumos vigentes. Probá quitar un renglón, ingresar cero, ingresar más de dos decimales y repetir un insumo.
3. Cambiá de proveedor con renglones cargados. Cancelá el aviso y verificá que se conserven las selecciones. Confirmá el cambio y verificá que se limpien los insumos incompatibles y sus precios.
4. Revisá y confirmá una compra en un entorno aislado. Verificá que se abra su ficha, que el proveedor esté enlazado y que subtotal, total y saldo correspondan a la respuesta de Spring.
5. Editá una compra pendiente sin pagos. Verificá que conserve el precio cargado, que pida confirmación al salir con cambios sin guardar y que la ficha y el listado reflejen la edición. Abrí la ficha de una compra ya pagada y verificá que no ofrezca edición.
6. Simulá un corte de conexión en el guardado. El formulario debe ofrecer **Revisar listado de compras** y advertir que la compra podría haberse guardado. Consultá el listado antes de repetir el alta.

### Playwright

```powershell
npm run test:e2e -- e2e/compras.spec.ts
```

Cada suite real exige el lanzador aislado. Consultá [aislamiento de Playwright con Spring](#aislamiento-de-playwright-con-spring).

```powershell
$env:API_PROXY_TARGET = 'http://127.0.0.1:18098'
$env:E2E_COMPRAS_ISOLATED = '1'
# Definí E2E_USERNAME y E2E_PASSWORD con el administrador sintético.
npm run test:e2e -- e2e/compras.spring.spec.ts --workers=1
```

El escenario crea sus proveedores, insumos y usuarios sintéticos en Spring aislado. Antes de escribir desde el navegador, verifica que el proxy lee un proveedor creado en esa misma instancia. En Windows, si Playwright queda esperando al cerrar Vite, se puede iniciar una vista previa por separado siguiendo [el flujo de Pedidos](#e2e-de-pedidos-con-escritura).

### Preparación para Pagos a proveedores

La ficha enlaza al [registro de pagos a proveedores](#pagos-a-proveedores--etapa-44) y al detalle de los pagos asociados.

## Pagos a proveedores · Etapa 4.4

Desde **Pagos a proveedores**, filtrá y elegí una compra para consultar su saldo e historial. También podés llegar desde **Consultar pagos del proveedor** en la ficha del proveedor, o usar **Registrar pago** en la ficha de una compra con saldo pendiente. ADMINISTRADOR y EMPLEADO pueden consultar y registrar. La ficha del pago enlaza la compra, el proveedor y el movimiento de Caja e indica si existe un comprobante. [Auditoría de API](AUDITORIA-API.md#contrato-de-pagos-a-proveedores--etapa-44)

El formulario registra un pago ya efectuado. Muestra saldo de la compra, saldo general y efectivo de la caja abierta, y ofrece únicamente los métodos activos. El importe admite hasta ocho enteros y dos decimales. Los pagos en efectivo requieren efectivo suficiente. Spring verifica nuevamente los saldos bajo bloqueo, genera el egreso vinculado y devuelve el pago confirmado. Se vuelven a consultar Compra, su historial, el listado de Compras, Caja y el estado de caja compartido con Dashboard.

### Registro y recuperación

1. Con una caja abierta en un entorno de pruebas, abrí una compra pendiente de ARS 30,00. Seleccioná **Registrar pago**, ingresá 8,25 en efectivo, pulsá **Revisar pago** y luego **Confirmar pago**.
2. Verificá estado **Parcial**, saldo pendiente de ARS 21,75 y un solo egreso **Pago a proveedor**. Entrá al detalle desde el historial y seguí el vínculo al movimiento. La edición de la compra queda deshabilitada tras el primer pago.
3. Si se pierde la respuesta, usá **Reintentar el mismo envío** con la misma cuenta y pestaña. El UUID, el destino y el cuerpo se conservan en memoria. Un 409 en curso respeta `Retry-After`. Si el intento inicial se revirtió, el reenvío puede ejecutarse en la caja que esté abierta en ese momento.
4. Si recargaste la pestaña o Spring indicó que la clave ya estaba asociada a otra operación, usá **Actualizar saldo e historial**. Revisá todas las páginas y pulsá **Ya concilié el envío** solo después de determinar qué ocurrió. La pestaña conserva únicamente el ID de la compra por cuenta. Cerrar la pestaña o cambiar de dispositivo puede eliminar el aviso. Spring carece de un GET de conciliación por UUID.
5. Registrá los ARS 21,75 restantes con una operación nueva y comprobá el estado **Pagado**. Si el segundo pago usa transferencia, el saldo general disminuye y el efectivo permanece en el valor que dejó el primer pago.

### Prueba aislada de extremo a extremo

Cada suite real exige el lanzador aislado. Consultá [aislamiento de Playwright con Spring](#aislamiento-de-playwright-con-spring).

```powershell
$env:E2E_PAGOS_PROVEEDOR_ISOLATED = '1'
$env:API_PROXY_TARGET = 'http://127.0.0.1:18099'
npm run test:e2e -- --workers=1 e2e/pagos-proveedor.spring.spec.ts
```

La prueba crea una compra, abre caja, registra un pago como EMPLEADO y descarta el primer `201` después de recibirlo de Spring. Verifica la identidad del reintento y del resultado recuperado, comprueba que existe un único pago y un único egreso, y completa el saldo como ADMINISTRADOR. Comprueba también la autorización y el arqueo. La variante `e2e/pagos-proveedor.spec.ts` intercepta solicitudes en el navegador para reproducir estados de error y probar la interfaz sin una base de datos.

## Comprobantes · Etapa 5.1

ADMINISTRADOR y EMPLEADO pueden registrar y consultar constancias internas de cobros y pagos a proveedores ya registrados. `/comprobantes` lleva a Pedidos y Compras. Las fichas de esas operaciones muestran sus comprobantes paginados por Spring. La ficha individual `/comprobantes/{id}` enlaza la operación, su pedido o compra y el movimiento de Caja que ya existe. [Contrato de API](AUDITORIA-API.md#contrato-de-comprobantes--etapa-51)

### Guía manual

1. Elegí **Comprobantes** en el panel, abrí **Buscar pedido** o **Buscar compra**, y entrá a la ficha. Revisá la sección **Comprobantes asociados** y sus páginas. Los filtros de Pedidos y Compras se aplican a esas operaciones.
2. Desde la ficha del pedido o de la compra, abrí el detalle de un cobro o pago existente. Si todavía admite un comprobante, completá la fecha de emisión y, opcionalmente, las observaciones. Usá **Revisar comprobante** y **Confirmar registro**.
3. Ante un conflicto o una respuesta incierta, pulsá **Consultar comprobante**. La consulta puede recuperar el registro aunque el navegador haya perdido el `201`. Si todavía no aparece, volvé a consultar antes de intentar otro registro.
4. Abrí **Ver comprobante**, verificá el importe y seguí los vínculos a pedido o compra, operación y Caja. **Imprimir constancia** abre el diálogo del navegador con una vista sin validez fiscal. Esa vista admite impresión o guardado como PDF si el navegador lo ofrece. Spring responde con JSON y carece de descargas documentales.

### Verificación

```powershell
npm run build
npm run lint
npm run format:check
npm test
npm run test:e2e -- e2e/comprobantes.spec.ts
```

Cada suite real exige el lanzador aislado. Consultá [aislamiento de Playwright con Spring](#aislamiento-de-playwright-con-spring).

```powershell
$env:E2E_COMPROBANTES_ISOLATED = '1'
$env:API_PROXY_TARGET = 'http://127.0.0.1:18051'
# Definí E2E_USERNAME y E2E_PASSWORD con el administrador sintético.
npm run test:e2e -- e2e/comprobantes.spring.spec.ts --workers=1
```

La prueba real registra un comprobante de cobro como EMPLEADO y uno de pago como ADMINISTRADOR. Verifica la recuperación del primer registro tras perder el `201`, los accesos sin JWT y el conflicto al repetir el alta. Compara los saldos de pedido, compra y caja y el historial de movimientos antes y después. Las regresiones de Spring incluyen siete pruebas con PostgreSQL sobre integridad, historial y concurrencia de comprobantes.

TypeScript estricto, build, ESLint y Prettier pasaron. Pasaron 151 pruebas de Vitest y 68 regresiones de Spring. La suite general de Playwright aprobó 83 escenarios y omitió 13 reservados a integraciones habilitadas por variables de entorno. El escenario de Comprobantes pasó por separado contra Spring y PostgreSQL 17.6 aislado. Se restauró solo estructura y se mantuvo Flyway deshabilitado.

## Inicio y navegación

El inicio reúne las cuatro acciones principales y el estado operativo. **Buscar módulos** permite navegar por nombre y **Atajos** muestra las teclas disponibles. Consultá la [guía de Inicio](../docs/dashboard.md).

## Reportes administrativos · Etapa 5.2

Desde **Control y administración → Reportes** o desde el panel de inicio, ADMINISTRADOR puede abrir Pedidos, Movimientos de Caja y Compras. Cada consulta envía solo sus filtros y una página a Spring, y muestra el resumen global informado por el mismo endpoint. Los importes en ARS conservan los centavos del BigDecimal original. Los enlaces de fila llevan a las fichas existentes de pedido, compra, movimiento y caja. [Contratos de Reportes](AUDITORIA-API.md#contratos-de-reportes--etapa-52)

Los filtros aplicados y la página se recuperan al volver con el navegador. La URL guarda fechas, códigos de catálogo, IDs técnicos y paginación. Los selectores consultan los catálogos activos. Un código histórico conocido se puede conservar mediante el parámetro de la URL si Spring todavía lo acepta. El tamaño predeterminado es 20 y puede ajustarse con `size` en la URL, entre 1 y 100. Las fechas siguen la hora comercial y ambos extremos son inclusivos.

Pedidos y Compras muestran valor de las operaciones. Consultá cobros, pagos y pendientes en las fichas. El neto de Caja corresponde a ingresos menos egresos filtrados. Consultá saldo disponible, efectivo y arqueo en la ficha de la caja. La API carece de filtro transversal por caja y series temporales.

**Generar PDF** descarga todos los registros de los filtros aplicados dentro del límite de 1.000 filas. Los selectores de Clientes y Proveedores permiten recorrer páginas. **Usar ID** conserva el acceso a registros históricos. Consultá el [contrato, límites y guía de prueba del PDF](../docs/reportes-pdf.md).

### Guía de prueba manual

1. Ingresá como ADMINISTRADOR y abrí **Reportes**. Entrá en **Pedidos**, elegí un día con pedidos en las dos fechas, seleccioná un estado y aplicá. Verificá que el período y las filas coincidan. Intentá aplicar un rango invertido y comprobá el mensaje de validación.
2. Con más de una página, usá **Siguiente** y verificá que se conserven los filtros y el resumen global de la consulta. Abrí un pedido, volvé con el navegador y comprobá que se recupere la misma página. Usá **Limpiar filtros** y verificá que vuelva a la primera página.
3. En **Movimientos de Caja**, filtrá por tipo y método. Compará ingresos, egresos y neto con los movimientos del historial conocido. Abrí los enlaces a una caja y a un movimiento.
4. En **Compras**, filtrá por proveedor y estado de pago. Compará el importe y el estado de una fila con su ficha. Aplicá una fecha sin actividad y verificá el estado vacío y los agregados en cero.
5. En una sesión EMPLEADO, verificá que el menú y el panel de inicio no incluyan Reportes y que abrir `/reportes/compras` directamente muestre **Acceso restringido**. En móvil, comprobá que el desplazamiento horizontal quede dentro de la tabla.

### Pruebas automatizadas

```powershell
npm run lint
npm run format:check
npm run build
npm test
npm run test:e2e -- --workers=1 e2e/reportes.spec.ts
```

Cada suite real exige el lanzador aislado. Consultá [aislamiento de Playwright con Spring](#aislamiento-de-playwright-con-spring).

```powershell
$env:API_PROXY_TARGET = 'http://127.0.0.1:18052'
$env:E2E_REPORTES_ISOLATED = '1'
# Definí E2E_USERNAME y E2E_PASSWORD de la cuenta sintética.
npm run test:e2e -- --workers=1 e2e/reportes.spring.spec.ts
```

Las regresiones de backend se ejecutan desde la raíz con `LAVANDERIA_TEST_JDBC_URL` y `LAVANDERIA_TEST_JDBC_USER` apuntando a la base desechable y con Flyway deshabilitado. El soporte de pruebas crea un esquema por clase. La selección excluye las clases de Caja y Movimientos que ejecutan V032 mediante `@Sql`.

```powershell
mvn.cmd '-Dtest=Reporte*Test,Pedido*Test,TransicionesPedidoTest,DetallePedidoTest,Compra*Test,ImportesCompraTest,MovimientoCajaControllerTest,CajaControllerTest,CajaServiceTest,CalculadorSaldoCajaTest' test
```

Pasaron TypeScript estricto, ESLint, Prettier, el build, 167 pruebas de Vitest y 134 regresiones de Spring. La suite general de Playwright aprobó 89 escenarios. El escenario real de Reportes pasó en una ejecución separada con PostgreSQL 17.6 aislado. Quedaron 13 escenarios de otros módulos que requieren su propia configuración de integración.

## Aislamiento de Playwright con Spring

Usá el clúster y las tres variables `LAVANDERIA_TEST_JDBC_*` descritas en las [pruebas PostgreSQL del backend](../README.md#pruebas-sobre-postgresql). Cargá un JWT y los cuatro valores sintéticos `LAVANDERIA_BOOTSTRAP_USERNAME`, `LAVANDERIA_BOOTSTRAP_PASSWORD`, `LAVANDERIA_BOOTSTRAP_NOMBRE` y `LAVANDERIA_BOOTSTRAP_EMAIL` en la terminal de Spring. Generá un nonce distinto por ejecución. En la terminal de Playwright, configurá el mismo nonce y las credenciales sintéticas equivalentes en `E2E_USERNAME` y `E2E_PASSWORD`.

Desde la raíz del repo, lanzá el proceso de pruebas con el puerto de la suite. Para Caja, el puerto es 18094.

```powershell
$env:E2E_ISOLATION_NONCE = [guid]::NewGuid().ToString('N')
.\mvnw.cmd spring-boot:test-run '-Dspring-boot.run.main-class=com.miraquetelavo.lavanderia.soporte.SpringE2e' '-Dspring-boot.run.jvmArguments=-Dspring.devtools.restart.enabled=false' '-Dspring-boot.run.arguments=18094'
```

El lanzador verifica la base desechable antes de crear el esquema y aprovisionar al usuario. Con la misma base de pruebas atestada, Hibernate prepara un esquema sintético y el lanzador aplica V032. Playwright comprueba el nonce en Spring y a través de su propio proxy antes de autenticar. Activá una sola bandera de suite real por ejecución. El smoke de autenticación usa `E2E_LOGIN_ISOLATED=1` y puerto `18053`. Para simulación, Vite usa un proxy local sin backend. [Comando test-run de Spring Boot](https://docs.spring.io/spring-boot/maven-plugin/run.html#goals-test-run)
