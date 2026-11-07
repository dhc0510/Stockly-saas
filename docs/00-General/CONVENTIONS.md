# Stockly — Convenciones de trabajo

Actualizado: 2026-11-05. Incluye únicamente acuerdos existentes; convenciones técnicas pendientes.

## Documentación y contexto

- Documentación oficial en docs/, en Markdown y compatible con Obsidian.
- Contexto general: 00-General/; comportamiento: 01-Specs/; planificación técnica: 02-Plans/; tareas: 03-Tasks/; pruebas y evidencias: 04-Testing/; decisiones relevantes: 05-Decisions/.
- Consultar únicamente el contexto necesario. No crear categorías documentales adicionales sin autorización.
- Separar acuerdos validados, propuestas e hipótesis. No convertir supuestos en decisiones ni tratar borradores como aprobaciones.
- Mantener identificadores consistentes entre documentación, Trello y GitHub; formato exacto pendiente de acuerdo.

## Flujo y aprobación

- Seguir SPEC → PLAN → TASKS → CÓDIGO → PRUEBAS → VALIDACIÓN.
- Definir cada funcionalidad completamente al abordarla y obtener aprobación explícita de SPEC, PLAN y TASKS antes de programar.
- Presentar el diseño para aprobación antes de implementar la interfaz.
- No ampliar alcance ni cambiar decisiones importantes sin autorización.
- Marcar checkboxes únicamente cuando exista implementación y validación real respaldada por evidencia.
- No reescribir SPEC o PLAN aprobados para reflejar progreso; solo actualizar checkboxes existentes cuando corresponda.
- Solicitar aprobación final antes de marcar una funcionalidad como terminada.

## Plantillas y extensión SDD

Usar SPEC-TEMPLATE.md, PLAN-TEMPLATE.md y TASKS-TEMPLATE.md de esta carpeta, proporcionadas por el propietario, como guías flexibles. Adaptar o agregar contenido necesario para cada funcionalidad; no imponer secciones sin utilidad.

Priorizar documentos breves, legibles, completos y sólidos. Evitar relleno, explicaciones genéricas y repetición; referenciar acuerdos existentes. Mantener detalle suficiente para implementar y verificar lo aprobado, incluidas reglas, errores, seguridad y dependencias relevantes. Ajustar la extensión a la complejidad sin límites artificiales ni pérdida de rigor. Se mantienen las aprobaciones SDD.

## Cambios y control de versiones

- Revisar el estado del repositorio antes y después de cambios cuando exista Git.
- Conservar el trabajo existente y sincronizar documentación y código cuando corresponda.
- No publicar secretos ni ejecutar cambios destructivos, despliegues productivos u operaciones Git remotas sin autorización.
- Realizar commits cuando estén autorizados, con mensajes claros que representen una unidad lógica de trabajo.

## Pendiente de definir

Nombres técnicos, organización interna del código, contratos, endpoints, errores, estilo, herramientas de pruebas, formato de ramas y commits. No se adopta por defecto lo sugerido en los borradores iniciales.

Referencia detallada: AGENT-RULES.md; mapa breve de instrucciones: ../../AGENTS.md.
## Flujo por funcionalidad y mockups

Seguir REGLA-DESARROLLO-POR-FUNCIONALIDAD.md y PROMPT-MOCKUPS-UI-UX.md de esta carpeta. El flujo exige aprobación en cada etapa e indicación explícita de comenzar desarrollo tras aprobar documentos.

La categoría docs/06-Mockups/ está autorizada: agrupar por funcionalidad y relacionar mockups con SPEC y Trello; no crear subcarpetas de ejemplo vacías. La identidad global sigue en DESIGN-SYSTEM.md.

## Seguimiento del equipo

Tablero: https://trello.com/b/wBG2J96c/stockly-desarrollo-del-producto

Columnas: Backlog / Pendiente; SPEC / Análisis; PLAN / Diseño; En desarrollo; En espera de otras funcionalidades para terminar lo que falte; Pruebas y revisión; Finalizado.

Backlog activo de desarrollo: STK-004 a STK-018, con prioridades propuestas. STK-002, STK-003 y STK-019 a STK-022 fueron archivadas: investigación, aprobaciones y evaluación se resuelven en el chat. STK-001 fue archivada por decisión del propietario: no se exige cerrar una primera versión de antemano; su capacidad se definirá progresivamente durante el desarrollo, sin prisa de entrega. Mantener referencias consistentes en Trello, mockups, documentación y GitHub. Integrar cambios y finalizar seguimiento tras pruebas y aprobación, con autorización para operaciones remotas.

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


## Búsquedas sin distinguir tildes

Regla permanente aprobada por el propietario el 2026-11-07: toda búsqueda de Stockly debe encontrar el resultado aunque se escriba sin tildes o con mayúsculas distintas, por ejemplo «cafe» encuentra «Café» y «guia» encuentra «GUÍA-083». Aplica al filtrado en el servidor y en el navegador. Cada funcionalidad con búsqueda debe probarlo.
