# SPEC — STK-007 | Consulta de existencias, búsqueda, filtros y sucursales

Estado: [validación local aprobada para publicar](../../04-Testing/STK-007/VALIDACION-LOCAL.md). [Decisión de precisión](../../05-Decisions/ADR-013.md). [Tarjeta de Trello](https://trello.com/c/mfpgKPWG/7-stk-007-consulta-de-existencias-b%C3%BAsqueda-y-filtros) · Mockups aprobados: [escritorio/responsive](../../06-Mockups/STK-007/existencias-responsive.html) y [móvil](../../06-Mockups/STK-007/existencias-mobile.html).

## 1. Objetivo y actores

Consultar existencias confiables por producto, variante y sucursal; encontrar productos rápidamente; mostrar estados y alertas de reposición; y administrar sucursales y acceso a ellas.

- **Administrador de empresa:** administra sucursales y el acceso de trabajadores a sucursales; consulta según sus sucursales asignadas.
- **Trabajador:** consulta únicamente con acceso de lectura u otro nivel autorizado del módulo de inventario. Puede configurar mínimos y umbrales solo si tiene permiso de edición del producto en el catálogo.
- **Administrador SaaS:** no recibe acceso operativo implícito a los datos de empresa, según STK-005.

## 2. Flujo

1. Después de crear la empresa, su administrador puede crear la primera sucursal desde el menú de inventario; STK-004 no se modifica. Puede crear más sucursales, editar sus datos, desactivar o borrar según las reglas de esta SPEC.
2. El administrador asigna a cada usuario las sucursales que puede consultar. Una nueva sucursal queda visible únicamente para el administrador hasta asignar acceso. Solo el administrador gestiona sucursales y esas asignaciones.
3. La vista de existencias abre el consolidado de las sucursales autorizadas y permite seleccionar una sucursal. Al consultar una sucursal, se muestran también los saldos de otras sucursales autorizadas para ese usuario.
4. La persona busca por nombre, SKU, código de barras o categoría; filtra por disponibilidad/estado y elige el orden de los resultados. Puede abrir una ficha para consultar sus variantes y saldos por sucursal.
5. El administrador o el trabajador con permiso de edición del producto lo asigna a una o más sucursales; la creación iniciada desde una sucursal conserva ese contexto. Si un producto queda sin asignación, se muestra el estado naranja «Sucursal pendiente» y requiere atención del administrador.
6. La campanita presenta alertas activas de stock mínimo y agotado para la sucursal asignada/autorizada de la persona. Cada alerta abre el producto y sucursal correspondientes. Ocultar una alerta solo afecta a esa persona.

## 3. Datos y reglas de stock

- Por producto/variante y sucursal se consulta **existencia física, reservado, disponible, mínimo y estado**. Disponible = existencia física − reservado. Las cantidades de existencias y reservas se muestran en la unidad base sin redondear: enteros para unidad y hasta tres decimales para kg, litro y metro. Los mínimos, el umbral bajo y el margen de recuperación mantienen su precisión de hasta dos decimales.
- Reservado corresponde a cantidades comprometidas en pedidos confirmados aún no despachados. Las ventas confirmadas descuentan la existencia de la sucursal indicada; mientras no exista un flujo de pedidos que origine reservas, reservado es 0. STK-007 consulta esos valores, no crea movimientos ni reservas.
- En productos con variantes, cada variante mantiene su propio saldo, mínimo y estado por sucursal. La ficha agrupadora presenta totales derivados de las variantes y no saldo propio.
- El mínimo, el umbral de «stock bajo» y el margen de recuperación se configuran por producto/variante y sucursal. Administrador y trabajadores con permiso de edición de ese producto pueden configurarlos. El umbral inicial de stock bajo es 2 unidades base; es configurable. El margen de recuperación también es configurable y no puede ser menor que 1 unidad base.
- Estados calculados con disponible, salvo el criterio configurable de agotado:
  - **Disponible, verde:** disponible por encima del umbral de stock bajo.
  - **Stock bajo, verde apagado:** disponible mayor que el mínimo y menor o igual al mínimo más el umbral bajo.
  - **Stock mínimo, amarillo:** disponible igual o menor que el mínimo.
  - **Agotado, rojo:** criterio de agotado cumplido; por defecto, disponible igual o menor que 0. El administrador o un trabajador con permiso de edición puede elegir si el criterio usa disponible o existencia física.
- Si una operación cruza un umbral sin quedar exactamente en él, se aplica el estado correspondiente al saldo final (por ejemplo, mínimo cuando queda en el mínimo o por debajo). Las etiquetas e iconos acompañan los colores; el color no es la única señal.
- El filtro **Disponible** selecciona artículos con cantidad disponible mayor que 0; por eso puede coincidir con los estados Stock bajo o Stock mínimo. Los filtros de estado seleccionan el estado calculado, y Agotado usa el criterio configurado.

## 4. Sucursales y acceso

- Datos de sucursal: nombre, código único dentro de la empresa, dirección, teléfono, correo de contacto, responsable y estado activa/inactiva.
- Solo el administrador de empresa crea, edita, desactiva, borra sucursales y asigna accesos. Borrar solo es posible si no hay productos asignados, existencias ni historial asociado. Si hay actividad, se conserva el historial y se desactiva; antes deben reasignarse las existencias mediante el flujo de movimientos correspondiente. STK-007 no realiza traslados.
- Las sucursales inactivas y su historial quedan fuera de la consulta operativa habitual, pero disponibles en la vista de inactivas. Las cantidades e historial no se borran al desactivar.
- La vista consolidada suma solo las sucursales que la persona puede consultar. Los datos de sucursales no autorizadas no aparecen en filas, búsquedas, filtros, totales, alertas ni enlaces.
- Las ventas deben indicar sucursal y no pueden vender más que el disponible allí; esa operación pertenece a STK-011 y se integra mediante su contrato.

## 5. Alertas

- La campanita solo avisa de **stock mínimo** y **agotado**. «Stock bajo» es una advertencia visual preventiva y no genera notificación.
- Al entrar en mínimo (disponible igual o menor al mínimo) o cumplir agotado, se genera una alerta para usuarios autorizados de esa sucursal. Se vuelve a notificar como máximo una vez cada 24 horas mientras siga activa.
- La alerta de mínimo permanece hasta que disponible alcanza el mínimo más el margen de recuperación configurado, o hasta que se cambie/elimine el mínimo. La alerta roja permanece hasta que deje de cumplirse el criterio de agotado. Al resolverse, se quita automáticamente.
- El usuario puede ocultar la alerta solo de su campanita; otras personas conservan la suya. Si la condición sigue activa, podrá volver a aparecer en el siguiente ciclo de 24 horas.
- Las notificaciones se consultan dentro de la aplicación y enlazan a la sucursal y producto/variante. Otros eventos quedan en la actividad de la empresa según STK-005.

## 6. Permisos y seguridad

- Aplicar los niveles de STK-005 (sin acceso, lectura, operar y administrar) y validarlos en servidor. Consulta requiere permiso de lectura del módulo de inventario; la configuración de umbrales requiere administrador de empresa o permiso de operar/administrar el producto en `CATALOG`.
- Gestión de sucursales, asignación de acceso a sucursales y eliminación física requieren el administrador fijo de empresa, independientemente del nivel de trabajador.
- Toda consulta y escritura deriva la empresa de la sesión autenticada. Aislar empresas y sucursales en servicios, búsquedas, agregados y alertas; denegar por defecto y evitar exposición mediante IDs manipulados.

## 7. Errores y casos especiales

- Sin permiso de módulo o sucursal: denegar la operación sin revelar si existen productos o cantidades en la ubicación restringida.
- Código de sucursal duplicado, datos inválidos o conflicto de guardado: explicar el campo o motivo y no aplicar cambios parciales.
- No borrar una sucursal con productos, saldo o historial; si conserva saldo, impedir su desactivación hasta que el flujo autorizado lo reasigne.
- Productos sin sucursal: mostrar «Sucursal pendiente» en naranja, sin inventar saldo ni dejar que un filtro por sucursal revele datos de otra.
- Sucursal sin productos, lista vacía, búsqueda sin resultados, error de carga y falta de conexión muestran estados diferenciados. La operación sin conexión se define en STK-014.
- Si falla la fuente de saldos, mostrar error de consulta; nunca sustituirlo por cero ni presentar datos en caché como actuales.

## 8. Criterios de aceptación

- [ ] AC-01 Administrador crea la primera sucursal después del alta de empresa y gestiona sus datos; código único por empresa; solo él puede crear, editar, desactivar, borrar y asignar accesos.
- [ ] AC-02 Producto/variante está asignado a una o más sucursales; un producto pendiente se identifica en naranja y puede asignarse por el administrador.
- [ ] AC-03 Consolidado inicial y selector de sucursal respetan acceso; totales, resultados y saldos de otras sucursales solo incluyen ubicaciones autorizadas.
- [ ] AC-04 Consultar existencia, reservado, disponible, mínimo y estado por sucursal, con la precisión de la unidad base; disponible resta reservas y conserva el vínculo de unidad de STK-006.
- [ ] AC-05 Variantes tienen saldos, mínimos y estados independientes; totales agrupados derivan de ellas.
- [ ] AC-06 Buscar por nombre, SKU, código de barras y categoría; filtrar por disponible, bajo, mínimo y agotado; ordenar por opciones visibles de la interfaz.
- [ ] AC-07 Estados aplican umbrales inclusivos acordados, configurables y con etiqueta además de color; el agotado usa por defecto disponible ≤ 0 y admite el criterio configurable.
- [ ] AC-08 Alerta de mínimo y agotado se genera para destinatarios autorizados, se repite cada 24 h mientras aplique, se resuelve según sus reglas y ocultarla afecta solo al usuario que la ocultó.
- [ ] AC-09 Ventas consultan/descuentan la sucursal indicada y rechazan cantidades superiores al disponible; validar el contrato al integrar STK-011.
- [ ] AC-10 Verificar permisos de lectura/edición, asignaciones de sucursal, aislamiento de empresa, errores, sucursal inactiva y respuestas móvil/escritorio según mockups.

## 9. Fuera de alcance y dependencias

No incluye registrar entradas, salidas, ajustes, reservas, ventas ni traslados; son responsabilidad de STK-008, STK-009 y STK-011. STK-007 consulta sus saldos y acuerda contratos con esos módulos antes de integrar, sin duplicar lógica. La operación offline se define en STK-014. Depende del catálogo y permisos de STK-006/STK-005.
