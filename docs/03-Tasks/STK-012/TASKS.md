# TASKS · STK-012

Documentación aprobada por el propietario el 2026-11-07. [SPEC](../../01-Specs/STK-012/SPEC.md) · [PLAN](../../02-Plans/STK-012/PLAN.md) · [Validación local](../../04-Testing/STK-012/VALIDACION-LOCAL.md).

## Base de datos
- [x] Migración V11 con medios por empresa, pagos de venta y devoluciones, con sus restricciones e índices. AC-01 a AC-08.

## Backend
- [x] Configuración de medios de pago con al menos uno activo y auditoría. AC-04.
- [x] Validación y registro de pagos al confirmar, con monto aplicado, vuelto y errores por pago. AC-01 a AC-03.
- [x] Pagos en la huella del reintento. AC-05.
- [x] Anulación con devolución exacta vinculada al movimiento inverso. AC-06 y AC-07.
- [x] Comprobante y listado con el estado del cobro y su filtro. AC-06 y AC-07.
- [x] Resumen por medio y día con días de Costa Rica y acceso por sucursal. AC-08.

## Frontend
- [x] Revisar y cobrar, con pagos combinados, pendiente y vuelto. AC-01 a AC-03.
- [x] Comprobante, columna y filtro de cobro, y Anular venta desde el listado. AC-06 y AC-07.
- [x] Anulación con Devolución del dinero. AC-06.
- [x] Pantallas de Medios de pago y Resumen de cobros con su menú. AC-04 y AC-08.
- [x] Borrador local con pagos. AC-05.

## Seguridad
- [x] Permisos de configurar, cobrar, anular y consultar, y aislamiento entre empresas y sucursales. AC-09.

## Integraciones
- [x] Contrato de pagos comunicado a Gabo el 2026-11-08 en la [tarjeta de STK-014](https://trello.com/c/RHfLlp05).

## Pruebas
- [x] Integración en MySQL, Vitest y Playwright a 320 y 1440 px, con evidencia en `docs/04-Testing/STK-012/`. AC-01 a AC-10.
- [x] Prueba de punta a punta en local con la aplicación conectada a MySQL. AC-10.

## Validación final
- [x] Aplicación en local aprobada por el propietario el 2026-11-07, con el ajuste del resumen (Entró, Salió y Quedó).
