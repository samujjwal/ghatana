package com.ghatana.datacloud.spi;

import java.util.*;

/**
 * Unified runtime context for observability and traceability across all Data Cloud workflows.
 *
 * <p><b>Purpose</b><br>
 * Provides a single source of truth for runtime identifiers (correlationId, tenantId, surface,
 * runId, jobId, agentId, pipelineId, artifactId) that must be propagated through all workflows
 * for complete observability and debugging capabilities.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * RuntimeContext context = RuntimeContext.builder()
 *     .correlationId("corr-123")
 *     .tenantId("tenant-456")
 *     .surface("api")
 *     .runId("run-789")
 *     .build();
 *
 * // Propagate through workflow
 * service.execute(context, request);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Unified runtime context for observability
 * @doc.layer shared-spi
 * @doc.pattern Context Object
 */
public final class RuntimeContext {

    private final String correlationId;
    private final String tenantId;
    private final String surface;
    private final String runId;
    private final String jobId;
    private final String agentId;
    private final String pipelineId;
    private final String artifactId;
    private final Map<String, String> additionalContext;

    private RuntimeContext(Builder builder) {
        this.correlationId = builder.correlationId;
        this.tenantId = builder.tenantId;
        this.surface = builder.surface;
        this.runId = builder.runId;
        this.jobId = builder.jobId;
        this.agentId = builder.agentId;
        this.pipelineId = builder.pipelineId;
        this.artifactId = builder.artifactId;
        this.additionalContext = Collections.unmodifiableMap(new HashMap<>(builder.additionalContext));
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getSurface() {
        return surface;
    }

    public String getRunId() {
        return runId;
    }

    public String getJobId() {
        return jobId;
    }

    public String getAgentId() {
        return agentId;
    }

    public String getPipelineId() {
        return pipelineId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public Map<String, String> getAdditionalContext() {
        return additionalContext;
    }

    public String get(String key) {
        return additionalContext.get(key);
    }

    public boolean hasCorrelationId() {
        return correlationId != null && !correlationId.isBlank();
    }

    public boolean hasTenantId() {
        return tenantId != null && !tenantId.isBlank();
    }

    public boolean hasRunId() {
        return runId != null && !runId.isBlank();
    }

    public boolean hasJobId() {
        return jobId != null && !jobId.isBlank();
    }

    public boolean hasAgentId() {
        return agentId != null && !agentId.isBlank();
    }

    public boolean hasPipelineId() {
        return pipelineId != null && !pipelineId.isBlank();
    }

    public boolean hasArtifactId() {
        return artifactId != null && !artifactId.isBlank();
    }

    public Builder toBuilder() {
        return new Builder()
                .correlationId(correlationId)
                .tenantId(tenantId)
                .surface(surface)
                .runId(runId)
                .jobId(jobId)
                .agentId(agentId)
                .pipelineId(pipelineId)
                .artifactId(artifactId)
                .additionalContext(new HashMap<>(additionalContext));
    }

    public RuntimeContext withCorrelationId(String correlationId) {
        return toBuilder().correlationId(correlationId).build();
    }

    public RuntimeContext withTenantId(String tenantId) {
        return toBuilder().tenantId(tenantId).build();
    }

    public RuntimeContext withSurface(String surface) {
        return toBuilder().surface(surface).build();
    }

    public RuntimeContext withRunId(String runId) {
        return toBuilder().runId(runId).build();
    }

    public RuntimeContext withJobId(String jobId) {
        return toBuilder().jobId(jobId).build();
    }

    public RuntimeContext withAgentId(String agentId) {
        return toBuilder().agentId(agentId).build();
    }

    public RuntimeContext withPipelineId(String pipelineId) {
        return toBuilder().pipelineId(pipelineId).build();
    }

    public RuntimeContext withArtifactId(String artifactId) {
        return toBuilder().artifactId(artifactId).build();
    }

    public RuntimeContext withAdditionalContext(String key, String value) {
        return toBuilder().additionalContext(key, value).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static RuntimeContext empty() {
        return new Builder().build();
    }

    public static RuntimeContext fromCorrelationId(String correlationId) {
        return new Builder().correlationId(correlationId).build();
    }

    public static RuntimeContext fromTenantId(String tenantId) {
        return new Builder().tenantId(tenantId).build();
    }

    @Override
    public String toString() {
        return "RuntimeContext{" +
                "correlationId='" + correlationId + '\'' +
                ", tenantId='" + tenantId + '\'' +
                ", surface='" + surface + '\'' +
                ", runId='" + runId + '\'' +
                ", jobId='" + jobId + '\'' +
                ", agentId='" + agentId + '\'' +
                ", pipelineId='" + pipelineId + '\'' +
                ", artifactId='" + artifactId + '\'' +
                ", additionalContext=" + additionalContext +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RuntimeContext that = (RuntimeContext) o;
        return Objects.equals(correlationId, that.correlationId) &&
                Objects.equals(tenantId, that.tenantId) &&
                Objects.equals(surface, that.surface) &&
                Objects.equals(runId, that.runId) &&
                Objects.equals(jobId, that.jobId) &&
                Objects.equals(agentId, that.agentId) &&
                Objects.equals(pipelineId, that.pipelineId) &&
                Objects.equals(artifactId, that.artifactId) &&
                Objects.equals(additionalContext, that.additionalContext);
    }

    @Override
    public int hashCode() {
        return Objects.hash(correlationId, tenantId, surface, runId, jobId, agentId, pipelineId, artifactId, additionalContext);
    }

    public static final class Builder {
        private String correlationId;
        private String tenantId;
        private String surface;
        private String runId;
        private String jobId;
        private String agentId;
        private String pipelineId;
        private String artifactId;
        private final Map<String, String> additionalContext = new HashMap<>();

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

        public Builder additionalContext(String key, String value) {
            this.additionalContext.put(key, value);
            return this;
        }

        public Builder additionalContext(Map<String, String> context) {
            this.additionalContext.putAll(context);
            return this;
        }

        public RuntimeContext build() {
            return new RuntimeContext(this);
        }
    }
}
