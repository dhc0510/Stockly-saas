# SPEC — STK-010 | Historial y trazabilidad de movimientos

Estado: aprobado por el propietario el 2026-11-07. Desarrollo autorizado el 2026-11-07. [Trello](https://trello.com/c/IXEr6umQ/10-stk-010-historial-y-trazabilidad-de-movimientos) · [Mockup aprobado](../../06-Mockups/STK-010/historial-responsive.html).

## Objetivo

Explicar por qué un artículo tiene el saldo que tiene: qué cambió, cuándo, quién lo hizo y por qué, sin ocultar ni alterar la historia. Reúne en una sola consulta las entradas, salidas, ajustes y anulaciones de STK-008 y STK-009. Es solo de lectura. El servidor limita todas las consultas a la empresa autenticada.

Además, corrige las búsquedas existentes para cumplir la regla de [búsquedas sin distinguir tildes](../../00-General/CONVENTIONS.md).

## Acceso

- Módulo propio **Historial**, asignado por rol. Cualquier nivel distinto de «sin acceso» permite consultar, porque el módulo no tiene acciones de escritura. El administrador de empresa siempre tiene acceso.
- Quien tiene acceso puede abrir el comprobante de cualquier movimiento solo para leerlo, aunque no tenga permiso de Entradas o de Salidas y ajustes. No puede registrar ni anular desde el historial.
- Este módulo no da acceso a Entradas ni a Salidas y ajustes, y esos módulos no dan acceso al historial.
- Sin acceso, la opción no aparece en el menú, la ruta directa muestra que no hay permiso y el servidor rechaza la consulta.

## Historial general

- Una fila por artículo de cada movimiento, de lo más reciente a lo más antiguo, con 25 filas por página. Una operación de tres artículos ocupa tres filas.
- Cada fila muestra fecha y hora de Costa Rica sin segundos, folio con enlace al comprobante, tipo, artículo con enlace a su historia, SKU, motivo, referencia, responsable, cambio con signo y saldo después. Un cambio de cero se muestra como **Sin diferencia**. El color nunca es la única señal del cambio.
- Una entrada, salida o ajuste anulado se marca como **Anulado**. La anulación aparece como su propia fila e indica qué folio anula.
- Nombre, SKU y unidad son los que tenía el artículo cuando ocurrió el movimiento, y el saldo después es el registrado en ese momento. No cambian con ediciones posteriores del catálogo ni con otros movimientos. El responsable se muestra con su nombre actual, como en los comprobantes de STK-008 y STK-009.
- Un artículo inactivo en el catálogo se marca como **Inactivo** y sus filas no se ocultan.

## Búsqueda y filtros

- **Buscar:** por folio o referencia.
- **Artículo:** por nombre, SKU o código de barras, con sugerencias que incluyen artículos inactivos marcados como tales.
- **Tipo:** todos, entrada, salida, ajuste o anulación.
- **Responsable:** todos o una de las personas con movimientos registrados en la empresa.
- **Periodo:** al abrir muestra los **últimos 30 días**, siempre visibles. Se puede elegir una **fecha específica** o un rango **desde y hasta**. Los días son de Costa Rica e incluyen el día completo. No se aceptan fechas futuras. Si la fecha inicial es posterior a la final, avisar y no consultar.
- **Limpiar filtros** vuelve a los últimos 30 días sin otros filtros.
- Los filtros se combinan. El listado indica el periodo aplicado.

## Historia por artículo

- Se abre desde el nombre de un artículo en cualquier fila o comprobante.
- Muestra nombre actual, SKU, unidad, estado en el catálogo y saldo actual en Principal.
- Lista **toda** la historia del artículo, sin límite de periodo, con el mismo orden y paginación, y permite filtrar por tipo.

## Comprobante de solo lectura

Muestra folio, tipo, estado, ubicación, fecha y hora, responsable, motivo, referencia y artículos. En ajustes: saldo antes, contado y diferencia. En los demás: cambio y saldo después. Enlaza el movimiento original con su anulación. No muestra acciones de registro ni de anulación.

## Estados y errores

Carga, sin movimientos, sin resultados con filtros, error de carga con reintento, y movimiento o artículo inexistente o de otra empresa, que se presenta como no encontrado. Conservar los filtros al volver del comprobante o de la historia por artículo no es obligatorio en esta versión.

## Búsquedas sin tildes en toda la aplicación

Toda búsqueda encuentra el resultado aunque se escriba sin tildes o con otras mayúsculas: «cafe» encuentra «Café» y «guia» encuentra «GUÍA-083». Debe cumplirse sin depender de la configuración por defecto de la base de datos. Comprende:

- Catálogo: búsqueda de productos y variantes, y lista de categorías.
- Existencias: búsqueda de STK-007, en servidor y navegador.
- Entradas, y Salidas y ajustes: búsqueda de movimientos y sugerencias de artículos.
- Empresas: búsqueda por nombre.
- Historial: todas sus búsquedas.

Una búsqueda exacta de código de barras por lector no cambia. Como consecuencia de la regla, «pina» también encuentra «piña». Esta corrección no cambia ninguna otra regla ni diseño de esas pantallas.

## Criterios de aceptación

- [x] AC-01 Mostrar una fila por artículo de entradas, salidas, ajustes y anulaciones, con todos sus datos, de lo más reciente a lo más antiguo y con paginación.
- [x] AC-02 Al abrir, mostrar los últimos 30 días. Filtrar por fecha específica y por rango, con días completos de Costa Rica, incluidos los movimientos cercanos a medianoche. Rechazar fechas futuras y un rango invertido.
- [x] AC-03 Buscar por folio y referencia, y filtrar por artículo, tipo y responsable, también combinados. Limpiar filtros restablece los valores iniciales.
- [x] AC-04 La historia por artículo muestra su saldo actual y toda su historia sin límite de periodo. El saldo después del movimiento más reciente coincide con el saldo actual.
- [x] AC-05 Conservar nombre, SKU, unidad y saldo históricos aunque cambie el catálogo o haya movimientos posteriores. Un artículo inactivo se encuentra y se marca como tal.
- [x] AC-06 Marcar lo anulado y mostrar la anulación como fila propia, con enlace entre ambos. Un ajuste sin diferencia se muestra como tal.
- [x] AC-07 El comprobante se abre para cualquier tipo de movimiento y no ofrece registrar ni anular.
- [x] AC-08 Verificar en el servidor el acceso del módulo con cada nivel, que Entradas o Salidas y ajustes no dan acceso al historial ni al revés, que el historial no permite escribir, y el aislamiento entre empresas también por identificador directo.
- [x] AC-09 Encontrar resultados sin tildes y con otras mayúsculas en cada búsqueda listada, en servidor y navegador. Un código de barras exacto se sigue encontrando.
- [ ] AC-10 Las funciones de STK-006, STK-007, STK-008, STK-009 y Empresas siguen funcionando, salvo la corrección de búsqueda.
- [ ] AC-11 Verificar escritorio y móvil según el mockup, incluido el selector de fecha y la lectura de filas en un celular físico.

## Fuera de alcance

Descargar o exportar (STK-017), registrar o anular desde el historial, múltiples sucursales, costos y valoración, ventas y devoluciones (STK-011 y STK-015), corregir los saldos en cero de Existencias de STK-007 y unir sucursales con la ubicación Principal.

## Dependencias

STK-005 (permisos), STK-006 (catálogo), STK-008 y STK-009 (movimientos). Cuando existan, ventas y devoluciones deberán incorporar sus movimientos a este historial mediante su propia SPEC.
