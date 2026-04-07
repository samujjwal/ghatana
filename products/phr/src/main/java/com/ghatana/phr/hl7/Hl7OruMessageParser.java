package com.ghatana.phr.hl7;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

/**
 * @doc.type class
 * @doc.purpose Parses inbound HL7 ORU^R01 lab messages into canonical PHR lab-result payloads
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class Hl7OruMessageParser {

    private static final DateTimeFormatter HL7_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public Hl7LabResultMessage parse(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("HL7 message is required");
        }

        Map<String, String[]> segments = new HashMap<>();
        for (String line : message.split("\\r?\\n")) {
            String[] parts = line.split("\\|", -1);
            if (parts.length > 0) {
                segments.put(parts[0], parts);
            }
        }

        String[] pid = requireSegment(segments, "PID");
        String[] obr = requireSegment(segments, "OBR");
        String[] obx = requireSegment(segments, "OBX");
        String[] msh = requireSegment(segments, "MSH");

        return new Hl7LabResultMessage(
            firstComponent(field(pid, 3)),
            field(obr, 3),
            field(msh, 3),
            firstComponent(field(obx, 3)),
            secondComponent(field(obx, 3), field(obr, 4)),
            parseDecimal(field(obx, 5)),
            field(obx, 6),
            field(obx, 7),
            field(obx, 8),
            field(obx, 11),
            parseTimestamp(field(obx, 14), field(obr, 7))
        );
    }

    private String[] requireSegment(Map<String, String[]> segments, String segmentName) {
        String[] segment = segments.get(segmentName);
        if (segment == null) {
            throw new IllegalArgumentException("Missing required HL7 segment: " + segmentName);
        }
        return segment;
    }

    private String field(String[] segment, int index) {
        return index < segment.length ? segment[index] : "";
    }

    private String firstComponent(String compositeField) {
        if (compositeField == null || compositeField.isBlank()) {
            return compositeField;
        }
        return compositeField.split("\\^", -1)[0];
    }

    private String secondComponent(String preferredCompositeField, String fallbackCompositeField) {
        String preferred = component(preferredCompositeField, 1);
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return component(fallbackCompositeField, 1);
    }

    private String component(String compositeField, int index) {
        if (compositeField == null || compositeField.isBlank()) {
            return null;
        }
        String[] values = compositeField.split("\\^", -1);
        return index < values.length ? values[index] : null;
    }

    private BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("HL7 OBX-5 value is required for lab import");
        }
        return new BigDecimal(value);
    }

    private Instant parseTimestamp(String preferred, String fallback) {
        String candidate = preferred != null && !preferred.isBlank() ? preferred : fallback;
        if (candidate == null || candidate.isBlank()) {
            return Instant.now();
        }
        try {
            return LocalDateTime.parse(candidate, HL7_TIMESTAMP).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException exception) {
            return Instant.now();
        }
    }
}