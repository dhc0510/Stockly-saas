# STK-009 — Validación local

Rama: `codex/stk-009`, creada desde `main` (`70bc225`). Validación del 2026-11-07. El propietario probó la aplicación y la aprobó en local ese mismo día.

## Entorno

- Instancia MySQL 9.7.1 aislada, en un puerto privado y con una carpeta de datos temporal. No se usó ninguna base existente del equipo. Flyway aplicó las migraciones V1 a V6 sobre una base vacía.
- **Limitación:** la versión aprobada para el proyecto es MySQL 8.4 LTS y STK-008 se validó con 8.0.46. Falta repetir las pruebas en 8.4 antes de un despliegue.
- Los códigos de dispositivo se recibieron en un buzón SMTP local de prueba. La cookie de sesión sin `Secure` fue una variable de ese proceso; no cambió la configuración del proyecto.

## Backend

Con `mvnw clean test`, sobre MySQL: 23 pruebas seleccionadas correctas, sin fallos.

| Clase | Pruebas |
|---|---|
| `StockMovementLocalIntegrationTest` (nueva) | 9 |
| `ReceiptLocalIntegrationTest` (STK-008, sin cambios) | 4 |
| `CatalogLocalIntegrationTest` | 3 |
| `CompanyValidationTest` | 4 |
| `DeletionJournalTest` | 3 |

Las pruebas nuevas comprueban motivos y cantidades, operaciones atómicas, reintentos concurrentes con un solo comprobante, salidas concurrentes que nunca dejan saldo negativo, ajustes que suben, bajan, llegan a cero y sin diferencia, saldo cambiado, anulación única con la serie ANU compartida, saldos históricos, filtros, cada fila de la tabla de acceso, separación entre entradas y movimientos, y aislamiento por empresa.

**No ejecutado:** la suite completa de empresas con Testcontainers requiere Docker, que no está instalado en este equipo.

## Frontend

Compilación correcta y 9 pruebas unitarias existentes correctas. No se agregaron pruebas unitarias de frontend: el flujo se verificó en navegador.

## Navegador

Recorrido automatizado en Edge con Playwright contra el backend real, sin errores de consola ni de página, a 1440 px y con emulación móvil a 320, 375 y 390 px:

- **Salidas:** lector simulado por código + Enter, suma de repetidos, código desconocido, kg con coma decimal, validaciones de motivo y de «Otro», rechazo de decimales en unidad y aviso de saldo negativo.
- **Borrador:** recuperación al recargar, limpieza al confirmar y al descartar, aviso en el listado, y aislamiento por tipo y por usuario en el mismo navegador.
- **Respuesta perdida:** el servidor registró la salida, el navegador no recibió la respuesta, y el reintento devolvió el mismo comprobante sin descontar otra vez.
- **Ajustes:** lector que suma a lo contado, diferencia positiva y negativa, contado cero y «Sin diferencia».
- **Saldo cambiado:** otra sesión modificó el saldo, el ajuste no se aplicó, mostró la nueva diferencia y se confirmó de nuevo.
- **Comprobante y anulación:** datos históricos, motivo obligatorio, ANU vinculado y sin segunda anulación.
- **Listado:** búsqueda, filtros por tipo y estado, sin resultados y estado vacío.
- **Permisos en la interfaz:** el operador no ve «Nuevo ajuste», su ruta directa queda bloqueada y consulta ajustes sin poder anular. El usuario de solo lectura no ve botones de registro. Entradas no muestra salidas.
- **Responsive:** sin desbordes horizontales y con acciones inferiores visibles. La revisión visual encontró que las columnas del editor se aplastaban a 320 px; se corrigió y se comprobó a 320 y 375 px.
- **Menú de empresa:** a pedido del propietario, cada opción queda en una sola línea. Se midió de 320 a 1440 px, sin texto cortado y sin desbordes en el listado ni en el editor.
- **Ajustes visuales del propietario:** botones de «Nueva salida» y «Nuevo ajuste» alineados, y flechas de las listas separadas del borde. Se midió que el texto más largo de los filtros cabe de 320 a 1440 px.

## Pendiente

- Verificar el teclado y el flujo en un iPhone físico, y probar un lector físico. La emulación móvil no reproduce el teclado real (AC-05 y AC-12).
- Repetir las pruebas en MySQL 8.4.

## Integración en la rama `paulo`

El propietario pidió publicar en `paulo`, que ya contenía STK-007 (`1f7e53c`) y no estaba actualizada con `main`. Se unió STK-009 sin forzar el push:

- La migración `V5__inventory_branches_and_thresholds.sql` de STK-007 pasó a ser `V7`, porque `V5` y `V6` ya corresponden a STK-008 y STK-009. Una base local donde se aplicó la antigua `V5` de STK-007 no arrancará con esta rama; hay que usar una base limpia.
- En `SecurityConfiguration` las reglas de entradas y de salidas y ajustes van antes de la regla general `/api/inventory/**`. El menú conserva Existencias, Sucursales y la campanita junto con Entradas y Salidas y ajustes.
- Sobre el resultado unido, con migraciones V1 a V7 en una base vacía: las mismas 23 pruebas de backend correctas, compilación y 9 pruebas de frontend correctas, y el recorrido completo de STK-009 en Edge sin fallos ni errores de consola.

**Pendiente de la unión:** Existencias de STK-007 aún muestra saldos en cero fijos (`InventoryService`), no lee los saldos de entradas, salidas y ajustes. Tampoco está decidido cómo unir sus sucursales con la ubicación Principal. El propietario aprobó STK-009 antes de esta unión; el resultado unido no ha tenido su revisión.
