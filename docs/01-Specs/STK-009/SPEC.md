# SPEC — STK-009 | Salidas, pérdidas y ajustes de inventario

Estado: aprobado por el propietario el 2026-11-07. Desarrollo autorizado el 2026-11-07. [Trello](https://trello.com/c/hGYZ9mtQ/9-stk-009-salidas-p%C3%A9rdidas-y-ajustes-de-inventario) · [Mockup aprobado](../../06-Mockups/STK-009/salidas-ajustes-responsive.html).

## Objetivo

Registrar lo que sale del inventario por razones distintas de una venta y corregir el saldo después de un conteo, con un comprobante trazable. Cada operación ocurre en la ubicación automática **Principal** de STK-008. El servidor limita todas las acciones a la empresa autenticada.

## Acceso

| Acción | Quién puede |
|---|---|
| Consultar salidas, ajustes y comprobantes | Administrador de empresa o acceso de lectura, operar o administrar al módulo **Salidas y ajustes** |
| Registrar salidas | Administrador o acceso de operar o administrar |
| Registrar ajustes | Administrador, u operar o administrar más el permiso especial **Registrar ajustes de inventario** |
| Anular salidas y ajustes | Administrador, o acceso al módulo más el permiso especial **Anular salidas y ajustes** |

Este módulo y estos permisos son independientes de los de entradas. La interfaz oculta las acciones no permitidas, y el servidor las rechaza aunque se soliciten directamente. Quien consulta el módulo ve salidas y ajustes, aunque no pueda registrar ajustes.

## Reglas comunes de registro

Salidas y ajustes funcionan como las entradas de STK-008:

- Buscar productos simples y variantes activas por nombre, SKU o código de barras. Si el código no existe en el catálogo activo, mostrar un mensaje claro. Mostrar unidad y existencia actual.
- Permitir hasta **200 líneas distintas** y hasta **1.000.000** de la unidad base por línea. Para «unidad», solo enteros. Para kg, litros y metros, hasta tres decimales. Aceptar coma decimal y usar teclado decimal en móvil.
- Al repetir un artículo, sumar en una sola línea, resaltarla y anunciar lo agregado y el total. Permitir editar o quitar líneas antes de confirmar. Un lector que envíe código + Enter suma 1 si el artículo se mide por unidad; para kg, litros o metros, selecciona el artículo y pide la cantidad.
- Exigir motivo. «Otro» requiere detalle de hasta 150 caracteres. La referencia es opcional, de hasta 100 caracteres. Impedir la revisión si queda un artículo o una cantidad sin agregar.
- Conservar el borrador en el navegador por usuario, empresa y tipo de operación, recuperarlo al volver y avisarlo en el listado. Borrarlo al confirmar con éxito o al descartarlo.
- Confirmar todas las líneas y saldos en una única operación. Si falla una línea, no registrar nada. Guardar automáticamente folio, fecha y hora, responsable y ubicación.
- Ante una respuesta perdida o incierta, conservar lo escrito y reintentar la misma operación. Si ya se registró, devolver el comprobante original sin aplicar de nuevo. Rechazar la misma clave de confirmación con datos distintos.
- Nunca dejar existencias negativas.

## Salidas

- Motivos: **Daño o rotura**, **Vencimiento**, **Robo o faltante**, **Consumo interno** u **Otro**.
- La cantidad debe ser mayor que cero. Cada línea muestra la cantidad, la existencia actual y lo que quedará.
- Si una línea dejaría saldo negativo, avisar en la línea e impedir continuar. Si el saldo cambia antes de confirmar y ya no alcanza, rechazar toda la salida, indicar los artículos y su existencia, y conservar lo escrito.

## Ajustes

- Motivos: **Conteo físico**, **Corrección de error** u **Otro**.
- La persona indica la **cantidad contada**, que puede ser cero. Stockly calcula la diferencia, sube o baja el saldo y lo deja igual a lo contado. Cada línea muestra el saldo en Stockly, lo contado y la diferencia.
- Una diferencia de cero se registra como **Sin diferencia** y no cambia el saldo. Así consta que el artículo se contó.
- Cada lectura del lector suma 1 a lo contado.
- Si el saldo de un artículo cambió desde que se agregó al ajuste, no aplicar ninguna línea. Mostrar el saldo anterior, el nuevo y la nueva diferencia, conservar lo contado y pedir confirmar otra vez. Un reintento de un ajuste ya registrado devuelve su comprobante, sin mostrar este aviso.

## Listado y comprobante

- La opción de menú **Salidas y ajustes** muestra un único listado paginado, del más reciente al más antiguo. Admite búsqueda por folio o referencia y filtros por tipo y estado.
- Los estados de la interfaz incluyen carga, vacío, error y sin resultados.
- Folios consecutivos por empresa, con secuencias independientes para salidas (SAL-0001) y ajustes (AJU-0001). Las anulaciones continúan la serie ANU existente, compartida con las de entradas.
- El comprobante incluye folio, tipo, estado, ubicación, fecha y hora de Costa Rica sin segundos, responsable, motivo y referencia.
- En salidas, cada línea muestra la cantidad retirada y la existencia posterior. En ajustes, muestra el saldo anterior, lo contado y la diferencia. Estos datos son históricos y no cambian con movimientos posteriores.

## Anulación

- Una persona autorizada puede anular una salida o un ajuste completo, una sola vez, con motivo obligatorio. Registrar un movimiento inverso vinculado, con actor y fecha, y mostrar la relación en el comprobante.
- La anulación de una salida devuelve la cantidad retirada. La de un ajuste revierte su diferencia, no restituye el saldo anterior.
- Si alguna línea dejaría existencias negativas, rechazar toda la anulación. Las correcciones parciales se hacen con un ajuste posterior.
- Una salida o un ajuste confirmado conserva sus datos y no se edita.

## Criterios de aceptación

- [x] AC-01 Registrar una salida de varias líneas con cada motivo. Exigir detalle en «Otro». Aceptar 1,25 kg y rechazar 1,25 unidades con un mensaje específico.
- [x] AC-02 Rechazar una salida que deje saldo negativo sin aplicar ninguna línea, también cuando el saldo cambió antes de confirmar.
- [x] AC-03 Registrar un ajuste que sube, otro que baja, uno a cero y una línea sin diferencia. El saldo queda igual a lo contado y el comprobante muestra saldo anterior, contado y diferencia.
- [x] AC-04 Si el saldo cambió antes de confirmar un ajuste, no aplicar nada, mostrar la nueva diferencia y permitir confirmar otra vez con lo contado.
- [ ] AC-05 Lector: código + Enter suma 1 en salidas y a lo contado en ajustes. Kg, litros o metros piden la cantidad. Un código desconocido muestra un mensaje claro.
- [x] AC-06 Dos reintentos de la misma operación producen un solo comprobante y un solo cambio de saldo. La misma clave con datos distintos se rechaza.
- [x] AC-07 Recuperar el borrador tras recargar, aislado por usuario, empresa y tipo. Descartar o confirmar lo limpia.
- [x] AC-08 Buscar y filtrar por folio, referencia, tipo y estado. Los folios SAL y AJU son consecutivos por empresa y ANU continúa la serie compartida. Los saldos del comprobante no cambian con movimientos posteriores.
- [x] AC-09 Anular salidas y ajustes solo una vez y con motivo. Si una línea quedaría negativa, mantener intactos el movimiento original y todos los saldos.
- [x] AC-10 Verificar en el servidor cada fila de Acceso y el aislamiento por empresa en todas las operaciones. Los permisos de entradas no dan acceso a salidas y ajustes, ni al revés, incluso consultando un movimiento por su identificador.
- [x] AC-11 Las entradas de STK-008 siguen funcionando. Su listado no muestra salidas ni ajustes, sus saldos son coherentes con ellos, y el primer movimiento bloquea el cambio de unidad del artículo.
- [ ] AC-12 Verificar escritorio y móvil según el mockup, incluido el teclado en iPhone, un lector físico, las acciones inferiores y la fecha sin cortes.

## Fuera de alcance

Múltiples sucursales y traslados, costos y valoración de pérdidas, aprobación previa por otra persona, correcciones parciales de un comprobante, artículos inactivos, borradores de servidor, operación sin conexión, descuento por ventas (STK-011), devoluciones (STK-015) e historial unificado de movimientos (STK-010).

## Dependencias

STK-005 (permisos), STK-006 (catálogo) y STK-008 (saldos, movimientos y ubicación Principal). STK-007 no está integrada en `main`; este alcance no depende de ella. Si entran las sucursales, entradas, salidas y ajustes deberán adaptarse juntos mediante una decisión aparte.
