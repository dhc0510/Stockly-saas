package com.stockly.billing;

import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import org.springframework.stereotype.Component;

/** Valida cada XML contra el XSD oficial v4.4 antes de firmarlo, sin acceder a recursos externos. */
@Component
public class FiscalSchemas {
    private static final Map<String, String> FILES = Map.of(
        "01", "hacienda/v4.4/FacturaElectronica_V4.4.xsd",
        "03", "hacienda/v4.4/NotaCreditoElectronica_V4.4.xsd",
        "04", "hacienda/v4.4/TiqueteElectronico_V4.4.xsd");
    private final Map<String, Schema> schemas = new ConcurrentHashMap<>();

    /** true si existe el XSD oficial del tipo; el del tiquete aún no está publicado en el sitio oficial. */
    public boolean available(String docType) {
        String file = FILES.get(docType);
        return file != null && getClass().getClassLoader().getResource(file) != null;
    }

    public void validate(String docType, byte[] xml) {
        try {
            schema(docType).newValidator().validate(new StreamSource(new ByteArrayInputStream(xml)));
        } catch (org.xml.sax.SAXException e) {
            throw new IllegalArgumentException("El XML no cumple el esquema v4.4: " + e.getMessage(), e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("No se pudo validar el XML.", e);
        }
    }

    private Schema schema(String docType) {
        return schemas.computeIfAbsent(docType, type -> {
            var url = getClass().getClassLoader().getResource(FILES.get(type));
            if (url == null) throw new IllegalStateException("Falta el XSD oficial del tipo " + type + ".");
            try {
                var factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
                factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
                // Solo se permiten esquemas empacados con Stockly (file: o jar:), nunca descargas.
                factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file,jar:file,jar,nested");
                factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
                return factory.newSchema(url);
            } catch (org.xml.sax.SAXException e) {
                throw new IllegalStateException("No se pudo cargar el XSD " + FILES.get(type) + ".", e);
            }
        });
    }
}
