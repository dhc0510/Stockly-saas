# TASKS — STK-005 | Acceso, usuarios y permisos

Estado: SDD aprobado y desarrollo autorizado e iniciado por el propietario el 2026-11-05. T02–T12 verificadas el 2026-11-09 (recorrido real 98/98 y Playwright 8/8); aprobación final del propietario el 2026-11-09. Evidencia: `../../04-Testing/STK-005/TEST-STK-005.md`. Decisiones de cierre: `../../05-Decisions/ADR-012.md`. Referencias: SPEC `../../01-Specs/STK-005/SPEC.md`, PLAN `../../02-Plans/STK-005/PLAN.md`, decisión `../../05-Decisions/ADR-003.md`, mockups `../../06-Mockups/STK-005/`.

## Aprobación previa

- [x] T01 Recibir indicación explícita del propietario para iniciar el desarrollo, además de la aprobación del SDD.

## Base de datos

- [x] T02 Diseñar migración compatible de cuentas, estado/versiones, roles/grants, desafíos, recuperación, dispositivos y sesión JDBC. Verificar preservación de datos STK-004. — Migración V2 en uso; STK-004 verificada sobre ella el 2026-11-09.

## Backend

- [x] T03 Implementar ingreso, desafío por correo, sesión persistente y cierre/revocación. — AC-01, AC-02 y AC-10 verificados, 2026-11-09.
- [x] T04 Implementar contraseña temporal, reemisión administrada para trabajadores, cambio inicial y recuperación. — AC-03 y AC-04 verificados, 2026-11-09.
- [x] T05 Implementar administración de trabajadores, roles y permisos por empresa. — AC-05 a AC-08 verificados, 2026-11-09.
- [x] T06 Implementar política de sesión empresarial y revocación por cambios de acceso. — AC-09 y AC-10 verificados, 2026-11-09.

## Frontend

- [x] T07 Implementar ingreso, verificación, primer cambio y recuperación según mockups aprobados. — Playwright a 375 y 1440 px, 2026-11-09.
- [x] T08 Implementar usuarios, roles/permisos y configuración de sesión responsive. — Playwright a 375 y 1440 px; corregida la tarjeta móvil de Usuarios, 2026-11-09.

## Integración y pruebas

- [x] T09 Integrar servicio de correo aprobado y configurar límites contra abuso. — SMTP genérico verificado con buzón local; límites de 1 por minuto y 5 por hora. Proveedor productivo fuera de alcance (SPEC §9).
- [x] T10 Verificar seguridad, tenant, sesiones indefinidas/revocables, concurrencia, expiración y errores; registrar evidencia en `docs/04-Testing/STK-005/`. — Recorrido real 98/98, 2026-11-09.
- [x] T11 Registrar acciones de acceso/permisos en auditoría y construir su consulta exclusiva para el administrador de empresa, usando el mockup aprobado. — AC-13 verificado, 2026-11-09.

## Validación final

- [x] T12 Revisar todos los criterios de SPEC con el propietario, revisar el diff, construir y ejecutar pruebas pertinentes; corregir y repetir hasta cumplirlos. — AC-01 a AC-13 verificados; build y 28 pruebas unitarias correctas, 2026-11-09.
- [x] T13 Obtener aprobación final local antes de finalizar. Solo entonces actualizar Trello/GitHub bajo las autorizaciones permanentes y reflejar pendientes reales. — Aprobación final del propietario el 2026-11-09 («aprobado push»).
