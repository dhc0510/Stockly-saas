# TASKS · STK-018

Aprobado por el propietario el 2026-11-09. Desarrollo autorizado el 2026-11-09; avance aprobado en local y publicado en `main` con pruebas de integración pendientes ([evidencia](../../04-Testing/STK-018/VALIDACION-LOCAL.md)). [SPEC](../../01-Specs/STK-018/SPEC.md) · [PLAN](../../02-Plans/STK-018/PLAN.md).

## Base de datos
- [x] Migración V18 con configuración, planes, suscripciones, pagos, avisos y facturas de Stockly, y la prueba inicial de las empresas existentes. AC-01 a AC-06.

## Backend
- [x] Planes y configuración del SaaS con validaciones y actividad. AC-01. — aprobada por el propietario el 2026-11-09 con la evidencia de regresión y del recorrido real; sin prueba de integración dedicada.
- [x] Estados por fechas, registro y anulación de pagos con su período, de forma idempotente, y cancelación. AC-03 y AC-04. — aprobada por el propietario el 2026-11-09 con la evidencia de regresión y del recorrido real; sin prueba de integración dedicada.
- [x] Tarea programada: transiciones, cierre de sesiones al suspender y avisos por correo sin repetir. AC-04 y AC-06. — aprobada por el propietario el 2026-11-09 con la evidencia de regresión y del recorrido real; sin prueba de integración dedicada.
- [x] Límites de la prueba en productos, usuarios y sucursales (`TRIAL_LIMIT`). AC-02.
- [x] Bloqueo de ingreso y sesiones por suscripción suspendida, con mensajes por rol. AC-04. — aprobada por el propietario el 2026-11-09 con la evidencia de regresión y del recorrido real; sin prueba de integración dedicada.
- [x] Facturación de Stockly: emisor configurable, factura por pago, nota de crédito al anular, despacho, PDF, XML y correo. AC-05. — aprobada por el propietario el 2026-11-09 con la evidencia de regresión y del recorrido real; sin prueba de integración dedicada.
- [x] API del administrador de Stockly y de «Mi suscripción». AC-07.

## Frontend
- [x] Panel, Planes, Configuración (incluida la facturación de Stockly) y Empresas con suscripción. AC-01 y AC-07.
- [x] Sección Suscripción en el detalle de empresa, con historial y facturas, registrar pago, anular y cancelar. AC-03, AC-05 y AC-07.
- [x] Franjas de aviso, ventana de límite, «Mi suscripción» y pantalla de ingreso suspendido. AC-02, AC-04, AC-06 y AC-07. — aprobada por el propietario el 2026-11-09 con la evidencia de regresión y del recorrido real; sin prueba de integración dedicada.

## Seguridad
- [x] Permisos `SAAS_ADMIN` y `COMPANY_ADMIN`, aislamiento entre empresas, secretos cifrados y actividad registrada. AC-08. — aprobada por el propietario el 2026-11-09 con la evidencia de regresión y del recorrido real; sin prueba de integración dedicada.

## Pruebas
- [x] Presentar la aplicación local al propietario antes de ejecutar pruebas.
- [x] Integración en MySQL con Hacienda simulada, regresión de STK-004, STK-005 y STK-013, Vitest y Playwright a 375 y 1440 px, con evidencia en `docs/04-Testing/STK-018/`. AC-01 a AC-08. — aprobada por el propietario el 2026-11-09 con la evidencia de regresión y del recorrido real; sin prueba de integración dedicada.

## Validación final
- [x] Aprobación final del propietario e integración en GitHub y Trello autorizadas. Aprobación final el 2026-11-09.

## Reverificación del 2026-11-11
- [x] Corregir el envío de avisos (columna `email_normalized`) con prueba de integración que falla con el código anterior. AC-06.
- [x] Recorrido real 62/62 y Playwright propio `subscriptions.spec.ts` 6/6 a 375 y 1440 px. AC-01 a AC-08.
- [x] Requisitos previos a producción registrados en ADR-014.
- [x] Aprobación final del propietario el 2026-11-11 («Aprobadas, publicar»).
