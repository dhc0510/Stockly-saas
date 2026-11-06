# PLAN INV-001

Versión 0.1, no aprobado. Referencia: SPEC INV-001.

## Arquitectura y componentes

Propuesta: monolito modular con identidad, empresas/permisos, catálogo e inventario. Servicio centralizado para escrituras de inventario; PostgreSQL con transacciones y RLS. Framework, ORM, autenticación, hosting y versiones pendientes de presupuesto y preferencias.

## Modelo lógico

Tenants; memberships; role_permissions; location_grants; products; skus; locations; stock_balances; movements; idempotency_records. Toda entidad de negocio lleva tenant_id. FK compuestas evitan referencias cruzadas. Saldo único por tenant/SKU/ubicación. Movimiento contiene tipo, cantidad firmada, motivo, referencia, actor, fecha UTC y compensates_id opcional único. Cantidades DECIMAL con escala por definir. Clave idempotente única por tenant/ámbito/clave, hash de contenido y resultado original para replay. Retención debe impedir duplicar operaciones aceptadas. Índices comienzan por tenant y cubren filtros aprobados. Privilegios sin modificación ni borrado del libro; escritura del saldo solo por servicio autorizado.

## Contratos propuestos

| Método y ruta | Entrada y salida |
|---|---|
| GET /v1/inventory | SKU, ubicación, estado, cursor, limit acotado; items, next_cursor |
| GET /v1/movements | Filtros autorizados; página estable por fecha e ID |
| POST /v1/movements | Idempotency-Key; sku_id, location_id, type, quantity decimal string, reason, reference |
| POST /v1/movements/{id}/compensations | Idempotency-Key y motivo; servidor deriva cantidad |
| GET /v1/movements/export.csv | Mismos filtros y permisos; CSV escapado y neutralizado |

Respuesta de escritura: ID, cantidad firmada, fecha y saldo al confirmar. Replay devuelve resultado original, que no debe presentarse como saldo actual. Error: code, message, field_errors, request_id. HTTP 401 sin sesión, 403 sin permiso, 404 inexistente o ajeno, 409 conflicto/saldo insuficiente, 422 validación, 429 límite y 503 contención agotada. Cerrar esquema OpenAPI y límites antes de implementación.

## Estrategia transaccional

1. Autenticar y autorizar tenant/ubicación; establecer contexto tenant local a transacción.
2. Reclamar clave idempotente con unicidad; comparar contenido y esperar resultado concurrente cuando corresponda.
3. Validar entidades y autorización vigente; crear saldo ausente con conflicto controlado y bloquear fila.
4. Compensaciones bloquean original y verifican unicidad. Documentar orden global de locks para evitar deadlocks.
5. Validar nuevo saldo, insertar libro y actualizar saldo en misma transacción.
6. Guardar resultado idempotente y confirmar; responder éxito solo tras commit. Reintentos acotados conservan clave.

## Migraciones y entrega

Implementar primero dependencias aprobadas. Crear tablas, constraints, índices, roles y RLS; probar escrituras y falta de contexto. Datos sintéticos. Ensayar migración en BD limpia y restauración aislada. CI: lint, tipos, pruebas, migraciones y aislamiento. Seleccionar GitHub/Trello antes de sincronizar. Sin despliegue autorizado.

## Estrategia de pruebas

Unitarias: signos, cantidades, mínimos y permisos. Integración PostgreSQL real: constraints, RLS, reconstrucción, rollback e idempotencia. API: contratos, filtros y acceso ajeno. Concurrencia con conexiones independientes y sincronización explícita: salidas, creación de saldo y compensaciones. Inyectar fallos antes del commit. Frontend/E2E: flujos aprobados, doble envío, desconexión, teclado, foco y responsive. Regresión de autorizaciones. Registrar commit, entorno, resultados y evidencias por AC en 04-Testing al ejecutar.

Riesgos: bypass RLS, contexto filtrado entre conexiones, deadlocks, duplicados, decimales incorrectos y compensación negativa. Mitigaciones: ADR-001, mínimo privilegio, contexto transaccional y pruebas negativas. Falta cerrar stack, escala, métricas, backups y políticas comerciales.