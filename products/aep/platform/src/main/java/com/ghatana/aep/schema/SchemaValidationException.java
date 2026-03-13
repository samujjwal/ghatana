/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.schema;

import com.ghatana.platform.schema.ValidationResult;

import java.util.Objects;

/**
 * Thrown when an AEP event payload fails schema validation and enforcement is enabled.
 *
 * <p>Carries structured {@link ValidationResult} details so callers can return
 * a structured error response without re-parsing the exception message.
 *
 * @doc.type class
 * @doc.purpose Structured exception for AEP schema validation failures
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public final class SchemaValidationException extends RuntimeException {

    private final String eventType;
    private final String schemaVersion;
    private final ValidationResult result;

    /**
     * @param eventType     logical event type
     * @param schemaVersion schema version that was checked
     * @param result        full validation result (never valid)
     */
    public SchemaValidationException(
            String eventType, String schemaVersion, ValidationResult result) {
        super(buildMessage(eventType, schemaVersion, result));
        this.eventType = Objects.requireNonNull(eventType);
        this.schemaVersion = Objects.requireNonNull(schemaVersion);
        this.result = Objects.requireNonNull(result);
    }

    /** @return logical event type that failed validation */
    public String getEventType() {
        return eventType;
    }

    /** @return schema version that was checked against */
    public String getSchemaVersion() {
        return schemaVersion;
    }

    /** @return full validation result including all field-level errors */
    public ValidationResult getResult() {
        return result;
    }

    private static String buildMessage(String eventType, String version, ValidationResult result) {
        StringBuilder sb = new StringBuilder("AEP schema validation failed:")
                .append(" eventType=").append(eventType)
                .append(" version=").append(version)
                .append(" errorCount=").append(result.errors().size());
        if (!result.errors().isEmpty()) {
            sb.append(" firstError=[").append(result.firstErrorPath())
              .append(": ").append(result.firstErrorMessage()).append("]");
        }
        return sb.toString();
    }
}
