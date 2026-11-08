# STK-015 · Validación local

El propietario recibió la aplicación local con ventas ficticias y autorizó las pruebas después de su revisión el 2026-11-08.

## Backend y MySQL

Flyway aplicó V1 a V15 en un esquema aislado con colación sensible a tildes y mayúsculas. Pasaron 38 pruebas. CompanyIntegrationTest quedó fuera porque requiere Docker.

- Se devolvieron tres unidades, se repusieron dos y se comprobó el movimiento DEV en Historial. La cantidad restante se devolvió sin reposición. El listado pasó de «Devuelta parcialmente» a «Devuelta por completo».
- Se verificaron los importes históricos, el reintegro dividido por medio, el resumen de Cobros y el caso sin cobro. Tres devoluciones fraccionarias sumaron exactamente el importe original de la línea.
- El reintento devolvió el mismo folio y una clave reutilizada con datos distintos se rechazó. Dos confirmaciones concurrentes por la última unidad produjeron una sola devolución. La anulación completa se bloqueó tras la primera devolución.
- Se rechazó el acceso de otra empresa, de una persona sin acceso a la sucursal y de un lector sin permiso de registro.
- Con Hacienda simulada, dos devoluciones esperaron la aceptación del original y produjeron dos notas de crédito distintas. El XML pasó la validación v4.4 y conservó el CABYS y la tarifa del documento firmado aunque el catálogo cambió. El rechazo del original conservó el reintegro y mostró «Revisión fiscal requerida».

## Interfaz

Compilación de producción correcta. Pasaron 17 pruebas de Vitest y 16 de Playwright. Con API simulada, el flujo de devolución pasó en Chromium a 320 px y 1440 px, incluida la reposición parcial, el reintegro sugerido y la recuperación de la misma clave después de perder la respuesta. Se verificaron regresiones de Entradas, Ventas, Cobros y Facturación.

El propietario dio su aprobación final para publicar la rama y abrir el PR el 2026-11-08.
