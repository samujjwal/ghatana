package com.ghatana.pipeline.registry.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Error handling strategy configuration for pipeline execution.
 *
 * <p>Defines how errors should be handled during pipeline execution,
 * including fail-fast, continue-on-error, or dead-letter queue strategies.
 *
 * @doc.type class
 * @doc.purpose Error handling configuration for pipelines
 * @doc.layer product
 * @doc.pattern ValueObject
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorHandling {

    /**
     * Error handling strategy.
     */
    @Builder.Default
    private Strategy strategy = Strategy.FAIL_FAST;

    /**
     * Dead letter queue topic (used when strategy is DEAD_LETTER).
     */
    private String deadLetterTopic;

    /**
     * Maximum number of errors before failing the pipeline.
     * Only applicable for CONTINUE strategy.
     */
    @Builder.Default
    private int maxErrors = 10;

    /**
     * Error handling strategies.
     */
    public enum Strategy {
        /**
         * Stop pipeline execution on first error.
         */
        FAIL_FAST,

        /**
         * Continue processing despite errors, up to maxErrors.
         */
        CONTINUE,

        /**
         * Send failed events to dead letter queue and continue.
         */
        DEAD_LETTER
    }
}

