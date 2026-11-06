# STK-004 — Implementación y verificación

Fecha: 2026-11-05, Costa Rica. Desarrollo autorizado mediante «INICIA EL DESAROLLO». Estado: implementación parcial verificada; pendiente de integración STK-005 y validación operativa. Sin commit: no existe revisión Git que identificar todavía. Los encabezados SDD conservan el estado al aprobar los documentos; este informe registra el avance posterior.

## Entregado

Backend Spring Boot con migración MySQL, alta transaccional empresa/cuenta, contraseña Argon2id, validaciones, unicidades, filtros, detalle/edición, control de versión, suspensión/reactivación y borrado confirmado. API y servicios exigen administrador SaaS; no hay usuario maestro, login ni bypass.

Frontend React con listado y formularios, detalle, filtros, confirmaciones, estados y adaptación 375/1440 px según la dirección visual aprobada. El selector de identificación y las excepciones de borrado reflejan los acuerdos posteriores al mockup. Se usa el nombre de producto en texto: falta un recurso original independiente del logo; no se ha reconstruido el isotipo.

## Verificación reproducible

| Comando desde raíz | Resultado |
|---|---|
| ./backend/mvnw.cmd -f backend/pom.xml verify | 20 pruebas: 13 integración MySQL/API, 4 validación y 3 registro de eliminación; sin fallos ni omisiones. JAR construido |
| npm --prefix frontend test | 9 pruebas de validación, formulario y contrato API; sin fallos |
| npm --prefix frontend run build | TypeScript y Vite correctos |
| npm --prefix frontend run test:e2e | 6 flujos Chromium: 3 en escritorio y 3 en móvil; sin fallos |

Entorno: Windows, Java 21.0.12.1, Node 24.21.0, npm 11.19.0, Docker 29.8.0; MySQL 8.4.8 efímero mediante Testcontainers. Versiones fijadas en backend/pom.xml, Wrapper y frontend/package-lock.json. Los puertos MySQL son temporales y Testcontainers retira sus contenedores.

Backend usa MySQL real, HTTP simulado con MockMvc e identidades de prueba. Navegador usa respuestas API simuladas únicamente dentro de Playwright: no es una sesión productiva ni una prueba integrada frontend→login→backend. La aplicación distribuida no contiene esos datos ni el simulador. Capturas reales de la interfaz en screenshots/01 a 07, variantes desktop/mobile; comprobación de ausencia de desbordamiento horizontal. Se revisaron visualmente listado móvil y detalle escritorio; no se afirma auditoría completa WCAG.

Cobertura relevante: duplicados normalizados, carrera de dos altas, rollback por administrador duplicado, hashes sin contraseña en respuesta, límites de identificación, filtros con zona Costa Rica, búsquedas literales, edición obsoleta, roles/CSRF, bloqueo por accessVersion incluso tras reactivación, nombre exacto de borrado, conservación de otras empresas, restauración sin reactivar eliminadas y rollback de borrado fallido. Navegador: registro→edición→suspensión→reactivación→eliminación; cancelar con Escape conserva estado y restaura foco; un nombre incorrecto no habilita eliminar.

## Criterios y tareas

- AC-01–04 comprobados; T01–04 marcadas con evidencia anterior.
- AC-05/07: mecanismos y pruebas con identidades de prueba presentes; pendientes autenticación/sesiones reales de STK-005. No marcados completos.
- AC-06: borrado y recuperación en base aislada comprobados; faltan infraestructura de respaldos cifrados, vencimiento máximo de 30 días y ensayo operativo. No marcado completo.
- AC-08: flujos y responsive comprobados; pendientes revisión final con el propietario, recurso original del logo y verificación de accesibilidad completa. No marcado completo.
- T05–10 permanecen pendientes: contienen implementación parcial, dependencias y validaciones aún no satisfechas. No marcar Finalizado ni solicitar aprobación final como si estuviera completo.

## Eliminación y recuperación

Configurar STOCKLY_DELETION_JOURNAL_DIRECTORY como ruta absoluta restringida e independiente de respaldos de BD. Sin registro durable se rechaza eliminar. Cada archivo contiene solo ID opaco, fecha y estado PREPARED/COMMITTED/ROLLED_BACK; no nombres, correos ni contraseña. Confirmar o revertir el registro sigue el resultado de la transacción. PREPARED sin resultado o archivo corrupto detienen la recuperación: reconciliar con el estado de origen antes de continuar; nunca asumir que hubo borrado ni reactivar silenciosamente.

En una base RESTAURADA Y AISLADA, con las variables apuntando a ella y al registro independiente vigente, ejecutar antes de habilitar tráfico:

```powershell
java -jar backend/target/stockly-0.0.1-SNAPSHOT.jar --spring.main.web-application-type=none --stockly.reconcile-deletions=true
```

El comando no expone servidor web; reaplica solo eliminaciones confirmadas y omite las revertidas. La prueba simula restauración de filas y valida reconciliación idempotente. No se ha ensayado recuperación de un backup productivo. Mantener registros al menos hasta que expiren todos los backups que puedan contener esas empresas; coordinar su vencimiento con el máximo aprobado de 30 días, sin habilitar backups antiguos. No hay purga automática ni servicio de backups desplegados en esta entrega. Futuros registros fiscales requerirán política específica antes de su integración.

## Pendientes de integración

STK-005 debe proporcionar sesión real, identidad SaaS, token CSRF y principal de empresa con accessVersion. No se habilita gestión real hasta entonces. La API solo confía en autorización de servidor; window.stocklySession transporta CSRF, no permisos. Cookies Secure requieren HTTPS para sesiones reales.

Trello no se pudo actualizar: su conector no está disponible y el navegador alternativo solicita iniciar sesión en el tablero privado. El estado que corresponde después de esta entrega es «En espera de otras funcionalidades para terminar lo que falte», por STK-005; no Finalizado. No se realizaron operaciones remotas en GitHub.

Advertencias del entorno: Mockito usa autoanexado de agente en Java 21 y Flyway informa que MySQL 8.4 es posterior a su última verificación declarada. Migración, validación de esquema y pruebas reales pasaron; no se ocultaron esas advertencias.
## Ampliación autorizada: demostración local

El propietario solicitó probar con datos ficticios sin esperar STK-005. Se añadió modo Vite `demo` en 127.0.0.1:5174, sin backend y con aviso visible. Dos pruebas adicionales, escritorio y móvil, pasaron contra el servidor local simulado (sin interceptores Playwright): registro, recarga, suspensión, reactivación, borrado y ausencia de contraseña en respuestas; ninguna llamada a localhost:8080. Las 9 pruebas unitarias y el build productivo también pasaron tras este cambio.

El simulador está en frontend/demo/server.ts y se registra solo en configureServer con modo demo; no se incorpora a la aplicación productiva. Estado efímero en memoria, sin base ni almacenamiento de contraseñas. La demo quedó abierta para revisión del propietario. La aprobación de esta revisión no sustituye la validación de permisos, sesiones ni persistencia reales.
## Aprobación de la revisión local — 2026-11-05

El propietario confirmó: «Listo, todo aprobado, todo funciono bien», después de probar la demostración local. Quedan aprobadas la interfaz y las operaciones simuladas presentadas de STK-004; no se solicitaron ajustes.

Esta aprobación cierra la revisión de la demo. Se conserva pendiente la integración con autenticación/sesiones reales de STK-005, las comprobaciones operativas de respaldos y los criterios no verificados. No equivale a autorizar commits, push o despliegues, ni a completar automáticamente las tareas dependientes. El siguiente trabajo funcional corresponde a definir STK-005 mediante el flujo SDD.