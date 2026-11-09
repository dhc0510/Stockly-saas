# STK-014 · Validación local

El propietario aprobó la revisión local y solicitó publicar en main el 2026-11-09.

- Compilación de producción correcta. Vitest pasó 28 pruebas, incluida la lectura de montos con símbolo de colón y espacios.
- Chromium a 375 y 1440 px, con API simulada y conexión del navegador desactivada. Se preparó la sucursal, se registró una venta con SINPE por ₡10 500,00 y se recuperó la constancia interna al recargar sin conexión. Al reconectar se verificaron un solo envío y el estado sincronizado.
- MySQL local de demostración con migración V19. Pasaron las 4 pruebas seleccionadas de ventas, pagos y comprobantes. La prueba de sincronización verificó que se conservaran el precio, la fecha y la referencia originales, que un reintento no descontara inventario dos veces y que una venta sin saldo pasara a revisión y se pudiera cerrar con detalle de reintegro.
- Pasaron 20 escenarios de la suite de navegador. Los 6 fallos de Empresas se reprodujeron en el mismo punto sobre origin/main antes de STK-014.
- La emisión fiscal de contingencia queda pendiente de definición. El comprobante de la venta sin internet es una constancia interna.
