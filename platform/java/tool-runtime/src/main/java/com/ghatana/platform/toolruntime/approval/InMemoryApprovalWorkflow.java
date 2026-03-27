/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.toolruntime.approval;

import io.activej.promise.Promise;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link ApprovalWorkflow} and {@link ApprovalGateway} implementation.
 *
 * <p>Action types that require approval are registered via {@link #requireApproval(String)}.
 * Suitable for development and testing; production deployments should integrate with
 * a persistent workflow engine (e.g. Temporal, Camunda).
 *
 * @doc.type class
 * @doc.purpose In-memory approval workflow for dev/test
 * @doc.layer platform
 * @doc.pattern Service
 */
public final class InMemoryApprovalWorkflow implements ApprovalWorkflow, ApprovalGateway {

    private final Map<String, ApprovalRequest> requests = new ConcurrentHashMap<>();
    /** Set of action types (global) that unconditionally require approval. */
    private final Set<String> requiredActionTypes = ConcurrentHashMap.newKeySet();

    /**
     * Register an action type as always requiring approval.
     *
     * @param actionType the action type to gate behind approval
     */
    public void requireApproval(String actionType) {
        requiredActionTypes.add(actionType);
    }

    // ---- ApprovalGateway ------------------------------------------------

    @Override
    public Promise<Boolean> requiresApproval(String tenantId, String agentId, String actionType) {
        return Promise.of(requiredActionTypes.contains(actionType));
    }

    @Override
    public Promise<String> requestApproval(
            String tenantId, String agentId, String actionType, Object context) {
        return submit(tenantId, agentId, actionType, context)
            .map(ApprovalRequest::requestId);
    }

    // ---- ApprovalWorkflow ------------------------------------------------

    @Override
    public Promise<ApprovalRequest> submit(
            String tenantId, String agentId, String actionType, Object context) {
        ApprovalRequest req = new ApprovalRequest(
            UUID.randomUUID().toString(),
            tenantId, agentId, actionType, context,
            ApprovalStatus.PENDING,
            Instant.now(), null, null
        );
        requests.put(req.requestId(), req);
        return Promise.of(req);
    }

    @Override
    public Promise<ApprovalRequest> approve(String requestId, String reviewerNote) {
        return decide(requestId, ApprovalStatus.APPROVED, reviewerNote);
    }

    @Override
    public Promise<ApprovalRequest> reject(String requestId, String reviewerNote) {
        return decide(requestId, ApprovalStatus.REJECTED, reviewerNote);
    }

    @Override
    public Promise<ApprovalRequest> get(String requestId) {
        ApprovalRequest req = requests.get(requestId);
        if (req == null) {
            return Promise.ofException(
                new IllegalArgumentException("No approval request found: " + requestId));
        }
        return Promise.of(req);
    }

    // ---- Internal -------------------------------------------------------

    private Promise<ApprovalRequest> decide(String requestId, ApprovalStatus status, String note) {
        ApprovalRequest existing = requests.get(requestId);
        if (existing == null) {
            return Promise.ofException(
                new IllegalArgumentException("No approval request found: " + requestId));
        }
        if (existing.status() != ApprovalStatus.PENDING) {
            return Promise.ofException(
                new IllegalStateException("Request " + requestId + " is already " + existing.status()));
        }
        ApprovalRequest updated = new ApprovalRequest(
            existing.requestId(), existing.tenantId(), existing.agentId(),
            existing.actionType(), existing.context(),
            status, existing.submittedAt(), Instant.now(), note
        );
        requests.put(requestId, updated);
        return Promise.of(updated);
    }
}
