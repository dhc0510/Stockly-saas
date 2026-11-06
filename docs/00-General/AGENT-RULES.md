# PROMPT MAESTRO — SDD + CONTEXT ENGINEERING + AGENTE IA
## 1. Objetivo general
Trabaja en este proyecto siguiendo **Specification-Driven Development (SDD)** y buenas prácticas de **Context Engineering**.

La documentación oficial ubicada en `docs/` es la **fuente de verdad del proyecto**.

No inventes requisitos, reglas de negocio, funcionalidades, decisiones técnicas, flujos, estructuras, datos ni comportamientos que no estén definidos.

Si una decisión necesaria no está documentada, es ambigua, contradictoria o admite varias interpretaciones razonables, pregunta antes de decidir.

No conviertas una suposición en una decisión del proyecto.

---
# 2. Configuración inicial obligatoria
Al recibir este prompt por primera vez:

1. Verifica que estás trabajando desde la **raíz del proyecto**.

2. Verifica que existe la carpeta `docs/`.

3. Verifica que existe esta estructura:

```text

docs/
├── 00-General/
├── 01-Specs/
├── 02-Plans/
├── 03-Tasks/
├── 04-Testing/
└── 05-Decisions/

```

4. Si alguna carpeta no existe, créala.

5. Verifica que `docs/` forme parte del repositorio Git y no esté excluida mediante `.gitignore`.

6. Crea o actualiza el archivo `AGENTS.md` en la raíz del proyecto con las reglas permanentes indicadas en la sección siguiente.

7. Verifica que dentro de `docs/00-General/` existan, cuando sean necesarios:

```text

PROJECT.md

SCOPE.md

STACK.md

CONVENTIONS.md

```

8. Si alguno no existe, no inventes su contenido. Créalo únicamente si hay información suficiente para completarlo; en caso contrario, pregunta.

9. Guarda este prompt maestro completo en:

```text

docs/00-General/AGENT-RULES.md

```

10. No sobrescribas documentación existente sin revisar primero su contenido.

---
# 3. AGENTS.md obligatorio
El archivo `AGENTS.md` de la raíz debe contener únicamente las instrucciones permanentes necesarias para trabajar correctamente en este proyecto.

Debe mantenerse breve y actuar principalmente como mapa hacia la documentación oficial.

Debe incluir como mínimo:

```md

# AGENTS.md
## Fuente de verdad
La documentación oficial del proyecto vive en `docs/`.

No inventes requisitos, reglas de negocio, funcionalidades, decisiones técnicas ni comportamientos.

Si falta información necesaria o existe una ambigüedad importante, pregunta antes de decidir.

## Flujo SDD
Toda funcionalidad debe seguir:

SPEC → PLAN → TASKS → CÓDIGO → PRUEBAS → VALIDACIÓN

## Documentación
- Contexto general: `docs/00-General/`

- Specs: `docs/01-Specs/`

- Plans: `docs/02-Plans/`

- Tasks: `docs/03-Tasks/`

- Testing: `docs/04-Testing/`
- Decisions: `docs/05-Decisions/`

Consulta únicamente la documentación relevante para la tarea actual.

## Implementación
- Modifica solo lo necesario para cumplir la tarea.

- Respeta el stack, las convenciones, contratos y patrones existentes.

- No agregues funcionalidades no solicitadas.

- No realices cambios importantes fuera del alcance sin autorización.

## Validación
Antes de considerar una tarea terminada:

- verifica la SPEC;

- verifica los criterios de aceptación;

- ejecuta las pruebas relevantes;

- verifica que el proyecto compile o construya;

- actualiza la documentación cuando corresponda.

## Estado SDD
Mantén los checkboxes de TASKS y criterios de aceptación sincronizados con el estado real del trabajo.

- Marca `[x]` únicamente después de implementar y validar el elemento correspondiente.
- Mantén `[ ]` cuando el elemento esté pendiente, parcialmente implementado o todavía no validado.
- No alteres el contenido aprobado de la SPEC o el PLAN para reflejar progreso; actualiza únicamente checkboxes ya existentes cuando corresponda.
- La evidencia de Testing debe respaldar los criterios de aceptación marcados como cumplidos.

## Git
- Revisa el estado del repositorio antes y después de realizar cambios.

- No sobrescribas trabajo existente.

- No ejecutes operaciones destructivas ni remotas sin autorización explícita.

```

No copies toda la documentación del proyecto dentro de `AGENTS.md`.

---
# 4. Estructura de documentación
Usa como estructura documental base exactamente:

```text

docs/
├── 00-General/
├── 01-Specs/
├── 02-Plans/
├── 03-Tasks/
├── 04-Testing/
└── 05-Decisions/

```

Uso de cada carpeta:

- `00-General/` → contexto estable del proyecto.

- `01-Specs/` → qué debe hacer cada funcionalidad.

- `02-Plans/` → cómo se implementará técnicamente cada SPEC.

- `03-Tasks/` → trabajo concreto, pequeño y ejecutable.

- `04-Testing/` → casos de prueba, estrategia y validaciones relevantes.
- `05-Decisions/` → decisiones técnicas o arquitectónicas relevantes que deban conservarse.

- Usa exactamente `docs/04-Testing/` para documentación de pruebas y `docs/05-Decisions/` para decisiones técnicas o arquitectónicas.

No crees carpetas adicionales por defecto.

Solo agrega una nueva categoría documental si el proyecto realmente lo necesita y existe autorización para hacerlo.

---
# 5. Documentación general
La carpeta `docs/00-General/` debe contener únicamente información estable y reutilizable.

Estructura recomendada:

```text

docs/00-General/

├── PROJECT.md

├── SCOPE.md

├── STACK.md

├── CONVENTIONS.md

└── AGENT-RULES.md

```

## PROJECT.md
Debe responder:

> ¿Qué estamos construyendo?

Puede contener:

- nombre del proyecto;

- objetivo;

- problema que resuelve;

- usuarios principales;

- funcionalidades principales;

- contexto general necesario.

No debe convertirse en una SPEC detallada.

## SCOPE.md
Debe definir:

- qué está incluido;

- qué está fuera de alcance;

- límites relevantes del proyecto.

Su propósito principal es impedir que el agente agregue funcionalidades por iniciativa propia.

## STACK.md
Debe indicar las tecnologías y decisiones técnicas generales ya definidas, por ejemplo:

- backend;

- frontend;

- base de datos;

- infraestructura;

- seguridad;

- herramientas principales.

No expliques tecnologías genéricas. Documenta únicamente lo que el proyecto utiliza.

## CONVENTIONS.md
Debe definir las convenciones estables de desarrollo que deben mantenerse en todo el proyecto.

Puede incluir, cuando corresponda:

- nombres de archivos, clases, métodos y variables;

- estructura de paquetes y carpetas;

- convenciones de endpoints y contratos;

- formato de DTOs y respuestas;

- manejo de errores;

- organización de componentes frontend;

- estilo de código;

- convenciones de testing;

- convenciones Git y formato de commits;

- patrones reutilizables del proyecto.

No debe contener requisitos funcionales ni decisiones específicas de una sola funcionalidad.

Las convenciones específicas de una funcionalidad deben permanecer en su SPEC o PLAN cuando corresponda.

## AGENT-RULES.md
Contiene este prompt maestro completo.

Sirve como referencia detallada.

`AGENTS.md` debe permanecer corto y apuntar a esta documentación cuando sea necesario.

---
# 6. Flujo SDD obligatorio
Toda funcionalidad debe seguir este orden:

```text

SPEC → PLAN → TASKS → CÓDIGO → PRUEBAS → VALIDACIÓN

```

No saltes directamente al código si la funcionalidad todavía no está suficientemente definida.

---
# 7. SPEC
La SPEC define **qué debe hacer la funcionalidad**.

Debe incluir únicamente lo necesario para definir su comportamiento.

Cuando aplique:

- objetivo;

- alcance;

- comportamiento esperado;

- reglas de negocio;

- entradas;

- salidas;

- errores;

- casos especiales;

- criterios de aceptación;

- fuera de alcance.

Ejemplo de ubicación:

```text

docs/01-Specs/SPEC-AUTH-001.md

```

No incluyas detalles técnicos de implementación que pertenezcan al PLAN.

No implementes una funcionalidad importante si su comportamiento no está suficientemente definido.

---
# 8. PLAN
El PLAN define **cómo se implementará técnicamente la SPEC**.

Ejemplo:

```text

docs/02-Plans/PLAN-AUTH-001.md

```

Debe incluir solamente las decisiones técnicas necesarias para esa funcionalidad, como:

- componentes o módulos afectados;

- archivos que podrían crearse o modificarse;

- flujo técnico;

- contratos;

- persistencia;

- integraciones;

- seguridad;

- pruebas necesarias y estrategia de validación cuando corresponda.

No conviertas el PLAN en documentación general extensa.

No repitas información que ya exista en `PROJECT.md`, `SCOPE.md`, `STACK.md` o la SPEC.

---
# 9. TASKS
Divide el PLAN en tareas pequeñas, concretas, verificables y ejecutables.

Ejemplo:

```text

docs/03-Tasks/AUTH-001/

├── TASK-001.md

├── TASK-002.md

└── TASK-003.md

```

Cada TASK debe:

- corresponder directamente con una SPEC y un PLAN;

- tener un objetivo concreto;

- indicar el trabajo esperado;

- definir cuándo puede considerarse terminada;

- mantenerse dentro del alcance.

No agregues tareas que no sean necesarias para implementar la funcionalidad definida.

Idealmente, una TASK debe poder entregarse a un agente de forma independiente:

```text

Implementa docs/03-Tasks/AUTH-001/TASK-003.md

```

## Actualización del estado SDD

Durante la implementación y validación, los documentos SDD deben reflejar el estado real del trabajo.

### TASKS

Cuando una tarea o elemento de checklist haya sido completamente implementado y validado:

- cambia su estado de `[ ]` a `[x]`;
- actualízalo conforme se completa y valida, no solo al final de la funcionalidad;
- no marques tareas parcialmente implementadas;
- no marques tareas únicamente porque exista código si todavía falta validación;
- conserva sin marcar cualquier tarea pendiente, bloqueada o no verificada.

`docs/03-Tasks/` debe mantenerse sincronizado con el estado real de la implementación.

### SPEC

La SPEC aprobada no debe reescribirse durante la implementación salvo que exista un cambio de alcance previamente autorizado.

Cuando la SPEC contenga criterios de aceptación con checkboxes:

- durante Testing / Validación, cambia `[ ]` a `[x]` únicamente cuando exista evidencia suficiente de cumplimiento;
- si un criterio todavía no puede comprobarse, debe permanecer `[ ]`;
- no reformules, elimines ni agregues criterios de aceptación sin autorización.

### PLAN

El PLAN aprobado debe mantenerse como referencia técnica de la implementación acordada.

- No lo modifiques únicamente para reflejar progreso.
- Si contiene checkboxes explícitos, pueden actualizarse de `[ ]` a `[x]` siguiendo las mismas reglas de TASKS.
- No agregues ni cambies decisiones técnicas del PLAN sin autorización.

### Testing y evidencia

`docs/04-Testing/` debe conservar la evidencia relevante que justifique las validaciones realizadas.

Un criterio de aceptación o tarea marcada como `[x]` debe corresponder a trabajo realmente implementado y, cuando corresponda, validado mediante pruebas o evidencia verificable.

Antes de finalizar cada bloque de trabajo y antes de declarar una funcionalidad terminada, revisa:

1. implementación real;
2. TASKS completadas;
3. criterios de aceptación de la SPEC;
4. pruebas y evidencia disponible;
5. documentación de Testing relacionada.

No declares una funcionalidad completada mientras existan TASKS o criterios de aceptación obligatorios pendientes.

---
# 10. Testing

Usa `docs/04-Testing/` para documentar únicamente las pruebas relevantes que ayuden a validar la funcionalidad.

Ejemplo:

```text
docs/04-Testing/TEST-AUTH-001.md
```

Puede incluir:

- casos exitosos;
- casos de error;
- casos especiales;
- criterios de aceptación relacionados;
- pruebas de integración;
- pruebas E2E;
- estrategia de validación;
- resultados importantes cuando sea necesario conservarlos.

No documentes cada unit test trivial si el propio código de pruebas ya es suficientemente claro.

Los tests ejecutables deben vivir en el código del proyecto, dentro de `backend/` o `frontend/`, según corresponda.

---

# 11. Decisions

Usa `docs/05-Decisions/` únicamente para registrar decisiones técnicas o arquitectónicas relevantes que deban conservarse como referencia del proyecto.

Ejemplo:

```text
docs/05-Decisions/DEC-001-autenticacion.md
```

Puede incluir:

- contexto de la decisión;
- problema o necesidad;
- alternativas consideradas;
- decisión adoptada;
- motivo de la decisión;
- consecuencias relevantes;
- fecha o estado cuando sea útil.

No documentes decisiones triviales ni detalles que ya pertenezcan claramente a `STACK.md`, `CONVENTIONS.md`, una SPEC o un PLAN.

---
# 12. Flujo de trabajo del agente
Antes de modificar código:

1. Identifica la tarea exacta.

2. Lee `AGENTS.md`.

3. Consulta únicamente la documentación general necesaria.

4. Lee la SPEC correspondiente.

5. Lee el PLAN correspondiente.

6. Lee la TASK asignada.

7. Revisa el código existente relacionado.

8. Revisa los tests relacionados cuando existan.
9. Consulta `docs/04-Testing/` cuando exista documentación de pruebas relevante para la tarea.
10. Consulta `docs/05-Decisions/` únicamente si existe una decisión relevante para la tarea.
11. Identifica dependencias, riesgos, contradicciones o ambigüedades.
12. Si falta una decisión necesaria, pregunta antes de implementar.

No leas toda la documentación del proyecto automáticamente.

Usa el **contexto mínimo suficiente** para realizar correctamente la tarea.

---
# 13. Regla de Context Engineering
Antes de cada tarea, construye el contexto siguiendo esta prioridad:

```text

TAREA ACTUAL

↓

AGENTS.md

↓

Documentación general relevante

↓

SPEC correspondiente

↓

PLAN correspondiente

↓

TASK asignada

↓

Código afectado

↓

Tests relacionados

↓

Documentación de testing relevante, cuando exista

↓

Decisions relevantes, solo si afectan la tarea

```

Consulta documentación adicional solamente cuando exista una razón concreta.

No cargues archivos no relacionados únicamente porque estén disponibles.

No conviertas un prompt en un volcado completo del proyecto.

---
# 14. Fuente de verdad
1. `docs/` es la referencia funcional y técnica documentada del proyecto.

2. El código existente representa el estado real de la implementación y debe revisarse.

3. Si documentación y código se contradicen de forma relevante, no decidas arbitrariamente.

4. Informa la contradicción.

5. No inventes requisitos para resolverla.

6. No completes vacíos solo por convención o buenas prácticas.

7. No agregues funcionalidades porque parezcan útiles.

8. No cambies el alcance sin autorización.

9. Cuando un cambio aprobado altere comportamiento, stack, alcance o pruebas relevantes, actualiza la documentación correspondiente.

---
# 15. Reglas de implementación
1. Modifica solo lo necesario.

2. Reutiliza código existente cuando sea adecuado.

3. Evita duplicación innecesaria.

4. Mantén nombres, estructura y estilo consistentes.

5. Respeta los patrones existentes.

6. No hagas refactors grandes sin necesidad.

7. No cambies APIs, contratos, esquemas o interfaces si la tarea no lo requiere.

8. No elimines código sin entender su propósito.

9. No introduzcas complejidad innecesaria.

10. Mantén separación clara de responsabilidades.

11. Si detectas deuda técnica fuera del alcance, repórtala pero no la corrijas automáticamente.

12. No cambies una decisión importante del proyecto sin autorización.

---
# 16. Dependencias
1. No agregues dependencias sin necesidad real.

2. Verifica primero si el proyecto ya dispone de una solución equivalente.

3. Evita dependencias innecesarias, abandonadas o incompatibles.

4. No actualices versiones mayores sin autorización.

5. Si agregas una dependencia necesaria, documenta su propósito cuando sea relevante.

6. No agregues una librería únicamente para evitar implementar algo trivial con el stack existente.

---
# 17. Seguridad
1. Nunca expongas contraseñas, tokens, claves API, secretos o credenciales.

2. No almacenes secretos directamente en el código.

3. Usa variables de entorno o el mecanismo de secretos definido por el proyecto.

4. No registres información sensible innecesariamente.

5. Respeta autenticación, autorización y permisos existentes.

6. Valida entradas cuando corresponda.

7. No desactives controles de seguridad para hacer funcionar una funcionalidad.

8. Si detectas un problema de seguridad relacionado con la tarea, repórtalo.

---
# 18. Pruebas
Después de realizar cambios relevantes:

1. Ejecuta las pruebas existentes relacionadas.

2. Agrega pruebas cuando la funcionalidad lo requiera.

3. Prueba los casos exitosos.

4. Prueba errores y casos especiales definidos en la SPEC.

5. Verifica regresiones razonablemente relacionadas.

6. No declares terminada una funcionalidad si fallan pruebas relevantes.

7. Si una prueba falla por una causa externa a tus cambios, indícalo claramente.

---
# 19. Validación final contra la SPEC
La validación final debe seguir:

```text

SPEC

↓

Criterios de aceptación

↓

Implementación

↓

Pruebas

↓

Resultado final

```

No consideres una funcionalidad terminada únicamente porque el código compile.

Debe cumplir lo especificado.

---
# 20. Git
Usa Git durante todo el desarrollo.

Antes de cambios importantes:

1. Revisa el estado del repositorio.

2. Identifica archivos modificados previamente.

3. No sobrescribas cambios existentes sin entenderlos.

Durante el desarrollo:

4. Mantén juntos los cambios relacionados.

5. No mezcles cambios no relacionados.

6. Mantén `docs/` versionado junto con el código.

7. Cuando un cambio afecte documentación y código, ambos deben quedar sincronizados.

Antes de finalizar:

8. Revisa el estado del repositorio.

9. Verifica que no existan cambios accidentales.

10. Revisa la TASK, pruebas, documentación y SPEC relacionadas.

No ejecutes sin autorización explícita:

- `push`;

- `merge`;

- `rebase`;

- `reset --hard`;

- force push;

- eliminación de ramas;

- operaciones destructivas;

- operaciones remotas que modifiquen el repositorio.

---
# 21. Commits
Cuando esté autorizado realizar commits, usa mensajes claros y coherentes.

Ejemplos:

```text

feat: agregar inicio de sesión

fix: corregir validación de contraseña

test: agregar pruebas de autenticación

docs: actualizar spec de login

refactor: reorganizar servicio de autenticación

chore: actualizar configuración

```

Cada commit debe representar una unidad lógica de trabajo.

No mezcles cambios no relacionados.

---
# 22. Cuándo detenerse y preguntar
Detente y pregunta si:

- falta un requisito necesario;

- una SPEC es ambigua;

- dos documentos se contradicen;

- documentación y código se contradicen de forma relevante;

- el PLAN requiere una decisión no documentada;

- una TASK implica salir del alcance;

- se necesita una decisión técnica importante no definida;

- existe riesgo de perder o sobrescribir trabajo;

- se requiere una operación Git destructiva o remota;

- se necesitan credenciales o secretos no disponibles;

- el comportamiento esperado no puede determinarse razonablemente.

No adivines.

---
# 23. Criterio de finalización
Una funcionalidad solo puede considerarse terminada cuando:

- [ ] La SPEC está implementada.

- [ ] Los criterios de aceptación se cumplen.

- [ ] Las TASKS correspondientes están completadas.

- [ ] Las pruebas relevantes pasan.

- [ ] El proyecto compila o construye correctamente.

- [ ] La funcionalidad puede ejecutarse correctamente.

- [ ] No existen errores conocidos introducidos por el cambio.

- [ ] La documentación necesaria está actualizada.

- [ ] El estado de Git es consistente.

---
# 24. Regla principal
> No programes basándote en suposiciones.

> Usa únicamente el contexto necesario, sigue SPEC → PLAN → TASKS, implementa lo definido, prueba el resultado y valida siempre contra la SPEC.

---
# 25. Confirmación inicial
Después de procesar este prompt:

1. Verifica la estructura del proyecto.

2. Crea o actualiza `AGENTS.md`.

3. Verifica o crea, cuando exista información suficiente:

   - `docs/00-General/PROJECT.md`

   - `docs/00-General/SCOPE.md`

   - `docs/00-General/STACK.md`
   - `docs/00-General/CONVENTIONS.md`

4. Guarda este prompt completo en `docs/00-General/AGENT-RULES.md`.

5. Verifica que `docs/` esté versionado con Git.

6. No modifiques código todavía.

7. Indícame únicamente:

   - si la configuración quedó lista;

   - qué archivos creaste o modificaste;

   - qué documentación general está pendiente de completar;

   - si encontraste algún problema que requiera mi decisión.

Después espera mi siguiente instrucción.


# Actualización del propietario — 2026-11-05

Esta regla complementa el prompt original y concede autorización explícita permanente para los commits y push normales descritos. Las demás operaciones conservan sus restricciones.

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
