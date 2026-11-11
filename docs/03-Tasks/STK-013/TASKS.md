# TASKS · STK-013

Documentación aprobada por el propietario el 2026-11-08. [SPEC](../../01-Specs/STK-013/SPEC.md) · [PLAN](../../02-Plans/STK-013/PLAN.md) · [Validación local](../../04-Testing/STK-013/VALIDACION-LOCAL.md).

## Preparación
- [x] Dependencias de firma y PDF y clave de cifrado aprobadas el 2026-11-08 y registradas en [ADR-007](../../05-Decisions/ADR-007.md).
- [x] XSD oficiales de factura, nota de crédito y respuesta de Hacienda versionados. URL de producción confirmadas en la [referencia v4.4](../../02-Plans/STK-013/REFERENCIA-HACIENDA-V4.4.md). URL de pruebas y del IdP confirmadas con la guía oficial y autorizadas por el propietario. Codificación de ubicación oficial incorporada. Pendiente: el XSD del tiquete, que sigue respondiendo 404.
- [x] El propietario confirmó el aviso a Gabo sobre el cambio en el catálogo.
- [x] El propietario indicó la cédula del proveedor de sistemas, configurada por variable de entorno.
- [ ] Recibir las credenciales del ambiente de pruebas de Hacienda.

## Base de datos
- [x] V12 y V13: CABYS y tarifa, identificación del cliente, código fiscal de sucursal, configuración, consecutivos y comprobantes. AC-01 a AC-08.

## Backend
- [x] Configuración cifrada, prueba de conexión, emisor, sucursales y activación de producción. AC-01 y AC-10.
- [x] Datos fiscales del catálogo. AC-02.
- [x] Creación del comprobante al confirmar, con tipo, consecutivo y clave. AC-03 y AC-06.
- [x] Cálculo fiscal, XML 4.4 validado contra el XSD y firma XAdES-EPES. AC-04 (validado con Hacienda simulada).
- [x] Cliente de Hacienda y proceso de envío con consulta por clave, reintentos y concurrencia. AC-06.
- [x] Rechazo y corrección con un comprobante nuevo. AC-07.
- [x] Nota de crédito y cancelación al anular. AC-08.
- [x] PDF, correo, descargas y reenvío. AC-09.

## Frontend
- [x] Pantalla de Facturación. AC-01 y AC-10.
- [x] CABYS y tarifa en el catálogo. AC-02.
- [x] Identificación del cliente y aviso de factura o tiquete en la venta. AC-03.
- [x] Sección de comprobante electrónico, corrección, nota de crédito y listado. AC-06 a AC-09.

- [x] Conservación de 5 años al borrar la empresa ([ADR-008](../../05-Decisions/ADR-008.md)), con archivo y purga diaria.

## Seguridad
- [x] Permisos, aislamiento y ausencia de secretos en respuestas y registros. AC-11.

## Pruebas
- [x] Unitarias, integración en MySQL con Hacienda simulada, Vitest y Playwright a 320 y 1440 px. AC-01 a AC-12, salvo AC-05 con Hacienda real.
- [ ] Factura, tiquete y nota de crédito aceptados en el ambiente de pruebas de Hacienda. AC-05.
- [x] Prueba de punta a punta en local con Hacienda simulada, con evidencia en `docs/04-Testing/STK-013/`. AC-12.

## Integraciones
- [x] Contrato de comprobantes entregado a Gabo el 2026-11-08 como comentario en la [tarjeta de STK-014](https://trello.com/c/RHfLlp05). Queda por acordar el campo de situación "sin internet".

## Validación final
- [x] Aplicación en local aprobada por el propietario el 2026-11-08, con Hacienda simulada. AC-05 queda pendiente de una prueba con el ambiente real de Hacienda.

## Ajuste del 2026-11-11: venta sin internet (aprobado por el propietario el 2026-11-11)
- [x] Aprobar el ajuste de SPEC §13, PLAN §9 y AC-13, y recibir la indicación de desarrollar. — «Aprobado, desarrolla», 2026-11-11.
- [x] Backend: situación 3 y fecha real al sincronizar, sin emitir en revisión ni al cerrar, y marca de envío fuera de plazo con su migración (V20). AC-13. — 2026-11-11.
- [x] Frontend: comprobante de la venta sincronizada, etiqueta «Sin internet» y aviso «Enviado fuera de plazo»; quitar el aviso de contingencia pendiente. AC-13. — 2026-11-11.
- [x] Pruebas de backend y recorrido real en navegador, con evidencia en `docs/04-Testing/STK-013/`. AC-13. — 18/18, 2026-11-11.
- [ ] Avisar a Gabo en la tarjeta de STK-014.

## Ajuste del 2026-11-11: nombre del cliente y comprobantes que no cumplen el esquema (aprobado por el propietario el 2026-11-11)
- [x] Aprobar el ajuste de SPEC §6 y §9 y recibir la indicación de desarrollar. — «Sí, aprobado y desarrolla», 2026-11-11.
- [x] Validar el nombre de la factura (3 a 100 caracteres) en el servidor y en el formulario de venta y de corrección. — 2026-11-11.
- [x] Marcar como Rechazado, con motivo y sin reintentar, el comprobante cuyo XML no cumple el esquema. — 2026-11-11.
- [x] Pruebas y evidencia (en vivo 10/10, Java, Vitest y Playwright); VTA-0014 corregida y aceptada; VTA-0015 queda para la revisión del propietario. — 2026-11-11.
- [x] Aprobación del propietario en local el 2026-11-11 («Aprobadas, publicar»).
