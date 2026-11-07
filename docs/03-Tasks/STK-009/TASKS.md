# TASKS — STK-009 | Salidas, pérdidas y ajustes de inventario

Estado: aprobado por el propietario el 2026-11-07. Desarrollo autorizado el 2026-11-07. [SPEC](../../01-Specs/STK-009/SPEC.md) · [PLAN](../../02-Plans/STK-009/PLAN.md).

## Base de datos y contratos

- [x] Crear la migración `V6` con secuencias, módulo y permisos especiales.
- [x] Extraer las operaciones compartidas de `ReceiptService` sin cambiar el contrato de entradas y confirmar que sus pruebas siguen pasando. AC-11.

## Backend

- [x] Implementar salidas: validación, motivos, rechazo por saldo negativo, folio SAL, atomicidad y reintentos. AC-01, AC-02 y AC-06.
- [x] Implementar ajustes: cantidad contada, diferencia, línea sin diferencia, saldo cambiado y folio AJU. AC-03, AC-04 y AC-06.
- [x] Implementar listado, filtros, búsqueda de artículos y comprobante con datos históricos. AC-05 y AC-08.
- [x] Implementar anulación vinculada con la serie ANU compartida, unicidad y control de saldo negativo. AC-09.

## Seguridad

- [x] Aplicar los permisos de la SPEC en el servidor, con empresa tomada de la sesión. AC-10.
- [x] Limitar `ReceiptService.get` a entradas y sus anulaciones, y el servicio nuevo a sus propios tipos. AC-10 y AC-11.

## Frontend

- [x] Agregar opción de menú, ruta y acceso por permisos. AC-10.
- [x] Implementar listado con filtros, estados de carga, vacío, error y sin resultados, y aviso de borrador. AC-07 y AC-08.
- [x] Implementar el editor de salidas y de ajustes, con lector, validaciones y borrador local. AC-01 a AC-05 y AC-07.
- [x] Implementar revisión, reintento, aviso de saldo cambiado, comprobante y anulación. AC-02, AC-04, AC-06, AC-08 y AC-09.

## Pruebas

- [x] Agregar pruebas de integración con MySQL de salidas, ajustes, concurrencia, claves repetidas, saldos históricos, anulación, permisos y aislamiento. AC-01 a AC-04, AC-06 y AC-08 a AC-11.
- [x] Ejecutar las pruebas de STK-008 y de frontend, y compilar backend y frontend. AC-11.
- [x] Verificar el flujo en navegador a 320, 375, 390 y 1440 px con lector simulado. AC-05, AC-07 y AC-12.
- [x] Guardar la evidencia en `docs/04-Testing/STK-009/VALIDACION-LOCAL.md`.

## Validación final

- [ ] Verificar teclado y flujo en un iPhone físico y probar un lector físico. AC-05 y AC-12.
- [x] Revisar todos los criterios de aceptación contra la SPEC.
- [x] Obtener aprobación de la aplicación local antes de publicar.
