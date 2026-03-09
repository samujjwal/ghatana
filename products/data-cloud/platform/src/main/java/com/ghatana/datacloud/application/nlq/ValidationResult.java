/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.application.nlq;

import java.util.List;

/**
 * Represents the validation result of an NLQ query plan.
 */
public record ValidationResult(
    boolean isValid,
    List<String> errors,
    List<String> warnings
) {
    public ValidationResult {
        errors = errors != null ? List.copyOf(errors) : List.of();
        warnings = warnings != null ? List.copyOf(warnings) : List.of();
    }
}
