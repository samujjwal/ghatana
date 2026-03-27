/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.toolruntime.change;

import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory {@link ChangeApprovalWorkflow} implementation with configurable risk threshold.
 *
 * <p>Risk scores are computed per {@link ChangeType}:
 * <ul>
 *   <li>{@code PERMISSION_GRANT} → 80</li>
 *   <li>{@code POLICY_UPDATE}    → 70</li>
 *   <li>{@code AGENT_DEPLOYMENT} → 65</li>
 *   <li>{@code TOOL_REGISTRATION}→ 60</li>
 *   <li>{@code DATA_SCHEMA_CHANGE}→ 55</li>
 *   <li>{@code CONFIG_CHANGE}    → 40</li>
 *   <li>{@code FEATURE_FLAG}     → 20</li>
 * </ul>
 *
 * Changes with a score &ge; threshold (default 60) enter {@link ChangeStatus#PENDING_REVIEW};
 * all others are immediately {@link ChangeStatus#APPROVED}.
 *
 * <p>Suitable for development and testing. Production deployments should integrate
 * with a durable workflow engine (e.g. Temporal, Camunda) with persistent storage.
 *
 * @doc.type class
 * @doc.purpose In-memory change approval workflow for dev/test
 * @doc.layer platform
 * @doc.pattern Service
 */
public final class InMemoryChangeApprovalWorkflow implements ChangeApprovalWorkflow {

    private static final int DEFAULT_AUTO_APPROVE_THRESHOLD = 60;

    private final int autoApproveThreshold;
    private final Map<String, ChangeRequest> store = new ConcurrentHashMap<>();

    /**
     * Creates a workflow with the default auto-approve threshold of 60.
     */
    public InMemoryChangeApprovalWorkflow() {
        this(DEFAULT_AUTO_APPROVE_THRESHOLD);
    }

    /**
     * Creates a workflow with a custom auto-approve threshold.
     *
     * @param autoApproveThreshold changes with risk score &lt; this value are auto-approved;
     *                             must be in range [0, 100]
     */
    public InMemoryChangeApprovalWorkflow(int autoApproveThreshold) {
        if (autoApproveThreshold < 0 || autoApproveThreshold > 100) {
            throw new IllegalArgumentException("threshold must be in [0, 100]");
        }
        this.autoApproveThreshold = autoApproveThreshold;
    }

    // ---- ChangeApprovalWorkflow ------------------------------------------

    @Override
    public Promise<ChangeRequest> submitChange(
            String tenantId, String requestingAgent,
            ChangeType changeType, String description,
            Map<String, Object> metadata) {
        int risk = riskScore(changeType);
        ChangeStatus status = risk < autoApproveThreshold
            ? ChangeStatus.APPROVED
            : ChangeStatus.PENDING_REVIEW;

        ChangeRequest req = new ChangeRequest(
            UUID.randomUUID().toString(),
            tenantId,
            requestingAgent,
            changeType,
            description,
            metadata == null ? Map.of() : Map.copyOf(metadata),
            status,
            risk,
            status == ChangeStatus.APPROVED ? "system" : null,
            status == ChangeStatus.APPROVED ? "Auto-approved (risk=" + risk + " < threshold=" + autoApproveThreshold + ")" : null,
            Instant.now(),
            status == ChangeStatus.APPROVED ? Instant.now() : null
        );
        store.put(req.changeId(), req);
        return Promise.of(req);
    }

    @Override
    public Promise<ChangeRequest> approve(String changeId, String reviewerId, String notes) {
        return transition(changeId, ChangeStatus.APPROVED, reviewerId, notes);
    }

    @Override
    public Promise<ChangeRequest> reject(String changeId, String reviewerId, String reason) {
        return transition(changeId, ChangeStatus.REJECTED, reviewerId, reason);
    }

    @Override
    public Promise<ChangeRequest> withdraw(String changeId) {
        return transition(changeId, ChangeStatus.WITHDRAWN, "requester", "Withdrawn by requester");
    }

    @Override
    public Promise<ChangeRequest> getChange(String changeId) {
        ChangeRequest req = store.get(changeId);
        if (req == null) {
            return Promise.ofException(
                new IllegalArgumentException("No change request found: " + changeId));
        }
        return Promise.of(req);
    }

    @Override
    public Promise<List<ChangeRequest>> listPending(String tenantId) {
        List<ChangeRequest> pending = store.values().stream()
            .filter(r -> r.tenantId().equals(tenantId))
            .filter(r -> r.status() == ChangeStatus.PENDING_REVIEW)
            .sorted((a, b) -> b.submittedAt().compareTo(a.submittedAt()))
            .collect(Collectors.toList());
        return Promise.of(pending);
    }

    // ---- Internals -------------------------------------------------------

    private Promise<ChangeRequest> transition(
            String changeId, ChangeStatus target, String reviewerId, String notes) {
        ChangeRequest existing = store.get(changeId);
        if (existing == null) {
            return Promise.ofException(
                new IllegalArgumentException("No change request found: " + changeId));
        }
        if (existing.status() != ChangeStatus.PENDING_REVIEW) {
            return Promise.ofException(
                new IllegalStateException(
                    "Change " + changeId + " is already " + existing.status()));
        }
        ChangeRequest updated = new ChangeRequest(
            existing.changeId(), existing.tenantId(), existing.requestingAgent(),
            existing.changeType(), existing.description(), existing.metadata(),
            target, existing.riskScore(), reviewerId, notes,
            existing.submittedAt(), Instant.now()
        );
        store.put(changeId, updated);
        return Promise.of(updated);
    }

    /**
     * Returns the baseline risk score [0–100] for the given change type.
     */
    private static int riskScore(ChangeType type) {
        return switch (type) {
            case PERMISSION_GRANT   -> 80;
            case POLICY_UPDATE      -> 70;
            case AGENT_DEPLOYMENT   -> 65;
            case TOOL_REGISTRATION  -> 60;
            case DATA_SCHEMA_CHANGE -> 55;
            case CONFIG_CHANGE      -> 40;
            case FEATURE_FLAG       -> 20;
        };
    }
}
