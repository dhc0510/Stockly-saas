# TASKS · STK-017

Aprobado por el propietario el 2026-11-08. Desarrollo autorizado el 2026-11-08; avance publicado en `main` a pedido del propietario, con pruebas de integración pendientes ([evidencia](../../04-Testing/STK-017/VALIDACION-LOCAL.md)). [SPEC](../../01-Specs/STK-017/SPEC.md) · [PLAN](../../02-Plans/STK-017/PLAN.md).

## Base de datos
- [x] Migración V17 con el módulo `REPORTS` y el índice de devoluciones por fecha. AC-06.

## Backend
- [x] Validación de período, período anterior y sucursales autorizadas en hora de Costa Rica. AC-01. — aprobada por el propietario el 2026-11-09 con la evidencia de regresión y del recorrido real; sin prueba de integración dedicada.
- [x] Resumen con anulaciones del mismo día y de otro día, devoluciones en su fecha, variaciones y serie por día o mes. AC-02. — aprobada por el propietario el 2026-11-09 con la evidencia de regresión y del recorrido real; sin prueba de integración dedicada.
- [x] Productos y categorías, vendedores y «Sin ventas». AC-03. — aprobada por el propietario el 2026-11-09 con la evidencia de regresión y del recorrido real; sin prueba de integración dedicada.
- [x] Cobros reutilizando el cálculo de la pantalla Cobros, con la línea «Sin cobro registrado». AC-04.
- [x] Helper `TabularExport` compartido con Por reponer, y exportación de Reportes a Excel (una hoja por reporte) y CSV. AC-05.

## Frontend
- [x] Opciones «Sin acceso» y «Solo lectura» para Reportes en el editor de roles. AC-06.
- [x] Ruta, menú y página Reportes con filtros, pestañas o lista desplegable, indicadores, gráfico con tabla y los cinco reportes en tabla y tarjetas. AC-01 a AC-04.
- [x] Menú Exportar, hoja de impresión y estados de carga, vacío, error y fechas inválidas. AC-05 y AC-06.
- [x] Ajustar el mockup con la línea de anulaciones de ventas anteriores y la nota de Cobros. AC-02 y AC-04.

## Seguridad
- [x] Permisos y aislamiento entre empresas y sucursales en todas las consultas y archivos. AC-06. — aprobada por el propietario el 2026-11-09 con la evidencia de regresión y del recorrido real; sin prueba de integración dedicada.

## Pruebas
- [x] Presentar la aplicación local al propietario antes de ejecutar pruebas.
- [x] Integración en MySQL, regresión de Por reponer, Vitest y Playwright a 320 y 1440 px, con evidencia en `docs/04-Testing/STK-017/`. AC-01 a AC-06. — aprobada por el propietario el 2026-11-09 con la evidencia de regresión y del recorrido real; sin prueba de integración dedicada.

## Validación final
- [x] Aprobación final del propietario e integración en GitHub y Trello autorizadas. Aprobación final el 2026-11-09.
