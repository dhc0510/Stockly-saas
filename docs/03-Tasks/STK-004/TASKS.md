# TASKS — STK-004 | Gestión de empresas

Estado: revisión local de la demo aprobada por el propietario el 2026-11-05; en espera de integración STK-005 y validación operativa. T01–04 verificadas; resto pendiente de integración/validación. Evidencia: ../../04-Testing/STK-004/TEST-STK-004.md. Referencias: ../../01-Specs/STK-004/SPEC.md y ../../02-Plans/STK-004/PLAN.md. Orden de ejecución: cierre → base/BD → backend/seguridad → frontend → pruebas → validación.

## Cierre previo

- [x] T01 Obtener aprobación de SPEC y propuestas del PLAN (incluida retención de respaldos), registrar decisiones aprobadas y recibir indicación de iniciar. Bloquea T02–T10.

## Base de datos

- [x] T02 Preparar base técnica autorizada y migraciones empresa/cuenta, unicidades y relaciones. Depende T01. Termina con migración verificable y modelo compartido con STK-005.

## Backend

- [x] T03 Alta atómica y validaciones de empresa/administrador según tabla aprobada de SPEC, incluidas longitudes y normalización para unicidad. Depende T02. Termina con AC-01/02 verificados.
- [x] T04 Listado, filtros, detalle y edición. Depende T03. Termina con contratos aprobados y AC-03/04 verificados.
- [ ] T05 Suspensión, reactivación y eliminación confirmada. Depende T04 y contrato de acceso STK-005; aplicar política de borrado del PLAN. Termina con AC-05/06 verificados.

## Seguridad

- [ ] T06 Integrar autorización real, aislamiento y tratamiento seguro de contraseña temporal. Depende T02 y contrato STK-005. Debe acompañar T03–T05; termina con AC-07 verificado, sin rutas administrativas sin protección.

## Frontend

- [ ] T07 Listado, detalle, registro y edición responsive según mockups. Depende T03/04/06. Termina con flujos y estados aprobados, sin exponer credenciales.
- [ ] T08 Diálogos de suspensión/reactivación/borrado y manejo de errores/reintentos. Depende T05/06/07. Termina con confirmaciones y navegación por teclado verificadas.

## Pruebas

- [ ] T09 Consolidar pruebas unitarias, integración, API y flujos UI; ejecutar concurrencia y regresión relevante. Depende T03–T08. Incluir pruebas de tipos de identificación, versiones concurrentes y restauración sin reactivar empresas eliminadas. Termina con AC-01–08 comprobados y evidencia en 04-Testing; corregir y repetir si falla.

## Validación final

- [ ] T10 Revisar localmente con propietario, verificar SPEC completa, construcción y pruebas; actualizar checkboxes solo con evidencia. Depende T09. Obtener aprobación final antes de Finalizado; GitHub y Trello se actualizan con autorización correspondiente.