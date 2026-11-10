# STK-005 — Verificación

Fecha: 2026-11-09, Costa Rica. Se verificó el código de `main` en `d8367ab` contra la [SPEC](../../01-Specs/STK-005/SPEC.md). Las decisiones de cierre están en [ADR-012](../../05-Decisions/ADR-012.md).

## Entorno

- React + Vite en 127.0.0.1:5173, Spring Boot (Java 21) en 8080 y MySQL 9.7 local (`stockly_demo`).
- Correo: buzón SMTP local de prueba; no se envía nada afuera.
- Navegador: Microsoft Edge con Playwright.

## Recorrido real

Pasaron 98 de 98 comprobaciones. El script crea dos empresas reales (A y B) con el administrador de Stockly, sus administradores, roles y nueve trabajadores. Lee los códigos y enlaces del buzón local. No imprime contraseñas, códigos ni enlaces.

Preparación ajena a STK-005: la prueba gratuita de STK-018 admite dos usuarios. Por eso se marcó como pagada la suscripción de las dos empresas de prueba. Para simular vencimientos se ajustaron fechas directamente en MySQL: código, enlace, contraseña temporal y última actividad de la sesión.

| Criterio | Comprobaciones | Lo verificado |
|---|---|---|
| AC-01 | 8 | Ingreso por usuario o correo. La contraseña temporal solo permite cambiarla. Contraseña mala y cuenta inexistente dan el mismo mensaje. Un fallo no crea sesión. Cinco fallos pausan 15 min, aun con la correcta. Contraseña nueva de 15–128. |
| AC-02 | 14 | Equipo nuevo pide código de 6 dígitos (10 min, guardado como hash). Código usado, vencido o en pausa no entra. Cinco errores pausan 15 min, y pedir otro código no reinicia el contador. Límite de un correo por minuto. «Recordar» crea confianza de 30 días; sin marcarlo no se crea. Equipo revocado vuelve a pedir código. |
| AC-03 | 11 | Misma respuesta para correo existente, inexistente o con el límite alcanzado. Enlace de un uso, 30 min, guardado como hash. Enlace alterado, usado o vencido no cambia nada. Recuperar cierra todas las sesiones abiertas. Correo no verificado no recibe enlace. |
| AC-04 | 12 | Alta con contraseña temporal de 24 h, guardada como hash y que no vuelve a mostrarse. Sirve una sola vez. Vencida no entra. Reemitir invalida la anterior y revoca sesiones y equipos. No se reemite a inactivos ni al administrador. |
| AC-05 | 4 | Desactivar cierra la sesión y revoca los equipos recordados. La cuenta no ingresa y se conserva en la lista como desactivada. |
| AC-06 | 15 | Niveles por módulo aplicados en servidor; lo no asignado se deniega. Módulo o nivel fuera del catálogo se rechaza. Cambiar el rol o editarlo cierra las sesiones con permisos viejos y, al volver, rigen los nuevos. El permiso especial «gestionar usuarios» habilita esa gestión. |
| AC-07 | 8 | Rol asignado no se elimina y uno libre sí. Rol de otra empresa no se asigna y no queda cuenta parcial. Usuario duplicado se rechaza. La empresa A no ve, desactiva, reasigna ni reemite a trabajadores de B, ni edita o borra sus roles. |
| AC-08 | 5 | Solo el SaaS crea al administrador inicial. No se puede desactivar, cambiar de rol ni reemitir, ni siquiera un trabajador con permiso de gestión. Un `role` o `companyId` enviado por el cliente se ignora. |
| AC-09 | 9 | Empresa nueva con 120 min. Se rechazan 0 y valores que no son múltiplos de 30. Se acepta 24 h (sin máximo). «Nunca» deja las sesiones actuales sin vencimiento y la cookie persistente. Volver a 30 min aplica a las sesiones abiertas, y una sesión inactiva más del límite se cierra. |
| AC-10 | 6 | Cookies HttpOnly, Secure y SameSite=Lax. Escritura sin CSRF da 403. Cerrar sesión invalida la sesión. Sin sesión, 401. El trabajador no usa rutas de Stockly. Suspender la empresa cierra sesiones de administrador y trabajadores. |
| AC-13 | 6 | Se registran alta, desactivación, reemisión, cambio de rol, roles (crear, editar, borrar) y política de sesión, con actor, objetivo y fecha. Sin contraseñas, códigos ni tokens. Solo el administrador fijo la ve; un trabajador con permiso de gestión no, y la otra empresa tampoco. |

## Pantallas (AC-11)

Nueva prueba `frontend/e2e/access.spec.ts`: 8 de 8 pasan en Edge, 4 flujos a 1440 px y 4 a 375 px. Usa la API simulada dentro de Playwright; el comportamiento del servidor está cubierto por el recorrido real.

- **Ingreso:** se recorre con Tab y se envía con Enter. Credenciales malas muestran un error accesible. El código acepta solo dígitos y uno incorrecto muestra error. Contraseñas que no coinciden no se envían.
- **Recuperación:** respuesta genérica, enlace inválido con error, cambio exitoso, y botón desactivado sin token.
- **Administración:** crear un rol con niveles y permisos especiales. Un rol asignado no se puede eliminar. Crear un trabajador muestra la contraseña temporal una sola vez; al ocultar o recargar no reaparece. Desactivar, «Nunca» en sesiones y actividad con los eventos.
- **Trabajador:** no ve Usuarios, Roles, Seguridad ni Actividad, y no se consultan esas rutas.
- **Capturas:** `screenshots/01` a `10`, en escritorio y móvil, sin desbordamiento horizontal.

**Corrección encontrada:** en móvil, la tarjeta de Usuarios ocultaba el selector de rol y los botones «Nueva contraseña» y «Desactivar», y se cortaba por la derecha. La causa era que la vista móvil de Empresas (STK-004) oculta las columnas 3 y 5 de las tablas compartidas. Se corrigió con reglas propias de Usuarios en `frontend/src/styles.css`: los campos van uno debajo de otro y los controles tienen 44 px de alto. La prueba de Empresas sigue pasando (6 de 6).

## Otras verificaciones

- `npm --prefix frontend test`: 28 pruebas, sin fallos.
- `npm --prefix frontend run build`: correcto.
- **AC-12:** STK-005 no permite ingresar sin conexión. El acceso sin red quedó definido e implementado en STK-014, que ya está integrada.

## Aprobación final

El propietario revisó en local y aprobó el 2026-11-09: «aprobado push».

## Pendientes conocidos

- Proveedor de correo de producción: fuera de alcance (SPEC §9).
- Auditoría WCAG completa: mejora general (ADR-012).
- No hay pruebas de integración Java propias de identidad (requieren Docker).
