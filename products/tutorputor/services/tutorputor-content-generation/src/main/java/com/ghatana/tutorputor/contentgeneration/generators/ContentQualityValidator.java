package com.ghatana.tutorputor.contentgeneration.generators;

import java.util.concurrent.Future;

/**
 * Validates the quality of generated content items.
 *
 * @doc.type interface
 * @doc.purpose Content quality validation abstraction for generators
 * @doc.layer product
 * @doc.pattern Strategy, Port
 */
public interface ContentQualityValidator {

    /**
     * Encapsulates the outcome of a content quality validation check.
     *
     * @param passed whether the content meets quality thresholds
     * @param score  quality score in the range [0.0, 1.0]
     */
    record ValidationResult(boolean passed, double score) {}

    /**
     * Validates a content item against an optional context object.
     *
     * @param <T>     the content type
     * @param content the content item to validate
     * @param context optional contextual data (may be {@code null})
     * @return a Future resolving to the validation result
     */
    <T> Future<ValidationResult> validate(T content, Object context);
}
