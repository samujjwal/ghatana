/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */
package com.ghatana.config.runtime.validation;

import java.util.Collections;
import java.util.List;

/**
 * Result of a runtime configuration validation.
 *
 * @doc.type record
 * @doc.purpose Validation result for runtime configuration
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record ValidationResult(boolean valid, List<String> errors) {

    /**
     * Creates a successful validation result.
     */
    public static ValidationResult success() {
        return new ValidationResult(true, Collections.emptyList());
    }

    /**
     * Creates a failed validation result.
     */
    public static ValidationResult failure(List<String> errors) {
        return new ValidationResult(false, errors);
    }

    /**
     * Creates a failed validation result with a single error.
     */
    public static ValidationResult failure(String error) {
        return new ValidationResult(false, List.of(error));
    }
}
