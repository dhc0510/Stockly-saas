# TASKS — STK-007 | Consulta de existencias y sucursales

Estado: validación local aprobada para publicar. [Evidencia](../../04-Testing/STK-007/VALIDACION-LOCAL.md). [SPEC](../../01-Specs/STK-007/SPEC.md) · [PLAN](../../02-Plans/STK-007/PLAN.md).

## Contratos y datos

- [x] Acordar con STK-008/009 el saldo físico por artículo/variante/sucursal y con STK-011 el descuento de venta por sucursal.
- [x] Acordar el módulo/permisos de inventario con STK-005 y mantener reservado en cero hasta que exista un flujo de pedidos; disponible = existencia física.
- [x] Persistir sucursales, código único, estado, asignación de usuarios, relación artículo-sucursal y mínimos/umbrales con máximo dos decimales.
- [x] Persistir estados de alerta por destinatario y controlar repetición de 24 h, ocultación personal y resolución automática.

## Backend y seguridad

- [x] Implementar alta/edición/desactivación/borrado condicionado de sucursales y asignación de acceso solo para administrador de empresa.
- [x] Implementar consulta, búsqueda, filtros, orden y agregados por empresa, artículo/variante y sucursales autorizadas.
- [x] Calcular disponible y estados; validar configuración de mínimo, umbral bajo, recuperación y criterio de agotado.
- [x] Implementar alertas de mínimo/agotado, destinatarios por sucursal, ciclo de 24 h, enlace de destino y ocultación individual.
- [x] Verificar autorización en servidor, tenant desde la sesión, privacidad de sucursales restringidas y transacciones sin cambios parciales.

## Frontend

- [x] Añadir Existencias y Sucursales al menú de STK-005 en escritorio y panel lateral móvil.
- [x] Implementar vista consolidada/por sucursal, producto/variantes, saldos, búsqueda, filtros, orden y estados accesibles.
- [x] Implementar campanita, acceso directo a producto/sucursal y ocultación por usuario.
- [x] Implementar gestión de sucursales, configuración de accesos y umbrales, con carga, vacío, error y confirmaciones.

## Ajustes para revisión local

- [x] Verificar el saldo real de la campanita, ocultación personal hasta el siguiente ciclo, revocación de acceso y navegación a la variante exacta.

## Pruebas y validación

- [x] Probar umbrales exactos, saltos de cantidad, recuperación, precisión por unidad base y criterio configurable de agotado.
- [x] Probar alertas repetidas, resueltas y ocultas por usuario; verificar permisos y destinatarios por sucursal.
- [x] Probar empresas/sucursales cruzadas, usuario con acceso parcial, agregados de variantes y venta sin saldo suficiente al integrar STK-011.
- [x] Probar baja/alta/borrado de sucursales, productos pendientes de asignación y preservación del historial.
- [x] Probar responsive, teclado, foco, contraste/etiquetas y estados de interfaz según mockups.
- [x] Registrar evidencia de pruebas en `docs/04-Testing/STK-007/`, revisar todos los AC y solicitar aprobación final del propietario.
