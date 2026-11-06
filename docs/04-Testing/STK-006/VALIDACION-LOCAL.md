# STK-006 — Evidencia de validación local

Fecha: 2026-11-06. Rama local: `codex/stk-006`.

- MySQL 8.0.46 en una base aislada. Flyway aplicó las migraciones V1 a V4.
- `npm run build` completó la compilación de TypeScript y Vite. `npm test` pasó 9 pruebas.
- Maven pasó 10 pruebas seleccionadas, incluidas 3 de catálogo contra MySQL. Se verificaron permisos, aislamiento de imágenes, reserva e historial de SKU, una sola asignación bajo concurrencia, reversión ante errores y conservación de la identidad al convertir a variantes.
- Edge con Playwright, a 375 y 1440 px. Sesión de prueba en la aplicación real, alta de un artículo con imagen, miniatura servida por el backend, conversión a variantes, búsqueda por código de barras, archivo y reactivación.
- Revisión posterior en Edge: gestión de categorías, conservación de productos al eliminar una categoría, importes completos en pantallas estrechas, navegación autenticada y perfil visible al pie del menú. El propietario aprobó el avance local el 2026-11-06.
- La prueba de unidad tras un movimiento usa una marca de movimiento preparada en la base. La integración con los módulos reales de movimientos y ventas queda pendiente.
- La suite completa del backend incluye `CompanyIntegrationTest`, que requiere Docker. En este equipo se ejecutaron las pruebas indicadas arriba con MySQL local.

Para repetir las pruebas de catálogo, apunta `STOCKLY_DB_URL` y las credenciales a una base MySQL de pruebas, configura `STOCKLY_CATALOG_TEST_DATABASE=true` y ejecuta `mvnw.cmd -Dtest=CatalogLocalIntegrationTest test` desde `backend/`.
