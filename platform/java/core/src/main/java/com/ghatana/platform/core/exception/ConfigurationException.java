/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.core.exception;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Exception thrown when a configuration error is detected.
 *
 * <p>Canonical exception for configuration errors across all platform and product
 * modules. Provides factory methods for common patterns (missing fields, invalid values).
 * Replaces deprecated copies in config.runtime.engine, data-cloud, and yappc.</p>
 *
 * @doc.type class
 * @doc.purpose Canonical configuration error exception
 * @doc.layer platform
 * @doc.pattern Exception
 */
public class ConfigurationException extends PlatformException {

    private final @Nullable String fieldName;
    private final @Nullable Object invalidValue;

    /**
     * Creates a configuration exception with a message.
     *
     * @param message human-readable description of the error
     */
    public ConfigurationException(@NotNull String message) {
        super(ErrorCode.CONFIGURATION_ERROR, message);
        this.fieldName = null;
        this.invalidValue = null;
    }

    /**
     * Creates a configuration exception with message and cause.
     *
     * @param message human-readable description
     * @param cause   root cause
     */
    public ConfigurationException(@NotNull String message, @Nullable Throwable cause) {
        super(ErrorCode.CONFIGURATION_ERROR, message, cause);
        this.fieldName = null;
        this.invalidValue = null;
    }

    /**
     * Creates a configuration exception for a specific field.
     *
     * @param message  human-readable description
     * @param field    the configuration field name
     * @param value    the invalid value (nullable)
     */
    public ConfigurationException(@NotNull String message, @NotNull String field, @Nullable Object value) {
        super(ErrorCode.CONFIGURATION_ERROR, message);
        this.fieldName = field;
        this.invalidValue = value;
        withMetadata("field", field);
        withMetadata("invalidValue", value);
    }

    // --- Factory methods ---

    /**
     * Creates an exception for a missing required configuration field.
     *
     * @param fieldName the missing field
     * @return new ConfigurationException
     */
    public static ConfigurationException missingField(@NotNull String fieldName) {
        return new ConfigurationException(
                "Required configuration field '" + fieldName + "' is missing",
                fieldName,
                null);
    }

    /**
     * Creates an exception for an invalid configuration value.
     *
     * @param fieldName   the field with the invalid value
     * @param value       the invalid value
     * @param reason      why it is invalid
     * @return new ConfigurationException
     */
    public static ConfigurationException invalidValue(
            @NotNull String fieldName,
            @Nullable Object value,
            @NotNull String reason) {
        return new ConfigurationException(
                "Invalid value for '" + fieldName + "': " + reason,
                fieldName,
                value);
    }

    /**
     * Creates an exception indicating a configuration field type mismatch.
     *
     * @param fieldName    the field name
     * @param expectedType the expected type
     * @param actualValue  the actual value
     * @return new ConfigurationException
     */
    public static ConfigurationException fieldError(
            @NotNull String fieldName,
            @NotNull String expectedType,
            @Nullable Object actualValue) {
        return new ConfigurationException(
                "Configuration field '" + fieldName + "' expected type " + expectedType
                        + " but got: " + actualValue,
                fieldName,
                actualValue);
    }

    public @Nullable String getFieldName() {
        return fieldName;
    }

    public @Nullable Object getInvalidValue() {
        return invalidValue;
    }
}
