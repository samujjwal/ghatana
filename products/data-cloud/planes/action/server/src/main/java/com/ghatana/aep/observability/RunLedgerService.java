/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.observability;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.aep.eventcloud.store.EventCloudRunLedger;
import com.ghatana.platform.core.util.JsonUtils;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Server-side facade that records run-lifecycle events to the {@link EventCloudRunLedger}
 * with distributed trace correlation.
 *
 * <p>Every pipeline run generates a sequence of ledger events that can be
 * joined across five dimensions to produce a full execution trace:
 * <pre>
 *   EventRecord  → intake event (type=&quot;event.received&quot;)
 *   RunRecord    → pipeline execution envelope (run.started / run.completed / run.failed)
 *   AgentStep    → individual agent/step outcome (run.step.completed)
 *   ReviewItem   → HITL decision (type=&quot;review.decision&quot;, correlates via runId)
 *   PolicyRecord → promoted policy (type=&quot;policy.promoted&quot;, correlates via skillId)
 * </pre>
 *
 * <p>The service is optional: when no {@link EventCloudRunLedger} is available it
 * silently no-ops so DevMode can still run without a DataCloud backend.
 *
 * @doc.type class
 * @doc.purpose Distributed-trace run ledger service — records run lifecycle events
 * @doc.layer product
 * @doc.pattern Facade
 */
public final class RunLedgerService {

    private static final Logger log = LoggerFactory.getLogger(RunLedgerService.class);

    @Nullable
    private final EventCloudRunLedger ledger;
    private final ObjectMapper mapper;

    /**
     * Creates a live service backed by the given ledger.
     *
     * @param ledger the run ledger (required)
     */
    public RunLedgerService(@NotNull EventCloudRunLedger ledger) {
        this.ledger = Objects.requireNonNull(ledger, "ledger required");
        this.mapper = JsonUtils.getDefaultMapper();
    }

    /**
     * Creates a no-op service (ledger unavailable — DevMode without DataCloud).
     */
    public RunLedgerService() {
        this.ledger = null;
        this.mapper = JsonUtils.getDefaultMapper();
    }

    // ─── Run lifecycle events ─────────────────────────────────────────────────

    /**
     * Records run.started — call immediately after the pipeline engine begins processing.
     *
     * @param runId      unique run identifier (from engine result)
     * @param tenantId   tenant this run belongs to
     * @param pipelineId pipeline identifier (may be null for ad-hoc event triggers)
     * @param traceId    optional distributed trace ID from incoming HTTP header
     * @param startedAt  when processing began
     * @return promise of void (fire-and-forget; errors are logged, not propagated)
     */
    public Promise<Void> recordRunStarted(
            @NotNull String runId,
            @NotNull String tenantId,
            @Nullable String pipelineId,
            @Nullable String traceId,
            @NotNull Instant startedAt) {
        if (ledger == null) return Promise.of(null);

        Map<String, Object> payload = new HashMap<>();
        payload.put("runId", runId);
        payload.put("tenantId", tenantId);
        payload.put("pipelineId", pipelineId != null ? pipelineId : "event");
        payload.put("traceId", traceId);
        payload.put("startedAt", startedAt.toString());

        return ledger.recordRunStarted(tenantId, runId, pipeline(pipelineId), toBytes(payload))
                .map(offset -> (Void) null)
                .then(Promise::of, e -> {
                    log.warn("[run-ledger] recordRunStarted failed runId={}: {}", runId, e.getMessage());
                    return Promise.of((Void) null);
                });
    }

    /**
     * Records run.completed — call when the pipeline finishes successfully.
     *
     * @param runId       unique run identifier
     * @param tenantId    tenant
     * @param pipelineId  pipeline identifier
     * @param traceId     optional trace ID
     * @param startedAt   processing start time (to compute duration)
     * @param detections  number of pattern matches or agent activations
     */
    public Promise<Void> recordRunCompleted(
            @NotNull String runId,
            @NotNull String tenantId,
            @Nullable String pipelineId,
            @Nullable String traceId,
            @NotNull Instant startedAt,
            int detections) {
        if (ledger == null) return Promise.of(null);

        Instant completedAt = Instant.now();
        Map<String, Object> payload = new HashMap<>();
        payload.put("runId", runId);
        payload.put("tenantId", tenantId);
        payload.put("pipelineId", pipelineId != null ? pipelineId : "event");
        payload.put("traceId", traceId);
        payload.put("status", "SUCCEEDED");
        payload.put("startedAt", startedAt.toString());
        payload.put("completedAt", completedAt.toString());
        payload.put("durationMs", java.time.Duration.between(startedAt, completedAt).toMillis());
        payload.put("detections", detections);

        return ledger.recordRunCompleted(tenantId, runId, pipeline(pipelineId), toBytes(payload))
                .map(offset -> (Void) null)
                .then(Promise::of, e -> {
                    log.warn("[run-ledger] recordRunCompleted failed runId={}: {}", runId, e.getMessage());
                    return Promise.of((Void) null);
                });
    }

    /**
     * Records run.failed — call when the pipeline fails.
     *
     * @param runId       unique run identifier
     * @param tenantId    tenant
     * @param pipelineId  pipeline identifier
     * @param traceId     optional trace ID
     * @param startedAt   processing start time
     * @param failureKind short error classifier
     * @param errorMessage human-readable error detail
     */
    public Promise<Void> recordRunFailed(
            @NotNull String runId,
            @NotNull String tenantId,
            @Nullable String pipelineId,
            @Nullable String traceId,
            @NotNull Instant startedAt,
            @NotNull String failureKind,
            @Nullable String errorMessage) {
        if (ledger == null) return Promise.of(null);

        Instant failedAt = Instant.now();
        Map<String, Object> payload = new HashMap<>();
        payload.put("runId", runId);
        payload.put("tenantId", tenantId);
        payload.put("pipelineId", pipelineId != null ? pipelineId : "event");
        payload.put("traceId", traceId);
        payload.put("status", "FAILED");
        payload.put("startedAt", startedAt.toString());
        payload.put("failedAt", failedAt.toString());
        payload.put("durationMs", java.time.Duration.between(startedAt, failedAt).toMillis());
        payload.put("failureKind", failureKind);
        payload.put("errorMessage", errorMessage);

        return ledger.recordRunFailed(tenantId, runId, pipeline(pipelineId), toBytes(payload))
                .map(offset -> (Void) null)
                .then(Promise::of, e -> {
                    log.warn("[run-ledger] recordRunFailed failed runId={}: {}", runId, e.getMessage());
                    return Promise.of((Void) null);
                });
    }

    /**
     * Records a HITL review decision against a run.
     *
     * <p>Stored as a {@code run.step.completed} event with step type "review.decision"
     * so it appears in the ledger's run event stream for a given tenant.
     *
     * @param reviewItemId  HITL review item identifier
     * @param runId         run that triggered this review
     * @param tenantId      tenant
     * @param skillId       skill/agent being reviewed
     * @param decision      "APPROVED" or "REJECTED"
     * @param decidedAt     when the decision was recorded
     */
    public Promise<Void> recordReviewDecision(
            @NotNull String reviewItemId,
            @Nullable String runId,
            @NotNull String tenantId,
            @NotNull String skillId,
            @NotNull String decision,
            @NotNull Instant decidedAt) {
        if (ledger == null) return Promise.of(null);

        String traceRunId = runId != null ? runId : "review:" + reviewItemId;
        Map<String, Object> payload = new HashMap<>();
        payload.put("stepType", "review.decision");
        payload.put("reviewItemId", reviewItemId);
        payload.put("runId", runId);
        payload.put("tenantId", tenantId);
        payload.put("skillId", skillId);
        payload.put("decision", decision);
        payload.put("decidedAt", decidedAt.toString());

        return ledger.recordStepCompleted(tenantId, traceRunId, "review:" + skillId, toBytes(payload))
                .map(offset -> (Void) null)
                .then(Promise::of, e -> {
                    log.warn("[run-ledger] recordReviewDecision failed itemId={}: {}", reviewItemId, e.getMessage());
                    return Promise.of((Void) null);
                });
    }

    /**
     * Records a policy promotion event.
     *
     * <p>Stored as a {@code run.step.completed} with step type "policy.promoted".
     *
     * @param policyId   the promoted policy identifier
     * @param tenantId   tenant
     * @param skillId    skill this policy governs
     * @param version    policy version
     * @param promotedAt when promotion occurred
     */
    public Promise<Void> recordPolicyPromotion(
            @NotNull String policyId,
            @NotNull String tenantId,
            @NotNull String skillId,
            @NotNull String version,
            @NotNull Instant promotedAt) {
        if (ledger == null) return Promise.of(null);

        Map<String, Object> payload = new HashMap<>();
        payload.put("stepType", "policy.promoted");
        payload.put("policyId", policyId);
        payload.put("tenantId", tenantId);
        payload.put("skillId", skillId);
        payload.put("version", version);
        payload.put("promotedAt", promotedAt.toString());

        return ledger.recordStepCompleted(tenantId, "policy:" + skillId, "policy:" + skillId, toBytes(payload))
                .map(offset -> (Void) null)
                .then(Promise::of, e -> {
                    log.warn("[run-ledger] recordPolicyPromotion failed policyId={}: {}", policyId, e.getMessage());
                    return Promise.of((Void) null);
                });
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private static String pipeline(@Nullable String pipelineId) {
        return pipelineId != null && !pipelineId.isBlank() ? pipelineId : "event";
    }

    private byte[] toBytes(Map<String, Object> payload) {
        try {
            return mapper.writeValueAsBytes(payload);
        } catch (JsonProcessingException e) {
            log.warn("[run-ledger] failed to serialize payload: {}", e.getMessage());
            return ("{}").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
