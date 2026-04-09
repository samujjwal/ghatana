package com.ghatana.core.domain.pipeline;


/**
 * Library of pre-built {@link PipelineSpec} templates for common streaming use cases.
 *
 * <p>Each template method returns a {@link PipelineSpecBuilder} pre-configured with
 * sensible defaults for the described use case. Callers can override any field before
 * calling {@link PipelineSpecBuilder#build()}.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * PipelineSpec spec = PipelineTemplateLibrary
 *     .eventEnrichment("my-enrichment-pipe", "tenant-alpha")
 *     .describedAs("Enriches raw events with customer attributes")
 *     .build();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Pre-built pipeline templates for common AEP streaming patterns
 * @doc.layer core
 * @doc.pattern Factory
 */
public final class PipelineTemplateLibrary {

    private PipelineTemplateLibrary() {}

    /**
     * Template: ingest raw events from a Kafka topic and write them to a sink unchanged.
     *
     * <p>Stages: {@code kafka-ingest} → {@code pass-through-sink}
     *
     * @param pipelineName  human-readable pipeline name
     * @param tenantId      owning tenant
     * @return pre-configured builder
     */
    public static PipelineSpecBuilder rawIngest(String pipelineName, String tenantId) {
        return PipelineSpecBuilder.create(pipelineName)
                .forTenant(tenantId)
                .describedAs("Ingests raw events from Kafka without transformation")
                .withConfiguration(cfg -> cfg
                        .executionMode("STREAMING")
                        .maxRetries(3)
                        .timeoutMs(30_000L)
                        .checkpointing(true))
                .addStage(PipelineStageSpecBuilder.create("kafka-ingest")
                        .ofType("KAFKA_SOURCE")
                        .describedAs("Reads raw events from the Kafka source topic")
                        .withConfiguration(cfg -> cfg
                                .parallelism(4)
                                .timeoutMs(5_000L)
                                .executionStrategy("AT_LEAST_ONCE")
                                .faultTolerant(true))
                        .build())
                .addStage(PipelineStageSpecBuilder.create("pass-through-sink")
                        .ofType("SINK")
                        .describedAs("Writes events to the sink without transformation")
                        .withConfiguration(cfg -> cfg
                                .parallelism(2)
                                .timeoutMs(5_000L)
                                .executionStrategy("AT_LEAST_ONCE")
                                .faultTolerant(true))
                        .build());
    }

    /**
     * Template: enrich events with data from an external lookup service before sinking.
     *
     * <p>Stages: {@code kafka-ingest} → {@code attribute-enrichment} → {@code enriched-sink}
     *
     * @param pipelineName  human-readable pipeline name
     * @param tenantId      owning tenant
     * @return pre-configured builder
     */
    public static PipelineSpecBuilder eventEnrichment(String pipelineName, String tenantId) {
        return PipelineSpecBuilder.create(pipelineName)
                .forTenant(tenantId)
                .describedAs("Enriches events with external attribute data before sinking")
                .withConfiguration(cfg -> cfg
                        .executionMode("STREAMING")
                        .maxRetries(3)
                        .timeoutMs(60_000L)
                        .checkpointing(true))
                .addStage(PipelineStageSpecBuilder.create("kafka-ingest")
                        .ofType("KAFKA_SOURCE")
                        .describedAs("Reads events from the Kafka source topic")
                        .withConfiguration(cfg -> cfg
                                .parallelism(4)
                                .timeoutMs(5_000L)
                                .executionStrategy("AT_LEAST_ONCE")
                                .faultTolerant(true))
                        .build())
                .addStage(PipelineStageSpecBuilder.create("attribute-enrichment")
                        .ofType("ENRICHMENT")
                        .describedAs("Adds customer attributes from the lookup service")
                        .withConfiguration(cfg -> cfg
                                .parallelism(8)
                                .timeoutMs(2_000L)
                                .executionStrategy("AT_LEAST_ONCE")
                                .faultTolerant(true))
                        .build())
                .addStage(PipelineStageSpecBuilder.create("enriched-sink")
                        .ofType("SINK")
                        .describedAs("Writes enriched events to the output sink")
                        .withConfiguration(cfg -> cfg
                                .parallelism(4)
                                .timeoutMs(5_000L)
                                .executionStrategy("AT_LEAST_ONCE")
                                .faultTolerant(true))
                        .build());
    }

    /**
     * Template: filter events by a predicate before forwarding to a sink.
     *
     * <p>Stages: {@code kafka-ingest} → {@code predicate-filter} → {@code filtered-sink}
     *
     * @param pipelineName  human-readable pipeline name
     * @param tenantId      owning tenant
     * @return pre-configured builder
     */
    public static PipelineSpecBuilder eventFilter(String pipelineName, String tenantId) {
        return PipelineSpecBuilder.create(pipelineName)
                .forTenant(tenantId)
                .describedAs("Filters events by a predicate before forwarding to the sink")
                .withConfiguration(cfg -> cfg
                        .executionMode("STREAMING")
                        .maxRetries(2)
                        .timeoutMs(30_000L)
                        .checkpointing(false))
                .addStage(PipelineStageSpecBuilder.create("kafka-ingest")
                        .ofType("KAFKA_SOURCE")
                        .withConfiguration(cfg -> cfg
                                .parallelism(4)
                                .executionStrategy("AT_LEAST_ONCE")
                                .faultTolerant(true))
                        .build())
                .addStage(PipelineStageSpecBuilder.create("predicate-filter")
                        .ofType("FILTER")
                        .describedAs("Drops events not matching the configured predicate")
                        .withConfiguration(cfg -> cfg
                                .parallelism(4)
                                .executionStrategy("AT_LEAST_ONCE")
                                .faultTolerant(true))
                        .build())
                .addStage(PipelineStageSpecBuilder.create("filtered-sink")
                        .ofType("SINK")
                        .withConfiguration(cfg -> cfg
                                .parallelism(2)
                                .executionStrategy("AT_LEAST_ONCE")
                                .faultTolerant(true))
                        .build());
    }

    /**
     * Template: aggregate events within tumbling time windows and emit summary records.
     *
     * <p>Stages: {@code kafka-ingest} → {@code windowed-aggregation} → {@code aggregation-sink}
     *
     * @param pipelineName  human-readable pipeline name
     * @param tenantId      owning tenant
     * @return pre-configured builder
     */
    public static PipelineSpecBuilder windowedAggregation(String pipelineName, String tenantId) {
        return PipelineSpecBuilder.create(pipelineName)
                .forTenant(tenantId)
                .describedAs("Aggregates events in tumbling windows and emits summary records")
                .withConfiguration(cfg -> cfg
                        .executionMode("STREAMING")
                        .maxRetries(3)
                        .timeoutMs(120_000L)
                        .checkpointing(true))
                .addStage(PipelineStageSpecBuilder.create("kafka-ingest")
                        .ofType("KAFKA_SOURCE")
                        .withConfiguration(cfg -> cfg
                                .parallelism(4)
                                .executionStrategy("AT_LEAST_ONCE")
                                .faultTolerant(true))
                        .build())
                .addStage(PipelineStageSpecBuilder.create("windowed-aggregation")
                        .ofType("WINDOWED_AGGREGATION")
                        .describedAs("Computes aggregate metrics over a tumbling window")
                        .withConfiguration(cfg -> cfg
                                .parallelism(4)
                                .executionStrategy("EXACTLY_ONCE")
                                .faultTolerant(true))
                        .build())
                .addStage(PipelineStageSpecBuilder.create("aggregation-sink")
                        .ofType("SINK")
                        .withConfiguration(cfg -> cfg
                                .parallelism(2)
                                .executionStrategy("EXACTLY_ONCE")
                                .faultTolerant(true))
                        .build());
    }
}
