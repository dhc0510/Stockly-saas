# PLAN · STK-017

Aprobado por el propietario el 2026-11-08. [SPEC](../../01-Specs/STK-017/SPEC.md) · [Mockup aprobado](../../06-Mockups/STK-017/reportes-responsive.html).

1. **Base de datos.** Migración `V17__reports.sql`:
   - Registra el módulo `REPORTS` («Reportes») en `permission_modules`.
   - Agrega el índice `sale_returns(company_id, created_at)` para filtrar las devoluciones por fecha.
   - No crea tablas nuevas: todo se calcula con `sales`, `inventory_movements` (venta, anulación, `actor_id` y `created_at`), `inventory_movement_lines`, `sale_line_prices`, `sale_returns`, `sale_return_lines`, catálogo y sucursales.
2. **Servicio `ReportService`** (nuevo, en `com.stockly.inventory`, solo lectura con `@Transactional(readOnly = true)`).
   - **Período:** valida el período y calcula el anterior con la zona `America/Costa_Rica`, como `SalePaymentService`.
   - **Sucursales:** las resuelve con `InventoryScope`.
   - **Ventas:** consulta las ventas del período y de anulación e identifica las anuladas el mismo día comparando las fechas de venta y anulación en Costa Rica. Con eso arma el resumen, la serie por día o mes, los productos y categorías y los vendedores, en `BigDecimal`.
   - **Cobros:** reutiliza `SalePaymentService.summary` y agrega solo la línea «Sin cobro registrado».
   - **Sin ventas:** parte de `inventory_branch_items` con los artículos activos, excluye los vendidos en el período y busca la última venta.
3. **API** en un nuevo `ReportController`, `/api/reports`, con `@PreAuthorize` para el administrador o `MODULE_REPORTS_*`:
   - `GET /summary`, `/products`, `/payments`, `/sellers` y `/idle`, con `from`, `to`, `branchId` y los filtros propios de cada uno.
   - `GET /export?format=xlsx|csv&report=…` con los mismos filtros.
4. **Exportación.** Se reutilizan fastexcel y la neutralización de CSV de STK-016. Para no duplicar código, la escritura de CSV y Excel de `RestockService` pasa a un helper compartido (`TabularExport`) sin cambiar el resultado de Por reponer. El Excel lleva una hoja por reporte y el CSV solo el reporte pedido.
5. **Roles.** En el editor de roles de `CompanyWorkspace.tsx`, el módulo `REPORTS` ofrece solo «Sin acceso» y «Solo lectura». El backend de roles no cambia.
6. **Interfaz.**
   - **Página:** nueva carpeta `features/reports` con `api.ts`, `ReportsPage.tsx` y `reports.css`.
   - **Ruta y menú:** ruta `/reports` y enlace en «Operación comercial», visibles solo con el permiso.
   - **Pantalla:** filtros de período y sucursal, pestañas en escritorio y lista desplegable en celular, indicadores, gráfico SVG de una serie con tooltip y tabla alternativa, tablas y tarjetas, menú Exportar y hoja de impresión con `@media print`.
   - **Estados:** carga, vacío, error y fechas inválidas.
   - **Mockup:** el bloque «Cómo se forma» suma la línea «Anulaciones de ventas anteriores» y la nota de Cobros se ajusta a la regla de la SPEC; son los únicos cambios.
7. **Validación.**
   - **Integración en MySQL:** reglas de fechas y zona horaria, anulaciones del mismo día y de otro día, devoluciones en su fecha, productos, vendedores, «Sin ventas», coincidencia con Cobros, archivos, permisos y aislamiento. Incluye una prueba de que Por reponer exporta igual después de mover el helper.
   - **Vitest:** formato y validación de períodos.
   - **Playwright:** a 320 y 1440 px.
   - **Revisión y evidencia:** primero se entrega la aplicación local al propietario y después se registra la evidencia en `docs/04-Testing/STK-017/`.

Antes de programar, avisar a Gabo que STK-017 toca `RestockService` (helper de exportación), `CompanyWorkspace.tsx` y la migración V17.
