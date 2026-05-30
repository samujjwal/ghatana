package com.ghatana.observability.correlation;

import java.time.Instant;
import java.util.*;

/**
 * Unified correlation context for async workflows and AI-mediated operations.
 *
 * <p><b>Purpose</b><br>
 * Provides a unified context for tracking operations across async workflows,
 * ensuring correlationId, tenantId, surface, runId, jobId, agentId, pipelineId,
 * and artifactId are consistently propagated through the system.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * CorrelationContext context = CorrelationContext.builder()
 *     .correlationId("req-123")
 *     .tenantId("tenant-456")
 *     .surface("data-cloud")
 *     .runId("run-789")
 *     .agentId("agent-abc")
 *     .build();
 *
 * // Propagate through async workflows
 * context.propagateTo(promise);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Unified correlation context for observability
 * @doc.layer platform
 * @doc.pattern Correlation Context
 */
public class CorrelationContext {

    private final String correlationId;
    private final String tenantId;
    private final String surface;
    private final String runId;
    private final String jobId;
    private final String agentId;
    private final String pipelineId;
    private final String artifactId;
    private final Map<String, String> additionalContext;
    private final Instant createdAt;

    private CorrelationContext(Builder builder) {
        this.correlationId = builder.correlationId;
        this.tenantId = builder.tenantId;
        this.surface = builder.surface;
        this.runId = builder.runId;
        this.jobId = builder.jobId;
        this.agentId = builder.agentId;
        this.pipelineId = builder.pipelineId;
        this.artifactId = builder.artifactId;
        this.additionalContext = Collections.unmodifiableMap(builder.additionalContext);
        this.createdAt = builder.createdAt != null ? builder.createdAt : Instant.now();
    }

    public String getCorrelationId() { return correlationId; }
    public String getTenantId() { return tenantId; }
    public String getSurface() { return surface; }
    public String getRunId() { return runId; }
    public String getJobId() { return jobId; }
    public String getAgentId() { return agentId; }
    public String getPipelineId() { return pipelineId; }
    public String getArtifactId() { return artifactId; }
    public Map<String, String> getAdditionalContext() { return additionalContext; }
    public Instant getCreatedAt() { return createdAt; }

    /**
     * Gets a value from additional context.
     */
    public Optional<String> getContextValue(String key) {
        return Optional.ofNullable(additionalContext.get(key));
    }

    /**
     * Creates a new context with an additional context value.
     */
    public CorrelationContext withContext(String key, String value) {
        Map<String, String> newContext = new HashMap<>(this.additionalContext);
        newContext.put(key, value);
        return new Builder(this)
                .additionalContext(newContext)
                .build();
    }

    /**
     * Creates a new context with a specific run ID.
     */
    public CorrelationContext withRunId(String runId) {
        return new Builder(this).runId(runId).build();
    }

    /**
     * Creates a new context with a specific job ID.
     */
    public CorrelationContext withJobId(String jobId) {
        return new Builder(this).jobId(jobId).build();
    }

    /**
     * Creates a new context with a specific agent ID.
     */
    public CorrelationContext withAgentId(String agentId) {
        return new Builder(this).agentId(agentId).build();
    }

    /**
     * Creates a new context with a specific pipeline ID.
     */
    public CorrelationContext withPipelineId(String pipelineId) {
        return new Builder(this).pipelineId(pipelineId).build();
    }

    /**
     * Creates a new context with a specific artifact ID.
     */
    public CorrelationContext withArtifactId(String artifactId) {
        return new Builder(this).artifactId(artifactId).build();
    }

    /**
     * Converts context to a map for logging/serialization.
     */
    public Map<String, String> toMap() {
        Map<String, String> map = new HashMap<>(additionalContext);
        if (correlationId != null) map.put("correlationId", correlationId);
        if (tenantId != null) map.put("tenantId", tenantId);
        if (surface != null) map.put("surface", surface);
        if (runId != null) map.put("runId", runId);
        if (jobId != null) map.put("jobId", jobId);
        if (agentId != null) map.put("agentId", agentId);
        if (pipelineId != null) map.put("pipelineId", pipelineId);
        if (artifactId != null) map.put("artifactId", artifactId);
        map.put("createdAt", createdAt.toString());
        return map;
    }

    /**
     * Generates a new correlation ID if not provided.
     */
    public static String generateCorrelationId() {
        return "corr-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Creates a new builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a builder from an existing context.
     */
    public static Builder builder(CorrelationContext context) {
        return new Builder(context);
    }

    /**
     * Builder for CorrelationContext.
     */
    public static class Builder {
        private String correlationId;
        private String tenantId;
        private String surface;
        private String runId;
        private String jobId;
        private String agentId;
        private String pipelineId;
        private String artifactId;
        private Map<String, String> additionalContext = new HashMap<>();
        private Instant createdAt;

        public Builder() {}

        public Builder(CorrelationContext context) {
            this.correlationId = context.correlationId;
            this.tenantId = context.tenantId;
            this.surface = context.surface;
            this.runId = context.runId;
            this.jobId = context.jobId;
            this.agentId = context.agentId;
            this.pipelineId = context.pipelineId;
            this.artifactId = context.artifactId;
            this.additionalContext = new HashMap<>(context.additionalContext);
            this.createdAt = context.createdAt;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder surface(String surface) {
            this.surface = surface;
            return this;
        }

        public Builder runId(String runId) {
            this.runId = runId;
            return this;
        }

        public Builder jobId(String jobId) {
            this.jobId = jobId;
            return this;
        }

        public Builder agentId(String agentId) {
            this.agentId = agentId;
            return this;
        }

        public Builder pipelineId(String pipelineId) {
            this.pipelineId = pipelineId;
            return this;
        }

        public Builder artifactId(String artifactId) {
            this.artifactId = artifactId;
            return this;
        }

        public Builder additionalContext(Map<String, String> additionalContext) {
            this.additionalContext = additionalContext != null ? additionalContext : new HashMap<>();
            return this;
        }

        public Builder addContext(String key, String value) {
            this.additionalContext.put(key, value);
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public CorrelationContext build() {
            // Generate correlation ID if not provided
            if (this.correlationId == null || this.correlationId.isEmpty()) {
                this.correlationId = generateCorrelationId();
            }
            return new CorrelationContext(this);
        }
    }

    @Override
    public String toString() {
        return "CorrelationContext{" +
                "correlationId='" + correlationId + '\'' +
                ", tenantId='" + tenantId + '\'' +
                ", surface='" + surface + '\'' +
                ", runId='" + runId + '\'' +
                ", jobId='" + jobId + '\'' +
                ", agentId='" + agentId + '\'' +
                ", pipelineId='" + pipelineId + '\'' +
                ", artifactId='" + artifactId + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CorrelationContext that = (CorrelationContext) o;
        return Objects.equals(correlationId, that.correlationId) &&
                Objects.equals(tenantId, that.tenantId) &&
                Objects.equals(surface, that.surface);
    }

    @Override
    public int hashCode() {
        return Objects.hash(correlationId, tenantId, surface);
    }
}
