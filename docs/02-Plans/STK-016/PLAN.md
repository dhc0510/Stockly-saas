# PLAN · STK-016

Aprobado por el propietario el 2026-11-08. [SPEC](../../01-Specs/STK-016/SPEC.md) · [Mockup aprobado](../../06-Mockups/STK-016/por-reponer-responsive.html).

1. **Base de datos.** Migración `V16__restock.sql`: agregar a `inventory_branch_items` `target_quantity DECIMAL(15,2) NULL`, `restock_paused BOOLEAN NOT NULL DEFAULT FALSE`, `restock_paused_by` y `restock_paused_at`, con CHECK de no negativo y FK del usuario dentro de la empresa. Sin tablas nuevas.
2. **Reglas en `InventoryService`.**
   - Ampliar `StockItem` con nivel deseado y «No reponer» y validarlos con los umbrales en `updateThresholds` (nivel deseado mayor que el mínimo, o que 0 sin mínimo; 2 decimales). El contrato `ThresholdInput` suma campos opcionales: los clientes actuales siguen funcionando.
   - En `syncItemAlerts`, un producto con «No reponer» desactiva sus alertas y no crea nuevas. Al desmarcarlo se reevalúa con la regla actual.
   - Registrar `RESTOCK_PAUSED` y `RESTOCK_RESUMED` en `AuditEventService`.
3. **Por reponer.** Nuevo `RestockService` que reutiliza la consulta de existencias (`items`: sucursales autorizadas, búsqueda sin tildes y categoría) y calcula en servidor `A pedir` y la base del cálculo en `BigDecimal`. Endpoints en `InventoryController` con los mismos permisos que Existencias:
   - `GET /api/inventory/restock` con filtros, totales y la lista de «No se reponen».
   - `GET /api/inventory/restock/count` para el contador del menú.
   - `GET /api/inventory/restock/export?format=xlsx|csv` con los mismos filtros.
4. **Exportación.** CSV generado a mano (RFC 4180, UTF-8, neutralización de fórmulas). Excel con la librería `org.dhatim:fastexcel` (Apache-2.0, solo escritura, sin dependencias pesadas): se agrega en la versión estable vigente al implementar, verificada en Maven Central. Apache POI se descarta por tamaño. Los archivos se generan en memoria con el mismo resultado de la lista para que coincidan con pantalla.
5. **Interfaz.** Nueva página `RestockPage.tsx` en `features/inventory` con ruta `/inventory/restock`, menú con contador, resúmenes, filtros, tabla y tarjetas, sección «No se reponen», menú Exportar y vista de impresión con hoja de estilos `@media print`. Ampliar la ventana de alertas existente con la sección Reposición y la sugerencia en vivo (el mismo cálculo, solo de vista previa). Etiqueta «No se repone» en Existencias.
6. **Validación.** Integración en MySQL (reglas de cálculo, validaciones, alertas con «No reponer», permisos y aislamiento, archivos), Vitest para el cálculo en vivo y Playwright a 320 y 1440 px. Entregar primero la aplicación local al propietario y registrar evidencia en `docs/04-Testing/STK-016/`.

Antes de programar, avisar a Gabo que STK-016 toca `InventoryService`, `InventoryController`, `InventoryPages.tsx` y la migración V16 para no editarlos al mismo tiempo.
