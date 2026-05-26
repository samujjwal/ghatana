package com.ghatana.yappc.services.intent;

import com.ghatana.yappc.domain.intent.IntentAnalysis;
import com.ghatana.yappc.domain.intent.IntentInput;
import com.ghatana.yappc.domain.intent.IntentSpec;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * @doc.type interface
 * @doc.purpose Emits platform evidence for intent lifecycle operations
 * @doc.layer service
 * @doc.pattern Port
 */
public interface IntentEvidenceService {

    /**
     * Records evidence for intent capture.
     *
     * @param input original intent input
     * @param spec captured intent specification
     * @return emitted evidence identifier
     */
    Promise<String> recordCapture(@NotNull IntentInput input, @NotNull IntentSpec spec);

    /**
     * Records evidence for intent analysis.
     *
     * @param spec analyzed intent specification
     * @param analysis generated analysis result
     * @return emitted evidence identifier
     */
    Promise<String> recordAnalysis(
            @NotNull IntentSpec spec,
            @NotNull IntentAnalysis analysis,
            @NotNull Map<String, Object> groundingMetadata);
}
