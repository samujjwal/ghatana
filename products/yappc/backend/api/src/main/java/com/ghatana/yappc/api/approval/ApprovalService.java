/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.approval;

import com.ghatana.yappc.api.approval.dto.*;
import com.ghatana.yappc.api.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Approval workflow service with in-memory state machine.
 *
 * <p>Manages multi-stage approval workflows with support for:
 * <ul>
 *   <li>Multi-stage sequential approval chains</li>
 *   <li>Per-stage approver lists and required approval counts</li>
 *   <li>Approve / reject decisions with comments</li>
 *   <li>Automatic stage advancement on quorum</li>
 *   <li>Workflow cancellation by initiator</li>
 *   <li>Tenant-isolated storage</li>
 * </ul>
 *
 * <p><b>Note:</b> This implementation uses in-memory storage.
 * Replace with a persistent store (PostgreSQL via platform:java:database)
 * before production use.
 *
 * @doc.type class
 * @doc.purpose Approval workflow state machine
 * @doc.layer service
 * @doc.pattern Service
 */
public class ApprovalService {

    private static final Logger logger = LoggerFactory.getLogger(ApprovalService.class);

    // Tenant → (WorkflowId → Workflow)
    protected final Map<String, Map<String, Workflow>> store = new ConcurrentHashMap<>();

    // =========================================================================
    // Domain Model (in-memory, package-private)
    // =========================================================================

    enum Status { PENDING, IN_PROGRESS, APPROVED, REJECTED, CANCELLED }
    enum Decision { APPROVE, REJECT }

    static final class Stage {
        final String name;
        final List<String> approvers;
        final int requiredApprovals;
        final boolean parallel;
        final List<ApprovalRecord> records = new ArrayList<>();

        Stage(String name, List<String> approvers, int requiredApprovals, boolean parallel) {
            this.name = name;
            this.approvers = approvers != null ? approvers : List.of();
            this.requiredApprovals = requiredApprovals > 0 ? requiredApprovals : 1;
            this.parallel = parallel;
        }

        long approveCount() {
            return records.stream().filter(r -> r.decision == Decision.APPROVE).count();
        }

        long rejectCount() {
            return records.stream().filter(r -> r.decision == Decision.REJECT).count();
        }

        boolean isResolved() {
            return approveCount() >= requiredApprovals || rejectCount() > 0;
        }

        boolean isApproved() {
            return approveCount() >= requiredApprovals;
        }
    }

    static final class ApprovalRecord {
        final String userId;
        final Decision decision;
        final String comments;
        final Instant timestamp;

        ApprovalRecord(String userId, Decision decision, String comments) {
            this.userId = userId;
            this.decision = decision;
            this.comments = comments;
            this.timestamp = Instant.now();
        }
    }

    static final class Workflow {
        final String id;
        final String tenantId;
        final String resourceType;
        final String resourceId;
        final String workflowType;
        final String initiator;
        final List<Stage> stages;
        Instant createdAt;  // package-private non-final for JDBC rehydration
        Status status;
        int currentStageIndex;
        Instant updatedAt;

        Workflow(String id, String tenantId, String resourceType, String resourceId,
                 String workflowType, String initiator, List<Stage> stages) {
            this.id = id;
            this.tenantId = tenantId;
            this.resourceType = resourceType;
            this.resourceId = resourceId;
            this.workflowType = workflowType;
            this.initiator = initiator;
            this.stages = stages;
            this.status = stages.isEmpty() ? Status.APPROVED : Status.PENDING;
            this.currentStageIndex = 0;
            this.createdAt = Instant.now();
            this.updatedAt = this.createdAt;
        }
    }

    // =========================================================================
    // Public API
    // =========================================================================

    public WorkflowResponse create(String tenantId, String userId, CreateWorkflowRequest req) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(req, "request");

        String workflowId = UUID.randomUUID().toString();
        List<Stage> stages = req.stages().stream()
                .map(s -> new Stage(s.name(), s.approvers(), s.requiredApprovals(), s.parallel()))
                .toList();

        Workflow wf = new Workflow(workflowId, tenantId,
                req.resourceType(), req.resourceId(), req.workflowType(), userId, stages);

        store.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>()).put(workflowId, wf);
        logger.info("[tenant={}] Created approval workflow {} (type={}, stages={})",
                tenantId, workflowId, req.workflowType(), stages.size());

        return toResponse(wf);
    }

    public WorkflowResponse get(String tenantId, String workflowId) {
        return toResponse(resolve(tenantId, workflowId));
    }

    public WorkflowResponse submitDecision(String tenantId, String userId,
                                            String workflowId, SubmitDecisionRequest req) {
        Workflow wf = resolve(tenantId, workflowId);

        if (wf.status == Status.CANCELLED) {
            throw new ApiException(409, "WORKFLOW_CANCELLED", "Workflow has been cancelled");
        }
        if (wf.status == Status.APPROVED || wf.status == Status.REJECTED) {
            throw new ApiException(409, "WORKFLOW_CLOSED",
                    "Workflow is already " + wf.status.name().toLowerCase());
        }
        if (wf.stages.isEmpty()) {
            throw new ApiException(409, "NO_STAGES", "Workflow has no stages");
        }

        Decision decision = parseDecision(req.decision());
        Stage currentStage = wf.stages.get(wf.currentStageIndex);

        // Check approver eligibility
        if (!currentStage.approvers.isEmpty() && !currentStage.approvers.contains(userId)) {
            throw new ApiException(403, "NOT_APPROVER",
                    "User is not an approver for stage: " + currentStage.name);
        }

        // Check for duplicate decision
        boolean alreadyDecided = currentStage.records.stream()
                .anyMatch(r -> r.userId.equals(userId));
        if (alreadyDecided) {
            throw new ApiException(409, "ALREADY_DECIDED",
                    "User has already submitted a decision for this stage");
        }

        // Record decision
        currentStage.records.add(new ApprovalRecord(userId, decision, req.comments()));
        wf.updatedAt = Instant.now();

        // Advance state machine
        if (decision == Decision.REJECT) {
            wf.status = Status.REJECTED;
            logger.info("[tenant={}] Workflow {} REJECTED at stage '{}' by {}",
                    tenantId, workflowId, currentStage.name, userId);
        } else if (currentStage.isApproved()) {
            // Advance to next stage or complete
            if (wf.currentStageIndex < wf.stages.size() - 1) {
                wf.currentStageIndex++;
                wf.status = Status.IN_PROGRESS;
                logger.info("[tenant={}] Workflow {} advanced to stage '{}' (index={})",
                        tenantId, workflowId,
                        wf.stages.get(wf.currentStageIndex).name, wf.currentStageIndex);
            } else {
                wf.status = Status.APPROVED;
                logger.info("[tenant={}] Workflow {} APPROVED (all stages complete)",
                        tenantId, workflowId);
            }
        } else {
            wf.status = Status.IN_PROGRESS;
        }

        return toResponse(wf);
    }

    public WorkflowResponse cancel(String tenantId, String userId, String workflowId) {
        Workflow wf = resolve(tenantId, workflowId);

        if (!wf.initiator.equals(userId)) {
            throw new ApiException(403, "NOT_INITIATOR",
                    "Only the workflow initiator can cancel");
        }
        if (wf.status == Status.APPROVED || wf.status == Status.REJECTED) {
            throw new ApiException(409, "WORKFLOW_CLOSED",
                    "Cannot cancel a workflow that is already " + wf.status.name().toLowerCase());
        }

        wf.status = Status.CANCELLED;
        wf.updatedAt = Instant.now();
        logger.info("[tenant={}] Workflow {} CANCELLED by {}", tenantId, workflowId, userId);

        return toResponse(wf);
    }

    public List<WorkflowResponse> getPending(String tenantId, String userId) {
        Map<String, Workflow> tenantStore = store.getOrDefault(tenantId, Map.of());
        return tenantStore.values().stream()
                .filter(wf -> wf.status == Status.PENDING || wf.status == Status.IN_PROGRESS)
                .filter(wf -> {
                    if (wf.stages.isEmpty()) return false;
                    Stage current = wf.stages.get(wf.currentStageIndex);
                    return current.approvers.isEmpty() || current.approvers.contains(userId);
                })
                .map(this::toResponse)
                .toList();
    }

    public List<ApprovalRecordResponse> getHistory(String tenantId, String workflowId) {
        Workflow wf = resolve(tenantId, workflowId);
        List<ApprovalRecordResponse> history = new ArrayList<>();
        for (int i = 0; i < wf.stages.size(); i++) {
            Stage stage = wf.stages.get(i);
            for (ApprovalRecord record : stage.records) {
                history.add(new ApprovalRecordResponse(
                        UUID.randomUUID().toString(),
                        i,
                        stage.name,
                        record.userId,
                        record.decision.name(),
                        record.comments,
                        record.timestamp.toString()));
            }
        }
        return history;
    }

    // =========================================================================
    // Internal
    // =========================================================================

    private Workflow resolve(String tenantId, String workflowId) {
        Map<String, Workflow> tenantStore = store.get(tenantId);
        if (tenantStore == null) {
            throw new ApiException(404, "NOT_FOUND", "Workflow not found: " + workflowId);
        }
        Workflow wf = tenantStore.get(workflowId);
        if (wf == null) {
            throw new ApiException(404, "NOT_FOUND", "Workflow not found: " + workflowId);
        }
        return wf;
    }

    private Decision parseDecision(String raw) {
        try {
            return Decision.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ApiException(400, "INVALID_DECISION",
                    "Decision must be APPROVE or REJECT, got: " + raw);
        }
    }

    private WorkflowResponse toResponse(Workflow wf) {
        List<StageResponse> stageResponses = new ArrayList<>();
        List<ApprovalRecordResponse> allRecords = new ArrayList<>();
        for (int i = 0; i < wf.stages.size(); i++) {
            Stage s = wf.stages.get(i);
            stageResponses.add(new StageResponse(s.name, s.approvers, List.of(), s.requiredApprovals, s.parallel));
            for (ApprovalRecord r : s.records) {
                allRecords.add(new ApprovalRecordResponse(
                        UUID.randomUUID().toString(), i, s.name,
                        r.userId, r.decision.name(), r.comments, r.timestamp.toString()));
            }
        }

        String currentStageName = wf.stages.isEmpty() ? ""
                : wf.stages.get(wf.currentStageIndex).name;

        return new WorkflowResponse(
                wf.id, wf.resourceType, wf.resourceId, wf.workflowType,
                wf.status.name(), wf.initiator, stageResponses, allRecords,
                wf.currentStageIndex, currentStageName,
                wf.createdAt.toString(), wf.updatedAt.toString());
    }
}
