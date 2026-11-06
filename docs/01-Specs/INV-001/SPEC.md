# INV-001 — Núcleo auditable de inventario

Versión 0.1. Borrador no aprobado, condicionado al descubrimiento.

## Objetivo y alcance

Registrar cambios autorizados y obtener existencias reconstruibles por SKU y ubicación. Incluye entradas, salidas, ajustes y compensaciones. Identidad, membresías, catálogo y ubicación son dependencias que requieren especificaciones propias aprobadas; este documento establece contratos mínimos. Fuera: reservas, transferencias, conteos formales, lotes, series, facturación, importación y valoración contable.

## Conceptos y reglas

SKU es la unidad inventariable. Producto simple tiene un SKU; variantes tienen SKU por combinación, sin saldo adicional en el padre. Físico es suma firmada del libro. Disponible = físico - reservado. En esta fase reservado = 0; tránsito no habilitado ni contado como disponible. Agotado si físico = 0; bajo mínimo si 0 < físico < mínimo. Mínimo nulo significa no configurado.

Tenant derivado de membresía autenticada. SKU y ubicación deben pertenecer al tenant y estar activos para operar; desactivación conserva historia. SKU único por empresa. Unidad inmutable tras movimientos hasta definir conversiones. Cantidades exactas, sin punto flotante; escala pendiente según piloto. Cantidad positiva en entrada del usuario; tipo determina signo. Prohibir saldos negativos en todas las rutas. No editar saldos directamente ni borrar movimientos. Fecha UTC del servidor, sin retroactividad en MVP.

## Actores y permisos

Permisos separados: inventory.read, movements.read, movements.create_receipt, movements.create_issue, inventory.adjust e inventory.compensate. Verificar membresía, empresa activa y alcance de ubicación en servidor. Administrador de empresa asigna permisos; administrador SaaS no hereda acceso operativo. Denegar por defecto.

## Requisitos funcionales

- RF-01 Consultar saldos por SKU/ubicación con búsqueda y paginación estable.
- RF-02 Entrada y salida requieren cantidad, motivo y referencia; registrar actor autenticado, fecha e ID.
- RF-03 Ajustes requieren permiso específico y justificación. Apertura inicial también es movimiento auditable.
- RF-04 Historial inmutable con filtros por SKU, ubicación, tipo, actor y fecha; CSV con mismos permisos y filtros.
- RF-05 Compensación completa vinculada al original, con motivo y permiso; una sola por original. Sin cadenas de compensaciones en MVP; error posterior requiere ajuste justificado. Rechazar si produce saldo negativo.
- RF-06 Repetición de clave idempotente con igual contenido devuelve resultado original sin efecto adicional; contenido diferente devuelve conflicto.

## Casos de uso

UC-01 Recepción: seleccionar SKU/ubicación, ingresar cantidad y motivo, confirmar; servidor autoriza y contabiliza atómicamente.
UC-02 Salida: mismo flujo; saldo insuficiente rechaza toda la operación y permite revisar saldo actualizado.
UC-03 Corrección: elegir original, revisar impacto y confirmar motivo; conservar ambos registros vinculados.
UC-04 Consulta: filtrar saldos e historia; IDs ajenos no revelan información.

## Validaciones y casos límite

Validar cantidades, unidad, campos obligatorios y límites del contrato definitivo. Timeout tras commit se resuelve repitiendo misma clave. Competencia por últimas unidades nunca permite exceso. Creación concurrente de saldo converge en fila única. Fallo parcial revierte libro, saldo e idempotencia. Compensación duplicada produce conflicto. Mostrar historia de inactivos. CSV neutraliza fórmulas. No confiar en permisos, actor o tenant enviados por cliente.

## Requisitos no funcionales y seguridad

Transacción única para libro/saldo/idempotencia. Aislamiento en BD, API, servicios, exportaciones, caché y archivos. Transporte cifrado, secretos fuera del código y logs sin credenciales. Objetivo WCAG 2.2 AA, teclado, foco y errores comprensibles. Paginación y consultas acotadas; metas de rendimiento pendientes del volumen. Retención, disponibilidad y recuperación pendientes; probar restauración antes del piloto.

## Criterios de aceptación

| ID | Resultado verificable |
|---|---|
| AC-01 | Entrada 10 y salida 3 dejan 7, coincidente con libro |
| AC-02 | Saldo 5 con dos salidas concurrentes de 4 deja 1 y una sola confirmación |
| AC-03 | Reintentos concurrentes de misma clave generan un movimiento; payload distinto da conflicto |
| AC-04 | Empresa A no accede a B mediante IDs, filtros, CSV ni rol real de BD |
| AC-05 | Sin permiso o ubicación autorizada no hay cambios |
| AC-06 | Fallo entre libro y saldo revierte todo; reintento seguro |
| AC-07 | Compensación conserva original, motivo y actor; duplicada o negativa se rechaza |
| AC-08 | Historial y exportación respetan filtros, permisos y zona horaria explícita |
| AC-09 | Flujos aprobados funcionan a 375 y 1440 px con teclado y errores accesibles |
| AC-10 | Reconstrucción del libro coincide con saldo y conserva historial de inactivos |

Dependencias: preguntas resueltas, ADR-001, contratos de identidad/catálogo/unidades/ubicación, documentos y diseño aprobados. Ver PLAN y TASKS INV-001.