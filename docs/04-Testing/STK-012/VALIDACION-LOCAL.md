# STK-012 · Validación local

[SPEC](../../01-Specs/STK-012/SPEC.md) · [TASKS](../../03-Tasks/STK-012/TASKS.md). Pruebas del 2026-11-07 en MySQL 9.7 aislado. El propietario aprobó la aplicación funcionando en local el 2026-11-07.

## MySQL y API

Flyway aplicó V11 sobre los esquemas de prueba y de revisión. Pasaron las 32 pruebas de integración locales: 25 en `stockly_test` y 7 de Historial en un esquema con colación sensible a tildes. CompanyIntegrationTest quedó fuera porque requiere Docker.

`SalePaymentLocalIntegrationTest` verifica:

- Los cuatro medios de pago están activos por omisión. Debe quedar al menos uno activo, solo el administrador los configura y cada cambio queda en Actividad. AC-04.
- No se confirma una venta sin pagos, con tarjeta por encima del total, con un cobro insuficiente o con un monto de tres decimales. Un medio inactivo responde `SALE_PAYMENT_METHOD_INACTIVE` e indica el pago afectado. Las existencias no cambian en ninguno de esos casos. AC-01, AC-02 y AC-04.
- Una venta de ₡3.000 pagada con ₡1.000 en tarjeta y ₡2.500 en efectivo guarda ₡2.000 aplicados y ₡2.500 recibidos en efectivo. La referencia se guarda sin espacios sobrantes. AC-01 a AC-03.
- El reintento idéntico devuelve la misma venta con dos pagos. La misma clave con otros pagos responde `SALE_CONFLICT`. AC-05.
- Una venta sin pagos, como las anteriores a STK-012, aparece como Sin cobro registrado y se anula sin devolución. Si se envía una devolución, se rechaza. AC-07.
- La anulación exige motivo y una devolución que sume exactamente lo cobrado, con medios activos o usados en el cobro. La referencia del efectivo se descarta. Las cantidades vuelven a la sucursal. AC-06.
- El resumen calcula cobrado, devuelto y neto por medio y día, y rechaza fechas futuras y rangos invertidos. Quien solo tiene acceso a Centro no ve Norte, y otra empresa no ve cobros ni ventas ajenas. Por HTTP, el lector no configura medios ni anula, pero sí consulta el resumen. AC-08 y AC-09.

`SaleLocalIntegrationTest` (STK-011) se adaptó para enviar el cobro y la devolución, y sigue validando precios, redondeo, reintento, anulación y concurrencia.

## Navegador

La compilación de producción salió correcta. Pasaron 15 pruebas de Vitest, entre ellas las de lectura de montos, pendiente, vuelto y monto aplicado en céntimos. Pasaron 6 pruebas de Playwright en Edge a 320, 375 y 1440 px, con la API simulada:

- Ventas: borrador, cobro con tarjeta rechazada por exceso, efectivo con vuelto de ₡45,69, reintento con la misma clave, comprobante y anulación con devolución.
- Cobros: listado con estado de cobro y botón Anular venta, devolución repartida en tres líneas de efectivo, bloqueo mientras falta dinero, resumen, validación de fechas y configuración de medios.
- Diseño de Entradas, sin cambios.

`companies.spec.ts` (STK-004) no forma parte de esta validación. Tampoco se ejecutó en la de STK-011, y este cambio no toca Empresas.

## Flujo real de punta a punta

React, Spring Boot y MySQL conectados en Edge. Se usó la empresa de prueba con las sucursales Central (vinculada a Principal) y Sucursal Norte. Pasaron 25 de 25 comprobaciones.

- A 1440 px, ₡5.000 en tarjeta y ₡10.000 en efectivo sobre ₡13.251,75 dieron ₡1.748,25 de vuelto. MySQL guardó `CARD 5000.00` y `CASH 8251.75` con ₡10.000 recibidos, y se descontaron 3 unidades de café y 1,5 kg de harina.
- Transferencia quedó desactivada y no se ofreció al cobrar. Se impidió desactivar el último medio y Actividad registró "Medios de pago actualizados".
- A 320 px se cobró con SINPE Móvil. Se perdió la respuesta después de que MySQL confirmara la venta, y el reintento devolvió VTA-0002 con un solo cobro.
- Desde el listado se anuló VTA-0001 con devolución en efectivo. Un faltante bloqueó la confirmación. MySQL guardó `CASH 5000.00` y `CASH 8251.75`, y las existencias volvieron a su valor anterior.
- El resumen del día mostró un neto de -₡5.000 en efectivo, ₡5.000 en tarjeta y ₡6.500 en SINPE, con ₡6.500 de total neto. Sucursal Norte no tuvo cobros.
- No hubo desbordamiento horizontal en cobro, comprobante, listado ni resumen a 320 px.

## Ajuste del resumen

Durante la revisión local, el propietario indicó que "neto" y el negativo del efectivo no se entendían. Eligió presentar los totales como **Entró**, **Salió (devoluciones)** y **Quedó**, con una tabla por medio de pago y una nota cuando un medio queda en negativo. Los cálculos no cambiaron. Se verificó a 320 y 1440 px sin desbordamiento, y la compilación, Vitest y Playwright volvieron a pasar.

Capturas: [cobro 1440](screenshots/cobro-1440.png) · [cobro 320](screenshots/cobro-320.png) · [anulación con devolución](screenshots/anular-devolucion-1440.png) · [listado 320](screenshots/listado-320.png) · [resumen 1440](screenshots/resumen-1440.png) · [resumen 320](screenshots/resumen-320.png) · [medios de pago](screenshots/medios-1440.png).

## Pendiente

- Comunicar a Gabo el contrato de pagos para STK-014: hecho el 2026-11-08 en su tarjeta.
- Prueba en un celular físico: el propietario aceptó el 2026-11-11 la verificación móvil emulada a 375 px.

## Reverificación sobre el código actual — 2026-11-11

Después de la aprobación del 07/11, STK-013, STK-014, STK-016, STK-018 y el ajuste del formulario de anulación (STK-011) tocaron el cobro y la anulación. Se volvió a verificar STK-012 sobre `main` en `1fd9606`, con React, Spring Boot y MySQL 9.7 local.

### Recorrido real

Pasaron 44 de 44 comprobaciones. El script crea dos empresas reales (A y B) y dos sucursales en A (Centro y Norte). Crea tres trabajadores de STK-005: consulta (solo Norte), cajero (solo Centro) y supervisor con permiso de anular (Centro). La suscripción de las empresas de prueba se marcó como pagada para no topar el límite de la prueba gratuita (STK-018).

- **AC-01 (4):**
  - venta con un medio y con tres medios combinados;
  - con pendiente no se confirma ni se descuenta;
  - sin cobro tampoco se confirma.
- **AC-02 (5):**
  - ₡13.251,75 pagados con ₡5.000 en tarjeta y ₡10.000 en efectivo dejan el efectivo aplicado en ₡8.251,75 y ₡1.748,25 de vuelto;
  - tarjeta o SINPE por encima del total se rechazan;
  - más de dos decimales se rechazan.
- **AC-03 (3):**
  - se guarda la referencia;
  - la tabla de pagos solo tiene empresa, venta, posición, medio, monto, recibido y referencia, sin campos de tarjeta;
  - una referencia de más de 60 caracteres se rechaza.
- **AC-04 (6):**
  - solo el administrador configura los medios, y siempre queda al menos uno activo;
  - el medio desactivado aparece inactivo, y el servidor lo rechaza (409) sin descontar;
  - el cambio queda en Actividad.
- **AC-05 (2):**
  - el reintento con la misma clave devuelve la misma venta con un solo cobro y sin volver a descontar;
  - con pagos distintos responde conflicto.
- **AC-06 (8):**
  - el cajero no anula;
  - el motivo es obligatorio y la devolución debe sumar exactamente lo cobrado;
  - la devolución combinada (tarjeta y efectivo) queda vinculada a la anulación;
  - el listado muestra «anulado» y filtra por cobro;
  - no se anula dos veces.
- **AC-07 (2):** una venta sin cobros, simulada borrando sus pagos en MySQL, aparece como «Sin cobro registrado» y se anula sin devolución.
- **AC-08 (8):**
  - el resumen de Centro cuadra por medio: cobrado, devuelto y neto;
  - el total por día cuadra;
  - el filtro por sucursal funciona;
  - se rechazan fechas futuras y un período invertido;
  - sin acceso a una sucursal se responde 404, y sin filtro solo se suman las sucursales visibles.
- **AC-09 (6):**
  - la otra empresa no ve ni anula ventas de A, ni consulta resúmenes de sus sucursales;
  - los medios son por empresa;
  - el cajero no ve el resumen de Norte.

### Pruebas automáticas (mismo código, 2026-11-11)

- **Playwright:** 40 de 40 en Edge a 1440 y 375 px. Incluye `payments.spec.ts` (anular desde el listado con devolución combinada, resumen y medios) y Ventas (AC-10).
- **Vitest:** 28 de 28.
- **Backend en MySQL aislado:** `SalePaymentLocalIntegrationTest` 2 de 2 y `SaleLocalIntegrationTest` 1 de 1.

### Ajuste de la revisión local

El propietario señaló que las columnas del historial de ventas no coincidían con sus títulos. La causa era que cada fila calculaba el ancho de Estado, Cobro y Acción según su propio contenido.

- Ahora las columnas son proporciones fijas, iguales para el encabezado y todas las filas.
- Las etiquetas largas, la fecha y el botón bajan de línea dentro de su columna.
- Medido en Edge a 1440 y 1600 px: cada columna empieza en el mismo punto en el encabezado y en todas las filas, y no hay desbordamiento. A 375 px se mantienen las tarjetas.
- Después del ajuste pasaron Playwright de ventas, cobros, devoluciones y facturación (8 de 8) y el build.

### Resultado

AC-01 a AC-10 siguen cumpliéndose sobre el código actual. No se encontraron regresiones funcionales.

### Aprobación final

El propietario revisó en local y aprobó el 2026-11-11: «Aprobado, publicar».
