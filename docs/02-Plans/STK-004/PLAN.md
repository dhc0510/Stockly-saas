# PLAN — STK-004 | Gestión de empresas

Estado: aprobado por el propietario en conversación el 2026-11-05 («sI APROBADOS»). Sin implementación ni pruebas ejecutadas; falta indicación explícita de iniciar desarrollo. Referencia: ../../01-Specs/STK-004/SPEC.md. Las propuestas de este PLAN quedan aprobadas mediante esta validación.

## 1. Backend

Propuesta: Java 21, Spring Boot 4.0.x estable, Maven Wrapper, Spring Web, Validation, Data JPA, Security, MySQL Connector/J y Flyway. Fijar revisiones exactas compatibles y publicadas al inicializar, sin snapshots ni cambios mayores silenciosos. MySQL 8.4 LTS. Monolito por módulos de empresas e identidad compartida; sin microservicios.

| Endpoint propuesto | Contrato |
|---|---|
| GET /api/companies | search, status, registeredFrom/To (fechas YYYY-MM-DD), page desde 0, size 20 por defecto y máximo 100; items y total |
| POST /api/companies | company: datos de SPEC; administrator: nombre, usuario, correo y contraseña temporal; 201 con empresa y cuenta sin contraseña |
| GET /api/companies/{id} | Datos de empresa, fecha y resumen del administrador; 200 |
| PATCH /api/companies/{id} | Solo datos editables de empresa y version; 200 |
| POST /api/companies/{id}/suspend o /reactivate | version; nuevo estado y version; 200 |
| POST /api/companies/{id}/delete | confirmationName y version; eliminación confirmada; 204 |

Se propone POST para eliminación confirmada para evitar depender de cuerpos DELETE. Campos JSON camelCase. Errores: code, message, fieldErrors, requestId; 400 entrada inválida, 401 sin sesión, 403 sin permiso, 404 inexistente/ajeno, 409 duplicado o versión obsoleta, 500 fallo interno sin detalles sensibles. Identificadores opacos del servidor.

Alta de empresa y administrador en una transacción. Restricciones únicas resuelven altas simultáneas; no responder éxito parcial. Reintento de alta ya aceptada produce conflicto identificable y permite consultar empresa, sin segunda cuenta. Campo version y bloqueo de fila en cambios de estado/borrado impiden sobrescribir cambios concurrentes; repetir estado actual devuelve estado actual sin nuevos efectos.

## 2. Frontend

React + TypeScript + Vite, React Router para vistas y fetch para API; formularios reutilizables sin otra biblioteca inicialmente. Preservar mockups 02–05, añadiendo selector de identificación y texto de borrado acordados. Controles nativos accesibles, campos etiquetados, errores asociados, foco en diálogos, estados carga/vacío/error/éxito y envío protegido contra doble clic. Registro, detalle, edición y confirmaciones; ningún login/2FA aquí.

Fechas guardadas UTC; filtros por día y visualización en America/Costa_Rica, límites inclusivos del día del usuario. Orden por fecha descendente e ID para desempatar; filtros combinables. Propuesta de búsqueda parcial sin distinguir mayúsculas por nombres y número normalizado; no buscar correos ni datos no solicitados.

## 3. Base de datos

Propuesta MySQL compartido con companies y users. companies: id, commercialName, legalName, identificationType, identificationNormalized UNIQUE, contactEmail, phone nullable, address nullable, status, createdAt, updatedAt, version, accessVersion. users: identidad, companyId, usernameNormalized UNIQUE, emailNormalized UNIQUE, passwordHash y mustChangePassword. Administrador inicial pertenece a una sola empresa; no limitar a un empleado por empresa. companyId obligatorio para cuentas de negocio; identidad SaaS diferenciada y sin permisos implícitos sobre datos operativos.

Toda futura tabla operativa llevará companyId y referencias compatibles con esa empresa. Repositorios operativos filtran por empresa autenticada; gestión global queda en servicio exclusivo del administrador SaaS. MySQL no adopta las políticas RLS de ADR-001. Migraciones versionadas; no generación destructiva automática de esquema.

## 4. Seguridad y dependencia STK-005

Spring Security con autorización de administrador SaaS en cada ruta; cookies de sesión HttpOnly/Secure, CSRF en escrituras y consulta de estado vigente de empresa. Hash de contraseña mediante PasswordEncoder Argon2id y límite de acceso a logs/errores; nunca persistir texto temporal ni devolverlo. Cambiar contraseña y 2FA pertenecen a STK-005.

Suspender incrementa accessVersion y revoca sesiones; reactivar no restaura sesiones antiguas. Desconectados aplican bloqueo al reconectar; no borrar operaciones locales pendientes silenciosamente. Su tratamiento corresponde al flujo offline, no a estas pantallas.

STK-004 puede probar servicios con identidades de prueba, pero no habilitar gestión real ni declarar AC-05/07 completos hasta integrar STK-005. No crear usuarios maestros con contraseña embebida ni rutas públicas para evitar esta dependencia.

## 5. Eliminación e integraciones

Gestión administrativa conectada; no integrar Hacienda ni procesar pagos. Borrar cuenta, sesiones y datos operativos propios dentro de transacción; verificar dependencias y no borrar información de otra empresa. Si hay datos sujetos a conservación, separarlos bajo acceso administrativo restringido, sin acceso del negocio eliminado. No usar cascadas generales.

Política propuesta para aprobar: respaldos cifrados con vencimiento máximo de 30 días; registro técnico mínimo de eliminación por ID opaco y fecha durante esa ventana, separado del respaldo restaurado. Toda restauración se mantiene aislada y reaplica eliminaciones antes de habilitar acceso. No copiar datos personales al registro de control. Probar recuperación y purga antes de producción. Esta política no presupone que ya exista hosting o backups.

En STK-004 no se generan documentos fiscales: no inventar archivos para conservar. Antes de incorporar facturación, definir conservación por tipo y normativa vigente en su SPEC; conservar documentos obligatorios y no incluirlos en el borrado operativo. La referencia oficial consultada describe cinco años y excepciones; no convertirla en un plazo universal para todos los datos: https://www.pgrweb.go.cr/DOCS/NORMAS/1/VIGENTE/D/2010-2019/2015-2019/2019/15BF6/12DE9C.HTML.

## 6. Validaciones técnicas

Aplicar tabla SPEC en cliente/servidor y normalización idéntica para índices únicos. Propuesta: trim en textos salvo contraseña, usuario ASCII [A-Za-z0-9._-], correo máximo 254 caracteres, teléfono máximo 30 con 8–20 dígitos y separadores permitidos; campos opcionales vacíos a null. Sin transformación de contraseña ni relleno de identificaciones. Longitudes adicionales se aprueban con este PLAN. Formato válido no verifica identidad fiscal.

## 7. Pruebas

JUnit y Spring Boot Test para reglas/API; Testcontainers con MySQL real para restricciones, carreras y rollback; Vitest + Testing Library para formularios y Playwright para flujos. Contenedor de prueba requiere runtime compatible; no autoriza despliegues ni uso de Docker en producción.

Cubrir AC-01–08: límites/duplicados, filtros y fechas, estados, permisos e IDs de otra empresa, cancelaciones, conflicto de versiones, reintentos, borrado y excepciones. Revisar teclado, foco, contraste y vistas 375/1440. Guardar comandos/resultados por criterio en docs/04-Testing/STK-004/ al ejecutar; sin marcar aprobaciones por mera compilación.

## 8. Archivos y entrega

Propuesta: backend/pom.xml; backend/src/main/java/com/stockly/{companies,identity}; backend/src/main/resources/db/migration/; pruebas en backend/src/test/. Frontend: package.json, src/features/companies/{pages,components,api}, src/lib/http y pruebas asociadas. No crear todos los archivos por plantilla: solo necesarios.

Tras aprobación actualizar STACK.md y registrar la estrategia multiempresa en 05-Decisions, sin adoptar ADR-001. Implementación inicial fija versiones en manifiestos, wrappers y lockfile. Sin infraestructura productiva, commit ni push en esta etapa.

Fuentes técnicas consultadas: https://docs.spring.io/spring-boot/4.0/system-requirements.html y https://dev.mysql.com/doc/refman/8.4/en/mysql-releases.html. Selecciones anteriores son propuestas para Stockly, no decisiones impuestas por las fuentes.