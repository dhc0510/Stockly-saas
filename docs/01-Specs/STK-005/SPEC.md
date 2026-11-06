# SPEC — STK-005 | Acceso, usuarios y permisos

Estado: aprobado por el propietario el 2026-11-05. Mockups aprobados el 2026-11-05. La aprobación no autoriza desarrollo. Decisiones aprobadas: ../../05-Decisions/ADR-003.md.

## 1. Objetivo

Dar acceso seguro a cuentas SaaS y de empresa, y permitir que el administrador de empresa gestione trabajadores y permisos por módulo sin romper el aislamiento entre empresas.

## 2. Actores

- Administrador SaaS: cuenta separada, aprovisionada durante instalación. Administra Stockly y crea la cuenta administradora inicial de cada empresa; no obtiene permisos operativos sobre empresas.
- Administrador de empresa: cuenta inicial de una sola empresa con todos sus permisos. No puede crear otras cuentas administradoras.
- Trabajador: cuenta de una sola empresa con un rol de trabajador asignado; no administra otras empresas.
- Usuario sin sesión: puede ingresar o solicitar recuperación de acceso; no existe autorregistro.

## 3. Casos de uso / Flujo

1. Ingreso: usuario o correo y contraseña. Si se usa contraseña temporal, se exige cambiarla antes de continuar. Si el dispositivo no está recordado, se envía un código al correo registrado; al verificarlo se puede recordar el dispositivo por 30 días.
2. Recuperación: ingresar correo; el sistema responde de forma genérica. Si corresponde, envía enlace de un solo uso válido 30 minutos. Al cambiar la contraseña, la persona vuelve a ingresar.
3. Crear trabajador: el administrador de empresa registra nombre, usuario y correo, asigna un rol de trabajador y solicita la cuenta. El sistema genera una contraseña temporal y la muestra una sola vez para entregarla al trabajador.
4. Reemitir contraseña temporal: el administrador puede generar una nueva para un trabajador activo; vence en 24 horas, se muestra una sola vez y se cambia al ingresar. SaaS atiende al administrador inicial.
5. Desactivar trabajador: el administrador bloquea el acceso y revoca sus sesiones y dispositivos recordados; se conserva la cuenta para mantener su trazabilidad.
6. Gestionar roles: el administrador crea y edita roles propios de la empresa, asigna permisos por módulo y los asigna a trabajadores. Un rol solo puede eliminarse si no tiene trabajadores asignados.
7. Configurar sesiones: el administrador escoge el cierre por inactividad en intervalos de 30 minutos, sin máximo predefinido, o «Nunca». «Nunca» persiste entre aperturas del navegador y no tiene vencimiento automático impuesto por Stockly.
8. Consultar actividad: el administrador fijo de empresa consulta quién creó, desactivó o cambió trabajadores, reemitió contraseñas, modificó roles/permisos o ajustó la política de sesión, con objetivo y fecha. Los trabajadores no pueden ver esta bitácora.

Mockups aprobados, móvil y escritorio: `../../06-Mockups/STK-005/`. Auditoría aprobada el 2026-11-05: `../../06-Mockups/STK-005/11-auditoria-escritorio-movil.png`. Los valores y nombres de muestra son ilustrativos.

## 4. Entradas

- Ingreso: usuario o correo y contraseña.
- Código: seis dígitos enviados al correo; recordar dispositivo es opcional.
- Nueva contraseña: contraseña temporal y nueva contraseña, o enlace de recuperación y nueva contraseña.
- Trabajador: nombre completo, nombre de usuario, correo y rol requerido.
- Rol: nombre y nivel de permiso por módulo; permisos especiales separados.
- Sesión: `Nunca` o cantidad de minutos positiva divisible entre 30.

## 5. Reglas

- Reutilizar unicidad global de usuario/correo, normalización y política de contraseña de STK-004: 15–128 caracteres, espacios permitidos y sin requisitos de mezcla de caracteres.
- Trabajadores creados en STK-005 reciben una contraseña temporal generada por el sistema, de un solo uso y válida 24 horas; deben cambiarla al ingresar. Se muestra una sola vez al administrador que crea la cuenta; nunca se persiste en texto claro ni se registra en logs. La contraseña temporal del administrador inicial sigue el flujo de provisión de STK-004.
- El administrador de empresa puede reemitir contraseña temporal solo a trabajadores activos. SaaS gestiona por su procedimiento seguro la recuperación del administrador inicial.
- Código de dispositivo: un solo uso, seis dígitos, vigencia de 10 minutos, máximo cinco intentos y pausa de 15 minutos al excederlos. Emitir un código nuevo no reinicia el contador. El correo se acepta con menor protección y no se denomina MFA.
- Intentos de contraseña: tras cinco fallos consecutivos, pausar nuevos intentos durante 15 minutos. Mantener respuestas genéricas sin revelar si la cuenta existe.
- Envío de correo: limitar códigos de dispositivo y enlaces de recuperación a uno por minuto y cinco por hora por cuenta. Mantener respuestas genéricas aunque se alcance el límite o la cuenta no exista.
- Dispositivo recordado: confianza de 30 días desde su verificación. Al expirar o revocarse, el siguiente ingreso vuelve a pedir código.
- Recuperación: enlace enviado solo al correo asociado, un solo uso y válido 30 minutos. Respuesta genérica para evitar revelar si la cuenta existe. El token no se guarda en texto claro; completar la recuperación revoca las sesiones existentes.
- Contraseñas, códigos, tokens y cookies nunca aparecen en logs. Los errores de ingreso no confirman si un usuario existe.
- Permisos denegados por defecto y verificados en servidor. El tenant se deriva de la identidad autenticada, nunca del `companyId` enviado por el cliente. El administrador SaaS no recibe acceso operativo implícito.
- Cada empresa tiene un rol administrador fijo con acceso completo y al menos una cuenta administradora activa. Solo el SaaS aprovisiona esa cuenta inicial. Los trabajadores no pueden recibir ni modificar ese rol.
- Cada trabajador tiene exactamente un rol propio de su empresa. El catálogo de módulos y acciones lo proveen las funcionalidades aprobadas; la empresa define roles, no módulos nuevos.
- Niveles por módulo: sin acceso, solo lectura, operar y administrar. Los detalles de cada acción se concretan en la SPEC del módulo. Gestión de usuarios/permisos y operaciones irreversibles requieren permisos especiales independientes.
- Cuenta desactivada no inicia sesión; sus sesiones y dispositivos de confianza quedan revocados. Suspensión de empresa también revoca sesiones según STK-004.
- Cierre por inactividad: las empresas nuevas empiezan en 120 minutos. El administrador puede elegir múltiplos de 30 minutos, sin máximo, o `Nunca`; esta opción significa que Stockly no aplica vencimiento por inactividad ni absoluto. La política nueva se evalúa en la siguiente actividad de cada sesión; si la inactividad ya supera el nuevo límite, se cierra esa sesión. El cierre manual, cambio o recuperación de contraseña, desactivación, revocación y suspensión también invalidan el acceso. El usuario o el navegador todavía pueden borrar la cookie o los datos del sitio.
- Registrar en bitácora actor, empresa, acción, tipo/ID del objetivo y fecha para las acciones indicadas en el flujo. No guardar contraseñas, códigos, tokens ni cookies. Solo el administrador fijo de esa misma empresa puede consultar eventos; el tenant se deriva de la sesión.
- El código por correo es una comprobación de dispositivo de menor protección; no es un segundo factor robusto. Riesgo aceptado y registrado en ADR-003.

## 6. Resultado esperado

Solo cuentas activas y autenticadas acceden a funciones autorizadas de su empresa. Administradores y trabajadores quedan aislados; las credenciales temporales y códigos no quedan expuestos; los permisos actualizados rigen en servidor.

## 7. Errores / Casos especiales

- Credenciales inválidas, cuenta desactivada o empresa suspendida: mensaje seguro y genérico; no crear sesión.
- Código incorrecto, vencido, ya usado o demasiados intentos: no autenticar; informar cómo reintentar cuando sea posible.
- Fallo o demora del correo: no confirmar la acción como exitosa; permitir reintento sujeto a límites.
- Enlace de recuperación inválido, vencido o usado: no cambiar la contraseña; solicitar un enlace nuevo.
- Si una contraseña temporal vence antes de verificar el correo, el trabajador solicita reemisión al administrador; no se envía enlace a un correo no verificado. SaaS atiende al administrador inicial.
- Usuario/correo duplicado, rol de otra empresa o rol no asignable: rechazar sin crear cuenta parcial.
- No permitir desactivar al administrador inicial ni borrar el último administrador activo.
- Sesiones `Nunca` pueden permanecer utilizables hasta revocación; pérdida de dispositivo requiere cerrar sesión o desactivar la cuenta.
- Si se pierde la cookie, se borran datos del navegador o se revoca la sesión, solicitar nuevo ingreso y aplicar de nuevo la verificación de dispositivo correspondiente.
- El acceso y autorización sin conexión se definen en STK-014; STK-005 no permite login, recuperación, cambios de cuenta ni permisos sin conexión.

## 8. Criterios de aceptación

- [ ] AC-01 Ingreso por usuario o correo aplica contraseña, estado de cuenta/empresa y contraseña temporal; no crea sesión ante fallo.
- [ ] AC-02 Dispositivo no recordado requiere código de un solo uso según tiempos e intentos acordados; recordar dura 30 días.
- [ ] AC-03 Recuperación no revela existencia de cuenta; enlace de un uso de 30 minutos cambia la contraseña y revoca sesiones previas.
- [ ] AC-04 Alta y reemisión de contraseña temporal de trabajador conservan 24 horas, un solo uso y cambio obligatorio; el valor se muestra una vez. La reemisión revoca sesiones y dispositivos. El administrador inicial conserva provisión STK-004 y recuperación administrada por SaaS.
- [ ] AC-05 Desactivar trabajador revoca sesiones y dispositivos; la cuenta conserva trazabilidad.
- [ ] AC-06 Administrador asigna un único rol por trabajador; niveles y permisos especiales respetan el catálogo vigente y deniegan por defecto.
- [ ] AC-07 Roles pertenecen a una empresa; un rol asignado no puede eliminarse; usuarios y IDs no cruzan límites entre empresas.
- [ ] AC-08 Solo existe el administrador inicial de empresa creado por SaaS en este alcance; no puede desactivarse ni quedar sin acceso.
- [ ] AC-09 Empresas nuevas usan 120 minutos; el administrador puede configurar múltiplos de 30 o «Nunca», aplica a sesiones actuales y futuras y persiste entre aperturas del navegador.
- [ ] AC-10 Cerrar sesión, restablecer contraseña, desactivar cuenta, revocar acceso o suspender empresa invalida sesiones; cookie HttpOnly/Secure, CSRF y autorización en servidor.
- [ ] AC-11 Flujos aprobados funcionan en 375 y 1440 px, con teclado, foco visible, errores accesibles y estados de carga/éxito/error.
- [ ] AC-12 Comportamiento offline de autenticación queda fuera de STK-005 y cubierto al especificar STK-014.
- [ ] AC-13 Las acciones de gestión indicadas generan auditoría sin secretos; solo el administrador fijo consulta eventos de su propia empresa.

## 9. Fuera de alcance

- Autorregistro, alta de empresas, gestión de más administradores por empresa y autenticación operativa sin conexión.
- Catálogo de productos, ventas, inventario y permisos de negocio específicos no definidos por sus propias SPEC.
- Proveedor de correo concreto, despliegue y configuración productiva.
- Inicio de sesión social, biometría, passkeys o app autenticadora.
