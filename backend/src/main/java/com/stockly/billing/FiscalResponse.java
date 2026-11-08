package com.stockly.billing;

import java.io.ByteArrayInputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

/** Lee el motivo del MensajeHacienda (DetalleMensaje) sin resolver entidades ni recursos externos. */
final class FiscalResponse {
    private FiscalResponse() {}

    static String reason(byte[] xml) {
        if (xml == null) return "Hacienda rechazó el comprobante.";
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var nodes = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml)).getElementsByTagNameNS("*", "DetalleMensaje");
            if (nodes.getLength() == 0) return "Hacienda rechazó el comprobante.";
            String text = nodes.item(0).getTextContent().strip().replaceAll("\\s+", " ");
            return text.length() > 1000 ? text.substring(0, 1000) : text;
        } catch (Exception e) {
            return "Hacienda rechazó el comprobante.";
        }
    }
}
