# STK-011 · Ventas conectadas al inventario

Aprobado por el propietario el 2026-11-07. Mockup aprobado en móvil y escritorio. [Mockup](../../06-Mockups/STK-011/ventas-responsive.html) · [Trello](https://trello.com/c/l2W7H9Yo/11-stk-011-ventas-conectadas-al-inventario).

Una persona autorizada registra una venta directa de uno o varios productos o variantes. Stockly confirma todas las líneas juntas, genera un comprobante interno y descuenta las cantidades de una sola sucursal.

## Registro

- Si la persona tiene acceso a una sola sucursal activa, Stockly la selecciona. Si tiene varias, la persona elige una antes de vender. El servidor valida que la sucursal y los artículos estén activos, que los artículos pertenezcan a esa sucursal y que la persona tenga acceso. Si no hay sucursales disponibles, muestra el motivo e impide iniciar la venta.
- Aceptar cantidades positivas en la unidad base. Para artículos por unidad, aceptar enteros. Para kg, litros y metros, aceptar hasta tres decimales. Existencias debe mostrar la misma precisión.
- Aplicar el precio vigente del catálogo para el producto o variante. Si falta el precio, identificar la línea e impedir la confirmación hasta configurarlo. Si el precio cambia mientras la venta está abierta, mostrar el importe anterior y el nuevo, actualizar el total y pedir una nueva confirmación.
- Calcular cada importe como cantidad por precio unitario y redondearlo a dos decimales. El total es la suma de los importes redondeados. Al confirmar, conservar las cantidades, precios e importes aplicados.
- El nombre, teléfono y correo electrónico del cliente son opcionales e independientes. Si se ingresan, conservarlos en la venta y mostrarlos en el comprobante. Validar el formato del correo cuando se ingrese. Conservar el borrador en el navegador por usuario y empresa. Permitir continuar o descartar. Si se pierde la respuesta al confirmar, conservar la operación y comprobar su resultado al reintentar. Si ya existe, devolver el mismo folio. Si sigue pendiente, validar otra vez los precios y las existencias.
- Confirmar la venta y descontar existencias en una sola operación. Si falta saldo, identificar cada artículo afectado y su cantidad disponible, conservar la venta en curso y permitir corregirla.

## Comprobante y anulación

El comprobante incluye folio consecutivo por empresa (VTA-0001), sucursal, fecha y hora, responsable, cliente si se ingresó, cantidades, precios e importe total. El listado permite buscar por folio o cliente y filtrar por sucursal y estado.

Una persona autorizada puede anular la venta completa una sola vez con motivo obligatorio. La anulación conserva la venta original, registra un movimiento inverso vinculado y devuelve las cantidades a la sucursal de origen. El comprobante muestra el motivo y el vínculo. La venta y su anulación aparecen en el historial de movimientos.

Los permisos de consultar, confirmar y anular ventas se asignan por separado mediante STK-005. El servidor aplica los permisos y el alcance de empresa y sucursal a cada operación.

## Criterios de aceptación

- [ ] AC-01 Seleccionar una sucursal autorizada y confirmar varias líneas. Si el disponible cambia antes de confirmar y alguna línea excede el saldo, conservar el borrador y aplicar cero descuentos de existencias.
- [ ] AC-02 Aceptar las cantidades y decimales acordados. Mostrar los saldos con la misma precisión. Rechazar una línea sin precio y pedir reconfirmación al cambiar el precio.
- [ ] AC-03 Conservar los precios históricos y comprobar el redondeo por línea. Por ejemplo, 0,125 kg a ₡1.234,50 por kg dan ₡154,31.
- [ ] AC-04 Recuperar y descartar el borrador local. Un reintento tras perder la respuesta devuelve el mismo comprobante sin descontar de nuevo.
- [ ] AC-05 Consultar el comprobante por folio y filtrar por sucursal y estado. Verificar el folio consecutivo por empresa, el cliente opcional y el historial vinculado.
- [ ] AC-06 Anular una sola vez con motivo y devolver las cantidades a la sucursal original. Verificar permisos e impedir acceso entre empresas o sucursales.
- [ ] AC-07 Verificar registro, validaciones, comprobante y anulación en móvil y escritorio.

## Fuera de alcance

Reservas, descuentos, crédito, caja, cobros (STK-012), comprobantes electrónicos (STK-013), confirmación sin conexión (STK-014) y devoluciones parciales (STK-015). Acordar con STK-012 y STK-013 el tratamiento de anulaciones de ventas con pagos o comprobantes al integrar esos módulos.
