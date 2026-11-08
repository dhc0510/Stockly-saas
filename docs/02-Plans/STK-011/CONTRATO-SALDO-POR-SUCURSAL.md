# STK-011 · Contrato de saldo por sucursal

El propietario aprobó la asignación de Principal y el contrato de saldo por sucursal el 2026-11-07. El propietario confirmó el visto bueno técnico de Paulo y autorizó iniciar el desarrollo. [ADR-005](../../05-Decisions/ADR-005.md). [Plan aprobado](PLAN.md).

## Estado actual

STK-007 guarda sucursales, accesos, asignaciones de artículos y umbrales. Sus consultas de existencias devuelven saldos de apertura en cero. STK-008 y STK-009 guardan los movimientos y saldos reales en la ubicación Principal. STK-010 consulta ese libro.

## Contrato

- Vincular cada sucursal operativa a una ubicación de inventario dentro de la misma empresa. Usar el saldo del libro, identificado por empresa, ubicación y artículo, para mostrar Existencias, validar Ventas y consultar Historial. Las asignaciones y los umbrales de STK-007 siguen asociados a la sucursal.
- Cuando existan saldos o historial previos, pedir al administrador que confirme qué sucursal corresponde a Principal. Si hay una sola sucursal, preseleccionarla y pedir confirmación. Si hay varias, pedir que el administrador elija una antes de confirmar. Vincular esa sucursal a la ubicación existente y conservar allí los saldos y movimientos. Si falta la asignación, mantener los datos en Principal y dejar pendiente habilitar ventas sobre ese stock. Las demás sucursales empiezan con una ubicación propia y saldo cero. Cualquier reparto de stock requiere movimientos autorizados en el flujo que se defina para traslados.
- Alinear Entradas, Salidas, Ajustes, Existencias e Historial con el vínculo antes de habilitar la venta por sucursal. Respetar los permisos de la sucursal y la asignación de cada artículo. Mostrar hasta tres decimales en las unidades fraccionarias acordadas.
- Confirmar venta y movimiento de salida en la misma transacción, con control de concurrencia sobre el saldo de la ubicación. Anular mediante un movimiento inverso en esa ubicación. El folio de venta sigue siendo consecutivo por empresa.

El propietario confirmó que Entradas, Salidas y Ajustes usan la misma selección que Ventas. Con una sucursal autorizada, se selecciona automáticamente. Con varias, la persona debe elegir una antes de registrar el movimiento.

## Puntos técnicos para revisar con Paulo

1. **Vínculo entre sucursal y ubicación.** Proponer una referencia con unicidad por empresa y ubicación, protegida por una clave foránea que incluya la empresa. Confirmar dónde se administrará el vínculo. Aplicar las reglas de STK-007 al borrar o desactivar una sucursal con saldo o historial.
2. **Lectura y escritura del saldo.** Hoy el libro usa Principal para bloquear el saldo y registrar movimientos. Confirmar cómo se pasará la ubicación de la sucursal autorizada a Entradas, Salidas, Ajustes y Ventas, y cómo Existencias e Historial leerán esa misma ubicación. La anulación debe resolver la ubicación del movimiento original.
3. **Folios y precisión.** Hoy los contadores de inventario se guardan en Principal y los folios son únicos por empresa. Confirmar cómo conservar esa unicidad al operar en varias ubicaciones. El saldo del libro ya admite tres decimales. Verificar que Existencias muestre, por ejemplo, 1,125 kg sin redondearlo a dos decimales.


## Comprobaciones del contrato

- Con una sola sucursal, aparece preseleccionada y el vínculo se guarda solo al confirmar. Con varias, se requiere elegir y confirmar. Mientras esté pendiente, las ventas sobre ese stock no están habilitadas.
- Un artículo con 10 unidades y movimientos en Principal conserva el saldo y su historial al vincular la sucursal correspondiente. Otra sucursal empieza en cero.
- Tras vender 3 unidades, Existencias muestra 7 en esa sucursal y el Historial permite rastrear la venta. Anular devuelve el saldo a 10, con ambos movimientos disponibles.
- Dos ventas concurrentes por las últimas unidades no pueden dejar saldo negativo ni descontar de una sucursal diferente.
