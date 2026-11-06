# TASKS — STK-007 | Consulta de existencias y sucursales

Estado: propuesta para aprobación. No se ha autorizado desarrollo. [SPEC](../../01-Specs/STK-007/SPEC.md) · [PLAN](../../02-Plans/STK-007/PLAN.md).

## Contratos y datos

- [ ] Acordar con STK-008/009 el saldo físico por artículo/variante/sucursal y con STK-011 el descuento de venta por sucursal.
- [ ] Acordar el módulo/permisos de inventario con STK-005 y confirmar origen de pedidos reservados; mientras no exista, disponible = existencia física.
- [ ] Persistir sucursales, código único, estado, asignación de usuarios, relación artículo-sucursal y mínimos/umbrales con máximo dos decimales.
- [ ] Persistir estados de alerta por destinatario y controlar repetición de 24 h, ocultación personal y resolución automática.

## Backend y seguridad

- [ ] Implementar alta/edición/desactivación/borrado condicionado de sucursales y asignación de acceso solo para administrador de empresa.
- [ ] Implementar consulta, búsqueda, filtros, orden y agregados por empresa, artículo/variante y sucursales autorizadas.
- [ ] Calcular disponible y estados; validar configuración de mínimo, umbral bajo, recuperación y criterio de agotado.
- [ ] Implementar alertas de mínimo/agotado, destinatarios por sucursal, ciclo de 24 h, enlace de destino y ocultación individual.
- [ ] Verificar autorización en servidor, tenant desde la sesión, privacidad de sucursales restringidas y transacciones sin cambios parciales.

## Frontend

- [ ] Añadir Existencias y Sucursales al menú de STK-005 en escritorio y panel lateral móvil.
- [ ] Implementar vista consolidada/por sucursal, producto/variantes, saldos, búsqueda, filtros, orden y estados accesibles.
- [ ] Implementar campanita, acceso directo a producto/sucursal y ocultación por usuario.
- [ ] Implementar gestión de sucursales, configuración de accesos y umbrales, con carga, vacío, error y confirmaciones.

## Pruebas y validación

- [ ] Probar umbrales exactos, saltos de cantidad, recuperación, dos decimales y criterio configurable de agotado.
- [ ] Probar alertas repetidas, resueltas y ocultas por usuario; verificar permisos y destinatarios por sucursal.
- [ ] Probar empresas/sucursales cruzadas, usuario con acceso parcial, agregados de variantes y venta sin saldo suficiente al integrar STK-011.
- [ ] Probar baja/alta/borrado de sucursales, productos pendientes de asignación y preservación del historial.
- [ ] Probar responsive, teclado, foco, contraste/etiquetas y estados de interfaz según mockups.
- [ ] Registrar evidencia de pruebas en `docs/04-Testing/STK-007/`, revisar todos los AC y solicitar aprobación final del propietario.
