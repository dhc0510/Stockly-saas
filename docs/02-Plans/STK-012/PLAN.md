# PLAN · STK-012

Aprobado por el propietario el 2026-11-07. [SPEC](../../01-Specs/STK-012/SPEC.md) · [Mockup](../../06-Mockups/STK-012/cobros-responsive.html).

## 1. Base de datos (V11)

- `company_payment_methods`: empresa, medio (`CASH`, `CARD`, `SINPE`, `TRANSFER`) y activo. Si una empresa no tiene filas, se tratan los cuatro como activos. Al guardar se crean las filas que falten.
- `sale_payments`: empresa, venta, posición, medio, monto aplicado, monto recibido (solo efectivo) y referencia. Clave foránea a `sales` por empresa. Restricciones: montos positivos, recibido mayor o igual al aplicado y referencia solo en los medios que no son efectivo.
- `sale_refunds`: empresa, venta, movimiento de anulación, posición, medio, monto y referencia. Claves foráneas a `sales` y al movimiento inverso, ambas por empresa.
- Índices por empresa y fecha para el resumen, apoyados en `inventory_movements.created_at`.
- No se modifican los datos de las ventas existentes. Una venta sin filas en `sale_payments` es Sin cobro registrado.

## 2. Backend

- **Medios de pago:** `PaymentMethodService` y su controlador, con `GET /api/sales/payment-methods` para quien puede vender y `PUT` solo para el administrador. El guardado valida que quede al menos uno activo y registra el cambio en la auditoría de STK-005.
- **Confirmar:** `SaleInput` recibe `payments` (medio, monto, referencia). En `SaleService.confirm` se valida todo después de calcular el total con los precios vigentes: medios activos, montos con dos decimales, sin exceso en los medios que no son efectivo y vuelto cubierto por el efectivo. Se calcula el monto aplicado y se inserta en la misma transacción que la venta. Errores: `SALE_PAYMENT_INVALID` (400) y `SALE_PAYMENT_METHOD_INACTIVE` (409), con el pago afectado en `fieldErrors`.
- **Reintentos:** los pagos ordenados entran en la huella del intento. Así la misma clave con otros pagos responde `SALE_CONFLICT`, y un intento ya registrado devuelve la venta original antes de revalidar medios.
- **Anular:** `VoidInput` recibe `refunds`. `SaleService.reverse` exige que sumen lo cobrado, con medios activos o usados en el cobro, y los inserta vinculados al movimiento inverso en la misma transacción. Una venta sin cobro rechaza devoluciones.
- **Consultar:** `Sale` incluye pagos y devolución. `SaleRow` incluye el estado del cobro. `list` acepta el filtro `payment` (`paid`, `voided`, `none`).
- **Resumen:** `GET /api/sales/payments/summary?branchId&from&to` con agregaciones en SQL por medio y día de Costa Rica (cobros por fecha de venta y devoluciones por fecha de anulación). Reutiliza la conversión de días a rango UTC y las validaciones de fecha de STK-010, y el filtro de sucursales visibles de `InventoryScope`.

## 3. Frontend

- `SalesPage.tsx`: la revisión de la venta pasa a **Revisar y cobrar**, con selector de medios activos, formulario de pago, lista de pagos y el bloque Total/Pagado/Pendiente/Vuelto. Confirmar solo se habilita sin pendiente.
- Comprobante con la sección Cobro y la devolución registrada. Listado con la columna y el filtro de cobro y el botón **Anular venta**.
- Pantalla de anulación con motivo obligatorio y la sección Devolución del dinero.
- Nuevas pantallas: **Resumen de cobros** y **Medios de pago** (solo administradores), con sus opciones de menú en `CompanyWorkspace.tsx` y sus títulos en `App.tsx`.
- El borrador local guarda también los pagos agregados. La clave de reintento se conserva como en STK-011.
- Los montos se aceptan con coma decimal y se calculan en céntimos para evitar errores de redondeo.

## 4. Seguridad

- Reglas en `SecurityConfiguration` antes de la regla general de ventas: configuración solo para `ROLE_COMPANY_ADMIN` y resumen con el permiso de consultar ventas.
- La empresa siempre sale de `StocklyPrincipal` y la sucursal se valida contra el acceso de la persona.
- La referencia se trata como texto libre: se recorta, se limita a 60 caracteres y se escapa al mostrarla.

## 5. Integraciones

- Avisar a Gabo (STK-014) que la venta sin conexión debe enviar los pagos dentro del mismo intento y con la misma clave, y que puede recibir `SALE_PAYMENT_METHOD_INACTIVE` o `SALE_PAYMENT_INVALID` para dejar la venta en revisión.
- STK-013 podrá leer los pagos de la venta para el comprobante electrónico. No se adelanta ningún formato.

## 6. Pruebas

- Integración en MySQL (`SalePaymentLocalIntegrationTest`): pago simple y mixto, vuelto, exceso rechazado, medio desactivado, reintento idéntico y con pagos distintos, anulación con devolución exacta e inexacta, venta anterior sin cobro, resumen por día y sucursal, permisos y aislamiento entre empresas.
- Vitest para el cálculo de pendiente, vuelto y monto aplicado en céntimos.
- Playwright a 320 y 1440 px para cobrar, anular desde el listado, configurar medios y ver el resumen.
- Prueba de punta a punta en local con React, Spring Boot y MySQL. Evidencia en `docs/04-Testing/STK-012/`.

## 7. Archivos afectados

- Backend: `V11__sale_payments.sql`, `SaleService.java`, `SaleController.java`, nuevos `PaymentMethodService.java`, `PaymentMethodController.java` y `PaymentSummaryService.java`, `SecurityConfiguration.java`.
- Frontend: `features/sales/SalesPage.tsx`, `sales.css`, nuevos `features/sales/PaymentSummaryPage.tsx`, `PaymentMethodsPage.tsx` y `payments.ts`, `CompanyWorkspace.tsx`, `App.tsx`.
- Pruebas: `SalePaymentLocalIntegrationTest.java`, `payments.test.ts`, `frontend/e2e/payments.spec.ts`.
