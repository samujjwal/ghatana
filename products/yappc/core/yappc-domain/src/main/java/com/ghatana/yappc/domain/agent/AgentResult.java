package com.ghatana.products.yappc.domain.agent;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/**
 * YAPPC-specific result of an AI agent execution.
 *
 * <p>Contains the output data, execution metrics, and tracing information
 * specialized for YAPPC's typed-output, per-request cost tracking, and
 * confidence scoring.
 *
 * <p><b>Relationship to platform AgentResult</b><br>
 * The platform module ({@code com.ghatana.agent.AgentResult}) is a generic,
 * untyped result object. This YAPPC record is intentionally richer —
 * it carries a typed {@code data} field, per-request {@link AgentMetrics}
 * with token/cost/confidence breakdown, and an {@link AgentTrace} for
 * distributed tracing. The two types serve different layers and are
 * deliberately kept separate. No inheritance is needed.
 *
 * @param <T> The type of the output data
 * @doc.type record
 * @doc.purpose YAPPC-specific agent execution result with rich metrics
 * @doc.layer product
 * @doc.pattern Value Object
 */
public record AgentResult<T>(
        boolean success,
        @Nullable T data,
        @Nullable AgentError error,
        @NotNull AgentMetrics metrics,
        @NotNull AgentTrace trace
) {

    /**
     * Creates a successful result.
     */
    public static <T> AgentResult<T> success(
            @NotNull T data,
            @NotNull AgentMetrics metrics,
            @NotNull AgentTrace trace
    ) {
        return new AgentResult<>(true, data, null, metrics, trace);
    }

    /**
     * Creates a failed result.
     */
    public static <T> AgentResult<T> failure(
            @NotNull AgentError error,
            @NotNull AgentMetrics metrics,
            @NotNull AgentTrace trace
    ) {
        return new AgentResult<>(false, null, error, metrics, trace);
    }

    /**
     * Execution metrics for an agent request.
     */
    public record AgentMetrics(
            long latencyMs,
            @Nullable Integer tokensUsed,
            @NotNull String modelVersion,
            @Nullable Double confidence,
            @Nullable Double costUSD
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private long latencyMs;
            private Integer tokensUsed;
            private String modelVersion = "unknown";
            private Double confidence;
            private Double costUSD;

            public Builder latencyMs(long latencyMs) {
                this.latencyMs = latencyMs;
                return this;
            }

            public Builder tokensUsed(Integer tokensUsed) {
                this.tokensUsed = tokensUsed;
                return this;
            }

            public Builder modelVersion(String modelVersion) {
                this.modelVersion = modelVersion;
                return this;
            }

            public Builder confidence(Double confidence) {
                this.confidence = confidence;
                return this;
            }

            public Builder costUSD(Double costUSD) {
                this.costUSD = costUSD;
                return this;
            }

            public AgentMetrics build() {
                return new AgentMetrics(latencyMs, tokensUsed, modelVersion, confidence, costUSD);
            }
        }
    }

    /**
     * Trace information for an agent execution.
     */
    public record AgentTrace(
            @NotNull String agentName,
            @NotNull String requestId,
            @NotNull Instant timestamp,
            @NotNull java.util.Map<String, Object> metadata
    ) {
        public static AgentTrace of(String agentName, String requestId) {
            return new AgentTrace(agentName, requestId, Instant.now(), java.util.Map.of());
        }

        public static AgentTrace of(String agentName, String requestId, java.util.Map<String, Object> metadata) {
            return new AgentTrace(agentName, requestId, Instant.now(), metadata);
        }
    }

    /**
     * Error information from an agent execution.
     */
    public record AgentError(
            @NotNull String code,
            @NotNull String message,
            @NotNull String agentName,
            boolean retryable,
            @Nullable String details
    ) {
        public static AgentError of(String code, String message, String agentName) {
            return new AgentError(code, message, agentName, false, null);
        }

        public static AgentError retryable(String code, String message, String agentName) {
            return new AgentError(code, message, agentName, true, null);
        }

        public static AgentError withDetails(String code, String message, String agentName, String details) {
            return new AgentError(code, message, agentName, false, details);
        }
    }
}
