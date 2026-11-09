# PLAN · STK-018

Aprobado por el propietario el 2026-11-09. [SPEC](../../01-Specs/STK-018/SPEC.md) · [Mockup aprobado](../../06-Mockups/STK-018/suscripciones-responsive.html).

1. **Base de datos.** Migración `V18__subscriptions.sql`:
   - **Configuración:**
     - `saas_settings` (una fila) con días de prueba, gracia y aviso, límites de la prueba, instrucciones de pago y `version`;
     - `saas_billing_settings` (una fila) con los datos del emisor, el ambiente, las credenciales y el certificado cifrados, y la secuencia de consecutivos.
   - **Planes:** `subscription_plans` con nombre único, precio mensual, anual opcional, descripción, `active` y `version`.
   - **Suscripciones:**
     - `company_subscriptions` (1:1 con la empresa): estado, plan, ciclo, fin de la prueba, «pagado hasta», cancelada en y motivo, y `version`;
     - `subscription_payments`: monto, períodos, fechas cubiertas, medio, referencia, `request_key` único, autor y anulación con motivo;
     - `subscription_notices`: aviso enviado por vencimiento, único por tipo y fecha.
   - **Facturas:** `saas_fiscal_documents`, igual que `fiscal_documents` de STK-013 pero con los datos del receptor copiados y sin FK en cascada a la empresa, para conservarlas por obligación legal.
   - **Empresas existentes:** cada una recibe su suscripción en Prueba desde la migración, según la regla aprobada en la SPEC.
2. **Backend, paquete nuevo `com.stockly.subscriptions`.**
   - **`SubscriptionService`:**
     - calcula el estado a partir de las fechas, en hora de Costa Rica;
     - registra pagos con su período, de forma idempotente y con bloqueo de fila;
     - anula pagos y recalcula;
     - cancela suscripciones;
     - consulta «Mi suscripción».
   - **`PlanService`** y **`SaasSettingsService`** con validaciones y control de versión.
   - **`SubscriptionScheduler`** (`@Scheduled` cada hora):
     - aplica las transiciones de estado;
     - al suspender, incrementa `companies.access_version` para cerrar las sesiones abiertas;
     - envía los avisos sin repetirlos, usando `subscription_notices` y `EmailDelivery`.
   - **`SubscriptionLimits`:** se llama desde la creación y reactivación de productos (`CatalogService`), usuarios (`IdentityManagementService`) y sucursales (`InventoryService`). Responde 409 con `TRIAL_LIMIT` y el límite alcanzado.
   - **Acceso:**
     - `CompanyAccessFilter` y el ingreso (`AuthService`) bloquean la suscripción suspendida con el código `SUBSCRIPTION_SUSPENDED`.
     - El ingreso devuelve al administrador las instrucciones de pago; a los demás, el mensaje de contactar a su administrador.
3. **Facturación de Stockly.**
   - **`SaasFiscalService`:** arma un `FiscalDraft` (Stockly como emisor, el comercio como receptor, el CABYS configurado, IVA del 13 % incluido) y reutiliza sin cambios `FiscalXmlBuilder`, `FiscalSigner`, `HaciendaClient`, `FiscalPdf` y `FiscalFiles`.
   - **Despachador y correos:** un despachador propio sobre `saas_fiscal_documents`, con los mismos reintentos y consulta de estado que `FiscalDispatcher`, y correo con PDF y XML.
   - **Notas de crédito:** al anular un pago se emite la nota de crédito que referencia su factura.
   - **Configuración:** validar y verificar las credenciales como en `BillingSettingsService`.
4. **API.**
   - **Administrador de Stockly:** `/api/saas/…` para panel, planes, configuración, facturación, la suscripción de cada empresa, pagos (registrar y anular), cancelación y descarga de facturas. La lista de empresas existente suma los datos de su suscripción.
   - **Administrador de la empresa:** `/api/subscription` para «Mi suscripción», las franjas de aviso y la descarga de sus facturas.
   - **Seguridad:** las rutas nuevas se agregan en `SecurityConfiguration`.
5. **Interfaz.**
   - **Administrador de Stockly:** el menú suma Panel, Empresas, Planes y Configuración, en `App.tsx` y `features/saas/`. El detalle de empresa (`CompanyDetail.tsx`) suma la sección Suscripción con el historial y las ventanas de registrar pago, anular y cancelar.
   - **Administrador de la empresa:**
     - «Mi suscripción», las franjas en `CompanyWorkspace.tsx` y la ventana de límite, que reacciona al código `TRIAL_LIMIT` en catálogo, usuarios y sucursales;
     - en `features/auth`, la pantalla de ingreso suspendido.
   - **Campos de texto largos:** crecen con el contenido y nunca muestran barra de desplazamiento.
6. **Validación.**
   - **Integración en MySQL:** fechas y estados (con el reloj inyectado), períodos al pagar y anular, idempotencia, límites, bloqueo de acceso, avisos únicos, facturas y notas de crédito contra la Hacienda simulada, permisos y aislamiento.
   - **Vitest:** cálculos de base e IVA y fechas en pantalla.
   - **Playwright:** a 375 y 1440 px.
   - **Revisión y evidencia:** primero se entrega la aplicación local al propietario y después se registra la evidencia en `docs/04-Testing/STK-018/`.

**Riesgo:** STK-018 toca archivos de otros módulos para aplicar los límites (`CatalogService`, `IdentityManagementService`, `InventoryService`) y el acceso (`CompanyAccessFilter`, `AuthService`). Antes de programar hay que avisar a Gabo. Las llamadas se limitan a una sola verificación en cada punto de creación o reactivación.
