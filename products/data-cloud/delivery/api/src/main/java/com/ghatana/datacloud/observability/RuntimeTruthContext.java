package com.ghatana.datacloud.observability;

import java.time.Instant;
import java.util.*;

/**
 * Unified runtime truth context for all async workflows.
 *
 * <p><b>Purpose</b><br>
 * Provides a single source of truth for correlation and runtime identifiers
 * across all async workflows, ensuring traceability and observability.
 *
 * <p><b>Identifiers</b><br>
 * - correlationId: Unique request trace ID across all services
 * - tenantId: Tenant isolation identifier
 * - surface: Surface origin (api, sdk, agent, pipeline)
 * - runId: Agent or workflow run identifier
 * - jobId: Processing job identifier
 * - agentId: Agent identifier
 * - pipelineId: Pipeline execution identifier
 * - artifactId: Entity or artifact identifier
 *
 * @doc.type class
 * @doc.purpose Unified runtime truth context for observability
 * @doc.layer product
 * @doc.pattern Observability Context
 */
public final class RuntimeTruthContext {

    private final String correlationId;
    private final String tenantId;
    private final String surface;
    private final String runId;
    private final String jobId;
    private final String agentId;
    private final String pipelineId;
    private final String artifactId;
    private final Instant createdAt;
    private final Map<String, String> additionalMetadata;

    private RuntimeTruthContext(Builder builder) {
        this.correlationId = builder.correlationId;
        this.tenantId = builder.tenantId;
        this.surface = builder.surface;
        this.runId = builder.runId;
        this.jobId = builder.jobId;
        this.agentId = builder.agentId;
        this.pipelineId = builder.pipelineId;
        this.artifactId = builder.artifactId;
        this.createdAt = builder.createdAt != null ? builder.createdAt : Instant.now();
        this.additionalMetadata = Map.copyOf(builder.additionalMetadata);
    }

    public String correlationId() {
        return correlationId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String tenantId() {
        return tenantId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String surface() {
        return surface;
    }

    public String getSurface() {
        return surface;
    }

    public String runId() {
        return runId;
    }

    public String getRunId() {
        return runId;
    }

    public String jobId() {
        return jobId;
    }

    public String getJobId() {
        return jobId;
    }

    public String agentId() {
        return agentId;
    }

    public String getAgentId() {
        return agentId;
    }

    public String pipelineId() {
        return pipelineId;
    }

    public String getPipelineId() {
        return pipelineId;
    }

    public String artifactId() {
        return artifactId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Map<String, String> additionalMetadata() {
        return additionalMetadata;
    }

    public Map<String, String> getAdditionalMetadata() {
        return additionalMetadata;
    }

    /**
     * Creates a new context with updated values.
     */
    public Builder toBuilder() {
        return new Builder()
                .correlationId(this.correlationId)
                .tenantId(this.tenantId)
                .surface(this.surface)
                .runId(this.runId)
                .jobId(this.jobId)
                .agentId(this.agentId)
                .pipelineId(this.pipelineId)
                .artifactId(this.artifactId)
                .createdAt(this.createdAt)
                .additionalMetadata(new HashMap<>(this.additionalMetadata));
    }

    /**
     * Creates a child context for a nested operation.
     */
    public RuntimeTruthContext createChild() {
        return new Builder()
                .correlationId(this.correlationId)
                .tenantId(this.tenantId)
                .surface(this.surface)
                .runId(this.runId)
                .jobId(this.jobId)
                .agentId(this.agentId)
                .pipelineId(this.pipelineId)
                .artifactId(this.artifactId)
                .additionalMetadata(new HashMap<>(this.additionalMetadata))
                .build();
    }

    /**
     * Adds metadata to the context.
     */
    public RuntimeTruthContext withMetadata(String key, String value) {
        Map<String, String> newMetadata = new HashMap<>(this.additionalMetadata);
        newMetadata.put(key, value);
        return new Builder()
                .correlationId(this.correlationId)
                .tenantId(this.tenantId)
                .surface(this.surface)
                .runId(this.runId)
                .jobId(this.jobId)
                .agentId(this.agentId)
                .pipelineId(this.pipelineId)
                .artifactId(this.artifactId)
                .createdAt(this.createdAt)
                .additionalMetadata(newMetadata)
                .build();
    }

    /**
     * Converts context to MDC (Mapped Diagnostic Context) for logging.
     */
    public Map<String, String> toMdc() {
        Map<String, String> mdc = new HashMap<>(this.additionalMetadata);
        if (correlationId != null) mdc.put("correlationId", correlationId);
        if (tenantId != null) mdc.put("tenantId", tenantId);
        if (surface != null) mdc.put("surface", surface);
        if (runId != null) mdc.put("runId", runId);
        if (jobId != null) mdc.put("jobId", jobId);
        if (agentId != null) mdc.put("agentId", agentId);
        if (pipelineId != null) mdc.put("pipelineId", pipelineId);
        if (artifactId != null) mdc.put("artifactId", artifactId);
        return Map.copyOf(mdc);
    }

    /**
     * Converts context to HTTP headers for propagation.
     */
    public Map<String, String> toHttpHeaders() {
        Map<String, String> headers = new HashMap<>();
        if (correlationId != null) headers.put("X-Correlation-ID", correlationId);
        if (tenantId != null) headers.put("X-Tenant-ID", tenantId);
        if (surface != null) headers.put("X-Surface", surface);
        if (runId != null) headers.put("X-Run-ID", runId);
        if (jobId != null) headers.put("X-Job-ID", jobId);
        if (agentId != null) headers.put("X-Agent-ID", agentId);
        if (pipelineId != null) headers.put("X-Pipeline-ID", pipelineId);
        if (artifactId != null) headers.put("X-Artifact-ID", artifactId);
        return Map.copyOf(headers);
    }

    /**
     * Creates context from HTTP headers.
     */
    public static RuntimeTruthContext fromHttpHeaders(Map<String, String> headers) {
        Builder builder = new Builder();
        if (headers.containsKey("X-Correlation-ID")) {
            builder.correlationId(headers.get("X-Correlation-ID"));
        }
        if (headers.containsKey("X-Tenant-ID")) {
            builder.tenantId(headers.get("X-Tenant-ID"));
        }
        if (headers.containsKey("X-Surface")) {
            builder.surface(headers.get("X-Surface"));
        }
        if (headers.containsKey("X-Run-ID")) {
            builder.runId(headers.get("X-Run-ID"));
        }
        if (headers.containsKey("X-Job-ID")) {
            builder.jobId(headers.get("X-Job-ID"));
        }
        if (headers.containsKey("X-Agent-ID")) {
            builder.agentId(headers.get("X-Agent-ID"));
        }
        if (headers.containsKey("X-Pipeline-ID")) {
            builder.pipelineId(headers.get("X-Pipeline-ID"));
        }
        if (headers.containsKey("X-Artifact-ID")) {
            builder.artifactId(headers.get("X-Artifact-ID"));
        }
        return builder.build();
    }

    /**
     * Generates a new correlation ID.
     */
    public static String generateCorrelationId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Generates a new run ID.
     */
    public static String generateRunId() {
        return "run-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Generates a new job ID.
     */
    public static String generateJobId() {
        return "job-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String correlationId;
        private String tenantId;
        private String surface;
        private String runId;
        private String jobId;
        private String agentId;
        private String pipelineId;
        private String artifactId;
        private Instant createdAt;
        private Map<String, String> additionalMetadata = new HashMap<>();

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

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder additionalMetadata(Map<String, String> additionalMetadata) {
            this.additionalMetadata = additionalMetadata != null ? new HashMap<>(additionalMetadata) : new HashMap<>();
            return this;
        }

        public Builder additionalMetadata(String key, String value) {
            this.additionalMetadata.put(key, value);
            return this;
        }

        public Builder withMetadata(String key, String value) {
            this.additionalMetadata.put(key, value);
            return this;
        }

        public RuntimeTruthContext build() {
            // Generate correlation ID if not provided
            if (this.correlationId == null) {
                this.correlationId = generateCorrelationId();
            }
            return new RuntimeTruthContext(this);
        }
    }

    @Override
    public String toString() {
        return "RuntimeTruthContext{" +
                "correlationId='" + correlationId + '\'' +
                ", tenantId='" + tenantId + '\'' +
                ", surface='" + surface + '\'' +
                ", runId='" + runId + '\'' +
                ", jobId='" + jobId + '\'' +
                ", agentId='" + agentId + '\'' +
                ", pipelineId='" + pipelineId + '\'' +
                ", artifactId='" + artifactId + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
