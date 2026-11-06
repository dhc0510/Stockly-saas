# PLAN — STK-006 | Catálogo de productos adaptable

Estado: aprobado para desarrollo el 2026-11-06. [SPEC](../../01-Specs/STK-006/SPEC.md) · [Mockup](../../06-Mockups/STK-006/catalogo-responsive.html).

1. **Datos y backend.** Crear el módulo de catálogo en Spring Boot y migraciones MySQL para fichas, artículos, categorías, atributos, códigos reservados e historial de cambios de SKU. Usar el identificador del artículo como referencia estable. Proteger la unicidad por empresa en la base de datos. Guardar cambios de SKU y conversiones de ficha en transacciones.
2. **Permisos e imágenes.** Usar el contexto de empresa y los roles de STK-004 y STK-005. Validar cada acción en el servidor. Validar archivos y controlar el acceso a la imagen por empresa. Ver [ADR-004](../../05-Decisions/ADR-004.md) para el almacenamiento y los límites aprobados.
3. **Interfaz.** Integrar catálogo, filtros, formularios, combinaciones, imagen y confirmaciones en React y TypeScript, siguiendo el mockup. Mostrar validaciones y estados de carga, vacío y error. Probar el flujo con teclado y en referencias de 375 y 1440 px.
4. **Contratos.** Acordar con inventario la consulta del primer movimiento y el vínculo por ID. Acordar con ventas la consulta de artículos activos. Verificar esos comportamientos con las dependencias reales antes del cierre.
5. **Validación.** Ejecutar pruebas de reglas, permisos, concurrencia en códigos, aislamiento, conversión y fallos de guardado. Compilar ambos módulos, probar en local y registrar evidencias en `docs/04-Testing/`.
