package com.ghatana.core.domain.pipeline;

import java.util.Objects;

/**
 * Ready-to-use pipeline templates for common AEP production quick-start scenarios.
 *
 * <p>Each factory method returns a fully pre-configured {@link PipelineSpecBuilder}
 * targeting a specific real-world pattern. Callers may override any field before
 * calling {@link PipelineSpecBuilder#build()}.
 *
 * <h3>Available Templates</h3>
 * <ul>
 *   <li>{@link #fraudDetection} — real-time fraud scoring with anomaly detection</li>
 *   <li>{@link #clickstreamAnalytics} — user clickstream enrichment and session analysis</li>
 *   <li>{@link #iotTelemetry} — IoT device telemetry ingestion with alerting</li>
 *   <li>{@link #auditLogPipeline} — compliance audit log collection and archiving</li>
 *   <li>{@link #multiTenantRouter} — route events to per-tenant downstream sinks</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * PipelineSpec fraudSpec = AepQuickStartTemplates
 *     .fraudDetection("payment-fraud-pipe", "tenant-alpha")
 *     .describedAs("Real-time fraud detection for payment events")
 *     .build();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Ready-to-use production quick-start pipeline templates (AEP-007.4)
 * @doc.layer core
 * @doc.pattern Factory
 */
public final class AepQuickStartTemplates {

    private AepQuickStartTemplates() {}

    /**
     * Template: real-time fraud detection pipeline.
     *
     * <p>Stages: {@code event-ingest} → {@code feature-extraction} →
     * {@code anomaly-detector} → {@code fraud-alert-sink}
     *
     * @param pipelineName human-readable name
     * @param tenantId     owning tenant
     * @return pre-configured builder
     */
    public static PipelineSpecBuilder fraudDetection(String pipelineName, String tenantId) {
        Objects.requireNonNull(pipelineName, "pipelineName must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        return PipelineSpecBuilder.create(pipelineName)
                .forTenant(tenantId)
                .describedAs("Real-time fraud detection pipeline with anomaly scoring")
                .withConfiguration(cfg -> cfg
                        .executionMode("STREAMING")
                        .maxRetries(3)
                        .timeoutMs(10_000L)
                        .checkpointing(true))
                .addStage(PipelineStageSpecBuilder.create("event-ingest")
                        .ofType("KAFKA_SOURCE")
                        .describedAs("Ingests payment events from the Kafka topic")
                        .withConfiguration(cfg -> cfg
                                .parallelism(8)
                                .timeoutMs(2_000L)
                                .executionStrategy("AT_LEAST_ONCE")
                                .faultTolerant(true))
                        .build())
                .addStage(PipelineStageSpecBuilder.create("feature-extraction")
                        .ofType("ENRICHMENT")
                        .describedAs("Extracts risk features from payment event fields")
                        .withConfiguration(cfg -> cfg
                                .parallelism(8)
                                .timeoutMs(1_000L)
                                .executionStrategy("AT_LEAST_ONCE")
                                .faultTolerant(true))
                        .build())
                .addStage(PipelineStageSpecBuilder.create("anomaly-detector")
                        .ofType("ML_SCORING")
                        .describedAs("Scores each event with the fraud ML model")
                        .withConfiguration(cfg -> cfg
                                .parallelism(4)
                                .timeoutMs(500L)
                                .executionStrategy("AT_LEAST_ONCE")
                                .faultTolerant(true))
                        .build())
                .addStage(PipelineStageSpecBuilder.create("fraud-alert-sink")
                        .ofType("SINK")
                        .describedAs("Writes high-risk events to the fraud alert store")
                        .withConfiguration(cfg -> cfg
                                .parallelism(4)
                                .timeoutMs(2_000L)
                                .executionStrategy("AT_LEAST_ONCE")
                                .faultTolerant(true))
                        .build());
    }

    /**
     * Template: user clickstream analytics pipeline.
     *
     * <p>Stages: {@code clickstream-ingest} → {@code session-enrichment} →
     * {@code aggregation} → {@code analytics-sink}
     *
     * @param pipelineName human-readable name
     * @param tenantId     owning tenant
     * @return pre-configured builder
     */
    public static PipelineSpecBuilder clickstreamAnalytics(String pipelineName, String tenantId) {
        Objects.requireNonNull(pipelineName, "pipelineName must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        return PipelineSpecBuilder.create(pipelineName)
                .forTenant(tenantId)
                .describedAs("Clickstream enrichment and session-level analytics")
                .withConfiguration(cfg -> cfg
                        .executionMode("STREAMING")
                        .maxRetries(2)
                        .timeoutMs(60_000L)
                        .checkpointing(true))
                .addStage(PipelineStageSpecBuilder.create("clickstream-ingest")
                        .ofType("KAFKA_SOURCE")
                        .describedAs("Reads user click events from the topic")
                        .withConfiguration(cfg -> cfg
                                .parallelism(16)
                                .timeoutMs(1_000L)
                                .executionStrategy("AT_LEAST_ONCE")
                                .faultTolerant(true))
                        .build())
                .addStage(PipelineStageSpecBuilder.create("session-enrichment")
                        .ofType("ENRICHMENT")
                        .describedAs("Joins clicks with session attributes from the lookup service")
                        .withConfiguration(cfg -> cfg
                                .parallelism(16)
                                .timeoutMs(500L)
                                .executionStrategy("AT_LEAST_ONCE")
                                .faultTolerant(true))
                        .build())
                .addStage(PipelineStageSpecBuilder.create("session-aggregation")
                        .ofType("WINDOWED_AGGREGATION")
                        .describedAs("Aggregates click metrics per session over 5-minute windows")
                        .withConfiguration(cfg -> cfg
                                .parallelism(8)
                                .timeoutMs(5_000L)
                                .executionStrategy("EXACTLY_ONCE")
                                .faultTolerant(true))
                        .build())
                .addStage(PipelineStageSpecBuilder.create("analytics-sink")
                        .ofType("SINK")
                        .describedAs("Writes aggregated session metrics to the analytics store")
                        .withConfiguration(cfg -> cfg
                                .parallelism(4)
                                .timeoutMs(2_000L)
                                .executionStrategy("EXACTLY_ONCE")
                                .faultTolerant(true))
                        .build());
    }

    /**
     * Template: IoT device telemetry ingestion with alerting.
     *
     * <p>Stages: {@code iot-ingest} → {@code telemetry-validation} →
     * {@code threshold-alert} → {@code time-series-sink}
     *
     * @param pipelineName human-readable name
     * @param tenantId     owning tenant
     * @return pre-configured builder
     */
    public static PipelineSpecBuilder iotTelemetry(String pipelineName, String tenantId) {
        Objects.requireNonNull(pipelineName, "pipelineName must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        return PipelineSpecBuilder.create(pipelineName)
                .forTenant(tenantId)
                .describedAs("IoT telemetry ingestion with threshold alerting")
                .withConfiguration(cfg -> cfg
                        .executionMode("STREAMING")
                        .maxRetries(3)
                        .timeoutMs(30_000L)
                        .checkpointing(true))
                .addStage(PipelineStageSpecBuilder.create("iot-ingest")
                        .ofType("MQTT_SOURCE")
                        .describedAs("Reads device telemetry from MQTT broker")
                        .withConfiguration(cfg -> cfg
                                .parallelism(4)
                                .timeoutMs(1_000L)
                                .executionStrategy("AT_LEAST_ONCE")
                                .faultTolerant(true))
                        .build())
                .addStage(PipelineStageSpecBuilder.create("telemetry-validation")
                        .ofType("FILTER")
                        .describedAs("Validates telemetry fields and drops malformed readings")
                        .withConfiguration(cfg -> cfg
                                .parallelism(4)
                                .timeoutMs(200L)
                                .executionStrategy("AT_LEAST_ONCE")
                                .faultTolerant(true))
                        .build())
                .addStage(PipelineStageSpecBuilder.create("threshold-alert")
                        .ofType("ENRICHMENT")
                        .describedAs("Emits alert events when metric values breach thresholds")
                        .withConfiguration(cfg -> cfg
                                .parallelism(4)
                                .timeoutMs(200L)
                                .executionStrategy("AT_LEAST_ONCE")
                                .faultTolerant(true))
                        .build())
                .addStage(PipelineStageSpecBuilder.create("time-series-sink")
                        .ofType("SINK")
                        .describedAs("Writes validated readings to the time-series database")
                        .withConfiguration(cfg -> cfg
                                .parallelism(4)
                                .timeoutMs(1_000L)
                                .executionStrategy("AT_LEAST_ONCE")
                                .faultTolerant(true))
                        .build());
    }

    /**
     * Template: compliance audit log collection and archiving.
     *
     * <p>Stages: {@code audit-ingest} → {@code compliance-filter} →
     * {@code pii-masking} → {@code audit-archive-sink}
     *
     * @param pipelineName human-readable name
     * @param tenantId     owning tenant
     * @return pre-configured builder
     */
    public static PipelineSpecBuilder auditLogPipeline(String pipelineName, String tenantId) {
        Objects.requireNonNull(pipelineName, "pipelineName must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        return PipelineSpecBuilder.create(pipelineName)
                .forTenant(tenantId)
                .describedAs("Compliance audit log collection with PII masking before archival")
                .withConfiguration(cfg -> cfg
                        .executionMode("STREAMING")
                        .maxRetries(5)
                        .timeoutMs(120_000L)
                        .checkpointing(true))
                .addStage(PipelineStageSpecBuilder.create("audit-ingest")
                        .ofType("KAFKA_SOURCE")
                        .describedAs("Ingests audit events from the compliance topic")
                        .withConfiguration(cfg -> cfg
                                .parallelism(4)
                                .timeoutMs(2_000L)
                                .executionStrategy("EXACTLY_ONCE")
                                .faultTolerant(true))
                        .build())
                .addStage(PipelineStageSpecBuilder.create("compliance-filter")
                        .ofType("FILTER")
                        .describedAs("Retains only events matching the regulatory scope")
                        .withConfiguration(cfg -> cfg
                                .parallelism(4)
                                .timeoutMs(200L)
                                .executionStrategy("EXACTLY_ONCE")
                                .faultTolerant(true))
                        .build())
                .addStage(PipelineStageSpecBuilder.create("pii-masking")
                        .ofType("ENRICHMENT")
                        .describedAs("Masks PII fields before persisting to the archive")
                        .withConfiguration(cfg -> cfg
                                .parallelism(4)
                                .timeoutMs(500L)
                                .executionStrategy("EXACTLY_ONCE")
                                .faultTolerant(true))
                        .build())
                .addStage(PipelineStageSpecBuilder.create("audit-archive-sink")
                        .ofType("SINK")
                        .describedAs("Writes masked audit records to the long-term archive")
                        .withConfiguration(cfg -> cfg
                                .parallelism(2)
                                .timeoutMs(5_000L)
                                .executionStrategy("EXACTLY_ONCE")
                                .faultTolerant(true))
                        .build());
    }

    /**
     * Template: multi-tenant event router to per-tenant downstream sinks.
     *
     * <p>Stages: {@code shared-ingest} → {@code tenant-classifier} →
     * {@code tenant-router} → {@code per-tenant-sink}
     *
     * @param pipelineName human-readable name
     * @param tenantId     owning tenant
     * @return pre-configured builder
     */
    public static PipelineSpecBuilder multiTenantRouter(String pipelineName, String tenantId) {
        Objects.requireNonNull(pipelineName, "pipelineName must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        return PipelineSpecBuilder.create(pipelineName)
                .forTenant(tenantId)
                .describedAs("Routes events to per-tenant sinks using a tenant classifier")
                .withConfiguration(cfg -> cfg
                        .executionMode("STREAMING")
                        .maxRetries(3)
                        .timeoutMs(30_000L)
                        .checkpointing(true))
                .addStage(PipelineStageSpecBuilder.create("shared-ingest")
                        .ofType("KAFKA_SOURCE")
                        .describedAs("Ingests events from the shared input topic")
                        .withConfiguration(cfg -> cfg
                                .parallelism(8)
                                .timeoutMs(1_000L)
                                .executionStrategy("AT_LEAST_ONCE")
                                .faultTolerant(true))
                        .build())
                .addStage(PipelineStageSpecBuilder.create("tenant-classifier")
                        .ofType("ENRICHMENT")
                        .describedAs("Extracts and validates the tenant identifier from each event")
                        .withConfiguration(cfg -> cfg
                                .parallelism(8)
                                .timeoutMs(200L)
                                .executionStrategy("AT_LEAST_ONCE")
                                .faultTolerant(true))
                        .build())
                .addStage(PipelineStageSpecBuilder.create("tenant-router")
                        .ofType("FILTER")
                        .describedAs("Routes events to the correct per-tenant downstream sink")
                        .withConfiguration(cfg -> cfg
                                .parallelism(8)
                                .timeoutMs(200L)
                                .executionStrategy("AT_LEAST_ONCE")
                                .faultTolerant(true))
                        .build())
                .addStage(PipelineStageSpecBuilder.create("per-tenant-sink")
                        .ofType("SINK")
                        .describedAs("Delivers events to the correct tenant-scoped storage")
                        .withConfiguration(cfg -> cfg
                                .parallelism(8)
                                .timeoutMs(2_000L)
                                .executionStrategy("AT_LEAST_ONCE")
                                .faultTolerant(true))
                        .build());
    }
}

