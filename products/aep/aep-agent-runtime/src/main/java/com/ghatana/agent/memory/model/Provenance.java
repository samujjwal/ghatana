package com.ghatana.agent.memory.model;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provenance tracks the origin and chain-of-custody for a memory item.
 * Every memory item must have provenance to support auditability and
 * trust scoring.
 *
 * @doc.type value-object
 * @doc.purpose Memory item provenance tracking
 * @doc.layer agent-memory
 */
@Value
@Builder
public class Provenance {

    /**
     * Source descriptor, e.g. "tool:grep", "user:input",
     * "consolidation:v2", "inference:gpt-4".
     */
    @Builder.Default
    @NotNull
    String source = "unknown";

    /** How confidence was determined. */
    @Builder.Default
    @NotNull
    ConfidenceSource confidenceSource = ConfidenceSource.LLM_INFERENCE;

    /** OpenTelemetry trace ID for correlating with distributed traces. */
    @Nullable
    String traceId;

    /** Agent that created this item. */
    @Builder.Default
    @NotNull
    String agentId = "";

    /** Session context when this item was created. */
    @Nullable
    String sessionId;

    /** Parent item ID if this was derived from another item. */
    @Nullable
    String parentItemId;

    /**
     * Source of confidence measurement.
     */
    public enum ConfidenceSource {
        HUMAN,
        LLM_INFERENCE,
        TOOL_OUTPUT,
        CONSOLIDATION,
        STATISTICAL
    }
}
