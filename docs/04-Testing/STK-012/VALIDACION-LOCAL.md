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

- Comunicar a Gabo el contrato de pagos para STK-014.
- Prueba en un celular físico.
