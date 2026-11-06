# TASKS — STK-005 | Acceso, usuarios y permisos

Estado: SDD aprobado y desarrollo autorizado e iniciado por el propietario el 2026-11-05. Referencias: SPEC `../../01-Specs/STK-005/SPEC.md`, PLAN `../../02-Plans/STK-005/PLAN.md`, decisión `../../05-Decisions/ADR-003.md`, mockups `../../06-Mockups/STK-005/`.

## Aprobación previa

- [x] T01 Recibir indicación explícita del propietario para iniciar el desarrollo, además de la aprobación del SDD.

## Base de datos

- [ ] T02 Diseñar migración compatible de cuentas, estado/versiones, roles/grants, desafíos, recuperación, dispositivos y sesión JDBC. Verificar preservación de datos STK-004.

## Backend

- [ ] T03 Implementar ingreso, desafío por correo, sesión persistente y cierre/revocación.
- [ ] T04 Implementar contraseña temporal, reemisión administrada para trabajadores, cambio inicial y recuperación.
- [ ] T05 Implementar administración de trabajadores, roles y permisos por empresa.
- [ ] T06 Implementar política de sesión empresarial y revocación por cambios de acceso.

## Frontend

- [ ] T07 Implementar ingreso, verificación, primer cambio y recuperación según mockups aprobados.
- [ ] T08 Implementar usuarios, roles/permisos y configuración de sesión responsive.

## Integración y pruebas

- [ ] T09 Integrar servicio de correo aprobado y configurar límites contra abuso.
- [ ] T10 Verificar seguridad, tenant, sesiones indefinidas/revocables, concurrencia, expiración y errores; registrar evidencia en `docs/04-Testing/STK-005/`.
- [ ] T11 Registrar acciones de acceso/permisos en auditoría y construir su consulta exclusiva para el administrador de empresa, usando el mockup aprobado.

## Validación final

- [ ] T12 Revisar todos los criterios de SPEC con el propietario, revisar el diff, construir y ejecutar pruebas pertinentes; corregir y repetir hasta cumplirlos.
- [ ] T13 Obtener aprobación final local antes de finalizar. Solo entonces actualizar Trello/GitHub bajo las autorizaciones permanentes y reflejar pendientes reales.
