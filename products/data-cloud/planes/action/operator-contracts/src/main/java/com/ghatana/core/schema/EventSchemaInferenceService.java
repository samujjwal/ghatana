/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.core.schema;

import java.util.List;
import java.util.Map;

/**
 * Service for inferring event schemas from sample events.
 * <p>
 * Analyzes the first N events to infer field types, required fields, and constraints.
 * Can be enhanced with ML-based schema learning in the future.
 *
 * @doc.type interface
 * @doc.purpose Infer event schemas from sample data
 * @doc.layer core
 * @doc.pattern Service
 */
public interface EventSchemaInferenceService {

    /**
     * Infer a schema from sample events.
     *
     * @param eventType the event type being analyzed
     * @param sampleEvents list of sample events (maps of field names to values)
     * @return inferred schema
     */
    EventSchema inferSchema(String eventType, List<Map<String, Object>> sampleEvents);

    /**
     * Validate an event against an inferred schema.
     *
     * @param event the event to validate
     * @param schema the schema to validate against
     * @return validation result
     */
    ValidationResult validateEvent(Map<String, Object> event, EventSchema schema);

    /**
     * Suggest schema improvements based on validation errors.
     *
     * @param schema the current schema
     * @param validationErrors list of validation errors
     * @return suggested schema improvements
     */
    EventSchema suggestImprovements(EventSchema schema, List<String> validationErrors);

    /**
     * Inferred event schema.
     */
    record EventSchema(
        String eventType,
        Map<String, FieldDefinition> fields,
        List<String> requiredFields,
        SchemaConstraints constraints
    ) {}

    /**
     * Field definition with type and constraints.
     */
    record FieldDefinition(
        String name,
        FieldType type,
        boolean required,
        boolean nullable,
        Object defaultValue,
        Map<String, Object> constraints
    ) {}

    /**
     * Field type enumeration.
     */
    enum FieldType {
        STRING,
        NUMBER,
        INTEGER,
        BOOLEAN,
        DATE,
        DATETIME,
        OBJECT,
        ARRAY,
        UNKNOWN
    }

    /**
     * Schema-level constraints.
     */
    record SchemaConstraints(
        int maxFieldCount,
        int maxPayloadSize,
        boolean allowUnknownFields
    ) {}

    /**
     * Validation result.
     */
    record ValidationResult(
        boolean valid,
        List<String> errors,
        List<String> warnings
    ) {}
}
