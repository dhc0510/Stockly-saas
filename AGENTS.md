# AGENTS.md

## 1. Fuente de verdad

La documentación oficial del proyecto se encuentra en `docs/`.

Antes de tomar decisiones o realizar cambios, consulta únicamente la documentación relevante para la tarea actual.

Documentación general:

- `docs/00-General/PROJECT-OVERVIEW.md`
- `docs/00-General/SCOPE.md`
- `docs/00-General/STACK.md`
- `docs/00-General/CONVENTIONS.md`
- `docs/00-General/DESIGN-SYSTEM.md` (respetar su estado de borrador y pendientes)
- `docs/00-General/AGENT-RULES.md` (reglas detalladas)

No inventes requisitos, funcionalidades, reglas de negocio, decisiones técnicas, flujos, estructuras, datos ni comportamientos.

Si falta información necesaria, existe una ambigüedad importante o hay una contradicción, pregunta antes de decidir.

---

## 2. Flujo SDD

Toda funcionalidad debe seguir este flujo:

SPEC → PLAN → TASKS → CÓDIGO → PRUEBAS → VALIDACIÓN

Ubicación de la documentación:

- Specs: `docs/01-Specs/`
- Plans: `docs/02-Plans/`
- Tasks: `docs/03-Tasks/`
- Decisiones: `docs/05-Decisions/`
- Mockups: `docs/06-Mockups/`
- Testing: `docs/04-Testing/`

No implementes una funcionalidad importante si su comportamiento no está suficientemente definido en su SPEC.

---

## 3. Antes de modificar código

Antes de realizar cambios:

1. Identifica la tarea exacta.
2. Lee la documentación relevante.
3. Lee la SPEC correspondiente.
4. Lee el PLAN correspondiente, si existe.
5. Lee las TASKS correspondientes.
6. Revisa el código existente.
7. Identifica dependencias, riesgos, contradicciones o ambigüedades.

No leas documentación innecesaria si no está relacionada con la tarea actual.

---

## 4. Reglas de implementación

- Modifica solo lo necesario.
- Respeta el alcance definido.
- Respeta la arquitectura existente.
- Respeta las convenciones del proyecto.
- Reutiliza código existente cuando sea adecuado.
- Evita duplicación innecesaria.
- No agregues funcionalidades no solicitadas.
- No realices refactors grandes fuera del alcance.
- No cambies APIs, contratos, esquemas o interfaces sin autorización.
- No elimines código sin entender primero su propósito.
- No introduzcas complejidad innecesaria.

Si detectas una mejora fuera del alcance, repórtala, pero no la implementes automáticamente.

---

## 5. Dependencias

- No agregues dependencias sin necesidad real.
- Verifica primero si el proyecto ya dispone de una solución equivalente.
- No actualices versiones mayores sin autorización.
- Evita dependencias innecesarias o incompatibles.
- Documenta el propósito de cualquier nueva dependencia importante.

---

## 6. Seguridad

- No expongas contraseñas, tokens, claves API, secretos o credenciales.
- No almacenes secretos directamente en el código.
- Usa variables de entorno o el mecanismo de secretos definido por el proyecto.
- Respeta los mecanismos existentes de autenticación y autorización.
- No desactives controles de seguridad para hacer funcionar una funcionalidad.

---

## 7. Pruebas y validación

Antes de considerar una tarea terminada:

- verifica la SPEC;
- verifica los criterios de aceptación;
- ejecuta las pruebas relevantes;
- prueba errores y casos especiales definidos;
- verifica que el proyecto compile o construya correctamente;
- verifica que la funcionalidad pueda ejecutarse correctamente;
- actualiza documentación y TASKS cuando corresponda.

Una funcionalidad no está terminada únicamente porque el código compile.

Debe cumplir lo especificado.

---

## 8. Git

Antes y después de realizar cambios:

- revisa el estado del repositorio;
- identifica archivos modificados previamente;
- no sobrescribas trabajo existente;
- no mezcles cambios no relacionados.

La aprobación local del propietario constituye autorización permanente para commit y push normales según «Cierre automático tras aprobación local». Fuera de esa excepción, no ejecutes sin autorización explícita:

- `push`
- `merge`
- `rebase`
- `reset --hard`
- force push
- eliminación de ramas
- operaciones destructivas
- operaciones remotas que modifiquen el repositorio

---

## 9. Decisiones importantes

Si una decisión importante no está definida:

1. Identifica el problema.
2. Explica brevemente las alternativas relevantes.
3. Pregunta antes de decidir.
4. Después de recibir aprobación, registra la decisión en:

`docs/05-Decisions/`

No conviertas una suposición en una decisión del proyecto.

---

## 10. Subagentes

Los subagentes son opcionales.

Si se utilizan:

- divide únicamente trabajo independiente o claramente separable;
- evita que varios agentes modifiquen los mismos archivos simultáneamente;
- proporciona a cada subagente únicamente el contexto relevante;
- todos deben respetar la SPEC, PLAN, TASKS y este `AGENTS.md`;
- ningún subagente puede cambiar requisitos por su cuenta;
- el agente principal debe revisar e integrar los resultados.

---

## 11. Cuándo detenerse y preguntar

Detente y pregunta si:

- falta un requisito necesario;
- una SPEC es ambigua;
- dos documentos se contradicen;
- documentación y código se contradicen de forma relevante;
- una TASK implica salir del alcance;
- se necesita una decisión importante de arquitectura;
- existe riesgo de perder o sobrescribir trabajo;
- se necesitan credenciales no disponibles;
- el comportamiento esperado no puede determinarse razonablemente.

No adivines.

---

## 12. Regla principal

No programes basándote en suposiciones.

Lee la documentación relevante, sigue:

SPEC → PLAN → TASKS → CÓDIGO → PRUEBAS → VALIDACIÓN

Implementa únicamente lo definido y valida siempre el resultado contra la SPEC.

## 13. Acuerdos de Stockly

- Responde de forma breve, clara y sólida. Mantén SPEC, PLAN y TASKS breves, legibles, completos y sin relleno ni duplicación; ajusta el detalle a la complejidad.
- Usa docs/00-General/SPEC-TEMPLATE.md, PLAN-TEMPLATE.md y TASKS-TEMPLATE.md como guías flexibles; agrega solo contenido necesario sin omitir reglas, seguridad, errores o criterios verificables relevantes.
- Sigue docs/00-General/REGLA-DESARROLLO-POR-FUNCIONALIDAD.md: aclarar → aprobar mockups cuando correspondan → aprobar SPEC/PLAN/TASKS → recibir indicación explícita de desarrollar → pruebas y revisión local → aprobación final → integración autorizada en GitHub y actualización de Trello. No avances de etapa sin aprobación.
- Guarda mockups por funcionalidad en docs/06-Mockups/. Consulta docs/00-General/PROMPT-MOCKUPS-UI-UX.md y DESIGN-SYSTEM.md; presenta versiones móvil y escritorio cuando correspondan. No alteres el logotipo ni fijes propuestas visuales como definitivas sin aprobación.
- El alcance aprobado incluye inventario, ventas, cobros, comprobantes electrónicos y venta sin internet con sincronización posterior para comercios de Costa Rica. No exigir definir previamente un MVP ni cerrar la capacidad de la primera versión: se determinará progresivamente durante el desarrollo, sin una fecha de entrega impuesta. Las reglas específicas se definen por funcionalidad; no asumas pagos electrónicos sin conexión.
- Stack aprobado: React + TypeScript + Vite; Java + Spring Boot; MySQL; PWA + IndexedDB mediante Dexie. Versiones y decisiones complementarias pendientes. Consulta STACK.md.
- Protege aislamiento multiempresa, permisos, trazabilidad e integridad de operaciones.
- Marca [x] únicamente tras implementar y validar con evidencia en docs/04-Testing/. Conserva [ ] para pendientes; no reescribas SPEC o PLAN aprobados para reflejar progreso. Solicita aprobación final antes de completar la funcionalidad.
- Los borradores INV-001, ADR-001 y OPEN-QUESTIONS.md no son decisiones aprobadas; consulta antes de reutilizarlos. No adoptes propuestas antiguas de PostgreSQL/RLS.
- No crees categorías documentales adicionales sin autorización. No inicialices Git, hagas commits ni despliegues productivos sin autorización correspondiente.
- Repositorio remoto: https://github.com/dhc0510/Stockly-saas (origin).
- Trello: https://trello.com/b/wBG2J96c/stockly-desarrollo-del-producto. Mantén referencias consistentes entre tarjetas, documentos y cambios; Finalizado requiere pruebas y aprobación.

## Uso de Trello

Trello se utiliza exclusivamente para gestionar funcionalidades de desarrollo. Las preguntas, aprobaciones, investigaciones y decisiones se tratan en el chat; los acuerdos relevantes se documentan. No crear tarjetas independientes para esos trámites ni convertir propuestas no aprobadas en funcionalidades confirmadas.


## Cierre automático tras aprobación local

Regla permanente aprobada por el propietario el 2026-11-05: cuando apruebe explícitamente la aplicación funcionando en local, esa aprobación autoriza hacer el commit y push normales de los cambios relacionados a origin (https://github.com/dhc0510/Stockly-saas) y actualizar la tarjeta correspondiente en Trello, sin volver a pedir permiso para esas acciones.

Antes de publicar, verificar pruebas pertinentes, alcance del diff, documentación, ausencia de secretos y trabajo ajeno. Subir únicamente los cambios relacionados, enlazar el commit o PR en Trello y verificar ambas actualizaciones. Mantener el flujo de ramas/PR y las protecciones existentes; esta regla no autoriza merge, force push, rebase, borrados, cambios de permisos ni despliegues productivos.

Reflejar el estado real: Finalizado únicamente si todos los criterios obligatorios están verificados y aprobados. Si se aprobó una demo o quedan dependencias, publicar el avance aprobado y actualizar Trello a En espera de otras funcionalidades para terminar lo que falte, indicando pendientes. No simular una finalización completa.

Si falta conexión, credenciales o hay un conflicto que impida publicar, conservar el trabajo y comunicar el bloqueo concreto y qué actualización falta; no pedir nuevamente la autorización ya concedida ni afirmar que se publicó sin comprobarlo.

## Inicio de sesión de trabajo: copia actualizada

Al iniciar cada nueva sesión de trabajo del proyecto, recordar al usuario que debe actualizar su copia con la última versión publicada en GitHub antes de desarrollar; no repetir el aviso en cada mensaje de una sesión ya verificada.

Antes de modificar archivos, revisar git status, rama y origin; consultar el remoto con git fetch y comparar HEAD con la rama remota correspondiente. Fetch está autorizado como consulta que no modifica el servidor ni los archivos de trabajo; no ejecutar pull, merge o rebase por este recordatorio sin autorización correspondiente.

Si la copia está atrasada, detener el desarrollo dependiente y ayudar al usuario a actualizarla preservando sus cambios. Si hay cambios sin guardar, divergencia, repositorio sin commits, remoto sin versión publicada o falta de conexión, explicar la situación y resolverla antes de afirmar que la copia está al día. No descartar trabajo, usar reset --hard ni sobrescribir archivos. Un repositorio nuevo sin publicación no tiene todavía una última versión que descargar; informarlo sin inventarla.

## Recordatorio al cerrar la sesión

Al terminar cada sesión de trabajo, recordar al usuario que la última versión debe quedar publicada en GitHub. Informar explícitamente si los cambios ya están subidos y, cuando corresponda, indicar rama y commit; si no lo están, explicar qué queda pendiente. No afirmar publicación sin verificar el remoto.

Este recordatorio complementa el cierre automático: después de aprobar la aplicación en local, hacer commit/push y actualizar Trello con la autorización permanente existente. Sin esa aprobación, recordar lo pendiente sin publicar por iniciativa propia ni confundir el recordatorio con autorización adicional.
