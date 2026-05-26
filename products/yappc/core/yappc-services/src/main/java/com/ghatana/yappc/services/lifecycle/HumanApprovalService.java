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
 * <p><b>Lifecycle</b></p>
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
 * <p><b>Persistence</b></p>
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
    private final ApprovalNotificationService notificationService;
    private final ApprovalRiskScorer riskScorer;
    private final ApprovalAuditLogger auditLogger;
    private final ApprovalDecisionOutcomeService decisionOutcomeService;

    /**
     * @param publisher           AEP event publisher for request / decision events
     * @param notificationService notification broadcaster; may be null (disabled)
     * @param riskScorer          AI risk scorer for approval routing; may be null (disabled)
     * @param auditLogger         compliance audit logger; may be null (disabled)
     */
    public HumanApprovalService(
            AepEventPublisher publisher,
            ApprovalNotificationService notificationService,
            ApprovalRiskScorer riskScorer,
            ApprovalAuditLogger auditLogger) {
        this(publisher, notificationService, riskScorer, auditLogger, ApprovalDecisionOutcomeService.noop());
    }

    /**
     * @param publisher              AEP event publisher for request / decision events
     * @param notificationService    notification broadcaster; may be null (disabled)
     * @param riskScorer             AI risk scorer for approval routing; may be null (disabled)
     * @param auditLogger            compliance audit logger; may be null (disabled)
     * @param decisionOutcomeService learning/evolve decision side-effect service
     */
    public HumanApprovalService(
            AepEventPublisher publisher,
            ApprovalNotificationService notificationService,
            ApprovalRiskScorer riskScorer,
            ApprovalAuditLogger auditLogger,
            ApprovalDecisionOutcomeService decisionOutcomeService) {
        this.publisher            = Objects.requireNonNull(publisher, "publisher must not be null");
        this.notificationService  = notificationService;
        this.riskScorer           = riskScorer;
        this.auditLogger          = auditLogger;
        this.decisionOutcomeService = Objects.requireNonNull(decisionOutcomeService, "decisionOutcomeService");
    }

    /**
     * Minimal constructor for backward-compatible usage and unit tests.
     *
     * @param publisher AEP event publisher for request / decision events
     */
    public HumanApprovalService(AepEventPublisher publisher) {
        this(publisher, null, null, null);
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

        // Fire-and-forget: structured notification + async risk scoring
        if (notificationService != null) {
            notificationService.notifyRequested(req)
                    .whenComplete((v, e) -> {
                        if (e != null) log.warn("[tenant={}] Notification failed id={}: {}", tenantId, req.id(), e.getMessage());
                    });
        }
        if (auditLogger != null) {
            auditLogger.logCreated(req).whenComplete((v, e) -> {
                if (e != null) log.warn("[tenant={}] Audit write failed id={}: {}", tenantId, req.id(), e.getMessage());
            });
        }
        if (riskScorer != null) {
            riskScorer.score(req).whenComplete((riskScore, e) -> {
                if (e != null) {
                    log.warn("[tenant={}] Risk scoring failed id={}: {}", tenantId, req.id(), e.getMessage());
                } else if (riskScore != null) {
                    log.info("[tenant={}] Risk score id={} level={} requiredApprovers={}",
                            tenantId, req.id(), riskScore.level(), riskScore.requiredApproverCount());
                }
            });
        }

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
        ApprovalRequest.ApprovalStatus priorStatus = findById(tenantId, requestId)
                .map(ApprovalRequest::status)
                .orElse(null);
        ApprovalRequest updated = transition(tenantId, requestId, decidedBy, true);
        log.info("[tenant={}] Approval approved id={} by={} priorStatus={}",
                tenantId, requestId, decidedBy, priorStatus);
        publishDecision(updated);
        if (notificationService != null) {
            notificationService.notifyApproved(updated, decidedBy)
                    .whenComplete((v, e) -> {
                        if (e != null) log.warn("[tenant={}] Notify-approved failed id={}: {}", tenantId, requestId, e.getMessage());
                    });
        }
        if (auditLogger != null) {
            auditLogger.logApproved(updated, decidedBy, priorStatus).whenComplete((v, e) -> {
                if (e != null) log.warn("[tenant={}] Audit-approved failed id={}: {}", tenantId, requestId, e.getMessage());
            });
        }
        return decisionOutcomeService.recordDecision(updated).map(ignored -> updated);
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
        ApprovalRequest.ApprovalStatus priorStatus = findById(tenantId, requestId)
                .map(ApprovalRequest::status)
                .orElse(null);
        ApprovalRequest updated = transition(tenantId, requestId, decidedBy, false);
        log.info("[tenant={}] Approval rejected id={} by={} priorStatus={}",
                tenantId, requestId, decidedBy, priorStatus);
        publishDecision(updated);
        if (notificationService != null) {
            notificationService.notifyRejected(updated, decidedBy)
                    .whenComplete((v, e) -> {
                        if (e != null) log.warn("[tenant={}] Notify-rejected failed id={}: {}", tenantId, requestId, e.getMessage());
                    });
        }
        if (auditLogger != null) {
            auditLogger.logRejected(updated, decidedBy, priorStatus).whenComplete((v, e) -> {
                if (e != null) log.warn("[tenant={}] Audit-rejected failed id={}: {}", tenantId, requestId, e.getMessage());
            });
        }
        return decisionOutcomeService.recordDecision(updated).map(ignored -> updated);
    }

    /**
     * Transitions a PENDING request to the REVIEWING state, indicating that a human
     * reviewer has picked it up and is actively examining the request.
     *
     * @param tenantId  tenant context
     * @param requestId ID of the request to move into review
     * @return the updated (REVIEWING) {@link ApprovalRequest}
     * @throws IllegalArgumentException if the request is not found
     * @throws IllegalStateException    if the transition is not allowed
     */
    public Promise<ApprovalRequest> startReview(String tenantId, String requestId) {
        Objects.requireNonNull(tenantId,  "tenantId");
        Objects.requireNonNull(requestId, "requestId");

        Map<String, ApprovalRequest> tenantStore = store.get(tenantId);
        if (tenantStore == null || !tenantStore.containsKey(requestId)) {
            throw new IllegalArgumentException("Approval request not found: " + requestId);
        }
        ApprovalRequest current = tenantStore.get(requestId);
        ApprovalStateMachine.assertCanStartReview(current.status(), requestId);

        ApprovalRequest reviewing = current.asReviewing();
        tenantStore.put(requestId, reviewing);
        log.info("[tenant={}] Approval review started id={}", tenantId, requestId);
        if (auditLogger != null) {
            auditLogger.logReviewStarted(reviewing).whenComplete((v, e) -> {
                if (e != null) log.warn("[tenant={}] Audit-review-started failed id={}: {}", tenantId, requestId, e.getMessage());
            });
        }
        return Promise.of(reviewing);
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
        ApprovalStateMachine.assertCanDecide(current.status(), requestId);

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
        putIfPresent(m, "requestingAgentId", req.requestingAgentId());
        m.put("approvalType",       req.approvalType().name());
        m.put("status",             req.status().name());
        m.put("tenantId",           req.tenantId());
        m.put("createdAt",          req.createdAt().toString());
        putIfPresent(m, "decidedAt", req.decidedAt() != null ? req.decidedAt().toString() : null);
        putIfPresent(m, "decidedBy", req.decidedBy());
        if (req.context() != null) {
            putIfPresent(m, "fromPhase", req.context().fromPhase());
            putIfPresent(m, "toPhase", req.context().toPhase());
            putIfPresent(m, "blockReason", req.context().blockReason());
            m.put("unmetCriteria",   req.context().unmetCriteria());
            m.put("missingArtifacts",req.context().missingArtifacts());
            putIfPresent(m, "workflowId", req.context().workflowId());
            putIfPresent(m, "planId", req.context().planId());
            putIfPresent(m, "priorPlanId", req.context().priorPlanId());
            putIfPresent(m, "evolutionProposalId", req.context().evolutionProposalId());
        }
        return m;
    }

    private static void putIfPresent(Map<String, Object> payload, String key, Object value) {
        if (value != null) {
            payload.put(key, value);
        }
    }
}
