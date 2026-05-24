package com.ghatana.aep.operator.contract;

import java.util.List;

/**
 * @doc.type record
 * @doc.purpose Reports operator validation success, errors, and warnings
 * @doc.layer product
 * @doc.pattern Contract
 */
public record ValidationResult(boolean valid, List<String> errors, List<String> warnings) {

    public ValidationResult {
        errors = List.copyOf(errors != null ? errors : List.of());
        warnings = List.copyOf(warnings != null ? warnings : List.of());
        if (valid && !errors.isEmpty()) {
            throw new IllegalArgumentException("valid result must not contain errors");
        }
    }

    public static ValidationResult ok() {
        return new ValidationResult(true, List.of(), List.of());
    }

    public static ValidationResult invalid(List<String> errors) {
        return new ValidationResult(false, errors, List.of());
    }
}
