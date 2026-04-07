package com.ghatana.core.domain.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Fluent DSL builder for constructing {@link PipelineSpec} instances.
 *
 * <p>Simplifies the creation of pipeline specifications by providing a readable,
 * declarative API that avoids long constructor argument lists.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * PipelineSpec spec = PipelineSpecBuilder.create("event-enrichment")
 *     .forTenant("tenant-alpha")
 *     .describedAs("Enriches incoming events with customer metadata")
 *     .withConfiguration(config -> config
 *         .maxRetries(3)
 *         .timeoutMs(5_000)
 *         .executionMode("STREAMING")
 *         .checkpointing(true))
 *     .addStage(PipelineStageSpecBuilder.create("ingest")
 *         .ofType("KAFKA_SOURCE")
 *         .withConnector("kafka-main")
 *         .build())
 *     .addStage(PipelineStageSpecBuilder.create("enrich")
 *         .ofType("ENRICHMENT")
 *         .build())
 *     .enabled()
 *     .build();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Fluent DSL for constructing PipelineSpec — reduces constructor verbosity
 * @doc.layer core
 * @doc.pattern Builder
 */
public final class PipelineSpecBuilder {

    private final String id;
    private final String name;
    private String tenantId;
    private String description;
    private boolean enabled = true;
    private final List<PipelineStageSpec> stages = new ArrayList<>();
    private ConfigurationBuilder configBuilder;

    private PipelineSpecBuilder(String id, String name) {
        this.id = id;
        this.name = name;
    }

    /**
     * Creates a builder for a pipeline with an auto-generated ID.
     *
     * @param name human-readable pipeline name
     * @return new builder
     */
    public static PipelineSpecBuilder create(String name) {
        Objects.requireNonNull(name, "Pipeline name must not be null");
        return new PipelineSpecBuilder(UUID.randomUUID().toString(), name);
    }

    /**
     * Creates a builder for a pipeline with an explicit ID.
     *
     * @param id   pipeline identifier
     * @param name human-readable pipeline name
     * @return new builder
     */
    public static PipelineSpecBuilder create(String id, String name) {
        Objects.requireNonNull(id, "Pipeline ID must not be null");
        Objects.requireNonNull(name, "Pipeline name must not be null");
        return new PipelineSpecBuilder(id, name);
    }

    /**
     * Sets the owning tenant for this pipeline.
     *
     * @param tenantId tenant identifier
     * @return this builder
     */
    public PipelineSpecBuilder forTenant(String tenantId) {
        this.tenantId = Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        return this;
    }

    /**
     * Sets a human-readable description for this pipeline.
     *
     * @param description pipeline description
     * @return this builder
     */
    public PipelineSpecBuilder describedAs(String description) {
        this.description = description;
        return this;
    }

    /**
     * Sets this pipeline as enabled (default).
     *
     * @return this builder
     */
    public PipelineSpecBuilder enabled() {
        this.enabled = true;
        return this;
    }

    /**
     * Sets this pipeline as disabled.
     *
     * @return this builder
     */
    public PipelineSpecBuilder disabled() {
        this.enabled = false;
        return this;
    }

    /**
     * Adds a pre-built {@link PipelineStageSpec} to the pipeline.
     *
     * @param stage stage specification to add
     * @return this builder
     */
    public PipelineSpecBuilder addStage(PipelineStageSpec stage) {
        Objects.requireNonNull(stage, "Stage must not be null");
        stages.add(stage);
        return this;
    }

    /**
     * Configures the pipeline execution settings via a {@link ConfigurationBuilder}.
     *
     * @param configurer consumer that configures the execution settings
     * @return this builder
     */
    public PipelineSpecBuilder withConfiguration(
            java.util.function.Consumer<ConfigurationBuilder> configurer) {
        Objects.requireNonNull(configurer, "Configurer must not be null");
        this.configBuilder = new ConfigurationBuilder();
        configurer.accept(this.configBuilder);
        return this;
    }

    /**
     * Builds and returns the fully configured {@link PipelineSpec}.
     *
     * @return configured pipeline specification
     * @throws IllegalStateException if required fields are missing
     */
    public PipelineSpec build() {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalStateException("Pipeline requires a tenantId — call forTenant()");
        }

        PipelineSpec.PipelineConfiguration config = configBuilder != null
                ? configBuilder.build()
                : new PipelineSpec.PipelineConfiguration(3, 30_000L, "STREAMING", false);

        PipelineSpec spec = new PipelineSpec(id, name, tenantId, description,
                stages, config, enabled);

        return spec;
    }

    // ─── Nested configuration builder ─────────────────────────────────────────

    /**
     * Fluent builder for {@link PipelineSpec.PipelineConfiguration}.
     *
     * @doc.type class
     * @doc.purpose Inner builder for pipeline execution configuration
     * @doc.layer core
     * @doc.pattern Builder
     */
    public static final class ConfigurationBuilder {

        private int maxRetries = 3;
        private long timeoutMs = 30_000L;
        private String executionMode = "STREAMING";
        private boolean checkpointing = false;

        /**
         * Sets the maximum number of retries for failed stage executions.
         *
         * @param maxRetries retry limit (must be &gt;= 0)
         * @return this builder
         */
        public ConfigurationBuilder maxRetries(int maxRetries) {
            if (maxRetries < 0) throw new IllegalArgumentException("maxRetries must be >= 0");
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * Sets the overall pipeline execution timeout.
         *
         * @param timeoutMs timeout in milliseconds (must be &gt; 0)
         * @return this builder
         */
        public ConfigurationBuilder timeoutMs(long timeoutMs) {
            if (timeoutMs <= 0) throw new IllegalArgumentException("timeoutMs must be > 0");
            this.timeoutMs = timeoutMs;
            return this;
        }

        /**
         * Sets the pipeline execution mode (e.g., {@code "STREAMING"}, {@code "BATCH"}).
         *
         * @param executionMode execution mode label
         * @return this builder
         */
        public ConfigurationBuilder executionMode(String executionMode) {
            this.executionMode = Objects.requireNonNull(executionMode, "executionMode must not be null");
            return this;
        }

        /**
         * Enables or disables state checkpointing for fault-tolerant execution.
         *
         * @param checkpointing {@code true} to enable checkpointing
         * @return this builder
         */
        public ConfigurationBuilder checkpointing(boolean checkpointing) {
            this.checkpointing = checkpointing;
            return this;
        }

        private PipelineSpec.PipelineConfiguration build() {
            return new PipelineSpec.PipelineConfiguration(maxRetries, timeoutMs, executionMode, checkpointing);
        }
    }
}

