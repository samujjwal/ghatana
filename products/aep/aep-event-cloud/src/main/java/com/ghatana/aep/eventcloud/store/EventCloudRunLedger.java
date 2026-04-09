/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.eventcloud.store;

import com.ghatana.datacloud.spi.EventLogStore;
import com.ghatana.datacloud.spi.EventLogStore.EventEntry;
import com.ghatana.datacloud.spi.TenantContext;
import com.ghatana.platform.types.identity.Offset;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Append-only run ledger backed by Data-Cloud's {@link EventLogStore}.
 *
 * <p>Every pipeline execution (run) is recorded as an immutable event in the
 * ledger. This provides a complete, ordered history of all AEP pipeline runs
 * for replay, audit, observability, and compliance.
 *
 * <h3>Event types</h3>
 * <ul>
 *   <li>{@code run.started} - Pipeline execution initiated</li>
 *   <li>{@code run.step.completed} - Individual pipeline step completed</li>
 *   <li>{@code run.completed} - Pipeline execution completed successfully</li>
 *   <li>{@code run.failed} - Pipeline execution failed</li>
 *   <li>{@code run.checkpoint} - Checkpoint recorded for replay</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Append-only pipeline run ledger backed by Data-Cloud EventLogStore
 * @doc.layer product
 * @doc.pattern Event Store, Ledger
 */
public final class EventCloudRunLedger {

    private static final Logger log = LoggerFactory.getLogger(EventCloudRunLedger.class);

    private static final String RUN_STARTED = "run.started";
    private static final String RUN_STEP_COMPLETED = "run.step.completed";
    private static final String RUN_COMPLETED = "run.completed";
    private static final String RUN_FAILED = "run.failed";
    private static final String RUN_CHECKPOINT = "run.checkpoint";

    private final EventLogStore eventLogStore;

    public EventCloudRunLedger(EventLogStore eventLogStore) {
        this.eventLogStore = Objects.requireNonNull(eventLogStore, "eventLogStore required");
    }

    /**
     * Records a pipeline run start event.
     *
     * @param tenantId   tenant identifier
     * @param runId      unique run identifier
     * @param pipelineId pipeline identifier
     * @param payload    JSON-encoded run metadata
     * @return promise of the assigned offset
     */
    public Promise<Offset> recordRunStarted(
            String tenantId, String runId, String pipelineId, byte[] payload) {
        return appendRunEvent(tenantId, RUN_STARTED, runId, pipelineId, payload);
    }

    /**
     * Records a pipeline step completion event.
     *
     * @param tenantId   tenant identifier
     * @param runId      unique run identifier
     * @param pipelineId pipeline identifier
     * @param payload    JSON-encoded step result
     * @return promise of the assigned offset
     */
    public Promise<Offset> recordStepCompleted(
            String tenantId, String runId, String pipelineId, byte[] payload) {
        return appendRunEvent(tenantId, RUN_STEP_COMPLETED, runId, pipelineId, payload);
    }

    /**
     * Records a pipeline run completion event.
     *
     * @param tenantId   tenant identifier
     * @param runId      unique run identifier
     * @param pipelineId pipeline identifier
     * @param payload    JSON-encoded run result
     * @return promise of the assigned offset
     */
    public Promise<Offset> recordRunCompleted(
            String tenantId, String runId, String pipelineId, byte[] payload) {
        return appendRunEvent(tenantId, RUN_COMPLETED, runId, pipelineId, payload);
    }

    /**
     * Records a pipeline run failure event.
     *
     * @param tenantId   tenant identifier
     * @param runId      unique run identifier
     * @param pipelineId pipeline identifier
     * @param payload    JSON-encoded failure details
     * @return promise of the assigned offset
     */
    public Promise<Offset> recordRunFailed(
            String tenantId, String runId, String pipelineId, byte[] payload) {
        return appendRunEvent(tenantId, RUN_FAILED, runId, pipelineId, payload);
    }

    /**
     * Records a checkpoint for replay.
     *
     * @param tenantId   tenant identifier
     * @param runId      unique run identifier
     * @param pipelineId pipeline identifier
     * @param payload    JSON-encoded checkpoint state
     * @return promise of the assigned offset
     */
    public Promise<Offset> recordCheckpoint(
            String tenantId, String runId, String pipelineId, byte[] payload) {
        return appendRunEvent(tenantId, RUN_CHECKPOINT, runId, pipelineId, payload);
    }

    /**
     * Reads run events for a specific tenant starting from an offset.
     *
     * @param tenantId tenant identifier
     * @param from     starting offset (inclusive)
     * @param limit    maximum events to read
     * @return promise of run event entries
     */
    public Promise<List<EventEntry>> readRunEvents(
            String tenantId, Offset from, int limit) {
        TenantContext tenant = TenantContext.of(tenantId);
        return eventLogStore.read(tenant, from, limit)
            .map(entries -> entries.stream()
                .filter(e -> e.eventType().startsWith("run."))
                .toList());
    }

    /**
     * Reads run events within a time range.
     *
     * @param tenantId  tenant identifier
     * @param startTime start time (inclusive)
     * @param endTime   end time (exclusive)
     * @param limit     maximum events to read
     * @return promise of run event entries
     */
    public Promise<List<EventEntry>> readRunEventsByTimeRange(
            String tenantId, Instant startTime, Instant endTime, int limit) {
        TenantContext tenant = TenantContext.of(tenantId);
        return eventLogStore.readByTimeRange(tenant, startTime, endTime, limit)
            .map(entries -> entries.stream()
                .filter(e -> e.eventType().startsWith("run."))
                .toList());
    }

    /**
     * Tails run events in real-time for a given tenant.
     *
     * @param tenantId tenant identifier
     * @param handler  callback for each run event
     * @return promise of a subscription handle
     */
    public Promise<EventLogStore.Subscription> tailRunEvents(
            String tenantId,
            java.util.function.Consumer<EventEntry> handler) {
        TenantContext tenant = TenantContext.of(tenantId);
        return eventLogStore.getLatestOffset(tenant)
            .then(latestOffset ->
                eventLogStore.tail(tenant, latestOffset, entry -> {
                    if (entry.eventType().startsWith("run.")) {
                        handler.accept(entry);
                    }
                }));
    }

    // =========================================================================
    // Internal
    // =========================================================================

    private Promise<Offset> appendRunEvent(
            String tenantId,
            String eventType,
            String runId,
            String pipelineId,
            byte[] payload) {
        EventEntry entry = EventEntry.builder()
            .eventId(UUID.randomUUID())
            .eventType(eventType)
            .payload(ByteBuffer.wrap(payload))
            .headers(Map.of(
                "runId", runId,
                "pipelineId", pipelineId))
            .idempotencyKey(runId + ":" + eventType + ":" + Instant.now().toEpochMilli())
            .build();

        TenantContext tenant = TenantContext.of(tenantId);
        return eventLogStore.append(tenant, entry)
            .whenResult(offset ->
                log.debug("[run-ledger] Recorded {} runId={} pipeline={} tenant={} offset={}",
                    eventType, runId, pipelineId, tenantId, offset))
            .whenException(e ->
                log.error("[run-ledger] Failed to record {} runId={} pipeline={} tenant={}: {}",
                    eventType, runId, pipelineId, tenantId, e.getMessage(), e));
    }
}
