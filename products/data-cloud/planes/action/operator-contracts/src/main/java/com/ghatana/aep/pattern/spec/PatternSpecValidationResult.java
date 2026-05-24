package com.ghatana.aep.pattern.spec;

import java.util.List;

/**
 * @doc.type record
 * @doc.purpose Reports PatternSpec structural validation errors
 * @doc.layer product
 * @doc.pattern Contract
 */
public record PatternSpecValidationResult(boolean valid, List<String> errors) {

    public PatternSpecValidationResult {
        errors = List.copyOf(errors != null ? errors : List.of());
        if (valid && !errors.isEmpty()) {
            throw new IllegalArgumentException("valid result must not contain errors");
        }
    }

    public static PatternSpecValidationResult ok() {
        return new PatternSpecValidationResult(true, List.of());
    }

    public static PatternSpecValidationResult invalid(List<String> errors) {
        return new PatternSpecValidationResult(false, errors);
    }
}
