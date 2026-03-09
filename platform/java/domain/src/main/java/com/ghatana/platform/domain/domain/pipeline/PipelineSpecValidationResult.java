package com.ghatana.platform.domain.domain.pipeline;

import java.util.List;
import java.util.Objects;

/**
 * Validation result for {@link PipelineSpec} structures.
 *
 * <p>Provides a simple boolean flag and a list of human-readable error messages.
 * Callers can treat {@code valid == true} as "no structural issues" and use
 * the error list for diagnostics or logging.
 *
 * @doc.type value-object
 * @doc.layer domain
 * @doc.purpose structural validation result for declarative pipeline specifications
 * @doc.pattern Value Object
 */
public record PipelineSpecValidationResult(boolean valid, List<String> errors) {

    public PipelineSpecValidationResult {
        Objects.requireNonNull(errors, "errors must not be null");
    }

    /**
     * Creates a successful validation result with no errors.
     */
    public static PipelineSpecValidationResult ok() {
        return new PipelineSpecValidationResult(true, List.of());
    }

    /**
     * Creates an invalid validation result with one or more errors.
     */
    public static PipelineSpecValidationResult invalid(List<String> errors) {
        return new PipelineSpecValidationResult(false, List.copyOf(errors));
    }
}
