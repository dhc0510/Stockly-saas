# STK-011 · Validación local

## MySQL y API

Flyway aplicó las migraciones V1 a V9 en MySQL 8.0.46 aislado. Pasaron las 31 pruebas seleccionadas. CompanyIntegrationTest quedó fuera porque requiere Docker.

- La configuración de Principal exige elegir una sucursal cuando hay varias y conserva las 10 unidades del saldo anterior. Una entrada de 1,250 kg en otra sucursal aparece en Existencias. Salidas, Ajustes y sus anulaciones afectan esa misma ubicación.
- Un precio desactualizado impide confirmar. El servidor redondea 0,125 kg a ₡1.234,50 por kg a ₡154,31 y conserva el importe histórico. Historial muestra la venta y el saldo resultante.
- VTA-0001 se puede consultar por folio. El reintento devuelve el comprobante original incluso si cambió el precio. La anulación repone la cantidad en la sucursal de origen y se rechaza una segunda anulación.
- De dos ventas concurrentes por un saldo insuficiente para ambas, solo se confirma una. El lector sin acceso a la sucursal no puede consultar su comprobante ni confirmar ventas.
- Se rechazan la desactivación de una sucursal con saldo y el borrado de una sucursal con saldo o historial.

## Navegador

Compilación de producción correcta. Pasaron 12 pruebas de Vitest y 10 de Playwright. La prueba de Ventas en Chromium a 320 px y 1440 px simuló la API y verificó selección de sucursal, coma decimal, borrador al recargar, reintento con la misma clave tras un fallo de red, navegación al comprobante y anulación. Las ocho pruebas restantes cubrieron el diseño de Entradas a 320, 375, 390 y 1440 px.

El propietario aprobó la aplicación funcionando en local el 2026-11-07.

## Flujo real de punta a punta

Chromium a 320 px y 1440 px con React, Spring Boot y MySQL conectados. Se usaron una empresa y un esquema de revisión aislados.

- Centro conservó el saldo de Principal. Norte quedó en cero. Se verificaron ambas sucursales desde la API de Existencias después de configurar el vínculo en pantalla.
- A 320 px se recuperó el borrador tras recargar, con dos líneas y una cantidad escrita con coma. La venta de 0,125 kg y 2 unidades quedó en ₡1.654,31. Se interrumpió la respuesta de red después de que MySQL confirmara la venta. El reintento devolvió VTA-0001 y el listado mantuvo una sola venta.
- Existencias e Historial reflejaron ambas líneas. El comprobante abrió por su URL sin desbordamiento horizontal.
- A 1440 px se anuló el comprobante desde la aplicación. Centro recuperó 10 kg y 5 unidades. Norte siguió en cero.
