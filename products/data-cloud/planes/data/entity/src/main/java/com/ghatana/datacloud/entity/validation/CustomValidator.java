/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.entity.validation;

import java.util.Map;
import java.util.Optional;

/**
 * Interface for custom field-specific validation logic.
 *
 * <p>Custom validators allow domain-specific validation rules that cannot
 * be expressed through the standard FieldValidation constraints. Examples
 * include business rule validation, cross-field dependencies, or complex
 * conditional logic.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * validator.registerCustomValidator("products", "price", (field, value, allData) -> {
 *     if (value instanceof Double price && price < 0) {
 *         return Optional.of("Price cannot be negative");
 *     }
 *     return Optional.empty();
 * });
 * }</pre>
 *
 * @doc.type interface
 * @doc.purpose Pluggable custom validation for domain-specific rules
 * @doc.layer product
 * @doc.pattern Strategy
 */
@FunctionalInterface
public interface CustomValidator {

    /**
     * Validates a field value with access to the complete entity data.
     *
     * <p>Implementations can perform cross-field validation or complex
     * business logic checks. The validator receives the field name,
     * its value, and the complete entity data map for context.
     *
     * @param fieldName the name of the field being validated
     * @param value     the field value (may be null)
     * @param allData   the complete entity data map for cross-field validation
     * @return Optional containing a violation message if validation fails,
     *         or empty if validation passes
     */
    Optional<String> validate(String fieldName, Object value, Map<String, Object> allData);
}
