# STK-013 · Validación local

[SPEC](../../01-Specs/STK-013/SPEC.md) · [TASKS](../../03-Tasks/STK-013/TASKS.md) · [Referencia v4.4](../../02-Plans/STK-013/REFERENCIA-HACIENDA-V4.4.md). Pruebas del 2026-11-08 en MySQL 9.7 aislado. El propietario aprobó la aplicación funcionando en local el 2026-11-08. Pendiente: la prueba con el ambiente real de pruebas de Hacienda (AC-05).

## Backend

Flyway aplicó V12 y V13. Pasaron 37 pruebas: 30 en `stockly_test`, recreada desde cero porque arrastraba restos de pruebas anteriores, y 7 de Historial en un esquema sensible a tildes. CompanyIntegrationTest quedó fuera porque requiere Docker.

**`FiscalDocumentXmlTest`, sin base de datos:**
- Consecutivo de 20 dígitos y clave de 50 según la nota 3. La cédula física se rellena a 12 dígitos.
- Desglose de IVA por línea que suma exactamente el total cobrado. Por ejemplo, ₡11.400 al 13 % dan ₡10.088,50 más ₡1.311,50.
- Se rechaza una tarifa transitoria en una venta.
- Una factura y una nota de crédito, firmadas con XAdES-EPES y la política oficial, **validan contra los XSD oficiales v4.4**. Llevan el medio de pago 06 (SINPE Móvil), la tarifa 08 y la unidad Kg.
- Una factura sin receptor y un PIN incorrecto se rechazan.

**`FiscalLocalIntegrationTest`, MySQL con Hacienda simulada:**
- Solo el administrador configura la facturación. Las credenciales quedan cifradas: la contraseña no aparece en la base ni en la API. Un PIN incorrecto se rechaza.
- La venta con cédula jurídica emite factura `00100001010000000001` y la venta sin identificación emite tiquete `00100001040000000001`. Una identificación con dígitos inválidos se rechaza.
- **Recepción y aceptación:** Hacienda recibe y luego acepta. El XML y el PDF se descargan con el permiso de consulta.
- **Respuesta perdida:** el siguiente ciclo consulta la clave y reenvía la misma, sin duplicar.
- **CABYS faltante:** el comprobante queda pendiente con el motivo y no se envía.
- **Rechazo y corrección:** el comprobante nuevo usa otro consecutivo, conserva el rechazado y queda vinculado a él. La corrección por HTTP exige el permiso de confirmar ventas.
- **Anulación:** anular una factura aceptada emite y acepta la nota de crédito con código 01 y la clave original. Anular un comprobante que no se envió lo cancela.

Las pruebas de STK-011 y STK-012 usan ahora cédulas numéricas, como exige STK-004, porque la clave de Hacienda incluye la cédula del emisor.

## Navegador

La compilación de producción salió correcta. Pasaron 17 pruebas de Vitest, entre ellas la validación de dígitos por tipo de identificación y la selección del comprobante vigente y su nota de crédito.

Pasaron 14 pruebas de Playwright en Edge a 320, 375, 390 y 1440 px, con la API simulada:
- **Comprobante:** listado con su tipo y estado, rechazo con corrección validada y el aviso de factura o tiquete en la nueva venta.
- **Facturación:** credenciales que solo indican que están guardadas, error por campo del emisor y producción bloqueada sin requisitos.
- **Pruebas anteriores:** las de Ventas, Cobros y Entradas siguen pasando.

## Flujo real de punta a punta

React, Spring Boot y MySQL conectados, con una **Hacienda simulada local** (token, `POST /recepcion` y `GET /recepcion/{clave}`). La cédula del proveedor de sistemas se configuró en el servidor con el valor que indicó el propietario. Pasaron 24 comprobaciones; el rechazo del PIN incorrecto se verificó en la corrida anterior.

**Configuración:**
- **Catálogo:** un CABYS de 3 dígitos no se guarda. El CABYS y la tarifa se guardan desde la pantalla.
- **Facturación:** se guardaron el emisor y las credenciales con una llave de prueba autofirmada. Los secretos no aparecen en la pantalla y quedan cifrados en MySQL.

**Factura y tiquete:**
- La factura quedó pendiente al confirmar y luego fue aceptada.
- El PDF descargado es un PDF válido. El XML incluye `ProveedorSistemas` y la política de firma.
- El correo salió con los adjuntos.
- El tiquete se emitió con el tipo 04 y fue aceptado.

**Rechazo y corrección (320 px):**
- El motivo de Hacienda se ve sin desborde.
- Se corrigió a cédula jurídica y el comprobante nuevo fue aceptado. El rechazado se conservó.

**Anulación, listado y producción:**
- Anular la factura emitió una nota de crédito, que fue aceptada y aparece en la venta.
- El listado muestra el tipo y el estado del comprobante, y no se desborda a 320 px.
- Activar producción quedó habilitado al cumplirse los requisitos. No se activó.

Capturas: [Facturación 1440](screenshots/facturacion-1440.png) · [Facturación 320](screenshots/facturacion-320.png) · [aceptada](screenshots/aceptada-1440.png) · [rechazada 320](screenshots/rechazada-320.png) · [nota de crédito](screenshots/nota-credito-1440.png) · [listado 1440](screenshots/listado-1440.png) · [listado 320](screenshots/listado-320.png).

## Pendiente

- **AC-05 con Hacienda real:** que el ambiente de pruebas de Hacienda acepte factura, tiquete y nota de crédito. Requiere las credenciales de pruebas de una empresa real, que el propietario cargará en Facturación, y los datos de esa empresa. Las URL de pruebas y del IdP ya están confirmadas con la guía oficial. El XSD oficial del tiquete sigue sin publicarse: el 2026-11-11 el enlace responde con la página de error 404 de Hacienda. Los tiquetes se firman sin validarlos contra su esquema.
- **Ubicación:** resuelto el 2026-11-08 con la codificación oficial v4.4. Provincia, cantón y distrito se eligen en listas.
- **Conservación:** resuelto con [ADR-008](../../05-Decisions/ADR-008.md). Al borrar una empresa, sus comprobantes aceptados y rechazados se archivan 5 años (migración V14). La prueba de integración verifica qué se archiva, la fecha límite y la purga diaria. Falta definir cómo consultará ese archivo el administrador de Stockly.
- **Contrato con Gabo:** entregado el 2026-11-08 en la tarjeta de STK-014. Venta sin internet (situación 3) resuelta el 2026-11-11 (ver abajo).
- **Celular físico:** el propietario aceptó el 2026-11-11 la verificación móvil emulada a 375 px.

## Ajustes durante la revisión del propietario

- La empresa de demo quedó en producción tras activarla desde la pantalla. Se devolvió a pruebas, porque no había comprobantes en producción. Ahora producción no se puede activar si el servidor no tiene las direcciones de producción de Hacienda; el servidor también lo rechaza.
- Los resultados de Facturación se muestran en un aviso flotante. Antes, el mensaje desplazaba la pantalla 61 px y se veía un parpadeo.
- La numeración de otra plataforma se explica con un ejemplo, muestra el número siguiente y valida en español. Se quitó un límite de longitud del campo que cortaba en silencio números largos.
- La ubicación del emisor usa la codificación oficial v4.4 en listas. Las URL de pruebas y del IdP se confirmaron con la guía oficial.
- Conservación de 5 años al borrar empresas ([ADR-008](../../05-Decisions/ADR-008.md)).
- La caja del logotipo en vector, aprobada por el propietario, aparece junto a la marca.

## Venta sin internet (ajuste del 2026-11-11, AC-13)

El propietario aprobó el ajuste de SPEC §13, PLAN §9 y AC-13, basado en el Reglamento de Comprobantes Electrónicos, Decreto 44739-H, art. 23.

Ahora, al sincronizarse, la venta sin internet emite su factura o tiquete:
- con la situación 3 y la hora real de la venta;
- con la marca «Enviado fuera de plazo» si llega después de dos días hábiles;
- sin emitir mientras está en revisión ni si se cierra.

### Recorrido real

Pasaron 18 de 18 comprobaciones, con React, Spring Boot, MySQL y Hacienda simulada, sobre la empresa local de prueba (ambiente de pruebas).

- **A tiempo:**
  - se sincroniza y emite el tiquete con situación 3 en la clave;
  - la fecha de emisión y la de la clave son la hora real de la venta;
  - sin marca de plazo;
  - queda aceptado por Hacienda simulada;
  - el XML firmado lleva esa `FechaEmision` y la misma clave;
  - reintentar la sincronización con la misma clave no crea otro comprobante.
- **Fuera de plazo:** se simuló un equipo que preparó y vendió hace 5 días y recién sincroniza, retrocediendo 5 días su preparación en MySQL.
  - con identificación emite factura, con situación 3 y su fecha real;
  - queda marcada «Enviado fuera de plazo», también en el listado.
- **Anulación:** anular la venta aceptada emite la nota de crédito en situación 1.
- **Pantallas (1440 y 375 px):** el comprobante muestra «Sin internet», «Enviado fuera de plazo» y la explicación, sin desbordamiento. El listado muestra «Fuera de plazo». Ya no aparece «Emisión fiscal de contingencia pendiente».
  - Capturas: [escritorio](screenshots/13-sin-internet-fuera-de-plazo-desktop.png) · [móvil](screenshots/13-sin-internet-fuera-de-plazo-mobile.png).

### Pruebas automáticas

- **`FiscalDocumentXmlTest` (5):** nueva prueba de situación en la clave y del plazo de dos días hábiles: viernes a martes, fin de semana y límite de medianoche en Costa Rica.
- **`SaleLocalIntegrationTest`:** ahora verifica que la venta sin internet sincronizada emite el tiquete con situación 3, su fecha real y sin marca. También verifica que la venta en revisión y la cerrada no emiten.
- **Demás suites en MySQL aislado:** pasan las de cobros, catálogo, entradas, reposición, salidas y ajustes, `FiscalLocalIntegrationTest` y las de validación. `CompanyIntegrationTest` requiere Docker.
- **Navegador:** Playwright 40 de 40 en Edge, Vitest 28 de 28 y build correcto. No se agregó una prueba Playwright propia para este caso; la interfaz se verificó con el recorrido real.

### Aprobación

El propietario revisó en local y aprobó el 2026-11-11: «aprobado pushhh». AC-05 (Hacienda real) y el XSD del tiquete siguen pendientes, así que STK-013 queda en espera, no finalizada.

## Nombre del cliente y comprobantes que no cumplen el esquema (ajuste del 2026-11-11)

**Problema encontrado:** al reverificar STK-018 se vio que las facturas VTA-0014 y VTA-0015 de la empresa local estaban atascadas en «Pendiente» con «Error inesperado al procesar».
- **Causa:** se confirmaron con cliente «a». El esquema v4.4 exige que el nombre del receptor tenga de 3 a 100 caracteres, pero Stockly solo validaba el máximo. El XML no pasaba la validación y el despachador lo reintentaba sin fin, cada vez más espaciado.

**Ajuste aprobado por el propietario** (SPEC §6 y §9):
- **Servidor:** `FiscalDocumentService.customer()` exige de 3 a 100 caracteres en el nombre de la factura, con error en el campo `customer`. Aplica a la venta en línea, a la sincronización sin internet y a la corrección.
- **Formulario:** la venta y la corrección usan la misma regla, `invoiceNameProblem()` en `fiscal.ts`.
- **Despachador:** si el XML firmado no cumple el esquema, no se envía ni se reintenta. Queda **Rechazado** con un motivo legible, «No se envió a Hacienda: el nombre del cliente debe tener de 3 a 100 caracteres. Corrige los datos del cliente.», y se corrige con el flujo existente. En ese caso el comprobante muestra «Motivo:» en vez de «Motivo de Hacienda:».

### Verificación

- **En vivo (10 de 10)**, con React, Spring Boot, MySQL y Hacienda simulada:
  - con «Reintentar ahora», VTA-0014 y VTA-0015 pasaron a Rechazado con el motivo del nombre y no se volvieron a intentar;
  - corregir con «a» se rechaza (400);
  - corregir VTA-0014 con «Ana Pérez» emitió un comprobante nuevo con otro consecutivo, que quedó aceptado, y el rechazado se conserva;
  - una venta nueva con factura y nombre de 2 letras no se confirma y no descuenta existencias;
  - en pantalla, a 1440 y 375 px, se ve el motivo sin desbordamiento.

  **VTA-0015 quedó rechazada a propósito**, para que el propietario la corrija desde la pantalla.
- **Backend:**
  - `FiscalDocumentXmlTest` 6 de 6, con la prueba nueva: nombre de 1 letra, 101 caracteres y nombre válido; tiquete sin restricción; XML con nombre «a» que no cumple el esquema y su motivo legible;
  - `FiscalLocalIntegrationTest`, `SaleLocalIntegrationTest`, `SalePaymentLocalIntegrationTest` y las suites de suscripciones pasan.
- **Frontend:** Vitest 29 de 29, con el caso nuevo; Playwright de ventas, facturación, cobros y devoluciones 8 de 8; build correcto.
- **Capturas:** [escritorio](screenshots/14-nombre-corto-rechazado-desktop.png) · [móvil](screenshots/14-nombre-corto-rechazado-mobile.png).

**Fuera de este ajuste:** el despachador de las facturas **de Stockly** (STK-018) conserva el reintento genérico. Allí el receptor es la razón social de la empresa registrada en STK-004.

### Aprobación final

El propietario revisó en local y aprobó el 2026-11-11: «Aprobadas, publicar».
