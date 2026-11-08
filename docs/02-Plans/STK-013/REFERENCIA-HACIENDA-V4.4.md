# STK-013 · Referencia técnica de Hacienda v4.4

Datos verificados el 2026-11-08 en documentos oficiales del Ministerio de Hacienda. Apoya el [PLAN](PLAN.md). Ante cualquier diferencia, prevalece el documento oficial vigente.

## Fuentes descargadas

- [Anexos y Estructuras v4.4](https://atv.hacienda.go.cr/ATV/ComprobanteElectronico/frmAnexosyEstructuras.aspx): página oficial con los XSD y anexos.
- Copias de esquemas en `backend/src/main/resources/hacienda/v4.4/`: `FacturaElectronica_V4.4.xsd`, `NotaCreditoElectronica_V4.4.xsd` y `MensajeHacienda_V4.4.xsd`.
  - El esquema de firma del W3C está en `xmldsig-core-schema.xsd`, sin su DOCTYPE, para validar sin cargar DTD externas.
  - Pendiente: `TiqueteElectronico_V4.4.xsd` seguía respondiendo 404 el 2026-11-08 en el sitio oficial. Los tiquetes se firman sin validarlos contra su XSD.
- Codificación de ubicación v4.4 (archivo oficial `Codificacionubicacion_V4.4`): convertida a `frontend/src/features/billing/ubicaciones.json`, con 7 provincias, 84 cantones y 492 distritos, sin barrios.
- *ANEXOS Y ESTRUCTURAS_V4.4.pdf*: notas 3, 4, 5, 6 y 8.1 y ejemplo de firma.
- Documentación de la API de recepción (`comprobantes-electronicos-api.html`).

## API de recepción

- **Producción:** `https://api.comprobanteselectronicos.go.cr/recepcion/v1/`.
- **Pruebas:** `https://api-sandbox.comprobanteselectronicos.go.cr/recepcion/v1/`. Autorizada por el propietario el 2026-11-08; responde 401 sin token.
- **IdP de pruebas (Guía de uso para IdP, oficial):** token en `https://idp.comprobanteselectronicos.go.cr/auth/realms/rut-stag/protocol/openid-connect/token` con `client_id=api-stag`, `grant_type=password`, `username` y `password` en x-www-form-urlencoded. El token dura unos 5 minutos y se renueva. La guía no incluye los valores de producción, que se configuran por variables de entorno cuando existan credenciales de producción.
- **Envío:** `POST /recepcion`, con un JSON que lleva `clave`, `fecha` (RFC 3339), `emisor` y `receptor` (tipo y número) y `comprobanteXml`, que es el XML firmado en UTF-8 y Base64.
  - **201:** recibido. El encabezado `Location` trae la URL para consultar el estado.
  - **400:** el encabezado `X-Error-Cause` trae la causa. Por ejemplo, el comprobante con esa clave ya fue recibido.
- **Consulta:** `GET /recepcion/{clave}` devuelve `ind-estado`, que puede ser `recibido`, `procesando`, `aceptado`, `rechazado` o `error`, y `respuesta-xml` en Base64. Si responde 400, el recurso no existe.
- **Autenticación:** token OIDC/OAuth 2.0 en `Authorization: bearer …`.
- **Límites de uso:** las respuestas traen los encabezados `X-Ratelimit-*`.

## Nota 3 · Consecutivo (20 dígitos)

| Posiciones | Contenido |
| --- | --- |
| 1-3 | Establecimiento: 001 para la casa matriz o el principal, 002 en adelante para las sucursales. |
| 4-8 | Terminal o punto de venta: `00001` si hay una sola o un servidor centralizado. |
| 9-10 | Tipo: 01 factura, 02 nota de débito, 03 nota de crédito, 04 tiquete, 05-07 confirmaciones, 08 compra, 09 exportación, 10 recibo de pago. |
| 11-20 | Número que empieza en 1 por sucursal o terminal. Al llegar al tope puede volver a 1. |

Si el comercio ya emitía con otra plataforma, debe **mantener su numeración** al cambiar. Stockly debe permitir indicar el último consecutivo usado.

## Clave numérica (50 dígitos)

506, seguido de:
- día, mes y año (DDMMAA);
- cédula del emisor en 12 dígitos (nota 4.1);
- consecutivo de 20 dígitos;
- situación: 1 normal, 2 contingencia, 3 sin internet;
- código de seguridad de 8 dígitos generado por el sistema.

**Nota 4.1 · Relleno de la cédula a 12 dígitos:**
- Física: tres ceros adelante.
- Jurídica y NITE: dos ceros.
- DIMEX: un cero si tiene 11 dígitos.

## Códigos usados por Stockly

- **Tipos de identificación (nota 4):**
  - 01 física, 02 jurídica, 03 DIMEX, 04 NITE;
  - 05 y 06 solo en casos especiales, sin uso en Stockly.
- **Condición de venta (nota 5):** 01 contado. Stockly no vende a crédito.
- **Medios de pago (nota 6):** 01 efectivo, 02 tarjeta, 04 transferencia o depósito bancario y 06 SINPE Móvil.
- **Impuesto (nota 8):** 01 IVA.
- **Tarifas de IVA (nota 8.1):**

| Código | Tarifa |
| --- | --- |
| 01 | 0 % (artículo 32, num. 1, RLIVA) |
| 02 | 1 % |
| 03 | 2 % |
| 04 | 4 % |
| 05 | Transitorio 0 % (solo notas de crédito y débito) |
| 06 | Transitorio 4 % (solo notas de crédito y débito) |
| 07 | Tarifa transitoria 8 % (solo notas de crédito y débito) |
| 08 | 13 % general |
| 09 | 0,5 % |
| 10 | Exenta |
| 11 | 0 % sin derecho a crédito |

  En el catálogo solo se ofrecen las tarifas de venta: 01, 02, 03, 04, 08, 09, 10 y 11.
- **Código de referencia de la nota de crédito:** 01 anula el documento de referencia.

## Nuevo campo `ProveedorSistemas`

La v4.4 exige la **cédula del proveedor de sistemas** que emite los comprobantes, de hasta 20 caracteres. Stockly envía la identificación de su titular. El propietario la indicó el 2026-11-08 y se configura en el servidor con `STOCKLY_HACIENDA_PROVIDER_ID`, no por empresa ni en el código.

## Firma (ejemplo oficial del Anexo)

- XAdES-EPES v1.3.2 o superior, con una sola firma por documento y canonicalización C14N 20010315.
- **Identificador de la política:** `https://cdn.comprobanteselectronicos.go.cr/xml-schemas/Resoluci%C3%B3n_General_sobre_disposiciones_t%C3%A9cnicas_comprobantes_electr%C3%B3nicos_para_efectos_tributarios.pdf`.
- **Hash de la política:** SHA-256 `DWxin1xWOeI8OuWQXazh4VjLWAaCLAA954em7DMh0h8=`.

## Archivos para correo y almacenamiento

`clave.xml`, `clave_respuesta.xml` y `clave.pdf`.
