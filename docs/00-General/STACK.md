# Stockly — Stack y herramientas

Actualizado: 2026-11-05. Stack base aprobado explícitamente por el propietario en conversación. Esta aprobación define tecnologías; no autoriza por sí sola implementar funcionalidades.

## Stack aprobado

| Parte | Tecnología | Uso previsto |
|---|---|---|
| Frontend | React + TypeScript + Vite | Aplicación web de gestión y ventas |
| Backend | Java + Spring Boot | Reglas de negocio y servicios del producto |
| Base de datos del servidor | MySQL | Persistencia central de los datos |
| Operación sin internet | PWA + IndexedDB mediante Dexie | Disponibilidad de la aplicación y almacenamiento local de operaciones pendientes |

La venta sin internet y la sincronización posterior forman parte del alcance general aprobado. Estas tecnologías no resuelven automáticamente duplicados, conflictos de existencias, recuperación local o comprobantes pendientes: sus reglas y mecanismos se definirán en las SPEC y PLAN correspondientes.

El aislamiento de datos entre empresas continúa siendo obligatorio. La estrategia aprobada usa MySQL compartido, companyId y autorización en servicios/repositorios (ADR-002); no se adopta la propuesta anterior basada en PostgreSQL y RLS.

## Herramientas de trabajo acordadas

| Herramienta | Uso |
|---|---|
| Obsidian | Conocimiento y documentación Markdown del proyecto |
| Trello | Planificación y seguimiento del trabajo |
| GitHub | Control de versiones y colaboración sobre código y documentación |

La elección de estas herramientas no implica que sus conexiones, tableros o repositorios estén configurados. Git está inicializado en main con origin https://github.com/dhc0510/Stockly-saas.git; configurar el remoto no implica haber publicado los archivos.

## Decisiones pendientes

- Revisiones exactas de dependencias y herramientas frontend; Java 21, Spring Boot 4.0.x estable y MySQL 8.4 LTS aprobados para STK-004.
- Fijar revisiones compatibles de las herramientas aprobadas en wrappers, manifiestos y lockfile durante la implementación.
- Infraestructura, alojamiento y despliegue.
- Definición detallada de STK-005 (login, 2FA y dispositivos); el contrato de sesión y autorización de STK-004 está aprobado.
- Configurar las herramientas de pruebas aprobadas al iniciar desarrollo.
- Servicios de facturación electrónica y procesamiento de pagos.
- Mecanismos de sincronización y manejo de conflictos durante desconexiones.

El archivo vacío docker-compose.yml no constituye aprobación de Docker. ADR-001 y el PLAN INV-001 existentes son borradores anteriores no aprobados; sus propuestas incompatibles con este stack no deben utilizarse como decisiones vigentes.

La actualización inicial fue solo documental. Con la autorización de desarrollo de STK-004 se inicializaron backend y frontend; sus versiones exactas están en pom.xml, Maven Wrapper, package.json y package-lock.json. Las decisiones adicionales requieren revisión y aprobación del propietario.
## Decisiones aprobadas con STK-004 — 2026-11-05

- Java 21, Spring Boot 4.0.x estable y Maven Wrapper; Spring Web, Validation, Data JPA, Security, Connector/J y Flyway; MySQL 8.4 LTS.
- Frontend: React Router y fetch; formularios reutilizables sin biblioteca adicional inicialmente.
- Seguridad: sesiones con cookies HttpOnly/Secure, protección CSRF, Argon2id y revocación por accessVersion; no sustituye la definición pendiente de STK-005.
- Pruebas: JUnit, Spring Boot Test, Testcontainers con MySQL, Vitest, Testing Library y Playwright. Contenedores de prueba no aprueban Docker para producción.
- Arquitectura y aislamiento: ADR-002. Respaldo y borrado: política aprobada en PLAN STK-004 (máximo 30 días); infraestructura no implementada.

La aprobación documental no autoriza por sí sola comenzar a programar. Los detalles específicos siguen en PLAN STK-004 para evitar duplicación.
## Revisiones fijadas durante STK-004

Spring Boot 4.0.9, Java 21, MySQL de pruebas 8.4.8 y Maven Wrapper 3.3.4 con Maven 3.10.0. Frontend: React 19.3.1, TypeScript 5.9.3, Vite 8.3.6, React Router 7.18.5; Inter y Lucide según Design System. Dependencias transitivas gestionadas por Spring Boot y lockfile npm. No hay servicio de autenticación de producción ni hosting configurados.