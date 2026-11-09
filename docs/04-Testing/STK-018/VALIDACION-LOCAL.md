# STK-018 · Validación local

El propietario revisó la aplicación local el 2026-11-09 y la aprobó: «aprobado push en main con el commit "STK-018 | Administración del SaaS y suscripciones"». El propietario dio la aprobación final el 2026-11-09 («marca como aprobadas las pruebas de STK-017 y STK-018»), aceptando la evidencia de este documento.

## Verificado

### Base de datos
Flyway aplicó V18 en `stockly_demo` y en `stockly_test`. Las empresas existentes empezaron en Prueba, según la regla aprobada.

### Backend
- **`stockly_test`:** pasaron 38 pruebas:
  - las 5 nuevas de `SubscriptionRulesTest`: estados, gracia, cancelación, inicio y fin del período, y monto esperado;
  - la regresión de Catálogo, Entradas, Salidas y ajustes, Por reponer, Ventas, Cobros y Facturación, incluida `FiscalDocumentXmlTest`, que confirma que el XML de las ventas de mercancías no cambió.
- **`stockly_as`:** pasaron las 7 de Historial.
- **`CompanyIntegrationTest`:** no se ejecutó porque requiere Docker.

### Interfaz
- **Compilación:** la de producción es correcta.
- **Vitest:** 28 pruebas, 2 nuevas para la base, el IVA y el fin del período en la vista previa del pago.
- **Playwright en Edge:** 20 pruebas de regresión en 375 y 1440 px. Se dejó fuera `companies.spec.ts` de la administración SaaS: ya fallaba antes de STK-018 porque depende del modo de demostración.

### Recorrido real
Se probó contra React, Spring Boot, MySQL (`stockly_demo`) y la Hacienda simulada: 16 de 16 comprobaciones pasaron, sin errores de página ni respuestas 5xx.

- **Empresa en prueba:**
  - muestra la franja con los días restantes y lo usado frente a los límites;
  - el servidor rechaza un producto por encima del límite con `409 TRIAL_LIMIT`;
  - se abre la ventana con los planes y las instrucciones de pago;
  - «Mi suscripción» carga.
- **Administrador de Stockly:**
  - el panel y la lista de empresas muestran la suscripción;
  - registrar un pago sin referencia de SINPE muestra el error en el campo;
  - con el pago registrado, la empresa pasa a «Activa»;
  - la **factura de Stockly fue aceptada por Hacienda (simulada)** y su PDF se descarga;
  - las instrucciones de pago se ven completas, sin barra de desplazamiento.
- **Empresa pagada:** desaparece la franja de prueba y la factura queda disponible para el comercio.
- **Celular:** el panel, el detalle con la suscripción y «Mi suscripción» no se salen de ancho.

Capturas en [screenshots](screenshots/).

## Ajustes durante la revisión del propietario

- Los campos de texto largos crecen con su contenido y nunca muestran barra de desplazamiento, también dentro de las ventanas modales (se vuelven a medir al abrirse).
- En el menú del administrador de Stockly solo se resalta la opción actual; antes había una sola opción y todas se veían resaltadas.
- Se corrigió el texto «1 empresa usa este plan» y el tamaño de la casilla «Disponible».

## Decisiones aplicadas al implementar

- **Unidad del servicio:** la factura de Stockly usa la unidad «Os» (otro tipo de servicio) y suma en `TotalServGravados`. Las ventas de mercancías no cambian. Debe confirmarse con el contador junto con el CABYS y la actividad económica.
- **Avisos:** «el día del vencimiento» y «al empezar la gracia» son el mismo día, así que se envía un solo correo, que además informa la fecha de suspensión. Los avisos son: antes del vencimiento, al vencer y al suspenderse.
- **Límites:** aplican mientras la empresa nunca haya pagado, incluida la gracia posterior a la prueba.
- **Factura rechazada:** al reintentar se emite un comprobante nuevo con otro número, y el rechazado queda reemplazado, como en STK-013.
- **Actividad del SaaS:** se registra en la tabla nueva `saas_audit_events`, porque la de empresas exige `company_id`.
- **Datos de revisión local:** el plan Estándar de ₡15 000 y el emisor de Stockly son ficticios. La llave `.p12` es autofirmada y solo funciona con la Hacienda simulada.

## Pruebas no ejecutadas, aceptadas por el propietario

El propietario decidió el 2026-11-09 dar la funcionalidad por terminada sin estas pruebas dedicadas. No se escribieron ni se ejecutaron; quedan como mejora recomendada si se quiere más cobertura automática.

- Integración en MySQL con datos controlados:
  - transiciones por fechas con reloj fijo y cierre de sesiones al suspender;
  - pago, reintento idempotente y anulación con recálculo;
  - cancelación;
  - límites al crear y reactivar productos, usuarios y sucursales;
  - avisos únicos;
  - factura y nota de crédito contra la Hacienda simulada.
- Permisos y aislamiento: `/api/saas` solo para `SAAS_ADMIN`; «Mi suscripción» y sus facturas solo para el `COMPANY_ADMIN` de su empresa; secretos cifrados que nunca se devuelven.
- Playwright propio de STK-018.
