# STK-013 · Facturación electrónica para Costa Rica

Aprobado por el propietario el 2026-11-08. Mockup aprobado en móvil y escritorio el 2026-11-08. [Mockup](../../06-Mockups/STK-013/facturacion-responsive.html) · [Trello](https://trello.com/c/fKnAceFl/13-stk-013-facturaci%C3%B3n-electr%C3%B3nica-para-costa-rica) · Depende de [STK-011](../STK-011/SPEC.md) y [STK-012](../STK-012/SPEC.md).

## 1. Objetivo

Emitir el comprobante electrónico que corresponde a cada venta, enviarlo a Hacienda, seguir su estado y entregarlo al cliente. Al anular una venta con un comprobante aceptado, emitir la nota de crédito. Stockly se conecta **directamente** con Hacienda, usando las credenciales de cada empresa.

La fuente oficial son los Anexos y Estructuras **v4.4** del Ministerio de Hacienda y la resolución de comprobantes electrónicos vigente. Los códigos, campos y validaciones exactos se toman de esos documentos, no de este texto. Los datos fiscales que elija cada comercio (CABYS, tarifas y actividad) son responsabilidad del comercio y su contador.

## 2. Actores

- **Administrador de la empresa:** configura la facturación, las credenciales, el emisor, las sucursales y el ambiente.
- **Persona que vende:** con permiso para confirmar ventas, emite al confirmar y corrige los rechazos.
- **Persona que anula:** con permiso para anular ventas, provoca la nota de crédito.
- **Persona que consulta ventas:** descarga los comprobantes y los reenvía por correo.
- **Quien gestiona el catálogo** (STK-006): registra el CABYS y la tarifa de IVA.

## 3. Tipos de comprobante

| Venta | Comprobante |
| --- | --- |
| Cliente con tipo y número de identificación | Factura electrónica |
| Público general, sin identificación | Tiquete electrónico |
| Anulación de una venta con comprobante aceptado | Nota de crédito electrónica por el total, referida al comprobante original |

Otros comprobantes (exportación, compra, nota de débito y recibo de pago) quedan fuera de alcance.

## 4. Configuración (solo administrador)

- **Credenciales:** usuario y contraseña de API, llave criptográfica `.p12` y su PIN, obtenidos en TRIBU-CR.
  - Se guardan cifrados y nunca se vuelven a mostrar ni se devuelven por la API.
  - Pueden reemplazarse.
  - Al guardarlas, Stockly prueba la conexión y valida la llave, y muestra el vencimiento de la llave.
- **Emisor:** el nombre y la identificación salen del registro de la empresa (STK-004) y no se editan aquí. El administrador completa:
  - actividad económica;
  - provincia, cantón, distrito y otras señas, según la codificación oficial;
  - correo para comprobantes.
- **Sucursales:**
  - Cada sucursal de Stockly tiene un código de Hacienda único por empresa (001, 002…), que se asigna en orden y luego no cambia.
  - Cada una tiene un solo punto de venta (00001) y consecutivos propios por tipo de comprobante y ambiente.
- **Ambiente:**
  - Toda empresa empieza en **pruebas**.
  - El administrador activa **producción** solo cuando las credenciales, el emisor y al menos un comprobante de prueba aceptado están listos, y lo confirma en un paso aparte. No se vuelve a pruebas desde la aplicación.
  - En pruebas, todas las pantallas y documentos indican que los comprobantes no tienen validez fiscal.
- Mientras la configuración esté incompleta, las ventas se confirman y sus comprobantes quedan **Pendientes**, con el motivo visible.

## 5. Datos fiscales del catálogo

- Cada variante del catálogo (STK-006) tiene un **código CABYS** de 13 dígitos y una **tarifa de IVA** de la tabla oficial.
- El precio del catálogo **incluye IVA**. No cambian los totales de ventas ni de cobros.
- Se puede vender un producto sin CABYS. La venta avisa del dato faltante y su comprobante queda Pendiente hasta completarlo.
- El cambio en el catálogo se coordina con Gabo antes de integrarlo.

## 6. Datos del cliente en la venta

- Se agregan el **tipo** (cédula física, cédula jurídica, DIMEX o NITE) y el **número de identificación** a los datos opcionales de STK-011. Se valida la cantidad de dígitos según el tipo.
- Con tipo y número, el nombre es obligatorio y se emite factura. Sin identificación, se emite tiquete.
- La venta muestra antes de confirmar qué comprobante se emitirá.
- El correo sigue siendo opcional. Si existe, se usa para entregar el comprobante.

## 7. Cálculo fiscal

- Por cada línea: **sin IVA = total ÷ (1 + tarifa)**, redondeado a céntimos, e **IVA = total − sin IVA**. Así la suma coincide exactamente con el total cobrado.
- El comprobante incluye el CABYS, la cantidad y la unidad de cada línea, y los medios de pago de STK-012 con sus códigos oficiales.
- Antes de implementar, se verifican contra los Anexos v4.4 las reglas de redondeo, decimales y unidades de medida.

## 8. Emisión y estados

1. Al confirmar la venta y el cobro, Stockly crea el comprobante en la **misma transacción**, con su consecutivo y su clave numérica, y lo deja **Pendiente de envío**. La confirmación de la venta no espera a Hacienda.
2. Un proceso en segundo plano lo genera según los Anexos v4.4, lo firma con la llave de la empresa y lo envía.
3. Estados:

| Estado | Significado |
| --- | --- |
| Pendiente de envío | Aún no se envía, falta un dato o Hacienda no respondió. Se reintenta automáticamente con espera creciente. |
| Enviado, en revisión | Hacienda lo recibió y no ha dado resultado. Stockly consulta el estado. |
| Aceptado | Hacienda lo aceptó. Se entrega al cliente. |
| Rechazado | Hacienda lo rechazó. Se muestra su motivo. |
| Cancelado | Nunca llegó a Hacienda y la venta se anuló. |

- Antes de reenviar, Stockly consulta por la clave si Hacienda ya lo recibió, para no duplicar.
- El consecutivo y la clave de un comprobante no cambian entre reintentos.
- La persona puede pedir **Reintentar ahora** en un comprobante pendiente.

## 9. Rechazo y corrección

- La venta queda con **Comprobante rechazado** y el motivo de Hacienda.
- La persona con permiso para confirmar ventas corrige los datos del cliente (tipo, número y nombre), o los quita para emitir tiquete. Stockly emite un **comprobante nuevo** con otro consecutivo.
- Si la causa es de configuración o del catálogo, se indica qué completar. El nuevo comprobante sale cuando se corrige.
- El comprobante rechazado se conserva como historial de la venta y no se reutiliza su consecutivo.

## 10. Anulación y nota de crédito

- Las reglas de anulación y devolución de STK-011 y STK-012 se mantienen.
- **Comprobante aceptado:** al anular, Stockly emite automáticamente una nota de crédito por el total, referida al comprobante original, con el motivo de la anulación. Sigue los mismos estados y reintentos.
- **Pendiente que nunca llegó a Hacienda:** se marca Cancelado y no se envía.
- **Enviado y en revisión:** al conocerse el resultado, se emite la nota de crédito si fue aceptado. Si fue rechazado, no hace falta.
- **Rechazado:** no requiere nota de crédito.

## 11. Entrega y consulta

- Cuando Hacienda acepta, si la venta tiene correo, Stockly envía el **PDF** (representación gráfica), el **XML firmado** y la **respuesta de Hacienda**. El fallo del correo no cambia el estado fiscal y se puede reenviar.
- Desde la venta se descargan el PDF, el XML y la respuesta, y se puede reenviar el correo.
- El listado de ventas muestra el tipo y el estado del comprobante, filtra por estado y tipo, y avisa cuando hay comprobantes rechazados o pendientes.
- Las ventas anteriores a STK-013 se muestran **Sin comprobante** y no se emiten de forma retroactiva.

## 12. Permisos, seguridad y conservación

- Descargar y reenviar: permiso de consultar ventas. Corregir un rechazo: permiso de confirmar ventas. Configurar la facturación: solo el administrador de la empresa.
- El servidor aplica la empresa, la sucursal y los permisos en cada operación. Una empresa nunca usa credenciales ni ve comprobantes de otra.
- Las credenciales se cifran con una clave que no está en la base de datos ni en el código. No aparecen en registros, errores ni respuestas.
- Los cambios de configuración y de ambiente quedan en Actividad.
- Los XML firmados y las respuestas de Hacienda se conservan sin modificar. El plazo y su relación con el borrado de empresas (STK-004) se definen según la normativa vigente antes de pasar a producción.

## 13. Contrato para STK-014 (venta sin conexión)

- Las ventas confirmadas sin conexión envían sus datos de cliente con la venta. El comprobante se crea en el servidor al sincronizar, con la situación oficial que corresponda a comprobantes generados sin internet, sin asumir aceptación inmediata.
- Gabo recibe los estados de esta SPEC y la regla de no reenviar sin consultar la clave antes de integrar STK-014.

**Ajuste del 2026-11-11, aprobado por el propietario el 2026-11-11.** Se basa en el Reglamento de Comprobantes Electrónicos, Decreto 44739-H, art. 23.

- **Cuándo se emite:** cuando el servidor confirma la venta sincronizada, crea su factura o tiquete con las mismas reglas de §7, en la misma transacción. Una venta en «Requiere revisión» (STK-014) no emite hasta que se confirma. Si se cierra sin confirmar, no emite nunca.
- **Situación y fecha:** la clave lleva la situación 3, «sin internet». La fecha de emisión y la fecha de la clave son la hora real de la venta en el equipo. El consecutivo se asigna al sincronizar, en el orden de llegada.
- **Plazo:** el Reglamento pide enviarlo a Hacienda a más tardar dos días hábiles después de generarlo. Stockly lo envía en cuanto se sincroniza. Si la venta llega después de ese plazo, Stockly igual lo emite y lo envía, y marca el comprobante como «Enviado fuera de plazo», visible para el administrador en la venta y en el listado.
  - Los días hábiles se cuentan de lunes a viernes, sin feriados. El aviso es informativo y no reemplaza la revisión del contador.
- **Después:** el envío, los reintentos, el rechazo, la corrección, la anulación y el correo siguen §8 a §11. Una corrección o una nota de crédito posteriores se emiten en situación normal, porque se generan con conexión.
- **Ventas:** la venta sincronizada muestra su comprobante como cualquier otra, ya sin el aviso «Emisión fiscal de contingencia pendiente».

## 14. Errores y casos especiales

- **Configuración incompleta o falta CABYS:** el comprobante queda Pendiente con el motivo, y se emite al completarse.
- **Credenciales o llave inválidas o vencidas:** se avisa al administrador y los comprobantes quedan Pendientes.
- **Hacienda no responde o hay un error temporal:** se reintenta. Si un comprobante pasa más de un tiempo configurable sin respuesta, se avisa.
- **Respuesta perdida:** se consulta por la clave y no se duplica.
- **Identificación con dígitos inválidos:** no se confirma la venta hasta corregirla o quitarla.
- **La venta se confirma sin conexión con Hacienda:** se registra igual (§8).
- **Dos procesos intentan enviar el mismo comprobante:** solo uno lo envía.

## 15. Criterios de aceptación

- [ ] AC-01 Configurar credenciales, emisor y sucursales. Las credenciales se guardan cifradas, nunca se muestran y se puede probar la conexión. Solo el administrador accede.
- [ ] AC-02 Registrar CABYS y tarifa en el catálogo y validar 13 dígitos. Un producto sin CABYS deja el comprobante pendiente con el motivo.
- [ ] AC-03 Una venta con identificación emite factura y una sin identificación emite tiquete. Se valida la identificación y la venta muestra qué se emitirá.
- [ ] AC-04 El desglose sin IVA e IVA suma exactamente el total por línea y por venta.
- [ ] AC-05 El XML cumple el esquema v4.4, se firma y lo acepta el ambiente de pruebas de Hacienda para factura, tiquete y nota de crédito.
- [ ] AC-06 Si Hacienda no responde, la venta se confirma y el comprobante queda pendiente. El reintento consulta la clave y no duplica. El consecutivo y la clave no cambian.
- [ ] AC-07 Un rechazo muestra el motivo y permite corregir. El comprobante nuevo usa otro consecutivo y el rechazado se conserva.
- [ ] AC-08 Anular una venta con comprobante aceptado emite una nota de crédito vinculada. Anular un comprobante pendiente no enviado lo cancela.
- [ ] AC-09 Al aceptarse, se envía el correo con PDF, XML y respuesta. Se puede descargar y reenviar.
- [ ] AC-10 Producción solo se activa con los requisitos cumplidos. En pruebas se indica que los comprobantes no tienen validez.
- [ ] AC-11 Permisos, aislamiento entre empresas y sucursales, y ausencia de credenciales en respuestas y registros.
- [ ] AC-12 Verificar venta, comprobante, rechazo, anulación, catálogo y configuración en móvil y escritorio.
- [ ] AC-13 (ajuste aprobado el 2026-11-11) Una venta sin internet sincronizada emite su factura o tiquete con situación 3 y la fecha real de la venta. No emite mientras está en revisión ni si se cierra. Si llega después de dos días hábiles se emite igual y se marca «Enviado fuera de plazo».

## 16. Fuera de alcance

Factura de exportación, de compra, nota de débito y recibo electrónico de pago; recepción y aceptación de comprobantes de proveedores; exoneraciones, descuentos, otros cargos y otras monedas; un catálogo de clientes reutilizables; varios puntos de venta por sucursal; la emisión en sí desde el modo sin conexión (STK-014); y la integración con proveedores de facturación externos.

## Referencias oficiales

- [Anexos y Estructuras v4.4 (ATV, Ministerio de Hacienda)](https://atv.hacienda.go.cr/ATV/ComprobanteElectronico/frmAnexosyEstructuras.aspx)
- [Anexos y Estructuras v4.4 (PDF)](https://www.hacienda.go.cr/docs/ANEXOS_Y_ESTRUCTURAS_V4.4.pdf)
- [Resolución sobre comprobantes electrónicos (SCIJ, PGR)](http://www.pgrweb.go.cr/DOCS/NORMAS/1/VIGENTE/S/2020-2029/2020-2024/2024/1936C/1701B7.HTML)
