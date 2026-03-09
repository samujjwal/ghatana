package com.ghatana.pipeline.registry.validation;

import lombok.Builder;
import lombok.Value;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of pipeline validation.
 *
 * @doc.type class
 * @doc.purpose Encapsulate pipeline validation results with errors and warnings
 * @doc.layer product
 * @doc.pattern ValueObject
 */
@Value
@Builder
public class ValidationResult {
    boolean valid;

    @Builder.Default
    List<String> errors = new ArrayList<>();

    @Builder.Default
    List<String> warnings = new ArrayList<>();

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }
}

