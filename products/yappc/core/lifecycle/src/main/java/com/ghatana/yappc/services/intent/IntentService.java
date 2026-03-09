package com.ghatana.yappc.services.intent;

import com.ghatana.yappc.domain.intent.IntentAnalysis;
import com.ghatana.yappc.domain.intent.IntentInput;
import com.ghatana.yappc.domain.intent.IntentSpec;
import io.activej.promise.Promise;

/**
 * @doc.type interface
 * @doc.purpose Captures product intent with AI-assisted parsing
 * @doc.layer service
 * @doc.pattern Service
 */
public interface IntentService {
    /**
     * Captures intent from human/agent input using LLM-based parsing.
     * 
     * @param input Raw intent input (text, structured data, etc.)
     * @return Promise of validated IntentSpec
     */
    Promise<IntentSpec> capture(IntentInput input);
    
    /**
     * Analyzes captured intent for feasibility, risks, and gaps.
     * 
     * @param spec The intent specification to analyze
     * @return Promise of analysis result
     */
    Promise<IntentAnalysis> analyze(IntentSpec spec);

    /**
     * Returns the total number of persisted intent entities.
     *
     * @return Promise resolving to the entity count
     */
    default Promise<Long> count() {
        return Promise.of(0L);
    }
}
