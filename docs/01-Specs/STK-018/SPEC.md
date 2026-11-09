# SPEC · STK-018 | Administración del SaaS y suscripciones

Aprobado por el propietario el 2026-11-09; todavía no autoriza desarrollo. Mockup aprobado por el propietario el 2026-11-09: [suscripciones-responsive.html](../../06-Mockups/STK-018/suscripciones-responsive.html) · [Trello](https://trello.com/c/4Hxye0Qm/18-stk-018-administraci%C3%B3n-del-saas-y-suscripciones). Se apoya en la gestión de empresas de [STK-004](../STK-004/SPEC.md), el acceso de [STK-005](../STK-005/SPEC.md) y el motor de facturación de [STK-013](../STK-013/SPEC.md).

## 1. Objetivo

Que Stockly funcione como negocio por suscripción:
- planes configurables;
- prueba con límites;
- vencimiento, gracia y suspensión automática por falta de pago;
- pagos registrados a mano, con su factura electrónica de Stockly enviada a Hacienda;
- un panel para administrar el SaaS.

Stockly no procesa dinero; el cobro automático con tarjeta queda fuera.

## 2. Actores

- **Administrador de Stockly** (`SAAS_ADMIN`):
  - gestiona planes, configuración y facturación de Stockly;
  - registra y anula pagos;
  - cancela suscripciones;
  - consulta el panel.
- **Administrador de la empresa** (`COMPANY_ADMIN`): ve «Mi suscripción», las franjas de aviso y los correos, y descarga sus facturas.
- **Demás usuarios de la empresa**: solo notan la suspensión, con un mensaje que los manda a su administrador.

## 3. Flujo

1. Al registrar una empresa (STK-004), su suscripción empieza en **Prueba** por los días configurados.
2. Durante la prueba, el administrador de la empresa ve una franja con los días restantes y lo usado frente a los límites. Al llegar a un límite, una ventana muestra los planes y las instrucciones de pago.
3. El comercio paga fuera de Stockly. El administrador de Stockly abre la empresa y usa **Registrar pago**. Stockly:
   - extiende «Pagado hasta»;
   - deja la suscripción en **Activa**;
   - emite la factura electrónica de Stockly al comercio, la envía a Hacienda y la manda por correo.
4. Antes y al vencer, el administrador de la empresa recibe avisos. Si no paga, la suscripción pasa a **Vencida en gracia** con acceso completo y, al terminar la gracia, a **Suspendida por falta de pago**.
5. Con un pago registrado, una empresa suspendida vuelve a **Activa** con nuevas sesiones.
6. **Cancelar suscripción** mantiene el acceso hasta el fin de lo pagado. Después la empresa queda suspendida y conserva sus datos.
7. El administrador de Stockly gestiona **Planes** y **Configuración** y sigue todo desde el **Panel**.

## 4. Reglas

### Planes
- **Datos:** nombre único, precio mensual y precio anual opcional, en colones con IVA incluido (13 %), y una descripción para el comercio.
- **Sin límites:** los planes pagados no tienen límites de uso.
- **Cambio de precio:** aplica desde el próximo pago; lo ya pagado no cambia.
- **Archivar:** un plan con historial no se borra. Archivado, no se ofrece para empezar, pero quien ya lo tiene puede renovarlo.

### Estados y fechas
Todas las fechas son días de Costa Rica.

| Estado | Cuándo | Acceso |
|---|---|---|
| Prueba | Desde el registro, por los días de prueba | Completo con límites |
| Activa | Hay un pago que cubre hoy | Completo |
| Vencida en gracia | Pasó «Pagado hasta» (o el fin de la prueba) y no pasaron los días de gracia | Completo |
| Suspendida por falta de pago | Terminó la gracia sin pago | Bloqueado; datos conservados |
| Cancelada | El administrador de Stockly la canceló | Completo hasta «Pagado hasta»; luego suspendida, sin gracia |

- **Al terminar la prueba sin pago:** también hay gracia.
- **Suspensión por falta de pago:** es distinta de la suspensión manual de STK-004, y las dos bloquean por separado.
- **Acceso bloqueado:** se bloquean el ingreso y las sesiones abiertas, igual que la suspensión de STK-004.
- **Cambios de estado:** los aplica una tarea automática al menos una vez por hora y también se recalculan al registrar o anular un pago.

### Pagos
- **Datos del pago:**
  - plan y ciclo (mensual o anual);
  - períodos (de 1 a 12);
  - monto recibido, que debe ser igual a precio × períodos, sin pagos parciales;
  - fecha del pago, que no puede ser futura;
  - medio: SINPE Móvil, transferencia o efectivo;
  - referencia, obligatoria para SINPE y transferencia.
- **Inicio del período:**
  - Si la suscripción está activa o en gracia, sigue desde «Pagado hasta», para que pagar tarde no regale días.
  - Si viene de la prueba, de una suspensión o de una cancelación ya vencida, empieza el día del pago.
- **Plan de la empresa:** el plan y el ciclo del último pago pasan a ser los de la empresa.
- **Anular un pago:** pide motivo, no borra el pago y recalcula «Pagado hasta» y el estado. Emite una nota de crédito por su factura.
- **Reintentos:** un reintento o doble clic no registra el pago dos veces.

### Facturación de Stockly
- **Emisor:** cada pago genera una factura electrónica de Stockly. Los datos del emisor salen de Configuración:
  - razón social, cédula jurídica y correo;
  - actividad económica y código CABYS del servicio, que define Stockly con su contador;
  - ubicación;
  - ambiente;
  - credenciales y certificado de Hacienda.
- **Receptor:** el comercio, con su razón social e identificación de STK-004.
- **Envío:** se usan el mismo formato, firma, envío, consulta de estado, PDF, XML y reintentos de STK-013.
- **Montos:** base = total ÷ 1,13, redondeada a 2 decimales, más el IVA, que es la diferencia.
- **Credenciales con problemas:** si faltan o fallan, el pago se registra igual y la factura queda pendiente; se reintenta al corregirlas.
- **Facturas rechazadas:** aparecen en el panel para corregirlas y reenviarlas.
- **Correo:** el comercio recibe por correo el PDF y el XML de cada factura y de cada nota de crédito.

### Prueba y límites
- **Valores por defecto** (configurables): 14 días de prueba, 7 de gracia y aviso 7 días antes.
- **Límites de la prueba:** 50 productos activos del catálogo, 2 usuarios activos (incluido el administrador) y 1 sucursal activa. La facturación electrónica de la empresa funciona completa, con envío a Hacienda.
- **Al llegar a un límite:**
  - El servidor rechaza crear o reactivar más productos, usuarios o sucursales, y muestra la ventana de planes.
  - Lo ya creado se conserva y sigue funcionando.
  - Con un plan pagado ya no hay límites.
- **Cambios de configuración:** aplican a las pruebas nuevas y a los próximos vencimientos.

### Avisos
- **Correos al administrador de la empresa:**
  - los días configurados antes del vencimiento;
  - el día del vencimiento;
  - al empezar la gracia;
  - al suspenderse.
  Cada aviso se envía una sola vez por vencimiento.
- **Franjas en la app:** el administrador de la empresa ve la franja de prueba, de vencimiento próximo y de gracia.
- **Instrucciones de pago:** las escribe el administrador de Stockly en Configuración y se muestran en «Mi suscripción», en la ventana de límites, en la pantalla de suspensión y en los correos.
- **Campos de texto largos:** crecen con el contenido y no muestran barra de desplazamiento.

### Empresas existentes
Las empresas registradas antes de STK-018 empiezan en **Prueba** desde la puesta en marcha, con los días y límites configurados. Lo que ya tienen se conserva aunque supere los límites. Aprobada por el propietario el 2026-11-09.

## 5. Seguridad

- **Accesos:**
  - Planes, configuración, pagos, panel y facturas de Stockly: solo `SAAS_ADMIN`.
  - «Mi suscripción»: solo el `COMPANY_ADMIN` de su propia empresa.
- **Límites y accesos:** se validan en el servidor.
- **Credenciales y certificado de Stockly:** se guardan cifrados con la clave de STK-013, nunca se devuelven ni se registran en logs, y se validan al guardarlos.
- **Actividad registrada:** quedan registrados con autor y fecha los cambios de planes y configuración, los pagos registrados y anulados, las cancelaciones y las suspensiones o reactivaciones automáticas.

## 6. Errores y casos especiales

- **Pago inválido:** un monto distinto del esperado, una referencia faltante, una fecha futura o un plan inexistente muestran el error en el campo y no se guarda nada.
- **Hacienda no responde:** el pago queda registrado y la factura se reintenta sola. Nunca se muestra como aceptada si no lo está.
- **Nota de crédito de una factura todavía pendiente:** se emite cuando la factura original queda aceptada, como en STK-013.
- **Anular un pago que no es el último:** se permite y se recalculan las fechas con los pagos restantes.
- **Empresa suspendida a mano (STK-004) que paga:** el pago se registra, pero el acceso sigue bloqueado hasta reactivarla a mano.
- **Empresa eliminada (STK-004):** se eliminan su suscripción y sus pagos. Las facturas de Stockly se conservan por obligación legal.
- **Errores de carga:** muestran el estado de error con la opción Reintentar.

## 7. Criterios de aceptación

- [ ] AC-01 Crear, editar y archivar planes en colones con IVA incluido; los cambios de precio no alteran lo ya pagado.
- [ ] AC-02 Una empresa nueva empieza en Prueba. Los límites bloquean en servidor la creación y la reactivación por encima del tope, conservando lo creado, y la facturación funciona en la prueba.
- [ ] AC-03 Registrar un pago valida los datos, calcula el período según las reglas, activa o reactiva la suscripción y no se duplica. Anular un pago recalcula las fechas.
- [ ] AC-04 Las transiciones a gracia, suspensión y cancelación siguen las fechas. La suspensión bloquea el ingreso y las sesiones abiertas, con mensajes distintos para el administrador y para los empleados, y un pago la quita.
- [ ] AC-05 Cada pago emite la factura de Stockly y la envía a Hacienda con base e IVA correctos; cada anulación emite una nota de crédito. Hay PDF, XML y correo, y los reintentos funcionan ante fallos.
- [ ] AC-06 Los avisos por franja y por correo salen una sola vez en las fechas acordadas, con las instrucciones de pago.
- [ ] AC-07 El panel, la lista de empresas con suscripción, el detalle con historial, «Mi suscripción» y la configuración se ven según el mockup en escritorio y celular.
- [ ] AC-08 Se respetan los permisos y el aislamiento entre empresas, los secretos cifrados y la actividad registrada.

## 8. Fuera de alcance

- Pasarela o cobro automático con tarjeta, y conciliación bancaria.
- Planes en dólares y tipo de cambio.
- Pagos parciales y prorrateo al cambiar de plan.
- Cupones y descuentos.
- Autorregistro y elección del plan por el comercio.
- Límites en planes pagados.
- Métricas avanzadas del SaaS (MRR, cancelación de clientes).
- Facturar suscripciones a extranjeros sin identificación costarricense.
- La operación sin conexión, que se define en STK-014.
