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

## Reverificación y pruebas propias — 2026-11-11

La tarjeta volvió de Finalizado a «En espera…», y después del 09/11 STK-005, 011, 012, 013 y 016 tocaron el ingreso, las ventas y la facturación. Se reverificó STK-018 sobre `main` en `306e1bd`, con React, Spring Boot, MySQL 9.7 local y Hacienda simulada. El propietario pidió recorrido real y un Playwright propio, y aprobó registrar en [ADR-014](../../05-Decisions/ADR-014.md) los requisitos previos a producción.

### Fallo encontrado y corregido: los avisos nunca se enviaban

La tarea de avisos buscaba el correo del administrador en la columna `users.email`, que no existe; la columna es `email_normalized`.
- **Efecto:** la tarea registraba el aviso como enviado y después fallaba con `BadSqlGrammarException`. El correo no salía y, como quedaba registrado, no se reintentaba. Desde que se publicó STK-018 no se había enviado ningún aviso de suscripción.
- **Corrección** en `SubscriptionScheduler`:
  - usa `email_normalized`;
  - busca a los administradores **antes** de registrar el aviso, para que un error no lo deje marcado como enviado.
- **Prueba nueva:** `SubscriptionSchedulerLocalIntegrationTest`, en MySQL, con el correo simulado. Comprueba que:
  - si el correo falla, el aviso se libera;
  - al volver se envía una sola vez al correo del administrador;
  - no se repite;
  - salen también los avisos al vencer y al suspenderse.

  Se confirmó que esta prueba **falla con el código anterior**, con el mismo error de SQL, y pasa con la corrección. Se activa con `STOCKLY_SUBSCRIPTIONS_TEST_DATABASE=true`.

### Recorrido real

Pasaron 62 de 62 comprobaciones. Para observar los avisos, el backend local corrió con la tarea de suscripciones cada 10 segundos, mediante la propiedad `stockly.subscriptions.delay-ms`, sin cambiar el código. El script crea tres empresas reales. Las fechas de vencimiento se movieron en MySQL para simular el paso del tiempo. La configuración global (límite de productos e instrucciones) se cambió temporalmente y se restauró al terminar.

- **AC-01 (8):**
  - crear un plan con precio mensual y anual; el nombre repetido y el precio inválido se rechazan;
  - se registra en Actividad;
  - un cambio de precio no altera lo pagado;
  - el plan archivado no se ofrece en «Mi suscripción» ni sirve para empezar, pero quien ya lo tiene lo renueva al precio nuevo.
- **AC-02 (8):**
  - la empresa nueva empieza en Prueba con límites;
  - el servidor rechaza con `TRIAL_LIMIT` crear y reactivar productos, la segunda sucursal y el tercer usuario;
  - «Mi suscripción» muestra el uso;
  - lo creado se conserva;
  - con un plan pagado ya no hay límites.
- **AC-03 (13):**
  - seis pagos inválidos se rechazan en su campo sin guardar nada: monto distinto, referencia faltante en SINPE, fecha futura, plan inexistente, 13 períodos y anual sin precio anual;
  - el pago desde la prueba empieza el día del pago y deja la suscripción Activa con su plan;
  - el reintento con la misma clave no duplica;
  - el pago estando activa continúa desde «Pagado hasta»;
  - anular exige motivo, recalcula con los pagos restantes aunque no sea el último, y conserva el pago con su motivo.
- **AC-04 (11):**
  - al pasar «Pagado hasta» entra en gracia con acceso completo, y al terminar la gracia queda Suspendida;
  - la suspensión cierra las sesiones abiertas del administrador y del trabajador;
  - el administrador no ingresa y recibe el mensaje con las instrucciones (`role=ADMIN`);
  - una contraseña errónea sigue dando el error genérico;
  - un pago reactiva con período desde el día del pago, y se ingresa con sesión nueva;
  - cancelar mantiene el acceso hasta «Pagado hasta» y no se repite; vencida, queda suspendida sin gracia;
  - una empresa suspendida a mano (STK-004) que paga sigue bloqueada.
- **AC-05 (7):**
  - cada pago emitió su factura de Stockly y quedó aceptada, y la anulación emitió su nota de crédito aceptada;
  - base = total ÷ 1,13 y el IVA es la diferencia: 20 000 + 2 600 = 22 600, 100 000 + 13 000 = 113 000 y 200 000 + 26 000 = 226 000;
  - el receptor es la empresa con su identificación;
  - PDF disponible y correos enviados al comercio;
  - el administrador de la empresa ve y descarga sus facturas.
- **AC-06 (5):**
  - aviso antes del vencimiento con las instrucciones de pago, sin repetirse en la siguiente revisión;
  - aviso al vencer y empezar la gracia, y aviso al suspenderse;
  - las instrucciones aparecen en «Mi suscripción».
- **AC-08 (9):**
  - Actividad registra pagos, anulaciones y cancelación;
  - el administrador de empresa no usa `/api/saas`;
  - «Mi suscripción» y las facturas son solo de la propia empresa;
  - las credenciales de Stockly nunca se devuelven y están cifradas en la base;
  - sin sesión, 401.
- **AC-07 (1):** el panel responde con estados y contadores de facturas. Las pantallas se cubren con Playwright.

Comportamiento observado y aceptado: si un pago se anula **antes** de que el despachador emita su factura, ese pago queda sin factura y sin nota de crédito, porque nunca llegó nada a Hacienda. Es coherente con STK-013, donde un comprobante pendiente que nunca se envió se cancela.

### Playwright propio (AC-07)

Nueva prueba `frontend/e2e/subscriptions.spec.ts`: 6 de 6 en Edge a 1440 y 375 px, con la API simulada dentro de Playwright.
- **Administrador de Stockly:**
  - panel, planes y configuración, sin mostrar secretos;
  - en el detalle de empresa, «Registrar pago»: el plan archivado no se ofrece, el monto se calcula (2 × ₡11 300 = ₡22 600) y se envía el pago correcto.
- **Administrador de empresa:** franja de prueba con el uso y «Mi suscripción» con las instrucciones.
- **Empresa suspendida:** la pantalla de ingreso explica la suspensión y cómo pagar.
- **Capturas:** `screenshots/e2e-*`, sin desbordamiento horizontal.
- **Detalle menor:** en celular, la tarjeta del pago muestra el medio y el período, pero la referencia solo aparece en la tabla de escritorio.

### Otras pruebas

- Playwright completo: 46 de 46.
- Vitest: 28 de 28.
- Build correcto.
- `SubscriptionRulesTest`: 5 de 5.

### Pendiente fuera de STK-018

- Requisitos previos a producción en [ADR-014](../../05-Decisions/ADR-014.md): confirmar CABYS, actividad y unidad con el contador, cargar los datos y credenciales reales de Stockly, y probar con Hacienda real.
- Las pruebas de integración Java del resto de reglas (pagos, límites, estados) siguen como mejora. Los avisos ya tienen su prueba.

### Aprobación final

El propietario revisó en local y aprobó el 2026-11-11: «Aprobadas, publicar».
