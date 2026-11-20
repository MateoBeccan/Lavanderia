# Inicio de Mirá que te lavo

El inicio muestra una bienvenida, cuatro acciones y el estado de la operación. Los indicadores consultan Caja abierta, clientes activos y pedidos registrados. Los enlaces del inicio y el menú del encabezado siguen disponibles con clic, toque y Tab.

**Buscar módulos** abre un selector local, con iconos y resultados filtrados por texto sin distinguir tildes. Usá ↑ y ↓ para recorrerlos, Enter para abrir el seleccionado y Escape para volver al control que abrió el diálogo. El selector incluye Categorías de insumos y muestra Reportes e Historial de caja a ADMINISTRADOR. Los destinos pendientes quedan fuera de la búsqueda.

| Tecla  | Acción desde Inicio                |
| ------ | ---------------------------------- |
| N      | Nuevo pedido                       |
| P      | Ver pedidos                        |
| C      | Abrir Cobros para elegir el pedido |
| J      | Abrir Caja                         |
| /      | Buscar módulos                     |
| ?      | Abrir ayuda de atajos              |
| Escape | Cerrar el diálogo activo           |

Los atajos de navegación funcionan en Inicio cuando el foco está fuera de un campo editable y no hay un diálogo activo. Las combinaciones con Ctrl, Alt o Meta conservan su comportamiento habitual.

Podés desactivar los atajos de una tecla desde **Atajos**. La preferencia se conserva en la pestaña.

## Prueba manual

1. Ingresá con cada rol y verificá las cuatro acciones, el logo y los tres indicadores. Repetí con un ancho de teléfono o tablet.
2. Abrí el buscador con clic o con `/`. Probá `categorias`, recorré los resultados con las flechas y abrí uno con Enter. Volvé a Inicio. Abrí la ayuda con `?` y cerrala con Escape.
3. Como EMPLEADO, buscá Reportes e Historial de caja. Verificá que no aparezcan. Como ADMINISTRADOR, comprobá que los dos se puedan abrir.
4. Escribí letras en la búsqueda y en el formulario de Nuevo pedido. Verificá que permanezcas en el campo o formulario actual. Abrí el menú del encabezado y comprobá que los atajos de navegación queden suspendidos hasta cerrarlo.

Desde `frontend`, ejecutá `npm run test:e2e -- e2e/auth.spec.ts`. Las pruebas cubren teclado, navegación, permisos, móvil y verificaciones automáticas de accesibilidad con axe.
