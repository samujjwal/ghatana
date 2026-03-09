/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.config.validation;

import java.util.Collections;
import java.util.List;

/**
 * Result of a configuration validation.
 *
 * @doc.type record
 * @doc.purpose Result of configuration validation with errors
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
     * Creates a failed validation result with errors.
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
