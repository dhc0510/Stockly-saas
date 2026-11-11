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

## Reverificación sobre el código actual — 2026-11-11

Después de la aprobación del 08/11, STK-017, STK-018 y STK-007 (`f086b4c`) tocaron Existencias e inventario. La tarjeta había vuelto de Finalizado a «En espera…» sin motivo registrado, así que se volvió a verificar STK-016 sobre `main` en `e5c8a04`, con React, Spring Boot y MySQL 9.7 local.

### Recorrido real

Pasaron 51 de 51 comprobaciones.

El script crea dos empresas reales (A y B) y dos sucursales en A (Centro y Norte). En Centro carga siete productos: agotado, en mínimo con nivel deseado, en stock bajo, uno en kg con saldo de 3 decimales, uno sin mínimo, uno para «No reponer» y uno cuyo nombre empieza con «=». Crea dos trabajadores de STK-005: consulta de Existencias con acceso solo a Norte, y configuración de alertas (CATALOG operar) en Centro. La suscripción de las empresas de prueba se marcó como pagada para no topar el límite de la prueba gratuita (STK-018).

- **AC-01 (9):**
  - por defecto salen agotados y en mínimo, sin stock bajo, ordenados por urgencia y nombre;
  - «Incluir stock bajo» agrega el de stock bajo;
  - los totales (3, 3, 1) cuadran;
  - la búsqueda funciona sin tildes y por SKU, y la categoría filtra;
  - en «todas», cada sucursal es una fila;
  - el contador del menú coincide con la lista.
- **AC-02 (9):**
  - un nivel deseado negativo, con 3 decimales, o igual o menor que el mínimo se rechaza en el campo;
  - guardar 12,50, cambiar a 30 y borrar recalculan «A pedir»;
  - sin mínimo, el nivel deseado debe ser mayor que 0;
  - no se crean movimientos.
- **AC-03 (5):**
  - con nivel deseado: 20 − 3 = 17;
  - sin nivel deseado: mínimo 5 + margen 2 − 0 = 7;
  - exacta con decimales del saldo: 2 + 1 − 1,235 = 1,765;
  - sin dato para sugerir cuando no hay mínimo ni nivel deseado;
  - resultado ≤ 0 cuando no hace falta pedir.
- **AC-04 (9):**
  - con alerta activa, «No reponer» saca el producto de la lista, los totales, el contador y la exportación, y desactiva sus alertas;
  - queda en «No se reponen» con quién y cuándo;
  - en Existencias conserva su estado y se marca «No se repone»;
  - queda en Actividad;
  - al desmarcarlo vuelve a la lista, al contador y a las alertas.
- **AC-05 (10):**
  - el CSV se llama `por-reponer-centro-2026-11-11.csv`, con las 12 columnas acordadas y exactamente las filas filtradas;
  - usa punto decimal (1.765) y neutraliza con `'` el texto que empieza con «=»;
  - respeta los filtros;
  - el `.xlsx` es válido, con el mismo nombre, las mismas filas y cantidades como números;
  - un formato desconocido se rechaza.
- **AC-06 (9):**
  - el lector solo ve, cuenta y exporta su sucursal, y no configura;
  - quien configura alertas fija el nivel deseado en su sucursal, pero no en otra;
  - la otra empresa no ve, cuenta, exporta ni configura nada de A;
  - sin sesión, la respuesta es 401.

Aclaraciones del recorrido:
- **Excel:** el texto que empieza con «=» se guarda como celda de texto. El archivo no contiene ninguna fórmula, así que Excel no lo ejecuta. El apóstrofo solo se agrega en el CSV.
- **Alertas:** se envían a quienes tienen acceso a la sucursal (STK-007). Por eso el script da acceso a Centro al administrador antes de comprobarlas.

### Pruebas automáticas

- **Backend en MySQL aislado:** `RestockLocalIntegrationTest` 4 de 4, `StockMovementLocalIntegrationTest` 9 de 9 y `ReceiptLocalIntegrationTest` 4 de 4.
- **Playwright en Edge:**
  - `restock.spec.ts` e `inventory.spec.ts`: 8 de 8 a 1440 y 375 px;
  - la batería completa: 40 de 40 hoy, sobre este código salvo el ajuste de columnas de Ventas.
- **Celular:** cubierto con la emulación a 375 px, como se acordó para STK-012 y STK-013.

### Resultado

AC-01 a AC-06 siguen cumpliéndose sobre el código actual. No se encontraron regresiones ni se modificó código de la aplicación.

### Aprobación final

El propietario revisó en local y aprobó el 2026-11-11: «aprobado push».
