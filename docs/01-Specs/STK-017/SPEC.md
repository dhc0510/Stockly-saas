# SPEC · STK-017 | Resúmenes y reportes operativos

Aprobado por el propietario el 2026-11-08; todavía no autoriza desarrollo. Mockup aprobado por el propietario el 2026-11-08: [reportes-responsive.html](../../06-Mockups/STK-017/reportes-responsive.html) · [Trello](https://trello.com/c/9SIvToN2/17-stk-017-res%C3%BAmenes-y-reportes-operativos). Reutiliza ventas (STK-011), cobros (STK-012), devoluciones (STK-015) y sucursales y catálogo (STK-006, STK-007) sin cambiar sus reglas.

## 1. Objetivo

Dar al comercio una pantalla **Reportes** con las cifras que hoy puede calcular con certeza: cuánto vendió, qué vendió, cómo le pagaron, quién vendió y qué no se vendió, por período y sucursal, para exportar o imprimir. No es contabilidad: Stockly no registra costos, así que no hay ganancia, margen ni valoración de inventario.

## 2. Actores

- **Consulta, exporta e imprime:** el administrador de la empresa y quien tenga el módulo nuevo **Reportes** con «Solo lectura» en su rol, siempre limitado a sus sucursales autorizadas.
- **Asigna el permiso:** quien hoy administra roles (STK-005). Los roles existentes empiezan sin acceso a Reportes.

## 3. Flujo

1. En «Operación comercial», **Reportes** abre en «Últimos 7 días» y en la única sucursal autorizada o, si hay varias, en «Todas mis sucursales».
2. La persona elige un período (Hoy, Ayer, Últimos 7 días, Este mes, Mes anterior o Personalizado con «Desde» y «Hasta») y una sucursal.
3. Elige un reporte: pestañas en escritorio y lista desplegable «Ver reporte» en celular.
   - **Resumen:** ventas netas, ventas realizadas, venta promedio y devoluciones, cada una con su variación contra el período anterior de igual duración. Gráfico de ventas netas por día, con la opción de verlo como tabla. Bloque «Cómo se forma» y los 3 productos más vendidos.
   - **Productos:** ventas por categoría y ranking de productos/variantes con cantidad, monto, devuelto y porcentaje del total. Incluye búsqueda sin distinguir tildes, filtro de categoría y orden por monto o cantidad.
   - **Cobros:** por medio de pago muestra pagos, cobrado, reintegrado y neto.
   - **Vendedores:** ventas, brutas, devoluciones, netas y venta promedio de cada persona.
   - **Sin ventas:** productos activos de la sucursal sin ventas en el período, con lo disponible hoy y la fecha de la última venta. El filtro «Solo con existencias» viene activado.
4. **Exportar** descarga en Excel (.xlsx), con una hoja por reporte, o en CSV, solo del reporte abierto. **Imprimir** abre una hoja con resumen, cobros, ventas por día y los 10 productos más vendidos.

## 4. Reglas de cálculo

- **Fechas:** se usa el día calendario de Costa Rica (UTC−6). El período incluye «Desde» y «Hasta» completos, no admite fechas futuras y dura hasta 366 días. El período anterior es el inmediatamente previo con la misma cantidad de días.
- **Ventas brutas:** suma del total de las ventas registradas en el período, tal como se cobraron, con impuestos incluidos.
- **Anulaciones:**
  - Si la venta se anula el mismo día, no suma en ventas, cantidad ni productos. Se muestra aparte como «Anuladas el mismo día», con cantidad y monto.
  - Si se anula otro día, la venta cuenta en su día. El día de la anulación su total se resta como «Anulaciones de ventas anteriores», igual que una devolución.
  - Así los días ya cerrados no cambian.
- **Devoluciones:** el monto de cada devolución (STK-015) resta en la fecha en que se registró, aunque la venta sea de otro período.
- **Ventas netas** = brutas − devoluciones − anulaciones de ventas anteriores.
- **Ventas realizadas:** cantidad de ventas del período, sin las anuladas el mismo día.
- **Venta promedio** = brutas ÷ ventas realizadas, redondeada a colones enteros solo al mostrarla.
- **Variación:** el porcentaje respecto al período anterior, redondeado a entero. Si el anterior es 0, se muestra «Sin datos del período anterior» en lugar de un porcentaje.
- **Gráfico por día:** hasta 62 días, una barra por día. Con más días, una por mes.
- **Productos:**
  - La cantidad está en la unidad de venta de cada producto y no se suma entre productos.
  - El monto es el de cada línea de venta.
  - «Devuelto» suma, en el período, las devoluciones y las anulaciones de ventas anteriores de ese producto.
  - En la categoría se usa la actual del catálogo; sin categoría aparece como «Sin categoría».
- **Cobros:** usa exactamente el mismo cálculo que el resumen de la pantalla Cobros (STK-012), para que las dos pantallas coincidan.
  - Cobrado: pagos por fecha de venta.
  - Reintegrado: reintegros de anulaciones por fecha de anulación más reintegros de devoluciones por fecha de devolución.
  - Neto = cobrado − reintegrado.
  - Las ventas registradas antes de STK-012 se muestran en la línea «Sin cobro registrado». Por eso el neto de cobros puede diferir de las ventas netas, y la pantalla lo explica.
- **Vendedores:** la venta se asigna a quien la registró. La devolución y la anulación de otro día restan a quien hizo la venta original, en su fecha. Se muestra el nombre actual de la persona y, si ya no existe, el que quedó guardado.
- **Sin ventas:**
  - Incluye productos/variantes activos y asignados a la sucursal sin ninguna venta en el período, contando como no vendidas las anuladas el mismo día.
  - La última venta es la más reciente en cualquier fecha; si nunca se vendió, dice «Nunca vendido».
  - En «Todas mis sucursales» cada fila es un producto en una sucursal.
- **Sucursales:** «Todas mis sucursales» suma solo las sucursales autorizadas, incluidas las inactivas que tengan ventas en el período.

## 5. Exportación e impresión

- Los archivos usan los mismos filtros y números que la pantalla. El nombre es `reportes-<sucursal o todas>-<desde>-a-<hasta>` y, para el CSV, se agrega el reporte, por ejemplo `-productos`.
- El Excel guarda montos y cantidades como números. El CSV va en UTF-8 con separador de coma y punto decimal, y neutraliza los textos que empiezan con `=`, `+`, `-` o `@`, igual que en STK-016.
- La hoja impresa lleva empresa, sucursal, período, fecha y hora de generación y la persona que la generó.

## 6. Seguridad

- **Permisos:** se crea el módulo `REPORTS`. En Roles solo ofrece «Sin acceso» y «Solo lectura». El servidor permite leer con cualquier nivel distinto de «Sin acceso» y al administrador de la empresa.
- **Validación en servidor:** en consulta, exportación e impresión se validan permiso, empresa (derivada de la sesión) y acceso a la sucursal. Las sucursales no autorizadas no aparecen en filas, totales, comparaciones ni archivos.
- **Solo lectura:** Reportes no modifica datos ni registra eventos de actividad.

## 7. Errores y casos especiales

- **Fechas inválidas:** «Desde» posterior a «Hasta», fechas futuras o más de 366 días muestran el mensaje en el filtro, no se consulta y Exportar e Imprimir quedan deshabilitados.
- **Sin permiso o sucursal no autorizada:** el servidor responde 403 o 404 sin revelar datos. El menú no muestra Reportes a quien no tiene el módulo.
- **Error de carga:** muestra «No pudimos cargar los reportes» sin cifras parciales y con la opción Reintentar.
- **Período sin movimientos:** estado vacío propio. Una búsqueda sin resultados también tiene su propio estado.
- **Exportación fallida:** se avisa y no se descarga un archivo incompleto.

## 8. Criterios de aceptación

- [ ] AC-01 Los filtros de período y sucursal funcionan, con las validaciones de fechas, la hora de Costa Rica y el período anterior correcto.
- [ ] AC-02 El resumen calcula ventas brutas, devoluciones, anulaciones, netas, cantidad, promedio y variaciones según las reglas, y el gráfico por día o mes coincide con su tabla.
- [ ] AC-03 Productos, categorías, vendedores y «Sin ventas» calculan según las reglas, incluida la búsqueda sin tildes.
- [ ] AC-04 Los cobros coinciden con el resumen de la pantalla Cobros para el mismo período y sucursal.
- [ ] AC-05 El Excel, el CSV y la impresión contienen los mismos números que la pantalla, con textos peligrosos neutralizados.
- [ ] AC-06 Se cumplen el módulo `REPORTS` en roles, los permisos, el aislamiento entre empresas y sucursales y los estados de carga, vacío y error, en escritorio y celular según el mockup.

## 9. Fuera de alcance

- Costos, ganancia, margen y valoración del inventario.
- Impuestos desglosados y reportes fiscales o contables.
- Cierre de caja y arqueo.
- Reportes por cliente.
- Gráficos personalizables y reportes programados por correo.
- Comparación entre sucursales lado a lado.
- Movimientos de inventario, que ya están en Historial (STK-010).
- La operación sin conexión, que se define en STK-014.
