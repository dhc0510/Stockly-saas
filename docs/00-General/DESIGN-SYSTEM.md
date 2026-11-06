# DESIGN-SYSTEM.md — Stockly

## 1. Identidad visual

**Nombre del proyecto:** Stockly.

**Estilo visual:** SaaS B2B moderno, minimalista, tecnológico, elegante y funcional. Interfaz limpia, con jerarquía clara, alta densidad informativa controlada y prioridad en la productividad de los comercios.

**Personalidad de marca:** Profesional, confiable, precisa, eficiente, accesible, cercana e innovadora. Debe transmitir orden y control sin resultar complicada para propietarios y empleados de pequeños comercios costarricenses.

**Referencias visuales:** Las dos imágenes proporcionadas por el propietario: (1) paleta cromática de cinco colores y (2) logotipo Stockly con isotipo de caja cúbica naranja y logotipo tipográfico blanco sobre fondo negro. Son referencias visuales oficiales iniciales; no se han definido referencias externas.

**Logotipo:** Imagen de referencia proporcionada, con símbolo de caja cúbica naranja y texto «Stockly» blanco sobre negro. Archivo original vectorial, versiones horizontal/compacta, claro/oscuro, monocromática, favicon y zonas de seguridad: **Por definir y preparar a partir del arte original autorizado**. No reconstruir ni alterar el logotipo sin aprobación.

---

## 2. Paleta de colores

| Elemento | Color HEX |
|---|---|
| Color primario de marca | `#F97316` |
| Color secundario de marca | `#18181B` |
| Color de acento suave | `#FDBA74` |
| Fondo principal (tema claro) | `#FFFFFF` |
| Fondo secundario (tema claro) | `#F4F4F5` |
| Texto principal (tema claro) | `#18181B` |
| Texto secundario (tema claro) | `#52525B` |
| Bordes (tema claro) | `#E4E4E7` |
| Éxito | `#15803D` |
| Advertencia | `#A16207` |
| Error | `#B91C1C` |

**Colores oficiales extraídos de la referencia:** `#18181B`, `#F97316`, `#FDBA74`, `#F4F4F5`, `#FFFFFF`. Los tonos para texto secundario, bordes y estados semánticos son **propuestas complementarias**, no colores del logotipo, sujetas a validación visual y de contraste.

**Reglas generales:**
- Preferir una interfaz de trabajo clara: blanco como superficie principal, gris muy claro para fondos alternativos, carbón para textos y naranja para acciones y elementos de identidad.
- Reservar `#F97316` para acciones primarias, indicadores activos y énfasis relevantes; evitar saturar tablas y paneles con naranja.
- Utilizar `#FDBA74` en fondos de acento y elementos decorativos suaves; no emplearlo como color de texto sobre blanco.
- Evitar texto blanco pequeño sobre `#F97316` sin comprobar contraste: la combinación no alcanza el contraste AA para texto normal. Preferir texto `#18181B` sobre el naranja, o un tono naranja más oscuro para botones con texto blanco, previa validación.
- Los estados de éxito, advertencia y error deben combinar color con iconos, etiquetas o mensajes; nunca depender solo del color.
- Un tema oscuro es una posibilidad futura, **por definir**. El fondo negro de la referencia del logotipo no obliga a que todo el panel sea oscuro.

---

## 3. Tipografía

**Fuente principal:** **Inter** (preferida para todo el producto). Fuente sans serif de excelente legibilidad en interfaces densas, tablas, formularios, códigos, cantidades y reportes. Cargar únicamente pesos y variantes necesarios.

**Fuente secundaria:** No requerida. Para códigos SKU y datos monoespaciados, usar una fuente monoespaciada del sistema solo cuando aporte claridad. Para cantidades y valores, preferir cifras tabulares (`font-variant-numeric: tabular-nums`).

**Estilo tipográfico:** Moderno, sobrio, preciso, limpio, funcional y accesible. Evitar fuentes ornamentales, ultrafinas o excesivamente condensadas.

**Jerarquía general:**
- Título principal de página: 28–32 px, peso 650–700.
- Títulos de sección: 20–24 px, peso 600–700.
- Subtítulos y encabezados de tarjetas: 16–18 px, peso 600.
- Texto habitual de interfaz: 14–16 px, peso 400–500; preferir 16 px para lectura y formularios móviles.
- Etiquetas y metadatos: 12–14 px, solo si el contraste y la legibilidad son suficientes.
- Cifras KPI: 24–32 px, peso 650–700, con números tabulares cuando corresponda.
- Interlineado: aproximadamente 1.4–1.6 para texto de lectura; más compacto para encabezados.

Los tamaños son una guía inicial del sistema, ajustables y verificables durante los mockups.

---

## 4. Estilo visual general

**Bordes y esquinas:** Ligeramente redondeados. Radio base de 8 px para campos, botones y tarjetas; 10–12 px para contenedores amplios. Evitar esquinas exageradamente redondeadas en tablas de trabajo.

**Sombras:** Muy suaves y discretas, solo para separar superficies elevadas como menús, modales y popovers. Preferir bordes sutiles en tablas y tarjetas.

**Espaciados:** Escala consistente de 4/8/12/16/24/32 px. Equilibrio entre respiración visual y densidad de información; no desperdiciar espacio en pantallas operativas.

**Iconografía:** Lucide, estilo outline, trazo consistente y tamaños habituales de 16–20 px. Acompañar acciones críticas de etiquetas de texto cuando sea necesario.

**Botones y controles:** Botón primario naranja con texto de contraste verificado; secundarios neutros; acciones destructivas diferenciadas; campos con etiquetas visibles, ayudas contextuales, estados de foco, error y deshabilitado. Áreas táctiles adecuadas. Confirmación para ajustes y eliminaciones con consecuencias relevantes.

**Imágenes y recursos gráficos:** Usar el logotipo original y recursos gráficos sobrios relacionados con inventario, productos, almacenes y comercio. Priorizar iconos y datos reales sobre ilustraciones decorativas. Evitar fotografías de stock genéricas, gráficos 3D excesivos y recursos que parezcan plantillas artificiales.

---

## 5. Principios generales de UI/UX

**Diseño responsive:** Enfoque Mobile-First, adaptable a móviles, tabletas y escritorios. Diseñar mockups de referencia de 375 px y 1440 px cuando corresponda. En escritorio, facilitar tablas, filtros y operaciones masivas; en móvil, priorizar búsqueda, consulta de existencias, escaneo cuando exista, entradas/salidas y tareas frecuentes. No ocultar funciones esenciales solo por tamaño de pantalla.

**Accesibilidad:** Objetivo WCAG 2.2 AA. Contraste mínimo 4.5:1 para texto normal y 3:1 para texto grande y componentes relevantes, según el criterio aplicable; foco visible, navegación por teclado, etiquetas accesibles, mensajes de error claros, tamaños táctiles adecuados y no depender exclusivamente del color. Validar combinaciones reales de color, no solo la paleta aislada.

**Interacciones y animaciones:** Sutiles, breves y funcionales (aproximadamente 120–200 ms cuando proceda). Respetar preferencias de movimiento reducido. Evitar animaciones que retrasen el registro de movimientos o la consulta de stock.

**Experiencia de usuario:** Simplicidad, rapidez, consistencia, claridad, prevención de errores, retroalimentación inmediata y facilidad de aprendizaje para personal no técnico. Las operaciones de inventario deben mostrar producto, ubicación, cantidad, tipo de movimiento y resultado de manera inequívoca. Diseñar estados vacíos, carga, error, confirmación y éxito. Favorecer búsqueda rápida, filtros útiles, tablas legibles y flujos eficientes.

---

## 6. Reglas para los agentes IA

1. Este documento es la fuente de verdad de la identidad visual global del proyecto.
2. Todas las interfaces deben respetar las decisiones visuales definidas aquí.
3. El diseño específico de cada pantalla o funcionalidad se define mediante sus respectivos mockups.
4. Los mockups deben respetar la identidad visual global.
5. Los SPEC pueden hacer referencia a los mockups y a este documento.
6. Se deben reutilizar estilos, componentes y patrones visuales existentes siempre que sea posible.
7. No modificar decisiones globales de diseño sin aprobación.
8. Si un mockup contradice este documento, no asumir cuál prevalece: señalar la contradicción y solicitar una decisión.
9. Mantener consistencia de tipografía Inter, escala de espaciados, radios, iconografía y estados de interfaz.
10. Validar contraste y accesibilidad antes de aprobar componentes; no suponer que todos los colores de marca sirven para texto o botones.
11. Distinguir los cinco colores proporcionados por el propietario de los colores funcionales propuestos; solicitar aprobación antes de fijar estos últimos como definitivos.
12. No generar un nuevo logotipo ni alterar el existente sin aprobación.
13. Seguir el flujo SDD: propuesta y mockup cuando corresponda, SPEC, PLAN y TASKS, aprobación, implementación, pruebas y revisión. No programar interfaces sin autorización.

---

## 7. Información del documento

**Estado:** Borrador, pendiente de aprobación del propietario.

**Última actualización:** 2026-11-05.

**Observaciones:** Nombre Stockly confirmado. Paleta principal y logotipo tomados de las referencias visuales proporcionadas. Tipografía Inter recomendada. Pendiente: archivo maestro/vectorial del logotipo, variantes oficiales, aprobación de colores semánticos y confirmación de tema claro como predeterminado.
