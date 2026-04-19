/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.core.schema;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Default implementation of event schema inference service.
 * <p>
 * Analyzes sample events to infer field types, required fields, and constraints.
 * Uses statistical analysis to determine field characteristics.
 *
 * @doc.type class
 * @doc.purpose Rule-based event schema inference
 * @doc.layer product
 * @doc.pattern Strategy
 */
public class DefaultEventSchemaInferenceService implements EventSchemaInferenceService {

    private static final Logger logger = LoggerFactory.getLogger(DefaultEventSchemaInferenceService.class);

    private static final int MIN_SAMPLE_SIZE = 5;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    @Override
    public EventSchema inferSchema(String eventType, List<Map<String, Object>> sampleEvents) {
        if (sampleEvents == null || sampleEvents.isEmpty()) {
            return createEmptySchema(eventType);
        }

        logger.info("Inferring schema for eventType={} from {} sample events", eventType, sampleEvents.size());

        Map<String, FieldStats> fieldStats = analyzeFields(sampleEvents);
        Map<String, FieldDefinition> fields = inferFieldDefinitions(fieldStats);
        List<String> requiredFields = inferRequiredFields(fieldStats);
        SchemaConstraints constraints = inferConstraints(sampleEvents);

        return new EventSchema(eventType, fields, requiredFields, constraints);
    }

    @Override
    public ValidationResult validateEvent(Map<String, Object> event, EventSchema schema) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Check required fields
        for (String requiredField : schema.requiredFields()) {
            if (!event.containsKey(requiredField)) {
                errors.add("Missing required field: " + requiredField);
            }
        }

        // Check field types
        for (Map.Entry<String, Object> entry : event.entrySet()) {
            String fieldName = entry.getKey();
            Object value = entry.getValue();
            FieldDefinition fieldDef = schema.fields().get(fieldName);

            if (fieldDef != null) {
                if (!validateFieldType(value, fieldDef.type())) {
                    errors.add("Field '" + fieldName + "' has invalid type. Expected: " + fieldDef.type() + ", Got: " + inferType(value));
                }
            } else if (!schema.constraints().allowUnknownFields()) {
                warnings.add("Unknown field: " + fieldName);
            }
        }

        // Check constraints
        if (event.size() > schema.constraints().maxFieldCount()) {
            errors.add("Event exceeds maximum field count: " + schema.constraints().maxFieldCount());
        }

        boolean valid = errors.isEmpty();
        return new ValidationResult(valid, errors, warnings);
    }

    @Override
    public EventSchema suggestImprovements(EventSchema schema, List<String> validationErrors) {
        Map<String, FieldDefinition> improvedFields = new HashMap<>(schema.fields());
        List<String> improvedRequired = new ArrayList<>(schema.requiredFields());

        // If there are many unknown field errors, suggest allowing unknown fields
        if (validationErrors.stream().anyMatch(e -> e.contains("Unknown field"))) {
            logger.info("Suggesting to allow unknown fields based on validation errors");
        }

        // If there are missing required field errors, consider making them optional
        if (validationErrors.stream().anyMatch(e -> e.contains("Missing required"))) {
            logger.info("Some required fields are frequently missing; consider making them optional");
        }

        SchemaConstraints improvedConstraints = new SchemaConstraints(
            schema.constraints().maxFieldCount(),
            schema.constraints().maxPayloadSize(),
            true // Suggest allowing unknown fields
        );

        return new EventSchema(schema.eventType(), improvedFields, improvedRequired, improvedConstraints);
    }

    private Map<String, FieldStats> analyzeFields(List<Map<String, Object>> sampleEvents) {
        Map<String, FieldStats> stats = new HashMap<>();

        for (Map<String, Object> event : sampleEvents) {
            for (Map.Entry<String, Object> entry : event.entrySet()) {
                String fieldName = entry.getKey();
                Object value = entry.getValue();

                FieldStats fieldStat = stats.computeIfAbsent(fieldName, k -> new FieldStats());
                fieldStat.recordValue(value);
            }
        }

        return stats;
    }

    private Map<String, FieldDefinition> inferFieldDefinitions(Map<String, FieldStats> fieldStats) {
        Map<String, FieldDefinition> fields = new HashMap<>();

        for (Map.Entry<String, FieldStats> entry : fieldStats.entrySet()) {
            String fieldName = entry.getKey();
            FieldStats stats = entry.getValue();

            FieldType type = stats.inferType();
            boolean required = stats.isAlwaysPresent();
            boolean nullable = stats.hasNulls();
            Object defaultValue = stats.inferDefaultValue();
            Map<String, Object> constraints = inferFieldConstraints(stats);

            fields.put(fieldName, new FieldDefinition(fieldName, type, required, nullable, defaultValue, constraints));
        }

        return fields;
    }

    private List<String> inferRequiredFields(Map<String, FieldStats> fieldStats) {
        return fieldStats.entrySet().stream()
            .filter(entry -> entry.getValue().isAlwaysPresent())
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }

    private SchemaConstraints inferConstraints(List<Map<String, Object>> sampleEvents) {
        int maxFieldCount = sampleEvents.stream()
            .mapToInt(Map::size)
            .max()
            .orElse(50);

        int maxPayloadSize = sampleEvents.stream()
            .mapToInt(event -> event.toString().length())
            .max()
            .orElse(10000);

        int allowedFieldCount = Math.max(5, maxFieldCount + 5);
        return new SchemaConstraints(allowedFieldCount, maxPayloadSize + 1000, false);
    }

    private Map<String, Object> inferFieldConstraints(FieldStats stats) {
        Map<String, Object> constraints = new HashMap<>();

        if (stats.type == FieldType.NUMBER) {
            if (stats.minValue != null) {
                constraints.put("min", stats.minValue);
            }
            if (stats.maxValue != null) {
                constraints.put("max", stats.maxValue);
            }
        }

        if (stats.type == FieldType.STRING) {
            if (stats.maxLength > 0) {
                constraints.put("maxLength", stats.maxLength);
            }
            if (stats.minLength > 0) {
                constraints.put("minLength", stats.minLength);
            }
            if (!stats.uniqueValues.isEmpty() && stats.uniqueValues.size() < 20) {
                constraints.put("enum", new ArrayList<>(stats.uniqueValues));
            }
        }

        return constraints;
    }

    private boolean validateFieldType(Object value, FieldType expectedType) {
        FieldType actualType = inferType(value);
        return actualType == expectedType || actualType == FieldType.UNKNOWN || expectedType == FieldType.UNKNOWN;
    }

    private FieldType inferType(Object value) {
        if (value == null) {
            return FieldType.UNKNOWN;
        }

        if (value instanceof Boolean) {
            return FieldType.BOOLEAN;
        }

        if (value instanceof Integer || value instanceof Long) {
            return FieldType.INTEGER;
        }

        if (value instanceof Float || value instanceof Double) {
            return FieldType.NUMBER;
        }

        if (value instanceof String) {
            String str = (String) value;
            if (isDateString(str)) {
                return FieldType.DATE;
            }
            if (isDateTimeString(str)) {
                return FieldType.DATETIME;
            }
            return FieldType.STRING;
        }

        if (value instanceof Map) {
            return FieldType.OBJECT;
        }

        if (value instanceof Collection) {
            return FieldType.ARRAY;
        }

        return FieldType.UNKNOWN;
    }

    private boolean isDateString(String str) {
        try {
            DATE_FORMATTER.parse(str);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private boolean isDateTimeString(String str) {
        try {
            Instant.parse(str);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private EventSchema createEmptySchema(String eventType) {
        return new EventSchema(eventType, Map.of(), List.of(), new SchemaConstraints(50, 10000, false));
    }

    private static class FieldStats {
        int totalCount = 0;
        int nullCount = 0;
        FieldType type = FieldType.UNKNOWN;
        Double minValue = null;
        Double maxValue = null;
        int minLength = Integer.MAX_VALUE;
        int maxLength = 0;
        Set<Object> uniqueValues = new HashSet<>();

        void recordValue(Object value) {
            totalCount++;
            
            if (value == null) {
                nullCount++;
                return;
            }

            FieldType valueType = inferType(value);
            if (type == FieldType.UNKNOWN) {
                type = valueType;
            }

            if (value instanceof Number) {
                double numValue = ((Number) value).doubleValue();
                if (minValue == null || numValue < minValue) {
                    minValue = numValue;
                }
                if (maxValue == null || numValue > maxValue) {
                    maxValue = numValue;
                }
            }

            if (value instanceof String) {
                int length = ((String) value).length();
                minLength = Math.min(minLength, length);
                maxLength = Math.max(maxLength, length);
                uniqueValues.add(value);
            }
        }

        FieldType inferType() {
            return type;
        }

        boolean isAlwaysPresent() {
            return nullCount == 0 && totalCount > 0;
        }

        boolean hasNulls() {
            return nullCount > 0;
        }

        Object inferDefaultValue() {
            if (nullCount > totalCount / 2) {
                return null;
            }
            if (!uniqueValues.isEmpty()) {
                return uniqueValues.iterator().next();
            }
            return null;
        }

        private FieldType inferType(Object value) {
            if (value instanceof Boolean) return FieldType.BOOLEAN;
            if (value instanceof Integer || value instanceof Long) return FieldType.INTEGER;
            if (value instanceof Float || value instanceof Double) return FieldType.NUMBER;
            if (value instanceof String) return FieldType.STRING;
            if (value instanceof Map) return FieldType.OBJECT;
            if (value instanceof Collection) return FieldType.ARRAY;
            return FieldType.UNKNOWN;
        }
    }
}
