/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.test.builder;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builder for creating deterministic pipeline test data.
 *
 * <p>Provides a fluent API for constructing pipelines and checkpoints.
 *
 * <p><strong>Example:</strong>
 * <pre>
 * {@code
 * Map<String, Object> pipeline = PipelineBuilder.create("etl-001")
 *     .withName("Daily ETL")
 *     .withStep("extract", Map.of("source", "jdbc"))
 *     .withStep("transform", Map.of("type", "sql"))
 *     .withStep("load", Map.of("target", "warehouse"))
 *     .withTenant("tenant-alpha")
 *     .withSchedule("0 0 * * *")
 *     .build();
 * }
 * </pre>
 *
 * @doc.type class
 * @doc.purpose Deterministic pipeline builder for test fixtures
 * @doc.layer product
 * @doc.pattern Builder, Test Fixture
 */
public final class PipelineBuilder {

    private String id;
    private String name;
    private String tenantId = "tenant-default";
    private String description = "Test pipeline";
    private final List<Map<String, Object>> steps = new ArrayList<>();
    private String schedule;
    private String status = "draft";
    private int version = 1;
    private Instant createdAt;
    private Instant updatedAt;
    private final Map<String, Object> metadata = new HashMap<>();

    private PipelineBuilder(String id) {
        this.id = id;
        this.name = "Pipeline-" + id;
        this.createdAt = Instant.parse("2026-01-01T00:00:00Z");
        this.updatedAt = createdAt;
    }

    /**
     * Start building a pipeline with the specified ID.
     *
     * @param id pipeline ID
     * @return new builder instance
     */
    public static PipelineBuilder create(String id) {
        return new PipelineBuilder(id);
    }

    /**
     * Set pipeline name.
     *
     * @param name pipeline name
     * @return this builder
     */
    public PipelineBuilder withName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Set tenant ID.
     *
     * @param tenantId tenant identifier
     * @return this builder
     */
    public PipelineBuilder withTenant(String tenantId) {
        this.tenantId = tenantId;
        return this;
    }

    /**
     * Set description.
     *
     * @param description pipeline description
     * @return this builder
     */
    public PipelineBuilder withDescription(String description) {
        this.description = description;
        return this;
    }

    /**
     * Add a step to the pipeline.
     *
     * @param name step name
     * @param config step configuration
     * @return this builder
     */
    public PipelineBuilder withStep(String name, Map<String, Object> config) {
        Map<String, Object> step = new HashMap<>();
        step.put("name", name);
        step.put("config", config);
        step.put("order", steps.size());
        steps.add(step);
        return this;
    }

    /**
     * Add an extract step.
     *
     * @param source source type (jdbc, api, file)
     * @param config additional config
     * @return this builder
     */
    public PipelineBuilder withExtractStep(String source, Map<String, Object> config) {
        Map<String, Object> fullConfig = new HashMap<>(config);
        fullConfig.put("source", source);
        return withStep("extract", fullConfig);
    }

    /**
     * Add a transform step.
     *
     * @param type transform type (sql, python, map)
     * @param config additional config
     * @return this builder
     */
    public PipelineBuilder withTransformStep(String type, Map<String, Object> config) {
        Map<String, Object> fullConfig = new HashMap<>(config);
        fullConfig.put("type", type);
        return withStep("transform", fullConfig);
    }

    /**
     * Add a load step.
     *
     * @param target target type (warehouse, api, file)
     * @param config additional config
     * @return this builder
     */
    public PipelineBuilder withLoadStep(String target, Map<String, Object> config) {
        Map<String, Object> fullConfig = new HashMap<>(config);
        fullConfig.put("target", target);
        return withStep("load", fullConfig);
    }

    /**
     * Set cron schedule.
     *
     * @param schedule cron expression
     * @return this builder
     */
    public PipelineBuilder withSchedule(String schedule) {
        this.schedule = schedule;
        return this;
    }

    /**
     * Set pipeline status.
     *
     * @param status status (draft, active, paused, archived)
     * @return this builder
     */
    public PipelineBuilder withStatus(String status) {
        this.status = status;
        return this;
    }

    /**
     * Set version.
     *
     * @param version version number
     * @return this builder
     */
    public PipelineBuilder withVersion(int version) {
        this.version = version;
        return this;
    }

    /**
     * Add metadata.
     *
     * @param key metadata key
     * @param value metadata value
     * @return this builder
     */
    public PipelineBuilder withMetadata(String key, Object value) {
        this.metadata.put(key, value);
        return this;
    }

    /**
     * Build the pipeline as a Map.
     *
     * @return pipeline as Map<String, Object>
     */
    public Map<String, Object> build() {
        Map<String, Object> pipeline = new HashMap<>();
        pipeline.put("id", id);
        pipeline.put("name", name);
        pipeline.put("tenantId", tenantId);
        pipeline.put("description", description);
        pipeline.put("steps", new ArrayList<>(steps));
        pipeline.put("schedule", schedule);
        pipeline.put("status", status);
        pipeline.put("version", version);
        pipeline.put("createdAt", createdAt.toString());
        pipeline.put("updatedAt", updatedAt.toString());
        pipeline.put("metadata", new HashMap<>(metadata));
        return pipeline;
    }

    // Common pipeline templates

    /**
     * Create a standard ETL pipeline.
     *
     * @param id pipeline ID
     * @return pipeline builder
     */
    public static PipelineBuilder etl(String id) {
        return PipelineBuilder.create(id)
            .withName("ETL Pipeline " + id)
            .withExtractStep("jdbc", Map.of("table", "source_data"))
            .withTransformStep("sql", Map.of("query", "SELECT * FROM source"))
            .withLoadStep("warehouse", Map.of("table", "analytics"));
    }

    /**
     * Create a streaming pipeline.
     *
     * @param id pipeline ID
     * @return pipeline builder
     */
    public static PipelineBuilder streaming(String id) {
        return PipelineBuilder.create(id)
            .withName("Streaming Pipeline " + id)
            .withExtractStep("kafka", Map.of("topic", "events"))
            .withTransformStep("map", Map.of("mappings", Map.of("userId", "customer_id")))
            .withLoadStep("api", Map.of("endpoint", "/ingest"));
    }

    /**
     * Create a batch pipeline.
     *
     * @param id pipeline ID
     * @return pipeline builder
     */
    public static PipelineBuilder batch(String id) {
        return PipelineBuilder.create(id)
            .withName("Batch Pipeline " + id)
            .withExtractStep("file", Map.of("path", "/data/input.csv", "format", "csv"))
            .withTransformStep("python", Map.of("script", "transform.py"))
            .withLoadStep("file", Map.of("path", "/data/output.parquet"))
            .withSchedule("0 2 * * *"); // Daily at 2 AM
    }
}
