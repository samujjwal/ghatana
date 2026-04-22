/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.test.builder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builder for creating deterministic pipeline test data.
 *
 * <p>Provides a fluent API for constructing pipelines and checkpoints.
 *
 * <p><strong>Example:</strong>
 * <pre>
 * {@code
 * Map<String, Object> pipeline = PipelineBuilder.create("etl-001 [GH-90000]")
 *     .withName("Daily ETL [GH-90000]")
 *     .withStep("extract", Map.of("source", "jdbc")) // GH-90000
 *     .withStep("transform", Map.of("type", "sql")) // GH-90000
 *     .withStep("load", Map.of("target", "warehouse")) // GH-90000
 *     .withTenant("tenant-alpha [GH-90000]")
 *     .withSchedule("0 0 * * * [GH-90000]")
 *     .build(); // GH-90000
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
    private final List<Map<String, Object>> steps = new ArrayList<>(); // GH-90000
    private String schedule;
    private String status = "draft";
    private int version = 1;
    private Instant createdAt;
    private Instant updatedAt;
    private final Map<String, Object> metadata = new HashMap<>(); // GH-90000

    private PipelineBuilder(String id) { // GH-90000
        this.id = id;
        this.name = "Pipeline-" + id;
        this.createdAt = Instant.parse("2026-01-01T00:00:00Z [GH-90000]");
        this.updatedAt = createdAt;
    }

    /**
     * Start building a pipeline with the specified ID.
     *
     * @param id pipeline ID
     * @return new builder instance
     */
    public static PipelineBuilder create(String id) { // GH-90000
        return new PipelineBuilder(id); // GH-90000
    }

    /**
     * Set pipeline name.
     *
     * @param name pipeline name
     * @return this builder
     */
    public PipelineBuilder withName(String name) { // GH-90000
        this.name = name;
        return this;
    }

    /**
     * Set tenant ID.
     *
     * @param tenantId tenant identifier
     * @return this builder
     */
    public PipelineBuilder withTenant(String tenantId) { // GH-90000
        this.tenantId = tenantId;
        return this;
    }

    /**
     * Set description.
     *
     * @param description pipeline description
     * @return this builder
     */
    public PipelineBuilder withDescription(String description) { // GH-90000
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
    public PipelineBuilder withStep(String name, Map<String, Object> config) { // GH-90000
        Map<String, Object> step = new HashMap<>(); // GH-90000
        step.put("name", name); // GH-90000
        step.put("config", config); // GH-90000
        step.put("order", steps.size()); // GH-90000
        steps.add(step); // GH-90000
        return this;
    }

    /**
     * Add an extract step.
     *
     * @param source source type (jdbc, api, file) // GH-90000
     * @param config additional config
     * @return this builder
     */
    public PipelineBuilder withExtractStep(String source, Map<String, Object> config) { // GH-90000
        Map<String, Object> fullConfig = new HashMap<>(config); // GH-90000
        fullConfig.put("source", source); // GH-90000
        return withStep("extract", fullConfig); // GH-90000
    }

    /**
     * Add a transform step.
     *
     * @param type transform type (sql, python, map) // GH-90000
     * @param config additional config
     * @return this builder
     */
    public PipelineBuilder withTransformStep(String type, Map<String, Object> config) { // GH-90000
        Map<String, Object> fullConfig = new HashMap<>(config); // GH-90000
        fullConfig.put("type", type); // GH-90000
        return withStep("transform", fullConfig); // GH-90000
    }

    /**
     * Add a load step.
     *
     * @param target target type (warehouse, api, file) // GH-90000
     * @param config additional config
     * @return this builder
     */
    public PipelineBuilder withLoadStep(String target, Map<String, Object> config) { // GH-90000
        Map<String, Object> fullConfig = new HashMap<>(config); // GH-90000
        fullConfig.put("target", target); // GH-90000
        return withStep("load", fullConfig); // GH-90000
    }

    /**
     * Set cron schedule.
     *
     * @param schedule cron expression
     * @return this builder
     */
    public PipelineBuilder withSchedule(String schedule) { // GH-90000
        this.schedule = schedule;
        return this;
    }

    /**
     * Set pipeline status.
     *
     * @param status status (draft, active, paused, archived) // GH-90000
     * @return this builder
     */
    public PipelineBuilder withStatus(String status) { // GH-90000
        this.status = status;
        return this;
    }

    /**
     * Set version.
     *
     * @param version version number
     * @return this builder
     */
    public PipelineBuilder withVersion(int version) { // GH-90000
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
    public PipelineBuilder withMetadata(String key, Object value) { // GH-90000
        this.metadata.put(key, value); // GH-90000
        return this;
    }

    /**
     * Build the pipeline as a Map.
     *
     * @return pipeline as Map<String, Object>
     */
    public Map<String, Object> build() { // GH-90000
        Map<String, Object> pipeline = new HashMap<>(); // GH-90000
        pipeline.put("id", id); // GH-90000
        pipeline.put("name", name); // GH-90000
        pipeline.put("tenantId", tenantId); // GH-90000
        pipeline.put("description", description); // GH-90000
        pipeline.put("steps", new ArrayList<>(steps)); // GH-90000
        pipeline.put("schedule", schedule); // GH-90000
        pipeline.put("status", status); // GH-90000
        pipeline.put("version", version); // GH-90000
        pipeline.put("createdAt", createdAt.toString()); // GH-90000
        pipeline.put("updatedAt", updatedAt.toString()); // GH-90000
        pipeline.put("metadata", new HashMap<>(metadata)); // GH-90000
        return pipeline;
    }

    // Common pipeline templates

    /**
     * Create a standard ETL pipeline.
     *
     * @param id pipeline ID
     * @return pipeline builder
     */
    public static PipelineBuilder etl(String id) { // GH-90000
        return PipelineBuilder.create(id) // GH-90000
            .withName("ETL Pipeline " + id) // GH-90000
            .withExtractStep("jdbc", Map.of("table", "source_data")) // GH-90000
            .withTransformStep("sql", Map.of("query", "SELECT * FROM source")) // GH-90000
            .withLoadStep("warehouse", Map.of("table", "analytics")); // GH-90000
    }

    /**
     * Create a streaming pipeline.
     *
     * @param id pipeline ID
     * @return pipeline builder
     */
    public static PipelineBuilder streaming(String id) { // GH-90000
        return PipelineBuilder.create(id) // GH-90000
            .withName("Streaming Pipeline " + id) // GH-90000
            .withExtractStep("kafka", Map.of("topic", "events")) // GH-90000
            .withTransformStep("map", Map.of("mappings", Map.of("userId", "customer_id"))) // GH-90000
            .withLoadStep("api", Map.of("endpoint", "/ingest")); // GH-90000
    }

    /**
     * Create a batch pipeline.
     *
     * @param id pipeline ID
     * @return pipeline builder
     */
    public static PipelineBuilder batch(String id) { // GH-90000
        return PipelineBuilder.create(id) // GH-90000
            .withName("Batch Pipeline " + id) // GH-90000
            .withExtractStep("file", Map.of("path", "/data/input.csv", "format", "csv")) // GH-90000
            .withTransformStep("python", Map.of("script", "transform.py")) // GH-90000
            .withLoadStep("file", Map.of("path", "/data/output.parquet")) // GH-90000
            .withSchedule("0 2 * * * [GH-90000]"); // Daily at 2 AM
    }
}
