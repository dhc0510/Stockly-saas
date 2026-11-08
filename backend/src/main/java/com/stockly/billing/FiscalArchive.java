package com.stockly.billing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * ADR-008: los comprobantes que llegaron a Hacienda se conservan 5 años desde su emisión aunque se borre la empresa.
 * Solo se archivan los aceptados y los rechazados: los pendientes o cancelados nunca tuvieron efecto fiscal.
 */
@Service
public class FiscalArchive {
    private static final Logger log = LoggerFactory.getLogger(FiscalArchive.class);
    static final int RETENTION_YEARS = 5;
    private final JdbcTemplate jdbc;

    FiscalArchive(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Copia los comprobantes de la empresa antes de borrarla. Debe correr en la misma transacción del borrado. */
    @Transactional(propagation = Propagation.MANDATORY)
    public int archiveCompany(String companyId) {
        return jdbc.update("""
            INSERT IGNORE INTO fiscal_archive(company_id,document_id,issuer_legal_name,issuer_id_type,issuer_id_number,doc_type,
              environment,consecutive,numeric_key,issued_at,status,signed_xml,pdf,response_xml,archived_at,retain_until)
            SELECT d.company_id,d.id,c.legal_name,c.identification_type,c.identification_normalized,d.doc_type,d.environment,
              d.consecutive,d.numeric_key,d.issued_at,d.status,d.signed_xml,d.pdf,d.response_xml,UTC_TIMESTAMP(6),
              d.issued_at + INTERVAL ? YEAR
            FROM fiscal_documents d JOIN companies c ON c.id=d.company_id
            WHERE d.company_id=? AND d.status IN ('ACCEPTED','REJECTED') AND d.signed_xml IS NOT NULL
            """, RETENTION_YEARS, companyId);
    }

    /** Elimina una vez al día lo que ya cumplió el plazo de conservación. */
    @Scheduled(cron = "${stockly.billing.archive-purge-cron:0 30 3 * * *}", zone = "America/Costa_Rica")
    public int purgeExpired() {
        int removed = jdbc.update("DELETE FROM fiscal_archive WHERE retain_until < UTC_TIMESTAMP(6) LIMIT 1000");
        if (removed > 0) log.info("Se eliminaron {} comprobantes archivados que cumplieron el plazo de conservación.", removed);
        return removed;
    }
}
