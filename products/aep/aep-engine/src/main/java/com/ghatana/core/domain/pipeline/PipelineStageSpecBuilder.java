package com.ghatana.core.domain.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Fluent DSL builder for constructing {@link PipelineStageSpec} instances.
 *
 * <p>Companion builder to {@link PipelineSpecBuilder}, designed for use with
 * {@link PipelineSpecBuilder#addStage(PipelineStageSpec)}.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * PipelineStageSpec stage = PipelineStageSpecBuilder.create("ingest")
 *     .ofType("KAFKA_SOURCE")
 *     .describedAs("Reads raw events from Kafka")
 *     .withConnector("kafka-connector-1")
 *     .withConnector("schema-registry-1")
 *     .withConfiguration(cfg -> cfg
 *         .parallelism(4)
 *         .timeoutMs(1_000)
 *         .executionStrategy("AT_LEAST_ONCE")
 *         .faultTolerant(true))
 *     .enabled()
 *     .build();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Fluent DSL for constructing PipelineStageSpec — companion to PipelineSpecBuilder
 * @doc.layer core
 * @doc.pattern Builder
 */
public final class PipelineStageSpecBuilder {

    private final String id;
    private final String name;
    private String stageType;
    private String description;
    private boolean enabled = true;
    private final List<String> connectorIds = new ArrayList<>();
    private StageConfigurationBuilder configBuilder;

    private PipelineStageSpecBuilder(String id, String name) {
        this.id = id;
        this.name = name;
    }

    /**
     * Creates a builder for a stage with an auto-generated ID.
     *
     * @param name human-readable stage name
     * @return new builder
     */
    public static PipelineStageSpecBuilder create(String name) {
        Objects.requireNonNull(name, "Stage name must not be null");
        return new PipelineStageSpecBuilder(UUID.randomUUID().toString(), name);
    }

    /**
     * Creates a builder for a stage with an explicit ID.
     *
     * @param id   stage identifier
     * @param name human-readable stage name
     * @return new builder
     */
    public static PipelineStageSpecBuilder create(String id, String name) {
        Objects.requireNonNull(id, "Stage ID must not be null");
        Objects.requireNonNull(name, "Stage name must not be null");
        return new PipelineStageSpecBuilder(id, name);
    }

    /**
     * Sets the stage type (e.g., {@code "KAFKA_SOURCE"}, {@code "ENRICHMENT"},
     * {@code "FILTER"}, {@code "SINK"}).
     *
     * @param stageType stage type label
     * @return this builder
     */
    public PipelineStageSpecBuilder ofType(String stageType) {
        this.stageType = Objects.requireNonNull(stageType, "Stage type must not be null");
        return this;
    }

    /**
     * Sets a human-readable description for this stage.
     *
     * @param description stage description
     * @return this builder
     */
    public PipelineStageSpecBuilder describedAs(String description) {
        this.description = description;
        return this;
    }

    /**
     * Registers a connector ID for this stage.
     *
     * @param connectorId connector to attach
     * @return this builder
     */
    public PipelineStageSpecBuilder withConnector(String connectorId) {
        Objects.requireNonNull(connectorId, "Connector ID must not be null");
        connectorIds.add(connectorId);
        return this;
    }

    /**
     * Marks this stage as enabled (default).
     *
     * @return this builder
     */
    public PipelineStageSpecBuilder enabled() {
        this.enabled = true;
        return this;
    }

    /**
     * Marks this stage as disabled.
     *
     * @return this builder
     */
    public PipelineStageSpecBuilder disabled() {
        this.enabled = false;
        return this;
    }

    /**
     * Configures the stage execution settings via a {@link StageConfigurationBuilder}.
     *
     * @param configurer consumer that configures execution settings
     * @return this builder
     */
    public PipelineStageSpecBuilder withConfiguration(
            java.util.function.Consumer<StageConfigurationBuilder> configurer) {
        Objects.requireNonNull(configurer, "Configurer must not be null");
        this.configBuilder = new StageConfigurationBuilder();
        configurer.accept(this.configBuilder);
        return this;
    }

    /**
     * Builds and returns the configured {@link PipelineStageSpec}.
     *
     * @return configured stage specification
     * @throws IllegalStateException if required fields are missing
     */
    public PipelineStageSpec build() {
        if (stageType == null || stageType.isBlank()) {
            throw new IllegalStateException("Stage requires a type — call ofType()");
        }

        PipelineStageSpec.StageConfiguration config = configBuilder != null
                ? configBuilder.build()
                : new PipelineStageSpec.StageConfiguration(1, 30_000L, "AT_LEAST_ONCE", true);

        return new PipelineStageSpec(id, name, stageType, description,
                connectorIds, config, enabled);
    }

    // ─── Nested configuration builder ─────────────────────────────────────────

    /**
     * Fluent builder for {@link PipelineStageSpec.StageConfiguration}.
     *
     * @doc.type class
     * @doc.purpose Inner builder for stage execution configuration
     * @doc.layer core
     * @doc.pattern Builder
     */
    public static final class StageConfigurationBuilder {

        private int parallelism = 1;
        private long timeoutMs = 30_000L;
        private String executionStrategy = "AT_LEAST_ONCE";
        private boolean faultTolerant = true;

        /**
         * Sets the degree of parallelism for this stage.
         *
         * @param parallelism number of parallel workers (must be &gt;= 1)
         * @return this builder
         */
        public StageConfigurationBuilder parallelism(int parallelism) {
            if (parallelism < 1) throw new IllegalArgumentException("parallelism must be >= 1");
            this.parallelism = parallelism;
            return this;
        }

        /**
         * Sets the stage execution timeout.
         *
         * @param timeoutMs timeout in milliseconds (must be &gt; 0)
         * @return this builder
         */
        public StageConfigurationBuilder timeoutMs(long timeoutMs) {
            if (timeoutMs <= 0) throw new IllegalArgumentException("timeoutMs must be > 0");
            this.timeoutMs = timeoutMs;
            return this;
        }

        /**
         * Sets the execution delivery strategy (e.g., {@code "AT_LEAST_ONCE"},
         * {@code "EXACTLY_ONCE"}).
         *
         * @param executionStrategy delivery guarantee label
         * @return this builder
         */
        public StageConfigurationBuilder executionStrategy(String executionStrategy) {
            this.executionStrategy = Objects.requireNonNull(executionStrategy,
                    "executionStrategy must not be null");
            return this;
        }

        /**
         * Enables or disables fault-tolerant execution for this stage.
         *
         * @param faultTolerant {@code true} to enable fault tolerance
         * @return this builder
         */
        public StageConfigurationBuilder faultTolerant(boolean faultTolerant) {
            this.faultTolerant = faultTolerant;
            return this;
        }

        private PipelineStageSpec.StageConfiguration build() {
            return new PipelineStageSpec.StageConfiguration(
                    parallelism, timeoutMs, executionStrategy, faultTolerant);
        }
    }
}
