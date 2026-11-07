# STK-008 — Validación local

Rama: `codex/stk-008`. Base MySQL 8.0.46 aislada para pruebas. Flyway aplicó las migraciones V1 a V5.

- Backend: 14 pruebas seleccionadas pasaron, incluidas cuatro de STK-008 con MySQL. Se comprobó el registro atómico, el reintento concurrente con una sola entrada, el rechazo de una clave repetida con datos distintos, los saldos históricos, la anulación concurrente, el rechazo por saldo negativo, los permisos y el aislamiento entre empresas.
- Frontend: compilación y nueve pruebas unitarias existentes correctas.
- Edge con Playwright a 375 y 1440 px: lector simulado por código + Enter para unidades y kg, código desconocido, coma decimal, advertencia de línea sin agregar, borrador tras recarga, comprobante, búsqueda y filtro, y anulación vinculada. Se simuló que el servidor confirmó la entrada pero se perdió la respuesta. Al recargar y reintentar, el navegador recibió el mismo folio.
- Búsqueda y barra inferior verificadas a 320, 375, 402 y 1440 px. Fecha del comprobante comprobada sin cortes en las referencias móvil y escritorio.
- El propietario probó la aplicación en navegador con anchos de 320, 375 y 390 px y en escritorio a 1440 px. Probó el ingreso por código simulado, la coma decimal y la recuperación del borrador. La revisión posterior verificó en Edge a 320, 375, 390 y 1440 px las rutas, el aviso del borrador, el foco en la cantidad inválida y la anulación con validación en español. El comprobante histórico ENT-0005 se comprobó a las 7:04 p. m. de Costa Rica. MySQL y JDBC se verificaron en UTC, junto con la fecha de Actividad y la duración de Sesiones. El propietario aprobó la aplicación local. Quedan pendientes la verificación en un iPhone físico y con un lector físico. La suite completa de empresas con Testcontainers requiere Docker.

Para repetir las pruebas con MySQL, configura `STOCKLY_RECEIPTS_TEST_DATABASE=true`, `STOCKLY_CATALOG_TEST_DATABASE=true` y las variables de conexión a una base de pruebas. Desde `backend/`, ejecuta `mvnw.cmd -Dtest=ReceiptLocalIntegrationTest,CatalogLocalIntegrationTest,CompanyValidationTest,DeletionJournalTest test`.
