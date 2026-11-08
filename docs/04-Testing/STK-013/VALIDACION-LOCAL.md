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

- **AC-05 con Hacienda real:** que el ambiente de pruebas de Hacienda acepte factura, tiquete y nota de crédito. Requiere las credenciales de pruebas de una empresa real, que el propietario cargará en Facturación, y los datos de esa empresa. Las URL de pruebas y del IdP ya están confirmadas con la guía oficial. El XSD oficial del tiquete sigue respondiendo 404, así que los tiquetes se firman sin validarlos contra su esquema.
- **Ubicación:** resuelto el 2026-11-08 con la codificación oficial v4.4. Provincia, cantón y distrito se eligen en listas.
- **Conservación:** resuelto con [ADR-008](../../05-Decisions/ADR-008.md). Al borrar una empresa, sus comprobantes aceptados y rechazados se archivan 5 años (migración V14). La prueba de integración verifica qué se archiva, la fecha límite y la purga diaria. Falta definir cómo consultará ese archivo el administrador de Stockly.
- **Contrato con Gabo:** entregado el 2026-11-08 en la tarjeta de STK-014. Falta acordar el campo que indica que una venta se hizo sin internet (situación 3).
- **Celular físico:** probar en un teléfono real.

## Ajustes durante la revisión del propietario

- La empresa de demo quedó en producción tras activarla desde la pantalla. Se devolvió a pruebas, porque no había comprobantes en producción. Ahora producción no se puede activar si el servidor no tiene las direcciones de producción de Hacienda; el servidor también lo rechaza.
- Los resultados de Facturación se muestran en un aviso flotante. Antes, el mensaje desplazaba la pantalla 61 px y se veía un parpadeo.
- La numeración de otra plataforma se explica con un ejemplo, muestra el número siguiente y valida en español. Se quitó un límite de longitud del campo que cortaba en silencio números largos.
- La ubicación del emisor usa la codificación oficial v4.4 en listas. Las URL de pruebas y del IdP se confirmaron con la guía oficial.
- Conservación de 5 años al borrar empresas ([ADR-008](../../05-Decisions/ADR-008.md)).
- La caja del logotipo en vector, aprobada por el propietario, aparece junto a la marca.
