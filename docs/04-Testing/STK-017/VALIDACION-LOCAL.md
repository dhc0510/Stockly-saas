# STK-017 · Validación local

El propietario revisó la aplicación local el 2026-11-08 y pidió publicarla en `main` con el commit «STK-017 | Resúmenes y reportes operativos». El propietario dio la aprobación final el 2026-11-09 («marca como aprobadas las pruebas de STK-017 y STK-018»), aceptando la evidencia de este documento.

## Verificado

- **Base de datos.** Flyway aplicó V17 en `stockly_demo` (módulo `REPORTS` e índice de devoluciones por fecha).
- **Regresión del backend.**
  - En `stockly_test` pasaron 33 pruebas, incluidas las 2 de Por reponer. Confirman que la exportación a Excel y CSV sigue igual después de moverla a `TabularExport`.
  - En `stockly_as` pasaron las 7 de Historial.
  - `CompanyIntegrationTest` no se ejecutó porque requiere Docker.
- **Interfaz.**
  - Compilación de producción correcta.
  - Vitest: 26 pruebas, 4 nuevas de períodos en hora de Costa Rica, validación de fechas y variación.
  - Playwright en Edge: `reports.spec.ts` pasa a 375 y 1440 px. Cubre pestañas y lista «Ver reporte», «Cómo se forma», variación, exportación CSV, validación de fechas, período personalizado e impresión sin desborde.
  - Las demás pruebas de Playwright también pasan, salvo las 6 de `companies.spec.ts`, de la administración SaaS, que no se tocó.
- **Recorrido real contra React, Spring Boot y MySQL (`stockly_demo`).**
  - Las cinco pestañas responden en escritorio y celular sin desborde.
  - Los cobros por medio de pago coinciden con el Resumen de cobros (STK-012) para el mismo período. AC-04.
  - Las ventas anuladas el mismo día se muestran aparte y no suman.
  - Las descargas de Excel (7 hojas) y de CSV por reporte funcionan.
  - El módulo «Reportes» aparece en Roles con «Sin acceso» y «Solo lectura».
  - En ese recorrido se corrigió un error: las cantidades vendidas salían negativas, porque la venta se guarda como salida.

## Ajustes durante la revisión del propietario

- En celular las listas usan una flecha propia con margen, porque la nativa quedaba pegada al borde.
- La letra de tablas y desgloses quedó en 14 px, como en el mockup.

## Pruebas no ejecutadas, aceptadas por el propietario

El propietario decidió el 2026-11-09 dar la funcionalidad por terminada sin estas pruebas dedicadas. No se escribieron ni se ejecutaron; quedan como mejora recomendada si se quiere más cobertura automática.

- Integración en MySQL con datos controlados:
  - anulaciones del mismo día y de otro día;
  - devoluciones en su fecha;
  - período anterior y límite de 366 días;
  - serie por mes;
  - productos, vendedores y «Sin ventas»;
  - archivos.
  Cubre AC-01 a AC-03 y AC-05.
- Integración de permisos y aislamiento: sin módulo (403), sucursal ajena (404), otra empresa y sin sesión (401). AC-06.
