package com.ghatana.phr.hie;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @doc.type class
 * @doc.purpose Transforms patient summary FHIR bundles into HL7 v2 messages for Nepal HIE submission
 * @doc.layer product
 * @doc.pattern Translator
 */
public final class NepalHieMessageBuilder {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter HL7_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

    public String buildPatientSummaryMessage(String patientId, String correlationId, String collectionBundleJson, NepalHieConfig config) {
        try {
            JsonNode bundle = OBJECT_MAPPER.readTree(collectionBundleJson);
            String patientName = patientId;
            StringBuilder obxSegments = new StringBuilder();
            StringBuilder rxeSegments = new StringBuilder();
            StringBuilder zimSegments = new StringBuilder();
            int obxIndex = 1;
            int rxeIndex = 1;
            int zimIndex = 1;

            for (JsonNode entry : bundle.path("entry")) {
                JsonNode resource = entry.path("resource");
                String resourceType = resource.path("resourceType").asText();
                switch (resourceType) {
                    case "Patient" -> patientName = textValue(resource.path("name").path(0).path("text"), patientId);
                    case "Observation" -> obxSegments.append("OBX|")
                        .append(obxIndex++)
                        .append("|TX|")
                        .append(escape(textValue(resource.path("code").path("text"), "Observation")))
                        .append("||")
                        .append(escape(textValue(resource.path("valueQuantity").path("value"), "")))
                        .append(' ')
                        .append(escape(textValue(resource.path("valueQuantity").path("unit"), "")))
                        .append("|||||F\r");
                    case "MedicationRequest" -> rxeSegments.append("RXE|")
                        .append(rxeIndex++)
                        .append("||")
                        .append(escape(textValue(resource.path("medicationCodeableConcept").path("text"), "Medication")))
                        .append("||")
                        .append(escape(textValue(resource.path("dosageInstruction").path(0).path("text"), "")))
                        .append("\r");
                    case "Immunization" -> zimSegments.append("ZIM|")
                        .append(zimIndex++)
                        .append("||")
                        .append(escape(textValue(resource.path("vaccineCode").path("text"), "Immunization")))
                        .append("||")
                        .append(escape(textValue(resource.path("occurrenceDateTime"), "")))
                        .append("\r");
                    default -> {
                        // Ignore unsupported resource types in the export summary.
                    }
                }
            }

            String controlId = controlId(patientId, correlationId);
            return "MSH|^~\\&|" + escape(config.sendingApplication()) + '|' + escape(config.sendingFacility()) + '|'
                + escape(config.receivingApplication()) + '|' + escape(config.receivingFacility()) + '|'
                + HL7_TIMESTAMP.format(Instant.now()) + "||ADT^A08|" + controlId + "|P|2.5\r"
                + "PID|||" + escape(patientId) + "||" + escape(patientName) + "\r"
                + "PV1|1|O|PHR-PORTAL\r"
                + "OBX|0|ED|FHIR-BUNDLE||" + escape(collectionBundleJson) + "|||||F\r"
                + obxSegments
                + rxeSegments
                + zimSegments;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Unable to transform FHIR bundle into Nepal HIE HL7 payload", exception);
        }
    }

    private String controlId(String patientId, String correlationId) {
        String raw = patientId + '-' + correlationId;
        return raw.replaceAll("[^A-Za-z0-9]", "");
    }

    private String escape(String value) {
        return value
            .replace("\\", "\\E\\")
            .replace("|", "\\F\\")
            .replace("^", "\\S\\")
            .replace("&", "\\T\\")
            .replace("\r", " ")
            .replace("\n", " ");
    }

    private String textValue(JsonNode node, String defaultValue) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return defaultValue;
        }
        String value = node.asText();
        return value == null || value.isBlank() ? defaultValue : value;
    }
}