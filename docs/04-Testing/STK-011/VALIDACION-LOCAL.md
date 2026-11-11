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

## Revisión de los ajustes finales

El propietario aprobó los cambios de interfaz y pidió integrarlos en main. La migración V10 se aplicó en el esquema aislado. La compilación de producción y las 53 pruebas seleccionadas pasaron (31 de backend, 12 de Vitest y 10 de Playwright). Las pruebas ampliadas verifican el contacto guardado en la venta, el borrador y el comprobante, la etiqueta Motivo y el bloqueo del fondo al abrir el menú móvil. CompanyIntegrationTest quedó fuera porque requiere Docker.

## Reverificación sobre el código actual — 2026-11-10

Después de la aprobación del 07/11, STK-012, STK-013, STK-014, STK-016, STK-017 y STK-018 modificaron Ventas. Se volvió a verificar STK-011 sobre `main` en `f086b4c`, con React, Spring Boot y MySQL 9.7 local.

### Recorrido real

Pasaron 42 de 42 comprobaciones. El script crea dos empresas reales (A y B), tres sucursales en A (una inactiva) y cuatro trabajadores con roles de STK-005: sin ventas, consulta, vendedor y supervisor con permiso de anular. Usa la API real a través del proxy de Vite. Las ventas se cobran en efectivo y las anulaciones registran el reintegro, como exige STK-012. La suscripción de las empresas de prueba se marcó como pagada para no topar el límite de la prueba gratuita (STK-018).

- **AC-01 (7):**
  - el vendedor solo ve y usa las sucursales a las que tiene acceso;
  - la sucursal inactiva no se ofrece ni admite ventas;
  - si una línea excede el saldo, se identifica el artículo y no se descuenta ninguna línea;
  - el descuento ocurre solo en la sucursal de la venta;
  - de dos ventas simultáneas por el mismo saldo, solo una se confirma.
- **AC-02 (7):**
  - por unidad solo se aceptan enteros y por kg hasta tres decimales;
  - cero o negativo se rechaza;
  - una línea sin precio se rechaza identificando el artículo;
  - si el precio cambió, se pide reconfirmar sin descontar;
  - un correo inválido se rechaza;
  - la búsqueda muestra el precio y el saldo de la sucursal.
- **AC-03 (2):** 0,125 kg a ₡1.234,50 dan ₡154,31, y el total es la suma de las líneas redondeadas. El comprobante conserva el precio histórico aunque cambie el catálogo.
- **AC-04 (1):** el reintento con la misma clave devuelve el mismo comprobante sin volver a descontar, aunque el precio haya cambiado.
- **AC-05 (10):**
  - los folios VTA-0001 y VTA-0002 son consecutivos, y la otra empresa empieza en VTA-0001;
  - se guardan nombre, teléfono y correo del cliente;
  - se consulta por folio, se busca por cliente sin distinguir mayúsculas y se filtra por sucursal y por estado;
  - la venta aparece en el historial de movimientos.
- **AC-06 (15):**
  - consultar, vender y anular son permisos separados: sin ventas da 403, la consulta no confirma y el vendedor no anula;
  - anular exige motivo, devuelve las cantidades a la sucursal original, deja el vínculo en la venta original y se rechaza una segunda vez;
  - la anulación aparece en el historial;
  - la otra empresa no consulta, anula ni vende con sucursales o artículos ajenos, ni ve las ventas de A en su listado.

### Pruebas automáticas

- **Backend (MySQL aislado):**
  - `SaleLocalIntegrationTest` (1) y `SalePaymentLocalIntegrationTest` (2) pasan;
  - también las demás suites de inventario: catálogo 3, entradas 4, reposición 4, salidas y ajustes 9 e historial 7, esta última en una base con intercalación sensible a tildes, como exige;
  - además `FiscalLocalIntegrationTest` 1, `FiscalDocumentXmlTest` 4, validación de empresas 4, registro de borrado 3 y suscripciones 5;
  - `CompanyIntegrationTest` no se ejecutó porque requiere Docker.
- **Playwright en Edge:** 40 de 40 en 1440 y 375 px. Incluye `sales.spec.ts` (borrador al recargar, reintento con la misma clave, comprobante y anulación), cobros, devoluciones y facturación (AC-07).

### Ajuste de la revisión local

En la revisión, el propietario señaló el formulario «Anular esta venta». Con reintegro en efectivo, la fila dejaba vacía la columna de «Referencia» y «Quitar» quedaba separado. Además, el motivo se podía redimensionar a mano.

- Ahora el monto ocupa ese espacio cuando el medio es efectivo, y las columnas siguen alineadas con las filas de otros medios.
- El motivo crece con el texto, sin barra ni control de tamaño, como en el resto de la aplicación.
- El título «Devolución del dinero» va dentro de su recuadro en vez de montado sobre el borde. El motivo conserva una altura mínima de tres líneas. Las etiquetas quedan separadas de sus campos.
- Se comprobó en Edge a 1440 y 375 px con una venta real, sin desbordamiento.
- Después del ajuste pasaron Playwright de ventas, cobros, devoluciones y facturación (8 de 8), las 28 pruebas unitarias y el build.

### Resultado

AC-01 a AC-07 siguen cumpliéndose sobre el código actual. No se encontraron regresiones funcionales.

### Aprobación final

El propietario revisó en local y aprobó el 2026-11-10: «Aprobado, publicar».
