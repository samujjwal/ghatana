/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Lifecycle Service
 */
package com.ghatana.yappc.services.lifecycle;

import com.ghatana.yappc.agent.AepEventPublisher;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages human-approval gates in the YAPPC lifecycle pipeline.
 *
 * <h2>Lifecycle</h2>
 * <ol>
 *   <li>{@link #requestApproval} — creates a new {@link ApprovalRequest} (PENDING) and
 *       publishes an {@code approval.requested} AEP event so the frontend can push a
 *       WebSocket notification to reviewers.</li>
 *   <li>{@link #approve} / {@link #reject} — a human submits a decision via the REST
 *       API; the request moves to APPROVED / REJECTED and an {@code approval.decided}
 *       AEP event is fired so listeners can resume the blocked pipeline.</li>
 *   <li>{@link #pendingFor} — returns all PENDING requests for a project so the UI can
 *       show a review queue.</li>
 * </ol>
 *
 * <h2>Persistence</h2>
 * <p>The current implementation uses an in-memory {@link ConcurrentHashMap} indexed
 * by tenant. This is sufficient for single-node deployments. A future
 * {@code JdbcHumanApprovalService} should extend this class and override the mutation
 * methods to also write to the {@code yappc.approval_requests} table (see V18 migration).
 *
 * @doc.type class
 * @doc.purpose Lifecycle human-approval gate service
 * @doc.layer product
 * @doc.pattern Service
 * @doc.gaa.lifecycle act
 */
public class HumanApprovalService {

    private static final Logger log = LoggerFactory.getLogger(HumanApprovalService.class);

    /** AEP event types. */
    private static final String EVENT_APPROVAL_REQUESTED = "approval.requested";
    private static final String EVENT_APPROVAL_DECIDED   = "approval.decided";

    // tenant → (requestId → request)
    private final Map<String, Map<String, ApprovalRequest>> store = new ConcurrentHashMap<>();

    private final AepEventPublisher publisher;

    /**
     * @param publisher AEP event publisher for request / decision events
     */
    public HumanApprovalService(AepEventPublisher publisher) {
        this.publisher = Objects.requireNonNull(publisher, "publisher must not be null");
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Creates a new approval request in the PENDING state and notifies AEP.
     *
     * @param tenantId           tenant owning the request
     * @param projectId          project requiring the approval
     * @param requestingAgentId  agent responsible for the blocked step (may be {@code null})
     * @param approvalType       the type of gate requiring approval
     * @param context            structured context for the reviewer
     * @return the freshly created, PENDING {@link ApprovalRequest}
     */
    public Promise<ApprovalRequest> requestApproval(
            String tenantId,
            String projectId,
            String requestingAgentId,
            ApprovalRequest.ApprovalType approvalType,
            ApprovalRequest.ApprovalContext context) {

        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(approvalType, "approvalType");
        Objects.requireNonNull(context, "context");

        ApprovalRequest req = new ApprovalRequest(
                UUID.randomUUID().toString(),
                projectId,
                requestingAgentId,
                approvalType,
                context,
                ApprovalRequest.ApprovalStatus.PENDING,
                tenantId,
                Instant.now(),
                null,
                null,
                null);

        store.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>()).put(req.id(), req);
        log.info("[tenant={}] Approval requested id={} type={} project={}", tenantId, req.id(), approvalType, projectId);

        // Fire-and-forget: notify AEP listeners (e.g., WebSocket push to frontend)
        publisher.publish(EVENT_APPROVAL_REQUESTED, tenantId, toPayload(req))
                .whenComplete((v, e) -> {
                    if (e != null) {
                        log.warn("AEP approval.requested event failed: {}", e.getMessage());
                    }
                });

        return Promise.of(req);
    }

    /**
     * Approves a pending approval request.
     *
     * @param tenantId   tenant context (used for isolation)
     * @param requestId  ID of the request to approve
     * @param decidedBy  user ID making the decision
     * @return the updated (APPROVED) {@link ApprovalRequest}
     * @throws IllegalArgumentException if the request is not found or already decided
     */
    public Promise<ApprovalRequest> approve(String tenantId, String requestId, String decidedBy) {
        ApprovalRequest updated = transition(tenantId, requestId, decidedBy, true);
        log.info("[tenant={}] Approval approved id={} by={}", tenantId, requestId, decidedBy);
        publishDecision(updated);
        return Promise.of(updated);
    }

    /**
     * Rejects a pending approval request.
     *
     * @param tenantId   tenant context
     * @param requestId  ID of the request to reject
     * @param decidedBy  user ID making the decision
     * @return the updated (REJECTED) {@link ApprovalRequest}
     * @throws IllegalArgumentException if the request is not found or already decided
     */
    public Promise<ApprovalRequest> reject(String tenantId, String requestId, String decidedBy) {
        ApprovalRequest updated = transition(tenantId, requestId, decidedBy, false);
        log.info("[tenant={}] Approval rejected id={} by={}", tenantId, requestId, decidedBy);
        publishDecision(updated);
        return Promise.of(updated);
    }

    /**
     * Returns all PENDING approval requests for a given project.
     *
     * @param tenantId  tenant context
     * @param projectId project to query
     * @return unmodifiable list of PENDING requests, ordered by creation time (newest first)
     */
    public List<ApprovalRequest> pendingFor(String tenantId, String projectId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(projectId, "projectId");

        Map<String, ApprovalRequest> tenantStore = store.getOrDefault(tenantId, Map.of());
        return tenantStore.values().stream()
                .filter(r -> r.isPending() && projectId.equals(r.projectId()))
                .sorted((a, b) -> b.createdAt().compareTo(a.createdAt()))
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Returns all PENDING approval requests for a tenant (across all projects).
     *
     * @param tenantId tenant context
     * @return unmodifiable list of all PENDING requests, newest first
     */
    public List<ApprovalRequest> allPending(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId");

        Map<String, ApprovalRequest> tenantStore = store.getOrDefault(tenantId, Map.of());
        return tenantStore.values().stream()
                .filter(ApprovalRequest::isPending)
                .sorted((a, b) -> b.createdAt().compareTo(a.createdAt()))
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Finds a request by its ID within the tenant scope.
     *
     * @param tenantId  tenant context
     * @param requestId request ID
     * @return the request if found, empty otherwise
     */
    public Optional<ApprovalRequest> findById(String tenantId, String requestId) {
        return Optional.ofNullable(
                store.getOrDefault(tenantId, Map.of()).get(requestId));
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    private ApprovalRequest transition(String tenantId, String requestId, String decidedBy, boolean approve) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(decidedBy, "decidedBy");

        Map<String, ApprovalRequest> tenantStore = store.get(tenantId);
        if (tenantStore == null || !tenantStore.containsKey(requestId)) {
            throw new IllegalArgumentException("Approval request not found: " + requestId);
        }
        ApprovalRequest current = tenantStore.get(requestId);
        if (!current.isPending()) {
            throw new IllegalStateException(
                    "Approval request " + requestId + " is already in state " + current.status());
        }

        ApprovalRequest updated = approve ? current.asApproved(decidedBy) : current.asRejected(decidedBy);
        tenantStore.put(requestId, updated);
        return updated;
    }

    private void publishDecision(ApprovalRequest req) {
        publisher.publish(EVENT_APPROVAL_DECIDED, req.tenantId(), toPayload(req))
                .whenComplete((v, e) -> {
                    if (e != null) {
                        log.warn("AEP approval.decided event failed id={}: {}", req.id(), e.getMessage());
                    }
                });
    }

    private static Map<String, Object> toPayload(ApprovalRequest req) {
        var m = new java.util.LinkedHashMap<String, Object>();
        m.put("requestId",          req.id());
        m.put("projectId",          req.projectId());
        m.put("requestingAgentId",  req.requestingAgentId());
        m.put("approvalType",       req.approvalType().name());
        m.put("status",             req.status().name());
        m.put("tenantId",           req.tenantId());
        m.put("createdAt",          req.createdAt().toString());
        m.put("decidedAt",          req.decidedAt() != null ? req.decidedAt().toString() : null);
        m.put("decidedBy",          req.decidedBy());
        if (req.context() != null) {
            m.put("fromPhase",       req.context().fromPhase());
            m.put("toPhase",         req.context().toPhase());
            m.put("blockReason",     req.context().blockReason());
            m.put("unmetCriteria",   req.context().unmetCriteria());
            m.put("missingArtifacts",req.context().missingArtifacts());
        }
        return m;
    }
}
