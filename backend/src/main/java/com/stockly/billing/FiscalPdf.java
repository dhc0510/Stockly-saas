package com.stockly.billing;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.openpdf.text.*;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

/** Representación gráfica del comprobante (SPEC §11). Se arma desde el borrador firmado, nunca desde datos editables. */
@Component
class FiscalPdf {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final java.util.Map<String, String> NAMES = java.util.Map.of(
        "01", "Factura electrónica", "03", "Nota de crédito electrónica", "04", "Tiquete electrónico");
    private static final java.util.Map<String, String> METHODS = java.util.Map.of(
        "CASH", "Efectivo", "CARD", "Tarjeta", "SINPE", "SINPE Móvil", "TRANSFER", "Transferencia");

    byte[] render(FiscalDraft draft, String environment) {
        var money = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-CR"));
        var out = new ByteArrayOutputStream();
        var document = new Document(PageSize.LETTER, 40, 40, 40, 40);
        PdfWriter.getInstance(document, out);
        document.open();
        var title = new Font(Font.HELVETICA, 16, Font.BOLD);
        var bold = new Font(Font.HELVETICA, 9, Font.BOLD);
        var normal = new Font(Font.HELVETICA, 9);
        if ("TEST".equals(environment)) {
            var warning = new Paragraph("AMBIENTE DE PRUEBAS · SIN VALIDEZ FISCAL", new Font(Font.HELVETICA, 11, Font.BOLD, new Color(55, 48, 163)));
            warning.setSpacingAfter(6);
            document.add(warning);
        }
        document.add(new Paragraph(NAMES.get(draft.docType()), title));
        document.add(new Paragraph("Consecutivo " + draft.consecutive() + "   ·   " + DATE.format(draft.issuedAt().atZone(FiscalNumbers.COSTA_RICA)), normal));
        document.add(new Paragraph("Clave " + draft.key(), normal));
        document.add(Chunk.NEWLINE);

        var parties = new PdfPTable(2);
        parties.setWidthPercentage(100);
        parties.addCell(partyCell("Emisor", draft.issuer(), bold, normal));
        parties.addCell(draft.receiver() == null ? cell("Receptor\nConsumidor final", normal) : partyCell("Receptor", draft.receiver(), bold, normal));
        document.add(parties);
        if (draft.reference() != null) {
            document.add(new Paragraph("Anula el comprobante " + draft.reference().key() + ". Motivo: " + draft.reference().reason(), normal));
        }
        document.add(Chunk.NEWLINE);

        var lines = new PdfPTable(new float[]{4, 1.3f, 1.4f, 0.9f, 1.6f, 1.4f, 1.6f});
        lines.setWidthPercentage(100);
        for (String header : new String[]{"Detalle", "CABYS", "Cantidad", "IVA", "Sin IVA", "IVA", "Total"}) lines.addCell(cell(header, bold));
        BigDecimal subtotal = BigDecimal.ZERO, tax = BigDecimal.ZERO;
        for (var line : draft.lines()) {
            var b = FiscalDraft.breakdown(line);
            subtotal = subtotal.add(b.subtotal());
            tax = tax.add(b.tax());
            lines.addCell(cell(line.detail(), normal));
            lines.addCell(cell(line.cabys(), normal));
            lines.addCell(cell(line.quantity().stripTrailingZeros().toPlainString() + " " + FiscalDraft.UNITS.get(line.unit()), normal));
            lines.addCell(cell(b.rate().stripTrailingZeros().toPlainString() + " %", normal));
            lines.addCell(cell(money.format(b.subtotal()), normal));
            lines.addCell(cell(money.format(b.tax()), normal));
            lines.addCell(cell(money.format(b.total()), normal));
        }
        document.add(lines);
        document.add(Chunk.NEWLINE);
        document.add(new Paragraph("Subtotal sin IVA: " + money.format(subtotal), normal));
        document.add(new Paragraph("IVA: " + money.format(tax), normal));
        document.add(new Paragraph("Total: " + money.format(subtotal.add(tax)), new Font(Font.HELVETICA, 12, Font.BOLD)));
        if (!draft.payments().isEmpty()) {
            var paid = new StringBuilder("Medios de pago: ");
            draft.payments().forEach((method, amount) -> paid.append(METHODS.get(method)).append(' ').append(money.format(amount)).append("   "));
            document.add(new Paragraph(paid.toString().strip(), normal));
        }
        document.add(Chunk.NEWLINE);
        document.add(new Paragraph("Emitida conforme a la resolución de comprobantes electrónicos vigente del Ministerio de Hacienda. Versión 4.4.",
            new Font(Font.HELVETICA, 7, Font.ITALIC, Color.GRAY)));
        document.close();
        return out.toByteArray();
    }

    private static PdfPCell partyCell(String label, FiscalDraft.Party party, Font bold, Font normal) {
        var phrase = new Phrase();
        phrase.add(new Chunk(label + "\n", bold));
        phrase.add(new Chunk(party.name() + "\nIdentificación " + party.idNumber()
            + (party.email() == null ? "" : "\n" + party.email()), normal));
        var cell = new PdfPCell(phrase);
        cell.setPadding(6);
        return cell;
    }

    private static PdfPCell cell(String text, Font font) {
        var cell = new PdfPCell(new Phrase(text == null ? "" : text, font));
        cell.setPadding(4);
        return cell;
    }
}
