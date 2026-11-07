# STK-010 — Validación local

Rama: `codex/stk-010`, creada desde `main` (`ff13e7c`). Validación del 2026-11-07. El propietario probó la aplicación y la aprobó en local ese mismo día.

## Entorno

- Instancia MySQL 9.7.1 aislada, en un puerto privado y con una carpeta de datos temporal. No se usó ninguna base existente del equipo.
- Las pruebas de backend corrieron sobre una base vacía con colación `utf8mb4_0900_as_cs`, que distingue tildes y mayúsculas, para demostrar que las búsquedas no dependen de la colación por defecto. Flyway aplicó las migraciones V1 a V8.
- **Limitación:** la versión aprobada es MySQL 8.4 LTS. Falta repetir las pruebas en 8.4, igual que en STK-009.

## Backend

Con `mvnw test`, sobre MySQL: 30 pruebas seleccionadas correctas, sin fallos.

| Clase | Pruebas |
|---|---|
| `InventoryHistoryLocalIntegrationTest` (nueva) | 7 |
| `StockMovementLocalIntegrationTest` | 9 |
| `ReceiptLocalIntegrationTest` | 4 |
| `CatalogLocalIntegrationTest` | 3 |
| `CompanyValidationTest` | 4 |
| `DeletionJournalTest` | 3 |

Las pruebas nuevas comprueban filas de cada tipo, orden y paginación, filtros y búsqueda, límites de día de Costa Rica cerca de medianoche, fechas futuras y rango invertido, datos históricos tras cambiar el catálogo, artículos inactivos, cada nivel del módulo, separación con Entradas y con Salidas y ajustes, y aislamiento entre empresas también por identificador directo. También comprueban búsquedas sin tildes en catálogo, existencias, entradas, salidas y ajustes, empresas e historial, y que el código de barras exacto no cambia.

**No ejecutado:** la suite completa de empresas con Testcontainers requiere Docker, que no está instalado en este equipo.

## Frontend

Compilación correcta y 12 pruebas unitarias correctas, 3 de ellas nuevas de `search.ts`.

## Navegador

Recorrido automatizado en Edge con Playwright contra el backend real, sin errores de consola ni de página, a 1440 px y a 320, 375, 390, 660, 768 y 1024 px:

- **Listado:** últimos 30 días al abrir, filas de entradas, salidas, ajustes y anulaciones, filtros, fecha específica, rango invertido, sin resultados y limpiar filtros.
- **Sin tildes:** «cafe» y «CAFÉ» en el historial, «cafe» y «camiseta basica» en catálogo, y «cafe» en las sugerencias de salidas.
- **Por artículo:** el saldo actual coincide con el saldo del movimiento más reciente.
- **Comprobante:** se abre sin acciones de registro ni anulación. Un identificador inexistente muestra «No se encontró el movimiento».
- **Permisos en la interfaz:** sin el módulo no aparece la opción y la ruta redirige. Con el módulo en lectura y sin permiso de entradas, se ve el historial y se abre el comprobante de una entrada.

La revisión visual encontró y corrigió dos defectos:

- **Menú:** en Entradas, Salidas y ajustes e Historial también se resaltaba Existencias. Venía de unir STK-007 con STK-008 y STK-009. Ahora se resalta solo la opción actual, comprobado a 1250 y 1440 px.
- **Celular angosto:** a 320 px se partían palabras como «Administrador» y el saldo actual. Se corrigió con el formato de tarjeta.

## Diferencia con el PLAN

Para la búsqueda de Empresas, el PLAN indicaba aplicar la colación en la consulta. Se fijó en las columnas `commercial_name` y `legal_name` mediante `V8`, porque ofrece la misma garantía sin cambiar la consulta. Las pruebas lo comprueban.

## Observaciones

- **Existencias:** su búsqueda se comprobó en servidor, y el filtro del navegador usa `search.ts`, pero no se recorrió en navegador con datos reales porque requiere sucursales y artículos asignados. Por eso AC-10 sigue sin marcar para STK-007, que además no tiene pruebas propias.
- **Método incorrecto:** una solicitud `POST` a una ruta de solo `GET` responde 500 en toda la aplicación, por el manejador general de errores, en lugar de 405. No escribe nada. Es anterior a STK-010.
- **Texto de unidades:** las cantidades muestran «40 metro» en singular, por la función compartida `amount`. Es anterior a STK-010.

## Pendiente

- Verificar el selector de fecha y la lectura de filas en un celular físico (AC-11).
- Revisar Existencias en navegador con datos reales (AC-10).
- Repetir las pruebas en MySQL 8.4.

## Ajuste visual del propietario

A pedido del propietario, en móvil cada fila se muestra como una tarjeta más limpia que la del mockup aprobado: artículo con cambio y saldo arriba, motivo debajo, y folio, fecha y responsable separados por una línea. Se comprobó de 320 a 1024 px, sin desbordes ni texto recortado. El escritorio no cambia.

## Corrección autorizada por el propietario: cerrar e iniciar sesión

Durante la revisión, el propietario encontró que, tras cerrar sesión, iniciar otra vez sin recargar respondía «No tienes permiso» con la letra dañada («operaci�n»). Viene de STK-005 y corregirlo fue autorizado junto con STK-010:

- Al cerrar sesión y antes de cada ingreso se pide un token CSRF nuevo.
- Los mensajes de seguridad se envían en UTF-8.

Se comprobó en Edge cerrar sesión e iniciar otra vez sin recargar. Las 30 pruebas de backend siguen correctas, y una de ellas comprueba la tilde del mensaje.
