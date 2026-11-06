# Desarrollo por funcionalidad

Regla permanente del propietario. Transcripción funcional de las instrucciones y del flujo visual proporcionados el 2026-11-05.

1. **Seleccionar y aclarar:** elegir la funcionalidad, explicar el resultado esperado y resolver con el propietario todas las dudas relevantes.
2. **Mockups:** si corresponde, crear las versiones necesarias, revisar y corregir; obtener aprobación antes de continuar.
3. **SDD:** crear SPEC, PLAN y TASKS según las guías flexibles y documentación general; revisar y obtener aprobación.
4. **Desarrollo:** esperar la indicación explícita de comenzar y seguir los documentos aprobados.
5. **Pruebas y revisión local:** ejecutar el proyecto, comprobar los criterios y revisar como usuario datos, flujos y responsive cuando corresponda. Corregir y repetir pruebas hasta que esté completo y validado; obtener aprobación final.
6. **Integración y seguimiento automática:** la aprobación explícita de la aplicación funcionando en local autoriza commit y push normales de los cambios relacionados a origin y actualización de Trello, sin pedir permiso nuevamente. Verificar lo publicado y enlazar el commit o PR. Finalizado solo con criterios obligatorios satisfechos; si se aprobó una demo o quedan dependencias, publicar el avance y dejar En espera con los pendientes.

No adelantarse de etapa sin aprobación del propietario. La aprobación documental no sustituye la indicación de comenzar el desarrollo ni autoriza publicar. La aprobación posterior de la aplicación en local sí autoriza commit/push normales y actualización de Trello. No autoriza merge, operaciones destructivas o despliegue productivo.

La imagen presenta selección y aclaración como pasos separados, produciendo siete pasos visuales; ambos formatos describen el mismo flujo.

## SDD con balance

Usar SPEC-TEMPLATE.md, PLAN-TEMPLATE.md y TASKS-TEMPLATE.md como guías flexibles. Priorizar documentos breves, legibles, completos y sólidos. Agregar contenido necesario, sin relleno ni repetición; mantener reglas, errores, seguridad y criterios verificables relevantes. Brevedad con rigor.
## Inicio de sesión de trabajo: copia actualizada

Al iniciar cada nueva sesión de trabajo del proyecto, recordar al usuario que debe actualizar su copia con la última versión publicada en GitHub antes de desarrollar; no repetir el aviso en cada mensaje de una sesión ya verificada.

Antes de modificar archivos, revisar git status, rama y origin; consultar el remoto con git fetch y comparar HEAD con la rama remota correspondiente. Fetch está autorizado como consulta que no modifica el servidor ni los archivos de trabajo; no ejecutar pull, merge o rebase por este recordatorio sin autorización correspondiente.

Si la copia está atrasada, detener el desarrollo dependiente y ayudar al usuario a actualizarla preservando sus cambios. Si hay cambios sin guardar, divergencia, repositorio sin commits, remoto sin versión publicada o falta de conexión, explicar la situación y resolverla antes de afirmar que la copia está al día. No descartar trabajo, usar reset --hard ni sobrescribir archivos. Un repositorio nuevo sin publicación no tiene todavía una última versión que descargar; informarlo sin inventarla.

## Recordatorio al cerrar la sesión

Al terminar cada sesión de trabajo, recordar al usuario que la última versión debe quedar publicada en GitHub. Informar explícitamente si los cambios ya están subidos y, cuando corresponda, indicar rama y commit; si no lo están, explicar qué queda pendiente. No afirmar publicación sin verificar el remoto.

Este recordatorio complementa el cierre automático: después de aprobar la aplicación en local, hacer commit/push y actualizar Trello con la autorización permanente existente. Sin esa aprobación, recordar lo pendiente sin publicar por iniciativa propia ni confundir el recordatorio con autorización adicional.
