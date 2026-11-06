# SPEC — STK-006 | Catálogo de productos adaptable

Estado: aprobado para desarrollo el 2026-11-06. [Tarjeta en Trello](https://trello.com/c/CdSAJdzH/6-stk-006-cat%C3%A1logo-de-productos-adaptable) · [Mockup aprobado para escritorio y móvil](../../06-Mockups/STK-006/catalogo-responsive.html).

## Objetivo y acceso

Cada empresa registra, organiza y consulta su catálogo sin campos obligatorios propios de un sector. STK-004 aporta el contexto de empresa y STK-005 permite asignar por separado los permisos de consulta, creación y edición, cambio de precio, y archivo y reactivación. Las operaciones y las imágenes se limitan a la empresa autenticada.

## Registro y edición

- El nombre es el único dato obligatorio al crear la ficha. Descripción, categoría e imagen son opcionales. Las categorías son reutilizables, de un nivel y propias de cada empresa. La ficha admite una imagen compartida por sus variantes, con vista previa, miniatura y acciones para reemplazarla o quitarla.
- Un producto simple es un artículo inventariable. En una ficha con variantes, cada combinación seleccionada es un artículo inventariable. La empresa define atributos y valores. Stockly muestra las combinaciones posibles y solo guarda las elegidas. La ficha agrupadora no mantiene saldo propio. Si muestra un total, se deriva de las variantes.
- Cada artículo tiene un identificador interno permanente y un SKU único por empresa. Se admite un SKU propio y se genera uno si se deja vacío al crear. La edición autorizada registra el código anterior, el nuevo, el actor y la fecha. Los códigos anteriores quedan reservados al mismo artículo y sus existencias y movimientos conservan el vínculo.
- Cada artículo admite un código de barras opcional, único por empresa, y un precio de venta opcional. Asignar o cambiar el precio requiere el permiso correspondiente.
- La unidad base predeterminada es «unidad», con opciones como kg, litro y metro. Se puede cambiar hasta el primer movimiento de inventario.
- Al convertir un producto simple en uno con variantes, el usuario elige qué variante conserva la identidad, el SKU, las existencias y el historial originales. Las demás reciben sus propios SKU y empiezan sin existencias.

## Consulta y archivo

El catálogo muestra activos por defecto. Permite buscar por nombre, SKU o código de barras, filtrar por categoría y consultar archivados. Un código de variante abre esa variante dentro de su ficha. Se pueden archivar y reactivar fichas o variantes. Una ficha archivada y sus variantes quedan fuera de las búsquedas habituales y de nuevas ventas mientras la ficha siga archivada. El historial se conserva.

## Errores y criterios de aceptación

Ante datos inválidos, códigos en conflicto, falta de permisos o fallo de guardado, informar el motivo aplicable sin exponer datos ajenos ni dejar cambios parciales.

- [ ] AC-01 Crear un producto simple con solo el nombre, con unidad y SKU asignados. Elegir categoría reutilizable y agregar, sustituir o quitar la imagen.
- [ ] AC-02 Crear solo las combinaciones seleccionadas, con SKU individuales y precios y códigos de barras opcionales. Impedir combinaciones duplicadas.
- [ ] AC-03 Rechazar duplicados de SKU y códigos de barras dentro de la empresa. Permitir el mismo código en otra empresa. Editar un SKU con trazabilidad y rechazar la reasignación del código anterior a otro artículo.
- [ ] AC-04 Convertir un producto simple y conservar su identidad e historial en la variante elegida. Bloquear el cambio de unidad después del primer movimiento. Verificar ambos contratos al integrar inventario.
- [ ] AC-05 Buscar y filtrar en móvil y escritorio, abrir la variante por su código y archivar o reactivar sin perder historial.
- [ ] AC-06 Verificar permisos en servidor y aislamiento de empresas en consultas, escrituras e imágenes.

## Fuera de alcance

Importación masiva, conversiones de unidades, edición de existencias, registro de movimientos y operación de venta. El catálogo aporta identidad, unidad y estado a esas funcionalidades.
