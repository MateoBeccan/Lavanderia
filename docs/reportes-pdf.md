# Exportación de reportes en PDF

En **Reportes**, elegí Pedidos, Movimientos de Caja o Compras. Aplicá los filtros para actualizar el resumen y la vista previa, y luego elegí **Generar PDF**. La descarga incluye todas las filas del filtro aplicado dentro de los límites. Podés descargar un documento con resumen en cero cuando la consulta esté vacía. Los selectores paginados muestran clientes y proveedores activos. **Usar ID** permite consultar registros históricos que no figuran en esos selectores.

## Contrato

Las rutas son GET autenticados con JWT y requieren ADMINISTRADOR, al igual que las vistas JSON. Cada ruta admite `fechaDesde` y `fechaHasta` como `yyyy-MM-dd`. Ambas son opcionales e inclusivas. Los timestamps almacenados se interpretan según la hora comercial usada por los reportes. La marca de generación usa el reloj comercial configurado, que se inicializa en `America/Argentina/Buenos_Aires`.

| Ruta                                 | Filtros adicionales                                   | Importes oficiales                                    |
| ------------------------------------ | ----------------------------------------------------- | ----------------------------------------------------- |
| `/api/reportes/pedidos/pdf`          | `clienteId`, `estadoPedidoCodigo`, `estadoPagoCodigo` | Total de pedidos registrados                          |
| `/api/reportes/movimientos-caja/pdf` | `tipoMovimientoCodigo`, `metodoPagoCodigo`            | Ingresos, egresos y neto de los movimientos filtrados |
| `/api/reportes/compras/pdf`          | `proveedorId`, `estadoPagoCodigo`                     | Total de compras registradas                          |

Los totales de Pedidos y Compras representan valor de las operaciones registradas. Para verificar cobros y pagos, consultá cada ficha. El neto de Caja es la diferencia entre ingresos y egresos filtrados. El saldo y el efectivo disponible pertenecen a la ficha de caja.

La respuesta correcta usa `application/pdf`, `Content-Disposition: attachment` y `Cache-Control: no-store`. El nombre incluye el reporte y la marca de generación. Los errores se devuelven como Problem Details. Un 400 indica filtros inválidos o que se superó un límite de consulta o de generación. Un 429 indica una exportación simultánea o demasiado reciente.

## Estrategia y límites

El PDF reutiliza las validaciones, filtros, agregados BigDecimal, orden estable y proyecciones de los reportes JSON. Una transacción de solo lectura con aislamiento `REPEATABLE_READ` obtiene el agregado y hasta 1.000 filas en una sola lectura de detalle, sin consultas por fila. Si la cantidad oficial supera las filas exportables, Spring rechaza la solicitud completa. `page` y `size` pertenecen a la vista previa. La transacción termina antes de componer el documento.

| Recurso               | Límite                                                                                                                                                                             |
| --------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Filas por exportación | 1.000                                                                                                                                                                              |
| Documento             | 160 páginas A4 y 8 MiB                                                                                                                                                             |
| Texto                 | 2.048 caracteres por celda. El contenido de cada fila debe caber en una página.                                                                                                    |
| Consulta y generación | 5 s por sentencia PostgreSQL y transacción con timeout de 8 s. Presupuesto de 15 s desde la recepción en la capa de exportación, comprobado durante la composición y la escritura. |
| Frecuencia            | Una generación simultánea y un inicio por segundo por instancia de Spring.                                                                                                         |

Apache PDFBox 3.0.8 compone los documentos en Spring. El logo proviene de la imagen del frontend incluida en el artefacto Maven. El documento usa fuentes estándar Helvetica compatibles con español y Windows-1252. Los glifos fuera de ese repertorio aparecen como signos de interrogación. Las tablas ajustan el texto, repiten el encabezado y muestran la página actual y el total. Cada hoja se identifica como documento interno sin validez fiscal.

## Verificación

1. En cada vista, aplicá filtros y compará el resumen con el PDF. En un reporte con varias páginas, generá desde la segunda página y verificá que se incluyan filas de la primera y de la última. Revisá los encabezados repetidos y el pie en A4.
2. Elegí un período sin actividad y descargá el PDF. Verificá la página con el resumen en cero y el mensaje de ausencia de filas. Probá también una consulta de más de 1.000 registros y comprobá el mensaje para acotar filtros.
3. Cambiá un filtro sin aplicarlo y comprobá que la descarga mantenga los filtros aplicados. Repetí con un proveedor histórico usando su ID. En EMPLEADO comprobá el acceso restringido.

Desde `frontend`, ejecutá `npm run test:e2e -- e2e/reportes.spec.ts`. Desde la raíz, ejecutá lo siguiente con PostgreSQL aislado y las variables `LAVANDERIA_TEST_JDBC_URL`, `LAVANDERIA_TEST_JDBC_USER` y `LAVANDERIA_TEST_JDBC_PASSWORD` configuradas. El soporte de pruebas valida que la base sea desechable, crea un esquema de prueba y mantiene Flyway deshabilitado.

```powershell
mvn.cmd '-Dtest=ReportePdfTest,ReporteControllerTest,ReporteServiceTest,ReportePostgresqlTest' test
```

Agregá `'-Dreportes.pdf.muestras=target/reportes-pdf-muestras'` para guardar los documentos de prueba y revisar la composición.
