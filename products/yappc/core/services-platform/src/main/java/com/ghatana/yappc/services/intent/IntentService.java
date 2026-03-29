package com.ghatana.yappc.services.intent;

import io.activej.promise.Promise;

/**
 * Service for managing software intents within the YAPPC system.
 *
 * @doc.type interface
 * @doc.purpose Contract for intent management operations
 * @doc.layer product
 * @doc.pattern Service
 */
public interface IntentService {

    /**
     * Captures a structured intent specification from raw user input.
     *
     * @param input the raw user intent input
     * @return promise of a structured {@link IntentSpec}
     */
    Promise<IntentSpec> capture(IntentInput input);

    /**
     * Analyzes a structured intent specification for feasibility and complexity.
     *
     * @param spec the intent specification to analyze
     * @return promise of an {@link IntentAnalysis} result
     */
    Promise<IntentAnalysis> analyze(IntentSpec spec);

    /**
     * Counts the total number of intents.
     *
     * @return promise of the total intent count
     */
    Promise<Long> count();
}
