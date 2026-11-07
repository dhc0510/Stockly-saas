# TASKS — STK-008 | Entradas de inventario

Estado: desarrollo autorizado. [SPEC](../../01-Specs/STK-008/SPEC.md) · [PLAN](../../02-Plans/STK-008/PLAN.md).

- [x] Definir los contratos de artículo, ubicación Principal, saldos, movimientos y permisos.
- [x] Implementar persistencia, confirmación atómica, existencia posterior histórica y reintentos sin duplicados. AC-04, AC-05 y AC-07.
- [x] Implementar anulación vinculada con motivo, unicidad y control de saldo negativo. AC-06 y AC-07.
- [x] Implementar búsqueda de artículos y entradas, registro, recuperación local, revisión y comprobante. AC-01 a AC-05 y AC-08.
- [x] Verificar cantidades, concurrencia, recuperación, permisos y aislamiento. Compilar y guardar [evidencia local](../../04-Testing/STK-008/VALIDACION-LOCAL.md). AC-01 a AC-07.
- [x] El propietario probó la aplicación en navegador a 320, 375, 390 y 1440 px, con lector simulado. AC-01 y AC-08.
- [ ] Verificar teclado y flujo en un iPhone físico y probar un lector físico. AC-01 y AC-08.
- [x] Obtener aprobación de la aplicación local antes de publicar.
