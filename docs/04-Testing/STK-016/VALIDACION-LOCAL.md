# STK-016 · Validación local

El propietario revisó la aplicación local con datos ficticios y la aprobó el 2026-11-08 («si, ya esta listo»). Luego pidió publicarla en `main` con el commit «STK-016 | Productos agotados y existencias bajas».

## Backend y MySQL

Flyway aplicó V1 a V16 en `stockly_test`, recreado vacío porque tenía datos huérfanos de corridas anteriores. Pasaron 35 pruebas, 2 de ellas nuevas en `RestockLocalIntegrationTest`. En `stockly_as`, con colación sensible a tildes, pasaron las 7 de Historial. `CompanyIntegrationTest` quedó fuera porque requiere Docker.

- **Lista y orden.** Agotados primero, luego mínimos y, con el filtro, stock bajo. Totales y contador del menú coinciden con la lista; la búsqueda «cafe» encuentra «Café». AC-01.
- **«A pedir».** Nivel deseado − disponible (160), mínimo + margen − disponible (6), sin sugerencia sin mínimo ni nivel deseado, y exactitud con los 3 decimales del saldo (13.875). AC-03.
- **Validación.** Nivel deseado igual al mínimo o con 3 decimales: 400 con el mensaje en el campo, sin cambios. AC-02.
- **«No reponer».** Desactiva las alertas activas y deja la campanita sin el producto. Lo saca de lista, totales y contador; guarda quién lo marcó y registra `RESTOCK_PAUSED`. Al desmarcarlo vuelve la alerta y se registra `RESTOCK_RESUMED`. Existencias lo marca con `restockPaused`. AC-04.
- **Exportación.** El CSV trae encabezado y las mismas filas, con la fórmula `=HYPERLINK(...)` neutralizada con `'`. El Excel abre como .xlsx válido con números y sin fórmulas; nombres `por-reponer-todas-…` y `por-reponer-centro-…`; formato inválido: 400. AC-05.
- **Permisos y aislamiento.** Un lector solo ve su sucursal; otra sucursal responde 404 en lista y exportación. El lector no puede configurar (403). Un editor no puede configurar en una sucursal ajena (404). Sin permiso de inventario: 403; otra empresa no ve filas ni configura (404); sin sesión: 401. AC-06.
- Regresiones de Catálogo, Entradas, Salidas y ajustes, Ventas, Cobros, Devoluciones y Facturación: pasan.

## Interfaz

Compilación de producción correcta. Pasaron 22 pruebas de Vitest, 6 nuevas del cálculo y la validación del nivel deseado, y 18 de Playwright en Edge a 320 px y 1440 px. `restock.spec.ts` cubre lista, exportación CSV, error y sugerencia en vivo, «No reponer», contador e impresión. También pasaron las regresiones de Ventas, Cobros, Devoluciones, Facturación y comprobante.

Recorrido real contra React, Spring Boot y MySQL (`stockly_demo`): 24 de 24 comprobaciones en escritorio y celular, incluidas las descargas reales de Excel y CSV, sin errores de página. Capturas en [screenshots](screenshots/).

## Ajustes durante la revisión del propietario

- **Lista con contenido angosto.** Con el menú lateral visible, la tabla se cortaba. Ahora la lista pasa a tarjetas según su propio ancho, por debajo de 900 px, y la tabla se puede desplazar si no entra.
- **Celular.**
  - El selector de sucursal ocupa su propia fila.
  - El buscador dice «Nombre, SKU o código».
  - En los resúmenes, el punto de color va al lado del número.
- **Contador del menú.** Se actualiza al guardar la configuración o volver a reponer.
- **Ícono de «Cerrar sesión».** Ahora es naranja de la marca (`#c2410c`) para que se vea cuando el navegador oscurece la página. Es un cambio global de la barra superior, pedido por el propietario.

## Decisiones aplicadas al implementar

- **Precisión de «A pedir».** La SPEC pedía 2 decimales exactos, pero el saldo admite 3 decimales desde STK-008. Para no redondear, «A pedir» se muestra exacto con hasta 3 decimales cuando el saldo los tiene. El nivel deseado conserva 2 decimales, como el mínimo.
- **Quién marcó «No reponer».** `restock_paused_by` guarda la cuenta sin FK, igual que el responsable en ventas y movimientos, para no bloquear borrados de cuentas. El PLAN mencionaba una FK.
- **Excel.** Se genera con `org.dhatim:fastexcel` 0.20.2 (Apache-2.0), verificado en Maven Central el 2026-11-08.
