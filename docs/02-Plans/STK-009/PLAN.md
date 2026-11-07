# PLAN — STK-009 | Salidas, pérdidas y ajustes de inventario

Estado: aprobado por el propietario el 2026-11-07. Desarrollo autorizado el 2026-11-07. [SPEC](../../01-Specs/STK-009/SPEC.md) · [Mockup aprobado](../../06-Mockups/STK-009/salidas-ajustes-responsive.html).

1. **Persistencia.** Crear una migración nueva `V6`, sin modificar `V5`. Agregar a `inventory_locations` las secuencias de salidas y ajustes. Registrar el módulo `INVENTORY_MOVEMENTS` y los permisos especiales `ADJUST_INVENTORY` y `VOID_INVENTORY_MOVEMENTS`; la pantalla de roles los toma del catálogo. Reutilizar `inventory_balances`, `inventory_movements` y `inventory_movement_lines`, con nuevos tipos `OUT` y `ADJUST`. En las líneas, `quantity` guarda el cambio con signo y `balance_after` el saldo posterior; en ajustes, lo contado es `balance_after` y el saldo anterior es `balance_after - quantity`. Se mantiene `CHECK quantity >= 0`.

2. **Operaciones compartidas.** Extraer de `ReceiptService` a un componente del paquete `inventory` el bloqueo de ubicación y artículos, el saldo, los folios, la escritura de movimientos y líneas, y el manejo de claves. Es el refactor mínimo para no crear una segunda lógica de stock. No cambia el comportamiento ni el contrato de entradas, y lo protegen sus pruebas actuales.

3. **Salidas y ajustes.** Crear `StockMovementService` y `StockMovementController` en `/api/inventory/movements`: listado, artículos, comprobante, salida (`POST /outflows`), ajuste (`POST /adjustments`) y anulación (`POST /{id}/void`). Cada operación corre en una transacción. Toma la ubicación en bloqueo, como STK-008, y atiende la clave de confirmación antes de validar saldos. Marca `has_movements` en todos los artículos de la operación, incluidas las líneas sin diferencia.

4. **Saldo cambiado.** Cada línea de ajuste envía `countedQuantity` y `expectedBalance`, el saldo visto por la persona. Con el bloqueo tomado, si algún saldo difiere, responder `409` con los saldos actuales por artículo y no escribir nada. Una salida responde `409` con los artículos sin saldo suficiente. La interfaz descarta entonces la clave de confirmación, actualiza las líneas y conserva lo escrito.

5. **Anulación.** Aplicar el inverso de `quantity` por línea, con tipo `VOID` y la serie ANU existente. Rechazar si ya está anulado, si el original no es `OUT` o `ADJUST`, o si algún saldo quedaría negativo.

6. **Seguridad.** Autorizar en `SecurityConfiguration` y con `@PreAuthorize`, según la tabla de la SPEC. Obtener la empresa de la sesión y filtrar cada consulta por ella.
   - **Separación de módulos:** limitar el servicio nuevo a `OUT`, `ADJUST` y las anulaciones de esos tipos. Limitar `ReceiptService.get` a `RECEIPT` y sus anulaciones: hoy devuelve cualquier movimiento por identificador, lo que expondría salidas y ajustes a quien solo tiene permiso de entradas.
   - **Orden de reglas:** al integrar STK-007, las reglas de `/api/inventory/receipts` y `/api/inventory/movements` deben ir antes de su regla general `/api/inventory/**`.

7. **Interfaz.** Crear `frontend/src/features/movements/` siguiendo los patrones de `features/receipts`: listado con filtros, editor por tipo, revisión, comprobante y anulación. Reutilizar `amount`, `receiptDate` y los estilos de entradas donde apliquen. Guardar el borrador y la clave de reintento por usuario, empresa y tipo. Agregar la opción de menú y la ruta `/inventory/movements` en `CompanyWorkspace.tsx`, y el acceso `movementAccess`.

8. **Validación.** Agregar pruebas de integración con MySQL equivalentes a `ReceiptLocalIntegrationTest`: atomicidad, concurrencia, claves repetidas, saldo cambiado, negativos, saldos históricos, anulación, permisos y aislamiento. Agregar una prueba de que el permiso de entradas no lee movimientos de este módulo. Ejecutar las pruebas de STK-008 y las de frontend, compilar ambos, y verificar en navegador a 320, 375, 390 y 1440 px. Registrar la evidencia en `docs/04-Testing/STK-009/` y presentar la aplicación local para aprobación.

## Riesgos conocidos

- **Migraciones:** STK-007 también usa `V5` y debe renumerarse después de esta `V6` al integrarla. Una base local donde ya se aplicó su `V5` no arrancará con `main`; para desarrollar se usará una base limpia o aislada, sin alterar datos existentes.
- **Rama:** el desarrollo se hará en una rama nueva desde `origin/main`, con integración mediante PR.
