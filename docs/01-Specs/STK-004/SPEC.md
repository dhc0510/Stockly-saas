# SPEC — STK-004 | Gestión de empresas

Estado: aprobado por el propietario en conversación el 2026-11-05 («sI APROBADOS»). Sin implementación ni pruebas ejecutadas; falta indicación explícita de iniciar desarrollo.

## 1. Objetivo

Permitir al administrador de Stockly registrar y gestionar negocios independientes y crear su administrador inicial.

## 2. Usuario / Actor

Administrador de Stockly: único actor autorizado para gestionar empresas en esta funcionalidad. Administrador de empresa: cuenta inicial creada, vinculada a una sola empresa; no administra otras empresas.

## 3. Casos de uso / Flujo

1. Consultar listado; buscar por nombre comercial, razón social o identificación; filtrar por activa/suspendida y fecha de registro.
2. Registrar datos de empresa y administrador inicial; empresa queda activa.
3. Consultar detalle y editar datos de empresa; no mostrar contraseñas guardadas.
4. Suspender con confirmación: conservar datos y bloquear acceso. Reactivar con confirmación: restaurar estado activo.
5. Eliminar: mostrar consecuencias, exigir escribir nombre comercial y confirmar; borrar definitivamente los datos operativos de la empresa, con las excepciones de conservación indicadas en las reglas.

Mockups aprobados: ../../06-Mockups/STK-004/02-listado-referencia-clara-desktop-mobile.png, 03-registro-detalle-edicion-desktop.png, 04-registro-detalle-edicion-mobile.png y 05-confirmaciones-desktop-mobile.png. El archivo 01 es una propuesta superada. Datos de muestra son ilustrativos.

## 4. Entradas

| Entidad | Obligatorios | Opcionales |
|---|---|---|
| Empresa | Nombre comercial, nombre o razón social, tipo y número de identificación, correo de contacto | Teléfono, dirección |
| Administrador inicial | Nombre completo, nombre de usuario, correo, contraseña temporal | Ninguno acordado |

Estado y fecha de registro los asigna el sistema. Confirmación de eliminación: nombre comercial de la empresa seleccionada.

## 5. Reglas

- Identificación única de empresa; usuario y correo de administrador únicos en todo Stockly. No se acordó unicidad del correo de contacto ni del nombre comercial.
- Empresa y cuenta inicial deben crearse juntas sin registros parciales.
- Sin autorregistro. El propietario de Stockly da acceso con contraseña temporal; el administrador debe cambiarla al ingresar.
- Acceso por correo o usuario, código por correo en dispositivo nuevo y recuerdo del dispositivo son acuerdos de STK-005; no se implementan sus pantallas en STK-004.
- Aislamiento obligatorio entre empresas. Suspensión bloquea el acceso e invalida las sesiones abiertas. Un dispositivo desconectado aplicará el bloqueo al recuperar conexión; no se garantiza cierre instantáneo sin internet. Eliminación no es suspensión ni borrado recuperable.
- La eliminación borra datos operativos; conserva únicamente registros sujetos a obligación legal y respaldos hasta su vencimiento. No implica borrar inmediatamente todas las copias. La política propuesta de respaldos y restauración se concreta en PLAN; los registros legales se separan de la operación y no reactivan la cuenta. La confirmación debe explicar estas excepciones; ajustar el texto del mockup aprobado a este acuerdo posterior.
- Editar empresa no implica editar o restablecer credenciales del administrador.

### Validaciones aprobadas

| Campo | Regla |
|---|---|
| Nombre comercial y razón social | 2–150 caracteres |
| Identificación | Tipos admitidos: cédula física, cédula jurídica, DIMEX y NITE; validar según tipo y comparar sin espacios ni guiones para unicidad |
| Correos | Formato válido; quitar espacios exteriores; comparar sin distinguir mayúsculas. Unicidad global solo para correo del administrador |
| Teléfono opcional | Aceptar código de país, espacios y guiones |
| Dirección opcional | Máximo 500 caracteres |
| Nombre completo del administrador | 2–150 caracteres |
| Nombre de usuario | 3–30 caracteres; letras sin tildes, números, punto, guion y guion bajo; único sin distinguir mayúsculas |
| Contraseña temporal | 15–128 caracteres; permitir espacios y pegado, sin combinaciones obligatorias de símbolos; cambio obligatorio al primer ingreso |

Aprobadas por el propietario en conversación el 2026-11-05. Tipos de identificación aprobados: cédula física, cédula jurídica, DIMEX y NITE. Formato normalizado: física 9 dígitos, jurídica 10, DIMEX 11 o 12, NITE 10; física/jurídica/DIMEX sin cero inicial. Conservar como texto, sin agregar ceros. Solo se valida estructura, no existencia ni situación tributaria. Fuente oficial: [Anexos 4.4, identificación](https://pgrweb.go.cr/DOCS/NORMAS/1/VIGENTE/S/2020-2029/2020-2024/2024/1936C/1701B7.HTML). No se definió caducidad de la contraseña temporal ni expiración del dispositivo recordado en esta funcionalidad.

## 6. Resultado esperado

Listado y detalle coherentes con cada cambio confirmado; datos independientes, sin empresas o administradores duplicados ni contraseñas expuestas.

## 7. Errores / Casos especiales

Campos ausentes, correo inválido y duplicados muestran error comprensible sin perder datos del formulario ni efectuar cambios parciales. Confirmación incorrecta no permite eliminar. Cancelar conserva el estado anterior. Errores de servicio no deben presentarse como éxito; evitar efectos duplicados al reenviar. Mostrar carga, vacío, sin resultados y errores recuperables.

### Precisiones para revisión conjunta

- La confirmación de eliminación dirá: «Se eliminarán los datos operativos y el acceso. Se conservará únicamente lo exigido legalmente y los respaldos hasta su vencimiento». Exigir el nombre comercial exacto, ignorando solo espacios exteriores.
- La política de respaldos y el contrato técnico de acceso se proponen en PLAN; requieren aprobación junto con estos documentos. La definición detallada de login/2FA permanece en STK-005.
- Incorporar selector de identificación al registro, edición y detalle según el acuerdo posterior a los mockups. Las demás pantallas aprobadas se conservan.

## 8. Criterios de aceptación

- [x] AC-01 Alta válida crea empresa activa y administrador asociado a una sola empresa, sin efecto parcial ante fallos.
- [x] AC-02 Obligatorios, correos y unicidades se validan también en servidor, incluyendo altas simultáneas.
- [x] AC-03 Listado, búsqueda y filtros devuelven resultados correctos, con vacío y sin resultados distinguibles.
- [x] AC-04 Detalle y edición conservan datos y no exponen contraseñas.
- [ ] AC-05 Suspender exige confirmación, bloquea nuevos accesos e invalida sesiones abiertas; equipos desconectados aplican bloqueo al reconectar. Reactivar restablece acceso con una nueva sesión; no restaura sesiones invalidadas.
- [ ] AC-06 Eliminación exige nombre y confirmación con excepciones explícitas; cancelar no modifica datos. Borrar datos operativos respetando únicamente la conservación legal y respaldos hasta su vencimiento según política aprobada.
- [ ] AC-07 Sin autorización no se accede a gestión global ni a datos de otra empresa por manipular IDs.
- [ ] AC-08 Interfaz respeta mockups, móvil/escritorio, teclado, foco y errores accesibles; reintentos no generan duplicados.

## 9. Fuera de alcance

Login, cambio de contraseña, recuperación, códigos y dispositivos de confianza (STK-005); ventas, facturación, inventario, suscripciones, sucursales y autorregistro. Definir sus contratos necesarios no autoriza implementarlas aquí.