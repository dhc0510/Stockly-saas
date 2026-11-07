# SPEC — STK-008 | Entradas de inventario

Estado: aprobado para desarrollo. [Trello](https://trello.com/c/9B804Olq/8-stk-008-entradas-de-inventario) · [Mockup](../../06-Mockups/STK-008/entradas-responsive.html).

## Objetivo y acceso

Registrar una recepción de uno o varios artículos en una sola operación, actualizar las existencias y conservar un comprobante trazable. Cada empresa opera en una ubicación automática **Principal**. STK-005 separa los permisos de consultar, registrar y anular. El servidor limita todas las acciones a la empresa autenticada.

## Preparar y confirmar

- Buscar productos simples y variantes activas por nombre, SKU o código de barras. Un lector que escriba el código y envíe Enter agrega una unidad si el artículo se mide por unidad. Para kg, litros o metros, selecciona el artículo y pide la cantidad. Si el código no existe en el catálogo activo, muestra un mensaje claro. Al elegir manualmente un artículo, mostrar la unidad base y la existencia actual junto a la cantidad.
- Permitir hasta **200 líneas distintas** por entrada y hasta **1.000.000** de la unidad base por línea, incluso al sumar cantidades. Aceptar cantidades positivas. Para «unidad», solo enteros. Para kg, litros y metros, hasta tres decimales. Aceptar coma decimal en la interfaz y usar teclado decimal en móvil.
- Cuando se agrega dos veces el mismo artículo, sumar en una sola línea, resaltarla brevemente y mostrar la cantidad agregada y el total. Permitir editar o quitar líneas antes de confirmar.
- Exigir un motivo entre **Recepción de mercancía**, **Carga inicial** u **Otro**. «Otro» requiere detalle. La referencia externa es opcional. Si hay un artículo seleccionado o una cantidad sin agregar, avisar e impedir pasar a revisión hasta que se agregue o se limpie.
- Conservar la entrada en curso en el navegador por usuario y empresa, incluidas las líneas y los campos pendientes. Recuperarla al recargar o volver a abrir la app. Borrarla al confirmar con éxito o al descartarla.
- Al confirmar, registrar todas las líneas y actualizar sus saldos en una única operación. Si falla una línea, no registrar la entrada ni aplicar los demás cambios. Guardar automáticamente folio, fecha y hora, responsable y ubicación. Asignar folios consecutivos por empresa, con secuencias independientes para entradas (ENT-0001) y anulaciones (ANU-0001).
- Ante una respuesta perdida o incierta, conservar lo escrito y reintentar la misma operación. Si ya se registró, devolver el comprobante original sin duplicar existencias.

## Comprobante, consulta y anulación

- El listado admite búsqueda por folio o referencia y filtro por estado. El comprobante interno incluye folio, estado, fecha, responsable, ubicación, motivo, referencia y líneas con cantidad recibida y existencia inmediatamente posterior a la confirmación. Esa existencia es histórica. Mostrar fecha y hora de Costa Rica sin segundos y mantenerlas juntas en el listado y el comprobante en móvil.
- Una entrada confirmada conserva sus datos. Una persona autorizada puede anularla completa una sola vez con motivo obligatorio. Registrar un movimiento inverso vinculado, con actor y fecha. Rechazar toda la anulación si alguna línea dejaría existencias negativas. Mostrar la relación en el comprobante.
- En móvil, apilar motivo y referencia. Mantener las acciones inferiores visibles al recorrer la entrada y ocultarlas durante la edición de campos. Al enfocar el buscador, llevarlo hacia arriba y dejar los resultados disponibles sobre el área de acciones.

## Criterios de aceptación

- [ ] AC-01 Buscar y agregar artículos manualmente o por código + Enter. Mostrar unidad y existencia actual. Sumar duplicados y anunciar el nuevo total.
- [ ] AC-02 Validar motivo, detalle de «Otro», cantidades y líneas pendientes. Aceptar 1,25 kg y rechazar 1,25 unidades con un mensaje específico.
- [ ] AC-03 Recuperar el borrador local tras recargar y aislarlo por usuario y empresa. Descartar o confirmar limpia el borrador.
- [ ] AC-04 Confirmar varias líneas de forma atómica. Dos reintentos de la misma operación producen un solo comprobante y un solo incremento por artículo.
- [ ] AC-05 Buscar y filtrar entradas y consultar la existencia posterior histórica, aunque el saldo actual cambie.
- [ ] AC-06 Anular solo una vez y con motivo. Si una línea quedaría negativa, mantener intactos el movimiento original y todos los saldos.
- [ ] AC-07 Verificar permisos y aislamiento por empresa en todas las operaciones. Confirmar la vinculación con el artículo de STK-006 y el bloqueo de cambio de unidad tras su primer movimiento.
- [ ] AC-08 Verificar escritorio y móvil, incluido el teclado en iPhone, resultados accesibles durante la búsqueda, botones inferiores y fecha sin cortes.

## Fuera de alcance

Costo de compra, valoración del inventario, múltiples sucursales, borradores de servidor, órdenes de compra, conversiones de unidades, confirmación sin conexión y correcciones parciales. Las correcciones parciales corresponden a salidas y ajustes.
