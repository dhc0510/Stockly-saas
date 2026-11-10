# STK-007 · Validación local

Fecha: 2026-11-10. Base: main, dab6e27. El propietario revisó la vista local y autorizó publicar y cerrar si las pruebas pasaban.

| Criterios | Evidencia |
| --- | --- |
| AC-01, AC-02 | MySQL y MockMvc. Alta, edición, código duplicado, baja, reactivación y borrado condicionado de sucursales. Asignación de accesos por administrador y de artículos pendientes por editor autorizado. Chromium comprobó el flujo del editor sin botones administrativos. |
| AC-03 a AC-07 | MySQL. Saldos por sucursal, disponibles con tres decimales, reservado en cero, filtros y búsquedas por los cuatro campos. Chromium. Consolidado, totales de variantes, selector, categorías, estado agotado, identificación de variante por código y cantidades enteras por unidad. |
| AC-08 | MySQL. Saldo real de la alerta, ocultación individual, conservación de la ocultación al cambiar el nivel deseado, nueva notificación en el ciclo siguiente y resolución al llegar al margen de recuperación. Revocar acceso bloquea consulta y ocultación de la alerta de esa sucursal. Chromium verificó navegación, foco y ocultación al recargar. |
| AC-09 | MySQL. Entradas, salidas, ajustes y ventas modifican el mismo libro por sucursal. Se verificaron las cantidades posteriores, el bloqueo por saldo insuficiente y la preservación del historial. |
| AC-10 | MySQL. Acceso por empresa y sucursal, lectura con permiso de Inventario, configuración con permiso de Catálogo y alcance de sucursal. Chromium. Estados de error de saldo, falta de conexión, búsqueda vacía y recuperación. Vista local aprobada por el propietario. |

Pasaron 18 pruebas de integración en MySQL 8.4 aislado (catálogo operativo de sucursales, reposición, entradas, ventas y movimientos), 28 de Vitest y 10 escenarios de Playwright en Chromium a 375 y 1440 px con API simulada. La compilación de producción pasó. No se usaron dispositivos físicos.

Las reservas siguen en cero hasta que exista un flujo de pedidos que las genere. La precisión de Existencias se documenta en [ADR-013](../../05-Decisions/ADR-013.md).
