# TASKS · STK-016

Aprobado por el propietario el 2026-11-08. Desarrollo autorizado por el propietario el 2026-11-08. [SPEC](../../01-Specs/STK-016/SPEC.md) · [PLAN](../../02-Plans/STK-016/PLAN.md).

## Base de datos
- [x] Migración V16 con nivel deseado y «No reponer» (quién y cuándo) por producto y sucursal. AC-02 y AC-04.

## Backend
- [x] Validar y guardar nivel deseado y «No reponer» en la configuración de alertas, con auditoría. AC-02 y AC-04.
- [x] Excluir de las alertas los productos con «No reponer» y reevaluarlos al desmarcar. AC-04.
- [x] Lista Por reponer con filtros, orden, totales, contador y cálculo de «A pedir». AC-01 y AC-03.
- [x] Exportación a Excel y CSV con los mismos filtros y neutralización de fórmulas. AC-05.

## Frontend
- [x] Página Por reponer con menú y contador, resúmenes, filtros, tabla, tarjetas y estados de carga, vacío y error. AC-01, AC-03 y AC-06.
- [x] Sección Reposición en la ventana de alertas, con sugerencia en vivo, y etiqueta «No se repone» en Existencias. AC-02 y AC-04.
- [x] Sección «No se reponen», menú Exportar y vista de impresión. AC-04 y AC-05.

## Seguridad
- [x] Permisos de consulta y configuración y aislamiento entre empresas y sucursales en lista, contador y archivos. AC-06.

## Pruebas
- [x] Presentar la aplicación local al propietario antes de ejecutar pruebas.
- [x] Integración en MySQL, Vitest y Playwright a 320 y 1440 px, con [evidencia](../../04-Testing/STK-016/VALIDACION-LOCAL.md). AC-01 a AC-06.

## Validación final
- [x] Aprobación final del propietario el 2026-11-08 e integración en GitHub (`main`) y Trello autorizadas.
