# PLAN · STK-013

Aprobado por el propietario el 2026-11-08. [SPEC](../../01-Specs/STK-013/SPEC.md) · [Mockup](../../06-Mockups/STK-013/facturacion-responsive.html).

## 0. Decisiones aprobadas

Aprobadas por el propietario el 2026-11-08 y registradas en [ADR-007](../../05-Decisions/ADR-007.md).

1. **Firma XAdES-EPES:** se agrega una librería mantenida para firmar con la llave `.p12`. La propuesta es la librería de firma de la Comisión Europea (DSS), que soporta XAdES-EPES y la canonicalización exigida. La alternativa es `xades4j`, más liviana. Implementar la firma a mano no se recomienda, por el riesgo de errores de seguridad y de rechazos.
2. **PDF:** se agrega una librería para generar la representación gráfica. La propuesta es OpenPDF, con licencia LGPL/MPL.
3. **Clave de cifrado:** `STOCKLY_BILLING_KEY` es una variable de entorno con 32 bytes en Base64. Si falta, la facturación queda deshabilitada y el resto de la aplicación funciona. Se cifra con AES-GCM de Java, sin otra dependencia.

## 1. Preparación técnica

- Descargar del sitio oficial y versionar en `backend/src/main/resources/hacienda/v4.4/`:
  - los XSD de factura, tiquete, nota de crédito y mensaje de Hacienda;
  - la codificación de ubicación;
  - la tabla de tarifas de IVA y los medios de pago.
- Anotar la fecha de descarga.
- Confirmar con la guía de IdP y la documentación de la API:
  - las URL de autenticación y de recepción en pruebas y en producción;
  - el formato de envío y la consulta por clave.
- Las URL se configuran por variables de entorno y nunca quedan fijas en el código.
- Pedir al propietario las credenciales del ambiente de pruebas de una empresa real. Sin ellas no se puede cumplir AC-05.

## 2. Base de datos (V12)

- `catalog_items`: `cabys_code CHAR(13)` y `tax_rate_code`, con CHECK de 13 dígitos. Se coordina con Gabo.
- `sales`: `customer_id_type` y `customer_id_number`, con CHECK de tipo y formato.
- `inventory_branches`: `fiscal_code CHAR(3)`, único por empresa y asignado en orden al crear la sucursal. Se llena también para las sucursales existentes.
- `billing_settings`, uno por empresa:
  - ambiente;
  - usuario y contraseña de API, llave y PIN, cifrados por separado con su vector de inicialización;
  - huella y vencimiento de la llave;
  - actividad, ubicación, correo y fecha de activación de producción.
- `fiscal_sequences`: empresa, sucursal, ambiente, tipo y último consecutivo, con bloqueo por fila.
- `fiscal_documents`:
  - identificación: empresa, id, venta, tipo, ambiente, sucursal, consecutivo de 20 y clave de 50, únicos por empresa;
  - referencia al comprobante original en las notas de crédito;
  - estado, intentos, próximo intento y motivo;
  - XML firmado y respuesta de Hacienda;
  - fechas de creación, envío y aceptación, y estado del correo.
- Índices por empresa y estado para el listado y para el proceso de envío.

## 3. Backend

- **`BillingSettingsService`** con su controlador `/api/billing/settings`, solo para el administrador:
  - guarda y reemplaza credenciales, y prueba la conexión pidiendo un token;
  - valida la llave y el PIN, y extrae el vencimiento;
  - guarda el emisor y activa producción con los requisitos de la SPEC.
  - Las respuestas nunca incluyen secretos.
- **`FiscalDocumentFactory`**, llamado desde `SaleService.confirm` y `reverse` en la misma transacción:
  - decide el tipo de comprobante;
  - toma el consecutivo con bloqueo y arma la clave;
  - guarda el comprobante en estado Pendiente;
  - al anular, aplica §10 de la SPEC.
- **`FiscalXmlBuilder`:** arma el XML 4.4 con el emisor, el receptor, las líneas con CABYS, el desglose de IVA, los medios de pago de STK-012 y las referencias de la nota de crédito. Cada XML se valida contra el XSD antes de firmarlo.
- **`FiscalSigner`:** firma XAdES-EPES con la llave descifrada solo en memoria.
- **`HaciendaClient`:** maneja el token del IdP y su renovación por empresa y ambiente, el envío y la consulta por clave. Tiene tiempos de espera acotados y nunca registra secretos ni XML completos.
- **`FiscalDispatcher`**, un `@Scheduled`:
  - toma comprobantes pendientes con `FOR UPDATE SKIP LOCKED`;
  - consulta la clave antes de reenviar y aplica una espera creciente;
  - pasa los estados según la respuesta y, al aceptarse, encola el correo.
- **`FiscalMailer`:** reutiliza `EmailDelivery` y adjunta el PDF, el XML y la respuesta.
- **Endpoints de la venta:**
  - descargas en `/api/sales/{id}/fiscal/{docId}/pdf|xml|response`;
  - `/resend` y `/retry`;
  - `/correct`, que actualiza los datos del cliente y emite un comprobante nuevo.
- **Listados:** `SaleService.list/get` incluyen el comprobante vigente y el historial, con filtros por estado y tipo.
- **Catálogo:** `CatalogService` valida y guarda el CABYS y la tarifa, coordinado con Gabo.

## 4. Frontend

- Pantalla **Facturación** (`/billing`, solo administrador): ambiente con la lista de requisitos, credenciales que solo muestran si están guardadas, emisor con selects de ubicación desde la codificación oficial, y sucursales con su código y sus consecutivos.
- **Nueva venta:**
  - tipo y número de identificación, con validación de dígitos;
  - aviso de factura o tiquete;
  - aviso de productos sin CABYS.
- **Comprobante de venta:** detalle fiscal y sección Comprobante electrónico según el mockup, con sus estados, acciones, corrección del rechazo y nota de crédito.
- **Listado:** columna, filtros y aviso de atención.
- **Catálogo:** campos de CABYS y tarifa, coordinado con Gabo.
- Una franja de ambiente de pruebas en Ventas y en Facturación.

## 5. Seguridad

- Las credenciales se cifran con AES-GCM y solo se descifran en memoria al firmar o autenticarse. Nunca salen en las respuestas, los registros ni los mensajes de error.
- La carga de la llave limita el tamaño y el tipo, y la valida con su PIN.
- Los permisos siguen la SPEC §12, y la empresa siempre sale de `StocklyPrincipal`.
- Los cambios de configuración y de ambiente se auditan.

## 6. Pruebas

- **Unitarias:** cálculo fiscal por línea, armado de la clave y el consecutivo, validación de los XML contra los XSD oficiales, cifrado y descifrado, y transiciones de estado.
- **Integración en MySQL con Hacienda simulada:** emisión en la confirmación, falta de CABYS, configuración incompleta, sin respuesta con consulta por clave sin duplicar, rechazo y corrección, nota de crédito y cancelación, concurrencia del proceso de envío, permisos y aislamiento, y secretos fuera de las respuestas.
- **Ambiente de pruebas real de Hacienda:** factura, tiquete y nota de crédito aceptados (AC-05), con las credenciales que entregue el propietario.
- **Navegador:** Vitest y Playwright a 320 y 1440 px. Prueba de punta a punta en local. Evidencia en `docs/04-Testing/STK-013/`.

## 7. Riesgos

- Las reglas exactas de redondeo, decimales y unidades de los Anexos pueden requerir ajustar el cálculo. Se resuelven con el XSD y el ambiente de pruebas antes de la interfaz.
- El cambio en el catálogo toca el módulo de Gabo: se le avisa antes de editar los archivos.
- Sin credenciales de prueba no se puede cerrar AC-05. El resto se valida con Hacienda simulada.

## 8. Archivos afectados

- Backend:
  - `V12__electronic_invoicing.sql`;
  - paquete nuevo `com.stockly.billing` (configuración, fábrica, XML, firma, cliente, proceso de envío y correo);
  - `SaleService`, `SaleController`, `CatalogService`, `InventoryService` (código fiscal de la sucursal) y `AuditEventService`;
  - `pom.xml`, con las dependencias aprobadas.
- Frontend:
  - `features/billing/` (configuración);
  - `features/sales/SalesPage.tsx` y nuevos componentes fiscales;
  - catálogo, `CompanyWorkspace.tsx` y `App.tsx`.
