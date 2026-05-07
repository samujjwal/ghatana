/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.application.nlq;

import java.util.List;

/**
 * Result object for asynchronous operations
 *
 * @doc.type record
 * @doc.purpose Result object for asynchronous operations
 * @doc.layer product
 * @doc.pattern ValueObject
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
