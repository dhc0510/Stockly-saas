# STK-015 · Devoluciones de venta

Aprobado por el propietario el 2026-11-08. El propietario aprobó el mockup el 2026-11-08. [Mockup](../../06-Mockups/STK-015/devoluciones-responsive.html) · [Decisiones aprobadas](../../05-Decisions/ADR-009.md) · [Trello](https://trello.com/c/W8Wq7Rlw/15-stk-015-devoluciones-y-efecto-sobre-ventas-e-inventario).

## Registro

1. Una persona con «Registrar devoluciones» y acceso a la sucursal original abre el comprobante de la venta. Stockly muestra lo vendido, lo ya devuelto y la cantidad pendiente por artículo.
2. La persona indica cuánto recibe y cuánto está apto para vender. Puede usar «Reponer todo» y ajustar la cantidad por línea. Las unidades no aptas quedan registradas en la devolución. Solo las aptas aumentan el saldo disponible de la sucursal original.
3. Selecciona un motivo obligatorio entre «Producto dañado», «Producto incorrecto», «El cliente cambió de opinión» y «Otro». «Otro» requiere detalle.
4. Revisa las cantidades, la reposición y el importe. Para una venta con cobro, Stockly sugiere sus medios de pago. La persona registra el reintegro que efectuó y puede cambiar el medio o repartir el importe. Para una venta sin cobro se registra solo la devolución de mercadería.
5. Stockly confirma la devolución, el movimiento de reposición si aplica y el reintegro registrado en una transacción. Asigna un folio DEV-0001 consecutivo por empresa. Un reintento del mismo intento devuelve el mismo folio.

## Reglas y consulta

- Admitir varias devoluciones por venta hasta agotar la cantidad de cada línea original. Validar la cantidad en la unidad base de la venta, con enteros para unidades y hasta tres decimales para kg, litros y metros. Rechazar cantidades por reponer que superen lo devuelto.
- Calcular con el precio de la venta original. Para partidas fraccionadas en varias devoluciones, Stockly asigna a cada devolución la diferencia entre el importe acumulado redondeado y lo ya devuelto. Al devolver toda la línea, el importe acumulado coincide con el importe original. En un cobro registrado, los medios deben sumar el reintegro calculado y el acumulado queda limitado a lo cobrado.
- Revalidar las cantidades pendientes al confirmar. Ante faltantes por concurrencia o errores de importe, identificar las líneas o montos afectados y conservar los datos ingresados para revisión. Un mismo identificador de intento con datos distintos se rechaza.
- Ofrecer «Anular venta» para ventas sin devoluciones y «Devolver lo restante» para ventas con devolución parcial. El segundo flujo precarga las cantidades pendientes. Una devolución confirmada conserva sus datos en el historial.
- En el listado mostrar «Devuelta parcialmente» o «Devuelta por completo». En la venta, listar los folios, cantidades devueltas y pendientes, reposición, reintegro y estado fiscal. En cada devolución mostrar fecha, responsable, motivo y referencia a la venta original. La consulta respeta empresa, permisos y acceso a la sucursal.

## Comprobante electrónico

| Estado del original | Resultado de la devolución |
| --- | --- |
| Aceptado | Generar automáticamente una nota de crédito por los artículos y cantidades devueltos, con referencia al original. |
| Pendiente de envío o en revisión | Guardar la devolución. El original sigue su flujo. Generar la nota al aceptarse. |
| Rechazado | Conservar la devolución y mostrar «Revisión fiscal requerida» y el motivo del rechazo. |
| Venta sin comprobante electrónico | Mostrar el comprobante interno de la devolución. |

La nota mantiene los impuestos y datos fiscales de las líneas originales que correspondan a las cantidades devueltas. Usar el código de referencia 06 para devolución de mercancía según el [anexo oficial v4.4](https://www.hacienda.go.cr/docs/ANEXOS_Y_ESTRUCTURAS_V4.4.pdf). Una devolución se vincula con una sola nota de crédito, incluso si se repite un aviso de aceptación.

## Criterios de aceptación

- [ ] AC-01 Registrar devoluciones sucesivas y rechazar cantidades que superen lo vendido, incluso con confirmaciones concurrentes.
- [ ] AC-02 Devolver tres unidades y reponer dos en la sucursal original. Comprobar que Historial refleje la reposición vinculada.
- [ ] AC-03 Verificar motivo y detalle, precio histórico, precisión, límite de reintegro y medios. Verificar también una venta sin cobro.
- [ ] AC-04 Reintentar tras perder la respuesta y recuperar el mismo folio con un solo efecto. Rechazar la misma clave con datos distintos.
- [ ] AC-05 Verificar una nota por devolución para un original aceptado, la espera para un original pendiente y el aviso para un original rechazado.
- [ ] AC-06 Devolver lo restante tras una devolución parcial y conservar el flujo de anulación completa para una venta sin devoluciones.
- [ ] AC-07 Verificar permisos y aislamiento entre empresas y sucursales. Revisar listado, comprobantes y flujo en escritorio y celular.

## Fuera de alcance

Corrección de devoluciones confirmadas, devoluciones sin venta original, cambios de producto, devoluciones entre sucursales, procesamiento de pagos y sincronización sin conexión. La resolución fiscal de un original rechazado se revisa desde facturación.
