# TASKS INV-001

Todas propuestas, sin autorización de desarrollo ni finalización. Todas referencian SPEC INV-001 y PLAN INV-001.

| ID | Descripción | Dependencias | Prioridad | Criterio de finalización |
|---|---|---|---|---|
| INV-001-T01 | Resolver descubrimiento y alcance | Propietario | P0 | Respuestas y límites registrados |
| INV-001-T02 | Especificar dependencias de identidad, catálogo y ubicación | T01 | P0 | Contratos y SPEC aprobados |
| INV-001-T03 | Cerrar arquitectura, stack y documentos | T01,T02 | P0 | Aprobación explícita registrada |
| INV-001-T04 | Identidad y mockups 1440/375 | T01 | P0 | Diseños, flujos y estados aprobados |
| INV-001-T05 | Repo, CI y entornos | T03 | P0 | Destinos acordados y checks sin secretos |
| INV-001-T06 | Implementar dependencias aprobadas | T02,T03,T05 | P0 | Identidad, permisos, SKU y ubicación verificados |
| INV-001-T07 | Migraciones, roles y RLS | T06 | P0 | BD limpia y AC-04/05 pasan |
| INV-001-T08 | Libro, saldo e idempotencia | T07 | P0 | AC-01/02/03/06/10 pasan |
| INV-001-T09 | Compensaciones | T08 | P0 | AC-05/07 pasan, incluida concurrencia |
| INV-001-T10 | Consultas, filtros y CSV | T08 | P1 | AC-04/08 pasan |
| INV-001-T11 | Interfaz aprobada | T04,T09,T10 | P1 | AC-09 y flujos verificados |
| INV-001-T12 | Validación integral y corrección | T11 | P0 | Todos los AC con evidencia y restauración probada |
| INV-001-T13 | Revisión final y seguimiento | T12 | P0 | Propietario aprueba antes de completar |

Checklist por tarea: alcance aprobado, entregable, verificación, evidencia, SPEC/PLAN y PR cuando exista.

Trello propuesto: Backlog, Por definir, En especificación, En diseño, Pendiente de aprobación, Listo para desarrollo, En desarrollo, En pruebas, En revisión, Completado y Bloqueado. Cada tarjeta: ID, título, descripción, prioridad, checklist, criterios, dependencias y enlaces documentales/GitHub. No se han creado recursos externos. PR requiere tarea aprobada, descripción y evidencia. Completado solo tras verificación y aprobación final.