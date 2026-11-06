# Stockly

SaaS para inventario y ventas de micro y pequeños comercios de Costa Rica. La visión incluye cobros, comprobantes electrónicos y venta sin internet con sincronización posterior. El MVP se definirá por funcionalidad.

## Para colaborar

Leer [AGENTS.md](AGENTS.md), [idea general](docs/00-General/PROJECT-OVERVIEW.md), [alcance](docs/00-General/SCOPE.md), [stack aprobado](docs/00-General/STACK.md) y [convenciones](docs/00-General/CONVENTIONS.md). Consultar después solo el contexto relevante para la tarea.

Flujo: aclarar → aprobar mockups cuando correspondan → aprobar SPEC, PLAN y TASKS → recibir indicación de desarrollar → pruebas y revisión local → aprobación final → integración autorizada en GitHub y actualización de Trello.

- [Regla por funcionalidad](docs/00-General/REGLA-DESARROLLO-POR-FUNCIONALIDAD.md).
- Plantillas flexibles: [SPEC](docs/00-General/SPEC-TEMPLATE.md), [PLAN](docs/00-General/PLAN-TEMPLATE.md), [TASKS](docs/00-General/TASKS-TEMPLATE.md). Documentos breves, legibles, completos y sólidos.
- [Design System](docs/00-General/DESIGN-SYSTEM.md), respetando su estado y pendientes; [reglas de mockups](docs/00-General/PROMPT-MOCKUPS-UI-UX.md). Guardar mockups por funcionalidad en docs/06-Mockups/.
- [Tablero Trello](https://trello.com/b/wBG2J96c/stockly-desarrollo-del-producto). Backlog no equivale a autorización de desarrollo.

Los borradores INV-001 y ADR-001 no son decisiones aprobadas. Documentación Markdown compatible con Obsidian. Backend y frontend de STK-004 inicializados. La versión compartida se publica en origin/main; el historial de Git identifica sus revisiones.
## Ejecutar y verificar STK-004

Requisitos: Java 21, Node 24 y MySQL 8.4. Maven se descarga mediante el Wrapper. Para las pruebas de integración se requiere Docker activo; Testcontainers crea y retira una base aislada, sin usar datos del negocio.

```powershell
./backend/mvnw.cmd -f backend/pom.xml verify
npm --prefix frontend ci
npm --prefix frontend test
npm --prefix frontend run build
```

Navegador (API simulada exclusivamente en las pruebas):

```powershell
cd frontend
npx playwright install chromium
npm run test:e2e
```

Para iniciar servicios, crear una base MySQL vacía y un usuario restringido, cargar en el proceso las variables de .env.example y ejecutar el backend; en otra terminal iniciar el frontend:

```powershell
./backend/mvnw.cmd -f backend/pom.xml spring-boot:run
npm --prefix frontend run dev
```

Vite atiende en http://127.0.0.1:5173 y reenvía /api al backend en localhost:8080. .env.example es una guía: Spring no la carga automáticamente. No guardar valores reales en el repositorio.

**Dependencia explícita:** aún no existe login ni administrador SaaS provisionado (STK-005). La API requiere autenticación y autorización reales; al abrir la aplicación sin esa integración se verá acceso restringido. No se incluyó un bypass ni un usuario de demostración. El contrato de integración entrega una sesión de servidor y un token CSRF a window.stocklySession; el frontend nunca asigna roles. Cookies Secure requieren HTTPS para la futura sesión real.

La eliminación requiere STOCKLY_DELETION_JOURNAL_DIRECTORY, ruta absoluta restringida fuera de los respaldos de la BD. Sin registro independiente durable, el servidor rechaza el borrado. No se ha configurado infraestructura de backups ni producción.

[Resultados, alcance de las pruebas y recuperación](docs/04-Testing/STK-004/TEST-STK-004.md). La funcionalidad sigue pendiente de integración STK-005, verificación operativa y aprobación final.
## Demostración local para revisión

Autorizada por el propietario después de la entrega parcial de STK-004. Ejecutar `npm --prefix frontend run dev:demo` y abrir http://127.0.0.1:5174/companies. No necesita backend, MySQL ni login. El aviso «Demostración local» distingue esta modalidad.

Permite probar listado, búsqueda, filtros, alta, edición, suspensión, reactivación y eliminación confirmada con datos ficticios. Los cambios viven solo en memoria del servidor Vite: sobreviven a recargar la página y desaparecen al reiniciar el servidor. No ingresar información ni contraseñas reales; la contraseña temporal de prueba se valida pero no se retiene ni devuelve.

El simulador se instala solo en el servidor de desarrollo con `--mode demo`, ligado a 127.0.0.1. No reenvía llamadas al backend ni altera sus controles. La compilación productiva no contiene datos ni rutas simuladas. Pruebas específicas: `cd frontend` y `npx playwright test --config playwright.demo.config.ts`.

Esta revisión valida la interfaz y los flujos simulados. Tras los ajustes y su aprobación visual, seguir con STK-005 y luego validar ambas funcionalidades contra el backend real antes de finalizar STK-004.
## Comenzar desde otra computadora

```powershell
git clone https://github.com/dhc0510/Stockly-saas.git
cd Stockly-saas
npm --prefix frontend ci
npm --prefix frontend run dev:demo
```

Abrir http://127.0.0.1:5174/companies para la demo aprobada. Antes de desarrollar, leer AGENTS.md y comprobar que la copia esté actualizada con GitHub, preservando cambios locales. STK-004 continúa pendiente de integración STK-005 y respaldos operativos; no tratar la demo como autenticación o persistencia reales. Las instrucciones completas de backend y pruebas están arriba.