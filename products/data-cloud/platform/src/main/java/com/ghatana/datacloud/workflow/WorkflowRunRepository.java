/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.workflow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.spi.EventLogStore;
import com.ghatana.datacloud.spi.TenantContext;
import com.ghatana.platform.types.identity.Offset;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Event-sourced repository for tracking {@code workflow.run.*} lifecycle events.
 *
 * <h2>Event Types Managed</h2>
 * <ul>
 *   <li>{@value #EVENT_RUN_STARTED} — recorded when a pipeline workflow begins</li>
 *   <li>{@value #EVENT_STEP_COMPLETED} — recorded on successful completion of a pipeline step</li>
 *   <li>{@value #EVENT_STEP_FAILED} — recorded when a pipeline step fails</li>
 *   <li>{@value #EVENT_RUN_FINISHED} — recorded when the overall run reaches a terminal state</li>
 * </ul>
 *
 * <h2>Event Sourcing Pattern</h2>
 * All mutations are expressed as append-only events written to {@link EventLogStore}.
 * State is reconstructed on demand by reading and folding the event stream, which gives:
 * <ul>
 *   <li>Full audit trail of every step outcome</li>
 *   <li>Ability to replay history for debugging and observability</li>
 *   <li>Alignment with AEP's event-sourced checkpoint model</li>
 * </ul>
 *
 * <h2>Payload Format</h2>
 * All payloads are JSON objects containing at minimum a {@code runId} field so that
 * {@link #getRunStatus} can filter events by run without secondary indexes.
 *
 * <h2>ActiveJ Promise Usage</h2>
 * All methods return {@link Promise} and are non-blocking on the ActiveJ eventloop.
 * The underlying {@link EventLogStore} implementations handle executor dispatch
 * internally (e.g. {@code Promise.ofBlocking} in the JDBC adapter).
 *
 * @doc.type class
 * @doc.purpose Event-sourced repository for workflow run lifecycle tracking
 * @doc.layer product
 * @doc.pattern Repository, Event Sourcing
 * @doc.gaa.lifecycle capture
 * @see EventLogStore
 */
public class WorkflowRunRepository {

    private static final Logger log = LoggerFactory.getLogger(WorkflowRunRepository.class);

    /** Event type appended when a new workflow run starts. */
    public static final String EVENT_RUN_STARTED    = "workflow.run.started";
    /** Event type appended when a pipeline step succeeds. */
    public static final String EVENT_STEP_COMPLETED = "workflow.step.completed";
    /** Event type appended when a pipeline step fails. */
    public static final String EVENT_STEP_FAILED    = "workflow.step.failed";
    /** Event type appended when the overall run reaches a terminal state. */
    public static final String EVENT_RUN_FINISHED   = "workflow.run.finished";

    /** Maximum number of events read per event-type when reconstructing run status. */
    private static final int READ_LIMIT = 10_000;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final EventLogStore eventLogStore;

    /**
     * Creates a {@code WorkflowRunRepository} backed by the given event log store.
     *
     * @param eventLogStore durable event log (not null)
     */
    public WorkflowRunRepository(EventLogStore eventLogStore) {
        this.eventLogStore = Objects.requireNonNull(eventLogStore, "eventLogStore");
    }

    // -----------------------------------------------------------------------
    // Write operations
    // -----------------------------------------------------------------------

    /**
     * Records the start of a new workflow run and returns a unique {@code runId}.
     *
     * <p>Appends a {@value #EVENT_RUN_STARTED} event carrying the workflow and pipeline
     * identifiers together with any caller-supplied metadata.
     *
     * @param tenantId   tenant identifier (not null)
     * @param workflowId logical workflow name (not null)
     * @param pipelineId AEP pipeline that the run belongs to (not null)
     * @param metadata   arbitrary key-value metadata attached to the run (may be null)
     * @return promise of the newly generated {@code runId}
     */
    public Promise<String> startRun(
            String tenantId,
            String workflowId,
            String pipelineId,
            Map<String, String> metadata) {

        Objects.requireNonNull(tenantId,   "tenantId");
        Objects.requireNonNull(workflowId, "workflowId");
        Objects.requireNonNull(pipelineId, "pipelineId");

        String runId = UUID.randomUUID().toString();
        Map<String, Object> payload = new HashMap<>();
        payload.put("runId",      runId);
        payload.put("workflowId", workflowId);
        payload.put("pipelineId", pipelineId);
        payload.put("startedAt",  Instant.now().toString());
        if (metadata != null && !metadata.isEmpty()) {
            payload.put("metadata", metadata);
        }

        return appendEvent(tenantId, EVENT_RUN_STARTED, payload)
                .map(offset -> {
                    log.info("Workflow run started: runId={} workflowId={} pipelineId={} offset={}",
                             runId, workflowId, pipelineId, offset.value());
                    return runId;
                });
    }

    /**
     * Records the successful completion of a single pipeline step.
     *
     * @param tenantId  tenant identifier (not null)
     * @param runId     parent run identifier, returned by {@link #startRun} (not null)
     * @param stepName  name of the completed step (not null)
     * @param output    step output data (may be null)
     * @return promise of the storage offset
     */
    public Promise<Offset> completeStep(
            String tenantId,
            String runId,
            String stepName,
            Map<String, Object> output) {

        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(runId,    "runId");
        Objects.requireNonNull(stepName, "stepName");

        Map<String, Object> payload = new HashMap<>();
        payload.put("runId",       runId);
        payload.put("stepName",    stepName);
        payload.put("completedAt", Instant.now().toString());
        if (output != null && !output.isEmpty()) {
            payload.put("output", output);
        }

        return appendEvent(tenantId, EVENT_STEP_COMPLETED, payload)
                .whenResult(offset ->
                    log.debug("Step completed: runId={} step={} offset={}", runId, stepName, offset.value()));
    }

    /**
     * Records the failure of a single pipeline step.
     *
     * @param tenantId     tenant identifier (not null)
     * @param runId        parent run identifier (not null)
     * @param stepName     name of the failed step (not null)
     * @param errorMessage human-readable error description (not null)
     * @return promise of the storage offset
     */
    public Promise<Offset> failStep(
            String tenantId,
            String runId,
            String stepName,
            String errorMessage) {

        Objects.requireNonNull(tenantId,     "tenantId");
        Objects.requireNonNull(runId,        "runId");
        Objects.requireNonNull(stepName,     "stepName");
        Objects.requireNonNull(errorMessage, "errorMessage");

        Map<String, Object> payload = new HashMap<>();
        payload.put("runId",    runId);
        payload.put("stepName", stepName);
        payload.put("failedAt", Instant.now().toString());
        payload.put("error",    errorMessage);

        return appendEvent(tenantId, EVENT_STEP_FAILED, payload)
                .whenResult(offset ->
                    log.warn("Step failed: runId={} step={} error={} offset={}",
                             runId, stepName, errorMessage, offset.value()));
    }

    /**
     * Records the terminal state of a workflow run.
     *
     * <p>Callers should pass either {@code "SUCCEEDED"} or {@code "FAILED"} as the
     * {@code status} value. Additional summary data (e.g. total steps, duration) may
     * be passed via {@code summary}.
     *
     * @param tenantId  tenant identifier (not null)
     * @param runId     parent run identifier (not null)
     * @param status    terminal state string, typically {@code "SUCCEEDED"} or {@code "FAILED"}
     * @param summary   optional summary data (may be null)
     * @return promise of the storage offset
     */
    public Promise<Offset> finishRun(
            String tenantId,
            String runId,
            String status,
            Map<String, Object> summary) {

        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(runId,    "runId");
        Objects.requireNonNull(status,   "status");

        Map<String, Object> payload = new HashMap<>();
        payload.put("runId",      runId);
        payload.put("status",     status);
        payload.put("finishedAt", Instant.now().toString());
        if (summary != null && !summary.isEmpty()) {
            payload.put("summary", summary);
        }

        return appendEvent(tenantId, EVENT_RUN_FINISHED, payload)
                .whenResult(offset ->
                    log.info("Workflow run finished: runId={} status={} offset={}",
                             runId, status, offset.value()));
    }

    // -----------------------------------------------------------------------
    // Read / query operations
    // -----------------------------------------------------------------------

    /**
     * Reconstructs the current status of a workflow run by folding its event history.
     *
     * <p>Reads all four event types from the store and filters by {@code runId} in memory.
     * Returns an empty {@link Optional} when no {@value #EVENT_RUN_STARTED} event exists
     * for the given {@code runId}.
     *
     * @param tenantId tenant identifier (not null)
     * @param runId    run identifier (not null)
     * @return promise of the optional run status
     */
    public Promise<Optional<WorkflowRunStatus>> getRunStatus(String tenantId, String runId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(runId,    "runId");

        TenantContext ctx = TenantContext.of(tenantId);
        Offset zero = Offset.zero();

        Promise<List<EventLogStore.EventEntry>> startedP  = eventLogStore.readByType(ctx, EVENT_RUN_STARTED,    zero, READ_LIMIT);
        Promise<List<EventLogStore.EventEntry>> completedP = eventLogStore.readByType(ctx, EVENT_STEP_COMPLETED, zero, READ_LIMIT);
        Promise<List<EventLogStore.EventEntry>> failedP    = eventLogStore.readByType(ctx, EVENT_STEP_FAILED,    zero, READ_LIMIT);
        Promise<List<EventLogStore.EventEntry>> finishedP  = eventLogStore.readByType(ctx, EVENT_RUN_FINISHED,  zero, READ_LIMIT);

        return Promises.toList(startedP, completedP, failedP, finishedP)
                .map(lists -> buildStatus(runId, lists.get(0), lists.get(1), lists.get(2), lists.get(3)));
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private Promise<Offset> appendEvent(String tenantId, String eventType, Map<String, Object> payload) {
        try {
            byte[] jsonBytes = MAPPER.writeValueAsBytes(payload);
            EventLogStore.EventEntry entry = EventLogStore.EventEntry.builder()
                    .eventType(eventType)
                    .timestamp(Instant.now())
                    .payload(jsonBytes)
                    .contentType("application/json")
                    .build();
            return eventLogStore.append(TenantContext.of(tenantId), entry);
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }

    private Optional<WorkflowRunStatus> buildStatus(
            String runId,
            List<EventLogStore.EventEntry> startedEvents,
            List<EventLogStore.EventEntry> completedEvents,
            List<EventLogStore.EventEntry> failedEvents,
            List<EventLogStore.EventEntry> finishedEvents) {

        // Find the started event for this run
        Map<String, Object> startPayload = startedEvents.stream()
                .map(this::parsePayload)
                .filter(m -> runId.equals(m.get("runId")))
                .findFirst()
                .orElse(null);

        if (startPayload == null) {
            return Optional.empty();
        }

        String workflowId = String.valueOf(startPayload.getOrDefault("workflowId", ""));
        String pipelineId = String.valueOf(startPayload.getOrDefault("pipelineId", ""));
        Instant startedAt = parseInstant(startPayload, "startedAt");

        // Count completed and failed steps for this runId
        long completedSteps = completedEvents.stream()
                .map(this::parsePayload)
                .filter(m -> runId.equals(m.get("runId")))
                .count();

        long failedSteps = failedEvents.stream()
                .map(this::parsePayload)
                .filter(m -> runId.equals(m.get("runId")))
                .count();

        // Determine terminal status
        Map<String, Object> finishPayload = finishedEvents.stream()
                .map(this::parsePayload)
                .filter(m -> runId.equals(m.get("runId")))
                .findFirst()
                .orElse(null);

        String terminalStatus = finishPayload != null
                ? String.valueOf(finishPayload.getOrDefault("status", "UNKNOWN"))
                : null; // null = still in progress

        Instant finishedAt = finishPayload != null
                ? parseInstant(finishPayload, "finishedAt")
                : null;

        return Optional.of(new WorkflowRunStatus(
                runId, workflowId, pipelineId, startedAt,
                (int) completedSteps, (int) failedSteps,
                terminalStatus, finishedAt));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parsePayload(EventLogStore.EventEntry entry) {
        try {
            byte[] bytes = new byte[entry.payload().remaining()];
            entry.payload().duplicate().get(bytes);
            return MAPPER.readValue(bytes, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse workflow event payload for eventId={}: {}",
                     entry.eventId(), e.getMessage());
            return Map.of();
        }
    }

    private Instant parseInstant(Map<String, Object> m, String field) {
        Object v = m.get(field);
        try {
            return v != null ? Instant.parse(v.toString()) : Instant.EPOCH;
        } catch (Exception e) {
            return Instant.EPOCH;
        }
    }

    // -----------------------------------------------------------------------
    // Value type
    // -----------------------------------------------------------------------

    /**
     * Immutable snapshot of a workflow run's current status, materialised from its
     * event log.
     *
     * @param runId          unique run identifier
     * @param workflowId     logical workflow name
     * @param pipelineId     AEP pipeline the run belongs to
     * @param startedAt      when {@value #EVENT_RUN_STARTED} was recorded
     * @param completedSteps count of {@value #EVENT_STEP_COMPLETED} events this run
     * @param failedSteps    count of {@value #EVENT_STEP_FAILED} events this run
     * @param terminalStatus {@code "SUCCEEDED"}, {@code "FAILED"}, or {@code null} if in progress
     * @param finishedAt     when {@value #EVENT_RUN_FINISHED} was recorded; {@code null} if in progress
     *
     * @doc.type record
     * @doc.purpose Immutable snapshot of a workflow run materialised from event log
     * @doc.layer domain
     * @doc.pattern Value Object
     */
    public record WorkflowRunStatus(
            String runId,
            String workflowId,
            String pipelineId,
            Instant startedAt,
            int completedSteps,
            int failedSteps,
            String terminalStatus,
            Instant finishedAt) {

        /**
         * @return {@code true} when the run has reached a terminal state
         */
        public boolean isFinished() {
            return terminalStatus != null;
        }

        /**
         * @return {@code true} when the run succeeded
         */
        public boolean isSucceeded() {
            return "SUCCEEDED".equals(terminalStatus);
        }

        /**
         * @return {@code true} when the run failed
         */
        public boolean isFailed() {
            return "FAILED".equals(terminalStatus);
        }
    }
}
