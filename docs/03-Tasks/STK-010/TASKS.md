# TASKS — STK-010 | Historial y trazabilidad de movimientos

Estado: aprobado por el propietario el 2026-11-07. Desarrollo autorizado el 2026-11-07. [SPEC](../../01-Specs/STK-010/SPEC.md) · [PLAN](../../02-Plans/STK-010/PLAN.md).

## Base de datos

- [x] Crear la migración `V8` con el módulo `INVENTORY_HISTORY` y los índices de consulta.

## Backend

- [x] Implementar el listado por línea con búsqueda, filtros, periodo y paginación. AC-01 a AC-03, AC-05 y AC-06.
- [x] Implementar sugerencias de artículos, incluidos inactivos, y lista de responsables. AC-03 y AC-05.
- [x] Implementar la historia por artículo con saldo actual. AC-04.
- [x] Implementar el comprobante de solo lectura para todos los tipos. AC-07.

## Seguridad

- [x] Aplicar los permisos en el servidor, con la regla antes de `/api/inventory/**` y la empresa tomada de la sesión. AC-08.

## Búsquedas sin tildes

- [x] Aplicar la colación explícita en las búsquedas de servidor de entradas, salidas y ajustes, catálogo, existencias, empresas e historial. AC-09.
- [x] Crear `search.ts` y usarlo en catálogo, categorías y existencias. AC-09.

## Frontend

- [x] Agregar opción de menú, ruta y acceso por permisos. AC-08.
- [x] Implementar el listado con filtros, periodo y estados de carga, vacío, error y sin resultados. AC-01 a AC-03, AC-05 y AC-06.
- [x] Implementar la historia por artículo. AC-04.
- [x] Implementar el comprobante de solo lectura. AC-06 y AC-07.

## Pruebas

- [x] Agregar pruebas de integración con MySQL del historial: tipos, filtros, límites de día, paginación, datos históricos, inactivos, permisos y aislamiento. AC-01 a AC-08.
- [x] Agregar pruebas de búsqueda sin tildes en servidor, sobre una base que distinga tildes, y pruebas unitarias de `search.ts`. AC-09.
- [x] Ejecutar las pruebas existentes y compilar backend y frontend. AC-10.
- [x] Verificar el flujo en navegador a 320, 375, 390 y 1440 px. AC-01 a AC-07 y AC-09.
- [x] Guardar la evidencia en `docs/04-Testing/STK-010/VALIDACION-LOCAL.md`.

## Validación final

- [ ] Verificar el selector de fecha y la lectura de filas en un celular físico. AC-11.
- [x] Revisar todos los criterios de aceptación contra la SPEC.
- [x] Obtener aprobación de la aplicación local antes de publicar.
