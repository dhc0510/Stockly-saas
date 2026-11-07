# PLAN — STK-010 | Historial y trazabilidad de movimientos

Estado: aprobado por el propietario el 2026-11-07. Desarrollo autorizado el 2026-11-07. [SPEC](../../01-Specs/STK-010/SPEC.md) · [Mockup aprobado](../../06-Mockups/STK-010/historial-responsive.html).

1. **Persistencia.** Crear la migración `V8`, sin modificar las anteriores. Registrar el módulo `INVENTORY_HISTORY`; la pantalla de roles lo toma del catálogo. No se crean tablas: `inventory_movements` e `inventory_movement_lines` ya guardan folio, tipo, responsable, motivo, referencia, nombre, SKU, unidad, cambio y saldo posterior. Agregar índices para consultar por empresa y fecha, por empresa y responsable, y por empresa y artículo.

2. **Consulta.** Crear `InventoryHistoryService` y `InventoryHistoryController` en `/api/inventory/history`, solo con `GET`:
   - Listado por línea con búsqueda y filtros, `LIMIT` de 26 y orden estable por fecha, movimiento y artículo.
   - Sugerencias de artículos, incluidos los inactivos, y lista de responsables con movimientos en la empresa.
   - Historia por artículo con su saldo actual.
   - Comprobante mediante `InventoryLedger.document`, con el alcance de todos los tipos.

   El navegador envía días de Costa Rica (`YYYY-MM-DD`). El servidor los valida y los convierte a un rango UTC con inicio incluido y fin excluido, usando `America/Costa_Rica`. Los últimos 30 días los calcula el navegador con la fecha de Costa Rica y los envía como rango, para que el periodo mostrado y el consultado sean el mismo.

3. **Seguridad.** Agregar en `SecurityConfiguration` la regla de `/api/inventory/history` antes de la regla general `/api/inventory/**`, y `@PreAuthorize` con `ROLE_COMPANY_ADMIN` o `MODULE_INVENTORY_HISTORY_READ`, `_OPERATE` o `_ADMIN`. Obtener la empresa de la sesión y filtrar cada consulta por ella. Filtrar la lista de responsables también por empresa.

4. **Búsquedas sin tildes.**
   - **Servidor:** aplicar `COLLATE utf8mb4_0900_ai_ci` de forma explícita a las columnas en cada `LIKE` de texto de `InventoryLedger`, `ReceiptService`, `StockMovementService`, `CatalogService`, `InventoryService` y el nuevo servicio. En `CompanyService`, hacer lo mismo con el mecanismo de Criteria disponible en la versión del proyecto. Si no fuera posible, reemplazar esa condición por una consulta equivalente sin cambiar el contrato.
   - **Navegador:** crear `frontend/src/lib/search.ts` con una función que quite tildes y mayúsculas, y usarla en `CatalogPage` e `InventoryPages`. Sin nuevas dependencias.
   - **Códigos:** la búsqueda exacta de código de barras por lector no cambia.

5. **Interfaz.** Crear `frontend/src/features/history/` siguiendo los patrones de `features/receipts` y `features/movements`: listado con filtros, historia por artículo y comprobante de solo lectura. Reutilizar `amount`, `receiptDate` y los estilos existentes donde apliquen. Agregar la opción de menú como última de Inventario, la ruta `/inventory/history` y el acceso `historyAccess`.

6. **Validación.** Agregar `InventoryHistoryLocalIntegrationTest` con MySQL: filas de cada tipo, filtros combinados, límites de día de Costa Rica cerca de medianoche, paginación, datos históricos tras cambiar el catálogo, inactivos, permisos por nivel, separación de módulos y aislamiento. Agregar pruebas de búsqueda sin tildes en cada servicio, y crear la base de prueba con una colación que distinga tildes para demostrar que no depende de la configuración por defecto. Agregar pruebas unitarias de `search.ts`. Ejecutar las pruebas de STK-006, STK-008 y STK-009, compilar ambos, y verificar en navegador a 320, 375, 390 y 1440 px. Registrar la evidencia en `docs/04-Testing/STK-010/` y presentar la aplicación local para aprobación.

## Riesgos conocidos

- **Colación:** `utf8mb4_0900_ai_ci` existe en MySQL 8 en adelante y exige columnas `utf8mb4`. Las columnas de nombres, SKU y códigos ya lo son. También trata la ñ como n, lo que la SPEC acepta. Con `COLLATE` explícito, MySQL puede no usar un índice en esas búsquedas; hoy ya se busca con `LIKE '%texto%'`, que tampoco lo usa, así que no se espera un cambio de rendimiento.
- **Versión de MySQL:** la aprobada es 8.4 LTS y este equipo solo tiene 9.7 disponible. La colación existe en ambas, pero las pruebas en 8.4 siguen pendientes, igual que en STK-009.
- **Volumen:** el listado por línea crece con cada movimiento. La paginación por `OFFSET` es suficiente para los volúmenes esperados y coincide con los demás listados.
- **Datos de Existencias:** STK-007 sigue mostrando saldos en cero. Por eso el saldo actual de la historia por artículo se lee de `inventory_balances`, no de esa pantalla.
- **Rama:** el desarrollo se hará en una rama nueva desde `origin/main`.
