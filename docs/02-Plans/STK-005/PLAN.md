# PLAN — STK-005 | Acceso, usuarios y permisos

Estado: aprobado por el propietario el 2026-11-05; desarrollo autorizado e iniciado. Referencia: ../../01-Specs/STK-005/SPEC.md. Decisiones del propietario: ../../05-Decisions/ADR-003.md.

## 1. Backend

Propuesta: ampliar módulo `identity` con Spring Security y autenticación por contraseña, desafío por correo para dispositivo no recordado, recuperación y autorización por permisos. Conservar Java 21, Spring Boot 4.0.x, MySQL 8.4, Argon2id, errores JSON, cookies HttpOnly/Secure/SameSite=Lax y CSRF de STK-004. `spring-boot-starter-session-jdbc` persiste sesiones; `spring-boot-starter-mail` provee el transporte SMTP genérico sin SDK de un proveedor específico.

Contratos tentativos, JSON camelCase:

| Ruta | Uso |
|---|---|
| `POST /api/auth/login` | Validar credenciales; crear sesión si el dispositivo es confiable o iniciar desafío por correo. |
| `POST /api/auth/device-challenges/verify` | Validar código y emitir sesión. |
| `POST /api/auth/logout`, `GET /api/auth/me` | Cerrar sesión y devolver identidad/permisos efectivos. |
| `POST /api/auth/password/first-change` | Cambiar contraseña temporal. |
| `POST /api/auth/password-recovery` | Solicitar enlace con respuesta genérica. |
| `POST /api/auth/password-recovery/complete` | Consumir token y guardar contraseña nueva. |
| `GET/POST /api/users`, `PATCH /api/users/{id}/role`, `POST /api/users/{id}/deactivate` | Listar y crear trabajadores; asignar rol y desactivar cuentas. |
| `POST /api/users/{id}/temporary-password` | Emitir contraseña temporal nueva para trabajador activo. |
| `GET/POST /api/roles`, `PATCH/DELETE /api/roles/{id}` | Consultar, crear, editar y borrar roles sin asignaciones. |
| `GET /api/permissions/modules` | Exponer el catálogo de módulos/permisos registrados por funcionalidades aprobadas. |
| `GET/PUT /api/company/security/session-policy` | Leer y guardar timeout empresarial. |
| `GET /api/audit` | Consultar eventos de la propia empresa, con filtro opcional por acción y paginación; solo administrador fijo. |

Revisar nombres, cuerpos, status codes y compatibilidad con STK-004 antes de fijarlos; son contratos propuestos.

## 2. Frontend

Propuesta React + TypeScript + React Router, `fetch` y formularios existentes: ingreso, código de dispositivo, cambio inicial, recuperación y cambio por enlace, lista/alta/desactivación de trabajadores, roles, asignación de permisos y configuración de sesión. Seguir mockups aprobados de `../../06-Mockups/STK-005/`; respetar 375/1440 px, errores asociados, foco/teclado, doble envío y estados de carga/éxito/error. El administrador inicial y el acceso SaaS se aprovisionan fuera de autorregistro.

## 3. Base de datos

Propuesta de migración Flyway aditiva y compatible con STK-004:

- Ampliar `users` para distinguir `SAAS_ADMIN`, `COMPANY_ADMIN` y trabajador; añadir estado y versión de seguridad. Mantener unicidad global de usuario/correo, `company_id` y contraseña hash. El administrador inicial conserva rol fijo, sin cuenta administradora adicional.
- `company_roles` y grants de permisos por módulo/acción, ambos con ámbito de empresa. Claves de permiso provienen del catálogo central aprobado.
- `auth_challenges` con código protegido, caducidad, contador de intentos y estado de consumo; `password_reset_tokens` de un uso con hash/caducidad; `trusted_devices` con token aleatorio protegido, empresa/usuario, fecha de expiración y revocación.
- En `companies`, timeout de inactividad en minutos con valor inicial 120; nulo representa «Nunca», valores positivos deben ser divisibles entre 30, sin máximo de producto.
- `identity_audit_events` con empresa, actor, tipo de acción, tipo/ID objetivo y fecha; no contiene secretos. Índice por empresa y fecha. Consultas limitadas al tenant y rol administrador fijo, paginadas y ordenadas de más reciente a más antigua.
- Persistir sesiones y atributos en MySQL; almacenar/expirar o revocar por usuario y empresa. No borrar históricos de usuarios al desactivar.

No alterar el esquema existente sin migración que preserve cuentas y datos. Definir índices, claves foráneas, retención de desafíos y compatibilidad MySQL al aprobar el PLAN.

## 4. Seguridad

- Propuesta de dependencia: `spring-boot-starter-session-jdbc`, gestionada por el BOM Spring Boot, para sesiones en MySQL; Spring Boot ofrece auto-configuración JDBC y `Session` admite timeout negativo para no expirar. Configurar cookie persistente para sobrevivir al cierre del navegador, además de HttpOnly/Secure/SameSite y rotación de ID tras autenticar. Limpiar solo sesiones revocadas/expiradas según política aprobada. La persistencia indefinida puede llenar almacenamiento y mantiene valor de una cookie robada; requiere métricas, revocación e instrucciones operativas.
- Timeout por empresa: modificar `maxInactiveInterval` de las sesiones de esa empresa; «Nunca» usa intervalo negativo y no fija vencimiento absoluto. La cookie persistente puede ser eliminada por el usuario, navegador o limpieza de datos; Stockly no puede garantizar que el navegador conserve datos para siempre.
- Revocar sesiones al recuperar contraseña, cambiar credenciales, desactivar cuenta o suspender empresa; invalidar autorizaciones existentes al cambiar permisos/rol. Tras cambiar correctamente la contraseña temporal, rotar el identificador de sesión y continuar autenticado con los permisos efectivos de la cuenta.
- Permisos derivados en servidor desde identidad, compañía, estado y rol. Rechazar `companyId`, rol o privilegio aportado por el cliente; aislamiento por empresa y denegación por defecto. La consulta de auditoría requiere `ROLE_COMPANY_ADMIN`, no un permiso delegable.
- Códigos y tokens de un solo uso, protegidos en reposo y nunca en logs. No iniciar sesión hasta comprobar código. Throttle de códigos/recovery contra enumeración y abuso.
- Aplicar límites acordados: código de 6 dígitos, 10 min, 5 intentos/15 min; contraseña temporal y reemisión de 24 h; recuperación de 30 min; contraseña con 5 fallos consecutivos y pausa de 15 min; correo limitado a 1 envío/minuto y 5/hora por cuenta.
- Al cambiar la política de inactividad, reevaluar las sesiones existentes en su siguiente actividad con la última actividad registrada; cerrar las que superen el nuevo límite.
- Aprovisionar el administrador SaaS inicial con procedimiento privado de instalación, sin secretos en el repositorio, seed público ni usuario predeterminado.

## 5. Integraciones

Definir una interfaz de correo desacoplada sobre SMTP y configuración por variables de entorno (`SPRING_MAIL_*`, `STOCKLY_MAIL_FROM`). No seleccionar proveedor ni añadir SDK específico hasta revisar servicios disponibles y aprobar proveedor. Desarrollo/pruebas usarían una bandeja de correo controlada localmente; producción requiere credenciales y proveedor pendiente.

## 6. Validaciones técnicas

Validar unicidad, estado, contraseña 15–128, límites/caducidad/uso único, rol de la misma empresa, prohibición de eliminar rol asignado, timeout nulo o múltiplo de 30, y autorización en cada ruta. Operaciones de alta/cambio de rol y sus grants deben ser transaccionales. Los cambios de rol/permisos surtirán efecto sin conservar privilegios obsoletos en sesiones.

## 7. Pruebas

JUnit/Spring Boot Test con MySQL mediante Testcontainers; Vitest/Testing Library y Playwright según herramientas ya aprobadas. Cubrir login, desafío, expiraciones/reintentos, recuperación y sesión persistente; acceso cruzado, permisos por módulo, último administrador, desactivación/revocación, roles asignados, códigos/tokens replay, cookie/CSRF y vistas responsive. Guardar evidencia en `docs/04-Testing/STK-005/` al implementar. No marcar criterios hasta verificar cada uno.

## 8. Archivos / componentes afectados

Propuesta: `backend/pom.xml`, `backend/src/main/java/com/stockly/identity/`, migraciones Flyway y pruebas backend; `frontend/src/features/auth/`, `frontend/src/features/users/`, `frontend/src/features/roles/`, `frontend/src/features/security/`, `frontend/src/lib/http.ts` y pruebas UI. Ajustar `Account`, `AccountRepository`, `StocklyPrincipal`, `SecurityConfiguration` y `CompanyAccessFilter` sin eliminar las protecciones existentes. Nuevos nombres/rutas requieren aprobación con este PLAN.

Fuentes técnicas oficiales consultadas: [Spring Boot — Spring Session](https://docs.spring.io/spring-boot/reference/web/spring-session.html), [Spring Session — sesiones JDBC](https://docs.spring.io/spring-session/reference/api.html), [Spring Session — cookie persistente](https://docs.spring.io/spring-session/reference/configuration/common.html). Riesgos generales: [NIST SP 800-63B](https://pages.nist.gov/800-63-4/sp800-63b.html) y [OWASP Session Management](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html).
