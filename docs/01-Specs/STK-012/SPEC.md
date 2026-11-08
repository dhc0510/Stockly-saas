# STK-012 · Cobros y medios de pago

Aprobado por el propietario el 2026-11-07. Mockup aprobado en móvil y escritorio el 2026-11-07. [Mockup](../../06-Mockups/STK-012/cobros-responsive.html) · [Trello](https://trello.com/c/uyzV1DAO/12-stk-012-cobros-y-medios-de-pago) · Depende de [STK-011](../STK-011/SPEC.md).

## 1. Objetivo

Registrar cómo pagó el cliente cada venta, y cómo se le devolvió el dinero si la venta se anula. Stockly **solo registra** los cobros. No procesa pagos, no mueve dinero y no se conecta con bancos, datáfonos ni pasarelas.

## 2. Actores

- **Persona que vende:** con permiso para confirmar ventas (STK-011), registra el cobro al confirmar.
- **Persona que anula:** con permiso para anular ventas, registra la devolución.
- **Administrador de la empresa:** activa o desactiva los medios de pago.
- **Persona con permiso para consultar ventas:** consulta comprobantes y el resumen de cobros de sus sucursales.

## 3. Flujo

1. La persona arma la venta como en STK-011 y pasa a **Revisar y cobrar**.
2. Elige un medio activo, escribe el monto y agrega el pago. Puede repetirlo con otros medios hasta cubrir el total.
3. Stockly muestra Total, Pagado, Pendiente y Vuelto en todo momento.
4. Con el pendiente en cero, la persona confirma. La venta, el descuento de existencias y los cobros se guardan en una sola operación.
5. El comprobante muestra los pagos, lo recibido y el vuelto.
6. Al anular, la persona escribe el motivo y registra cómo devolvió el dinero. La anulación, la reposición de existencias, la anulación de los cobros y la devolución se guardan juntas.

## 4. Medios de pago

| Medio | Datos | Vuelto |
| --- | --- | --- |
| Efectivo | Monto recibido | Sí |
| Tarjeta (datáfono externo) | Monto y referencia opcional | No |
| SINPE Móvil | Monto y referencia opcional | No |
| Transferencia | Monto y referencia opcional | No |

- La referencia admite hasta 60 caracteres, como el número de comprobante o de autorización. Stockly no pide ni guarda números de tarjeta, vencimientos, códigos de seguridad ni otros datos de la tarjeta.
- El administrador activa o desactiva cada medio para su empresa. Al menos uno debe quedar activo. Las empresas nuevas empiezan con los cuatro activos.
- Desactivar un medio no cambia los cobros ya registrados. Se aplica a las ventas que se confirmen después.

## 5. Reglas del cobro

- Una venta nueva solo se confirma con el cobro completo. No hay saldos pendientes ni ventas a crédito.
- Se pueden combinar medios, incluido el mismo medio más de una vez.
- Los montos son positivos, en colones y con hasta dos decimales.
- Tarjeta, SINPE Móvil y transferencia no pueden superar el pendiente al momento de agregarlos.
- Solo el efectivo da vuelto. El vuelto es lo pagado menos el total y no puede superar el efectivo recibido.
- Para cada pago se guarda el **monto aplicado**. En efectivo también se guarda lo recibido; el vuelto se descuenta del efectivo. La suma de los montos aplicados es igual al total de la venta.
- El servidor vuelve a validar todo al confirmar: medios activos, montos, pendiente en cero y vuelto permitido.
- Un reintento con la misma clave y los mismos datos, incluidos los pagos, devuelve la venta original sin registrar otro cobro. Si los pagos son distintos, responde conflicto.

## 6. Anulación y devolución

- El botón **Anular venta** aparece en el comprobante y en cada venta confirmada del listado. El motivo sigue siendo obligatorio.
- Si la venta tiene cobro, la persona registra la **devolución del dinero**. Viene sugerida con los mismos medios y montos del cobro, y se puede cambiar el medio, repartir entre varios medios o quitar líneas.
- La devolución debe sumar exactamente lo cobrado. No se admiten devoluciones parciales.
- Los medios disponibles para devolver son los activos más los que se usaron en el cobro original. Tarjeta, SINPE Móvil y transferencia admiten referencia opcional.
- Al anular, los cobros quedan en estado **Cobro anulado** y la devolución queda vinculada a la anulación, con fecha y responsable.
- Las ventas sin cobro registrado se anulan como en STK-011, sin devolución.

## 7. Ventas anteriores

Las ventas confirmadas antes de STK-012 se muestran como **Sin cobro registrado**. No se les puede agregar un cobro después.

## 8. Listado y comprobante

- El listado muestra el estado del cobro: Cobrada, Cobro anulado o Sin cobro registrado. Se puede filtrar por ese estado, además de los filtros de STK-011.
- El comprobante muestra cada pago con su medio, su detalle (lo recibido o la referencia) y el monto aplicado, junto con el total, lo recibido y el vuelto. Si hubo devolución, muestra la fecha, el responsable y cada medio con su monto y su referencia.

## 9. Resumen de cobros

- Muestra lo cobrado, lo devuelto y el **neto** por medio de pago, el total neto y una tabla del neto por día.
- Se filtra por sucursal y por rango de fechas (desde y hasta). Por omisión muestra el día actual. Se rechazan los rangos invertidos y las fechas futuras.
- El cobro cuenta en el día de la venta y la devolución en el día de la anulación, con días de Costa Rica. El efectivo cuenta el monto aplicado, sin el vuelto.
- Solo incluye las sucursales a las que la persona tiene acceso. No incluye apertura ni cierre de caja.

## 10. Permisos y seguridad

- Registrar cobros va incluido en el permiso de confirmar ventas. Registrar la devolución va incluido en el permiso de anular ventas.
- Solo el administrador de la empresa configura los medios de pago.
- El resumen y los cobros del comprobante requieren el permiso de consultar ventas.
- El servidor aplica la empresa, la sucursal y los permisos en cada operación. Una empresa no ve ni modifica cobros ni configuración de otra.

## 11. Venta sin conexión (para STK-014)

- Todos los medios se podrán registrar sin conexión. Stockly solo anota el pago; el comercio verifica la tarjeta, el SINPE o la transferencia.
- La venta y sus pagos viajan juntos con la misma clave de reintento. Mientras no se sincronicen quedan pendientes.
- Si al sincronizar un medio está desactivado o los pagos no cuadran con el total, la venta queda para revisión según el contrato de STK-011. Se comunica a Gabo antes de cerrar STK-012.

## 12. Errores y casos especiales

- **No hay medios activos:** se avisa que un administrador debe activar al menos uno y no se puede cobrar.
- **Monto inválido:** se pide un monto positivo con hasta dos decimales.
- **Tarjeta, SINPE o transferencia mayor al pendiente:** se rechaza indicando el pendiente.
- **Medio desactivado mientras se cobra:** al confirmar se identifica el pago afectado y se pide quitarlo. No se registra nada.
- **Pendiente mayor que cero o vuelto mayor que el efectivo:** no se confirma.
- **Se pierde la respuesta al confirmar:** el reintento devuelve el mismo folio con un solo cobro.
- **Devolución que no suma lo cobrado o sin motivo:** no se confirma la anulación.
- **Venta ya anulada:** se rechaza la segunda anulación.

## 13. Criterios de aceptación

- [ ] AC-01 Confirmar una venta con un solo medio y con varios medios combinados. La suma aplicada es igual al total y no se puede confirmar con pendiente.
- [ ] AC-02 Calcular el vuelto solo con efectivo. Por ejemplo, una venta de ₡13.251,75 pagada con ₡5.000 en tarjeta y ₡10.000 en efectivo da ₡1.748,25 de vuelto, y el efectivo queda aplicado en ₡8.251,75. Rechazar tarjeta, SINPE o transferencia por encima del pendiente.
- [ ] AC-03 Guardar la referencia opcional y no guardar datos de la tarjeta.
- [ ] AC-04 Activar y desactivar medios como administrador, con al menos uno activo. Un medio desactivado no se ofrece y el servidor lo rechaza al confirmar.
- [ ] AC-05 Un reintento tras perder la respuesta devuelve la misma venta con un solo cobro. Un reintento con pagos distintos responde conflicto.
- [ ] AC-06 Anular desde el listado o el comprobante con motivo obligatorio y devolución que sume exactamente lo cobrado. Los cobros quedan anulados y la devolución vinculada.
- [ ] AC-07 Las ventas de STK-011 se muestran como Sin cobro registrado y se anulan sin devolución.
- [ ] AC-08 El resumen muestra cobrado, devuelto y neto por medio y por día, filtra por sucursal y fechas, y respeta el acceso por sucursal.
- [ ] AC-09 Verificar permisos y aislamiento entre empresas y sucursales.
- [ ] AC-10 Verificar cobro, comprobante, anulación, configuración y resumen en móvil y escritorio.

## 14. Fuera de alcance

Procesar pagos o integrarse con bancos, datáfonos o pasarelas; ventas a crédito, abonos o saldos pendientes; apertura y cierre de caja; descuentos y propinas; devoluciones parciales (STK-015); comprobantes electrónicos (STK-013); el funcionamiento sin conexión en sí (STK-014); y monedas distintas al colón.
