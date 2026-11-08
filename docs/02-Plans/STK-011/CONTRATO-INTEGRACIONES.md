# STK-011 · Contrato para Cobros, Comprobantes y Sincronización

Contrato aprobado por el propietario el 2026-11-07. [ADR-006](../../05-Decisions/ADR-006.md).

## Identidad de la venta

La API de Ventas devuelve un identificador interno en `movement.id`, el folio visible en `movement.folio` y la sucursal en `sale.branchId`. El vínculo para [Cobros](https://trello.com/c/uyzV1DAO) y [Comprobantes](https://trello.com/c/fKnAceFl) es empresa más identificador interno. El folio sirve para localizar y mostrar la venta. La anulación conserva el identificador original y registra un movimiento inverso. Las reglas para anular una venta con un cobro o un comprobante electrónico se acuerdan al diseñar esos módulos.

## Reintentos de sincronización

[STK-014](https://trello.com/c/RHfLlp05) guarda el UUID `requestKey` al preparar cada envío. Si se pierde la respuesta, reenvía la misma clave con la misma sucursal, cliente, artículos, cantidades y precios esperados. Si la venta ya existe, la API devuelve el folio original, incluso si después cambiaron el precio o el saldo.

| Respuesta de Ventas | Tratamiento en STK-014 |
| --- | --- |
| `SALE_PRICE_CHANGED` | Conservar el borrador, mostrar los precios nuevos por artículo y pedir confirmación. |
| `SALE_LINE_ERROR` | Mostrar los artículos afectados, incluido su saldo disponible cuando falte stock. Pedir una corrección antes de otro intento. |
| `SALE_CONFLICT` | La clave ya tiene otros datos. Revisar el intento original antes de enviar una operación nueva. |
| `SALE_NOT_READY` | Dejar la venta pendiente de revisión hasta que se habilite la sucursal. |

Si el servidor confirmó que no registró la venta y la persona modifica el borrador, el envío corregido usa una clave nueva. Ante pérdida de red o de la respuesta, se conserva la clave anterior hasta conocer el resultado.
