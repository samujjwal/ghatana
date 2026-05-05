package com.ghatana.core.domain.pipeline;

import java.util.List;
import java.util.ArrayList;
import java.util.Objects;

/**
 * Pipeline specification for stream processing.
 *
 * <p>Defines the structure and configuration of a data processing pipeline
 * including stages, connectors, and execution parameters.
 *
 * <p>Includes version field for optimistic concurrency control to detect
 * conflicting updates when multiple clients modify the same pipeline.
 *
 * @doc.type class
 * @doc.purpose Pipeline specification for stream processing with version conflict detection
 * @doc.layer core
 * @doc.pattern Component
*/
public class PipelineSpec {
    private final String id;
    private final String name;
    private final String tenantId;
    private final String description;
    private final List<PipelineStageSpec> stages;
    private final PipelineConfiguration configuration;
    private final boolean enabled;
    private final long version;

    /**
     * Creates a new pipeline specification.
     *
     * @param id Pipeline identifier
     * @param name Pipeline name
     * @param tenantId Tenant identifier
     * @param description Pipeline description
     * @param stages List of pipeline stages
     * @param configuration Pipeline configuration
     * @param enabled Whether pipeline is enabled
     * @param version Version number for optimistic concurrency control
     */
    public PipelineSpec(String id, String name, String tenantId, String description,
                       List<PipelineStageSpec> stages, PipelineConfiguration configuration, boolean enabled, long version) {
        this.id = Objects.requireNonNull(id, "Pipeline ID cannot be null");
        this.name = Objects.requireNonNull(name, "Pipeline name cannot be null");
        this.tenantId = Objects.requireNonNull(tenantId, "Tenant ID cannot be null");
        this.description = description;
        this.stages = stages != null ? new ArrayList<>(stages) : new ArrayList<>();
        this.configuration = configuration;
        this.enabled = enabled;
        this.version = version;
    }

    /**
     * Creates a new pipeline specification with version 0 (for new pipelines).
     *
     * @param id Pipeline identifier
     * @param name Pipeline name
     * @param tenantId Tenant identifier
     * @param description Pipeline description
     * @param stages List of pipeline stages
     * @param configuration Pipeline configuration
     * @param enabled Whether pipeline is enabled
     */
    public PipelineSpec(String id, String name, String tenantId, String description,
                       List<PipelineStageSpec> stages, PipelineConfiguration configuration, boolean enabled) {
        this(id, name, tenantId, description, stages, configuration, enabled, 0L);
    }

    /**
     * Gets the pipeline identifier.
     *
     * @return pipeline ID
     */
    public String getId() {
        return id;
    }

    /**
     * Gets the pipeline name.
     *
     * @return pipeline name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the tenant identifier.
     *
     * @return tenant ID
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * Gets the pipeline description.
     *
     * @return pipeline description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Gets the pipeline stages.
     *
     * @return list of pipeline stages
     */
    public List<PipelineStageSpec> getStages() {
        return new ArrayList<>(stages);
    }

    /** Record-style accessor — equivalent to {@link #getName()}. */
    public String name() { return name; }

    /** Record-style accessor — equivalent to {@link #getTenantId()}. */
    public String tenantId() { return tenantId; }

    /** Record-style accessor — equivalent to {@link #getStages()}. */
    public List<PipelineStageSpec> stages() { return getStages(); }

    /**
     * Gets the pipeline configuration.
     *
     * @return pipeline configuration
     */
    public PipelineConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Checks if the pipeline is enabled.
     *
     * @return true if enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Gets the pipeline version for optimistic concurrency control.
     *
     * @return version number
     */
    public long getVersion() {
        return version;
    }

    /**
     * Adds a stage to the pipeline.
     *
     * @param stage stage to add
     */
    public void addStage(PipelineStageSpec stage) {
        Objects.requireNonNull(stage, "Stage cannot be null");
        stages.add(stage);
    }

    /**
     * Removes a stage from the pipeline.
     *
     * @param stage stage to remove
     * @return true if stage was removed
     */
    public boolean removeStage(PipelineStageSpec stage) {
        return stages.remove(stage);
    }

    /**
     * Gets the number of stages in the pipeline.
     *
     * @return stage count
     */
    public int getStageCount() {
        return stages.size();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PipelineSpec that = (PipelineSpec) o;
        return id.equals(that.id) && tenantId.equals(that.tenantId) && version == that.version;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, tenantId, version);
    }

    @Override
    public String toString() {
        return String.format("PipelineSpec{id='%s', name='%s', tenant='%s', stages=%d, enabled=%s, version=%d}",
            id, name, tenantId, stages.size(), enabled, version);
    }

    /**
     * Creates a new PipelineSpec with an incremented version.
     *
     * @return new PipelineSpec with version + 1
     */
    public PipelineSpec withIncrementedVersion() {
        return new PipelineSpec(id, name, tenantId, description, stages, configuration, enabled, version + 1);
    }

    /**
     * Checks if this version matches the expected version for optimistic concurrency control.
     *
     * @param expectedVersion the expected version
     * @return true if versions match
     */
    public boolean isVersion(long expectedVersion) {
        return this.version == expectedVersion;
    }

    /**
     * Pipeline configuration.
     */
    public static class PipelineConfiguration {
        private final int maxRetries;
        private final long timeoutMs;
        private final String executionMode;
        private final boolean checkpointing;

        public PipelineConfiguration(int maxRetries, long timeoutMs, String executionMode, boolean checkpointing) {
            this.maxRetries = maxRetries;
            this.timeoutMs = timeoutMs;
            this.executionMode = executionMode;
            this.checkpointing = checkpointing;
        }

        public int getMaxRetries() { return maxRetries; }
        public long getTimeoutMs() { return timeoutMs; }
        public String getExecutionMode() { return executionMode; }
        public boolean isCheckpointing() { return checkpointing; }
    }
}
