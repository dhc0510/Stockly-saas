# SPEC · STK-016 | Productos agotados y existencias bajas

Aprobado por el propietario el 2026-11-08; todavía no autoriza desarrollo. Mockup aprobado por el propietario el 2026-11-08: [por-reponer-responsive.html](../../06-Mockups/STK-016/por-reponer-responsive.html) · [Trello](https://trello.com/c/q4jPOAbX/16-stk-016-productos-agotados-y-existencias-bajas) · Base: [SPEC de STK-007](../STK-007/SPEC.md).

## 1. Objetivo

Ayudar a reponer: reunir en una pantalla los productos agotados y en su mínimo, sugerir cuánto pedir y evitar avisos de productos que no se van a reponer. STK-016 reutiliza el mínimo, el umbral de stock bajo, el margen de recuperación, los estados y la campanita de STK-007 sin cambiar sus reglas.

## 2. Actores

- **Consulta y exporta:** quien hoy puede consultar Existencias (STK-007), limitado a sus sucursales autorizadas.
- **Configura nivel deseado y «No reponer»:** quien hoy puede configurar alertas: administrador de empresa o permiso de operar/administrar en `CATALOG`, con acceso a la sucursal.

## 3. Flujo

1. En el menú de Inventario, **Por reponer** muestra un contador con los productos agotados y en mínimo de las sucursales autorizadas, sin contar los marcados «No reponer».
2. La pantalla abre en la única sucursal autorizada o, si hay varias, en «Todas mis sucursales». Cada fila es un producto/variante en una sucursal; las cantidades de sucursales distintas no se suman.
3. Por defecto lista agotados y en mínimo, ordenados por urgencia (agotado, mínimo, bajo) y luego por nombre. El interruptor **Incluir stock bajo** agrega los de stock bajo. Hay búsqueda (nombre, SKU, código de barras, sin distinguir tildes) y filtro de categoría. Arriba se muestran los totales de agotados, en mínimo y stock bajo.
4. Cada fila muestra producto/variante, SKU, categoría, sucursal (en la vista de todas), estado, disponible, mínimo, nivel deseado y **A pedir** con la explicación de su cálculo.
5. **Configurar** abre la ventana de alertas de STK-007 (también desde Existencias) con la sección nueva **Reposición**: «Reponer hasta (nivel deseado)», con la sugerencia calculada en vivo, y la casilla «No reponer en esta sucursal».
6. La sección plegable **No se reponen en esta sucursal** lista los productos marcados, con quién y cuándo los marcó, y permite **Volver a reponer**.
7. **Exportar** descarga la lista filtrada en Excel (.xlsx) o CSV. **Imprimir** abre una vista de impresión que también permite guardar como PDF desde el navegador.

## 4. Reglas

- **Nivel deseado:** opcional, por producto/variante y sucursal, cantidad con hasta 2 decimales en la unidad base. Si hay mínimo, debe ser mayor que el mínimo; si no, mayor que 0. Vacío significa sin definir.
- **A pedir**, exacta con 2 decimales, sin redondear:
  - Con nivel deseado: nivel deseado − disponible.
  - Sin nivel deseado y con mínimo: mínimo + margen de recuperación − disponible (lo necesario para que se quite la alerta).
  - Sin ninguno de los dos: «Sin dato para sugerir», con acceso a configurarlo si la persona tiene permiso.
  - Si el resultado es 0 o menor, se muestra «—» con «No hace falta pedir todavía».
- «Disponible» y los estados se calculan como en STK-007, incluido el criterio configurable de agotado.
- **No reponer:** por producto/variante y sucursal. Mientras esté marcado:
  - no genera alertas de mínimo ni de agotado, y al marcarlo se desactivan las activas;
  - no aparece en la lista, totales, contador ni exportaciones de Por reponer;
  - en Existencias conserva su estado y muestra además la etiqueta «No se repone»;
  - existencias, ventas y movimientos no cambian.
  Al desmarcarlo vuelve a evaluarse con las reglas de STK-007. Se guarda quién y cuándo lo marcó.
- Cambiar el nivel deseado no afecta las alertas. Ningún dato de STK-016 crea pedidos ni movimientos de inventario.
- **Exportación e impresión** respetan sucursal, búsqueda, categoría e «Incluir stock bajo». Columnas: sucursal, producto, variante, SKU, categoría, estado, disponible, unidad, mínimo, nivel deseado, a pedir y base del cálculo. Nombre del archivo: `por-reponer-<sucursal o todas>-<AAAA-MM-DD>`. El Excel guarda las cantidades como números. El CSV se genera en UTF-8 con separador de coma y punto decimal; los textos que empiezan con `=`, `+`, `-` o `@` se neutralizan para que la hoja no los ejecute como fórmulas.
- La hoja de impresión incluye empresa, sucursal, filtros, fecha y hora de Costa Rica, persona que la generó y una columna «Pedido» en blanco para anotar a mano.
- Productos y sucursales inactivos, y productos sin sucursal, quedan fuera como en Existencias.

## 5. Seguridad

- Validar en servidor permisos y acceso a sucursal en consulta, exportación y configuración. Derivar la empresa de la sesión. Datos de sucursales no autorizadas no aparecen en filas, totales, contador ni archivos.
- Registrar en la actividad de la empresa quién marca o desmarca «No reponer».

## 6. Errores y casos especiales

- Nivel deseado inválido (negativo, más de 2 decimales, no mayor que el mínimo): mensaje en el campo y no se guarda nada.
- Sin permiso o sucursal no autorizada: denegar sin revelar si el producto existe.
- Error de carga: «No pudimos cargar la lista», sin mostrar cantidades anteriores; Exportar e Imprimir deshabilitados.
- Lista vacía: «Todo al día» con la sugerencia de incluir stock bajo. Búsqueda sin resultados: estado propio.
- Si falla la exportación, avisar sin descargar un archivo incompleto.

## 7. Criterios de aceptación

- [ ] AC-01 Por reponer lista agotados y en mínimo por sucursal, en orden de urgencia; «Incluir stock bajo», búsqueda sin tildes y categoría filtran correctamente; los totales y el contador del menú coinciden con la lista.
- [ ] AC-02 Guardar, cambiar y borrar el nivel deseado con sus validaciones; la sugerencia en vivo coincide con la del servidor.
- [ ] AC-03 «A pedir» aplica las tres reglas de cálculo y el caso sin sugerencia, exacta con 2 decimales.
- [ ] AC-04 Marcar «No reponer» desactiva sus alertas, lo saca de la lista, totales, contador y archivos, muestra «No se repone» en Existencias y registra quién y cuándo; desmarcarlo lo reevalúa.
- [ ] AC-05 Excel, CSV e impresión contienen exactamente las filas filtradas, con las columnas acordadas, números correctos y textos peligrosos neutralizados.
- [ ] AC-06 Permisos de consulta y configuración, aislamiento entre empresas y sucursales, y estados de carga, vacío y error; escritorio y celular según el mockup.

## 8. Fuera de alcance

Días de cobertura, resumen por correo, órdenes de compra, proveedores, tiempos de entrega, reposición automática, traslados entre sucursales y cambios a las reglas de alertas de STK-007. La operación sin conexión se define en STK-014.
