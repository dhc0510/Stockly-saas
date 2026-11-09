# SPEC · STK-014 · Venta sin internet y sincronización

Aprobado por el propietario. Desarrollo autorizado el 2026-11-09. [Decisiones](../../05-Decisions/ADR-010.md) · [Mockup aprobado](../../06-Mockups/STK-014/venta-sin-internet-responsive.html) · [Trello](https://trello.com/c/RHfLlp05/14-stk-014-venta-sin-internet-y-sincronizaci%C3%B3n).

## Preparar el equipo y vender

1. El vendedor inicia sesión con internet. Stockly prepara y actualiza automáticamente los datos de la sucursal autorizada: catálogo, precios, existencias y medios de pago. En Ventas indica cuándo el equipo está listo y explica qué falta si la preparación queda incompleta.
2. Sin internet, puede registrar nuevas ventas durante un máximo de 24 horas desde la última verificación en línea de la sesión y los permisos, siempre que la sucursal esté preparada. Al vencer el acceso, pide reconectar y conserva las operaciones pendientes.
3. Stockly muestra el último saldo conocido y su hora de actualización. Resta las ventas pendientes del mismo equipo para calcular el saldo estimado disponible e impide superar ese saldo. Usa las reglas de cantidades e importes de Ventas y de registro de pagos de Cobros.
4. Al confirmar, guarda juntos la venta, los cobros y la clave de intento en el equipo. Si el guardado falla, muestra el error sin presentar la venta como guardada. El comprobante provisional es una constancia interna. Muestra referencia local, fecha y hora de la venta, artículos, cobros y estado «Pendiente de sincronización».
5. Una venta confirmada conserva los precios e importes acordados. Un borrador que sigue abierto se contrasta con el catálogo vigente al reconectar y pide confirmación si cambia el precio.

## Sincronización y revisión

- Con la app abierta, sincroniza al recuperar la conexión. También sincroniza al reabrir con conexión y ofrece «Sincronizar ahora» y el número de pendientes. Si la respuesta se pierde, reintenta con la misma clave. Una venta y sus cobros producen un solo resultado.
- El servidor revalida empresa, sesión, permisos, sucursal, artículos, existencias y cobros. Si se revocaron los permisos, bloquea la sincronización automática y conserva el registro pendiente para revisión sin exponerlo a otra cuenta. Un medio desactivado o un descuadre de pagos pasa a revisión según STK-012.
- Cuando confirma, el servidor registra juntos venta, descuento de existencias y cobros. Asigna el folio definitivo y conserva la referencia local para consulta. Ventas indica que la emisión fiscal de contingencia queda pendiente de definir. El comprobante provisional interno no habilita emisión ni envío fiscal automático.
- Si falta saldo, el servidor conserva la venta y sus cobros originales en «Requiere revisión» y muestra las líneas afectadas. Mantiene pendientes el descuento de existencias y la emisión fiscal. Una persona autorizada en la sucursal puede abrirla desde cualquier equipo con conexión, reintentar la venta completa cuando haya saldo o cerrarla con motivo y registrar cómo efectuó el reintegro, si aplica. El cierre y su responsable quedan visibles en el historial.
- Al cerrar sesión con pendientes, Stockly muestra cuántos hay y ofrece sincronizar si hay conexión. Si se cierra la sesión, las operaciones locales quedan disponibles al volver a entrar con la misma cuenta y empresa. Otra cuenta no puede verlas ni enviarlas.
- Las devoluciones y anulaciones de ventas confirmadas se hacen con conexión una vez disponible el folio definitivo. La resolución de ventas en revisión sigue su propio flujo.

## Criterios de aceptación

- [ ] AC-01 Preparar una sucursal con conexión y verificar los estados listo, incompleto y acceso vencido. Revocar un permiso y verificar el bloqueo al reconectar.
- [ ] AC-02 Registrar una venta sin conexión con artículos y cobros. Verificar el saldo estimado, el bloqueo al excederlo y el comprobante provisional. Recuperar la operación al recargar.
- [ ] AC-03 Reconectar o reabrir con conexión, probar el botón manual y simular una respuesta perdida. Verificar un solo folio, descuento y cobro, con el mismo precio, fecha de venta y referencia local. Verificar también el cambio de precio en un borrador.
- [ ] AC-04 Simular que dos equipos agotan el mismo saldo. Verificar que el faltante pase a revisión y que un usuario autorizado pueda reintentar o cerrar con motivo y registrar un reintegro desde otro equipo. Verificar que el cierre no descuente existencias.
- [ ] AC-05 Verificar los conflictos de cobro, la salida de sesión con pendientes y el aislamiento entre cuentas, empresas y sucursales.
- [ ] AC-06 Verificar que la venta sin internet se sincronice como registro interno y muestre la emisión fiscal pendiente. Comprobar el acceso a devoluciones y anulaciones con conexión y folio definitivo.
- [ ] AC-07 Revisar en móvil y escritorio los estados de preparación, venta, comprobante, pendientes y revisión.

## Fuera de alcance

Emisión fiscal de contingencia para ventas registradas sin internet, inicio de sesión sin internet, procesamiento de pagos por Stockly, devoluciones y anulaciones sin conexión y correcciones parciales de ventas pendientes.
