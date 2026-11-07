# STK-008 — Corrección visual y revisión de requisitos

2026-11-07. Solicitado por el propietario para corregir y publicar.

Corrección: la regla .receipt-note posterior anulaba el margen de .receipt-draft-notice. Se aumentó especificidad y se dejó separación de 20 px respecto al panel de búsqueda. El aviso usa grid con 12 px de separación interna. En móvil, Continuar/Descartar y Cancelar/Confirmar descarte se distribuyen en dos columnas iguales, admiten texto en varias líneas y tienen altura mínima de 44 px.

Verificación Chromium: 320/375/390/1440 px, separación medida 20 px, botones >=44 px y sin desbordamiento horizontal. Capturas en screenshots/draft-layout-*.png. Regresión reproducible: frontend/e2e/receipt-layout.spec.ts. La comprobación visual usa sesión y respuestas simuladas solo dentro de pruebas; no cambia el acceso real.

Build frontend correcto y 12 pruebas unitarias aprobadas. Backend: ReceiptLocalIntegrationTest, CatalogLocalIntegrationTest, CompanyValidationTest y DeletionJournalTest ejecutadas sin fallos en BD aislada stockly_review_checks (MySQL 8.4), sin usar la base del negocio.

Revisión de lo solicitado en la tarjeta: existe recepción de artículos STK-006, movimiento trazable, incremento de saldos y contrato compartido con salidas/historial. Las pruebas de entradas verifican atomicidad, idempotencia concurrente, histórico, anulación sin negativos, permisos y aislamiento. La asignación Gabo y el orden de coordinación de Trello son planificación, no un criterio ejecutable del producto.

No se declara cumplimiento total de todos los escenarios: siguen pendientes lector físico e iPhone físico (incluido teclado real), ya identificados en la validación previa. No se modifican reglas funcionales ni se marcan criterios sin evidencia.