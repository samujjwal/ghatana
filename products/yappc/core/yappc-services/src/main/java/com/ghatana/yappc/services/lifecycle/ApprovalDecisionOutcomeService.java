package com.ghatana.yappc.services.lifecycle;

import com.ghatana.platform.governance.security.TenantContext;
import com.ghatana.yappc.services.evolve.EvolutionService;
import com.ghatana.yappc.services.learn.LearningEvidenceService;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Applies side effects that must happen after a terminal human approval decision.
 *
 * @doc.type class
 * @doc.purpose Records approval decisions as learning evidence and bridges explicit evolution approvals
 * @doc.layer product
 * @doc.pattern Service
 * @doc.gaa.lifecycle act
 */
public final class ApprovalDecisionOutcomeService {

    private static final ApprovalDecisionOutcomeService NOOP = new ApprovalDecisionOutcomeService();

    private final LearningEvidenceService learningEvidenceService;
    private final EvolutionService evolutionService;
    private final boolean enabled;

    /**
     * Creates a decision outcome service with learning and evolution integrations.
     *
     * @param learningEvidenceService service that persists Learn evidence
     * @param evolutionService service that applies evolution proposal approvals
     */
    public ApprovalDecisionOutcomeService(
            @NotNull LearningEvidenceService learningEvidenceService,
            @NotNull EvolutionService evolutionService) {
        this.learningEvidenceService = Objects.requireNonNull(learningEvidenceService, "learningEvidenceService");
        this.evolutionService = Objects.requireNonNull(evolutionService, "evolutionService");
        this.enabled = true;
    }

    private ApprovalDecisionOutcomeService() {
        this.learningEvidenceService = LearningEvidenceService.noop();
        this.evolutionService = null;
        this.enabled = false;
    }

    /**
     * Returns a no-op implementation for tests or legacy constructors.
     *
     * @return disabled decision outcome service
     */
    public static ApprovalDecisionOutcomeService noop() {
        return NOOP;
    }

    /**
     * Records the terminal decision and, when the request explicitly names an
     * evolution proposal, applies the same decision to the evolve loop.
     *
     * @param request terminal approval request
     * @return promise that completes when required decision side effects complete
     */
    public Promise<Void> recordDecision(@NotNull ApprovalRequest request) {
        Objects.requireNonNull(request, "request");
        if (!enabled || !isTerminalDecision(request)) {
            return Promise.complete();
        }

        LearningEvidenceService.EvidenceContext evidenceContext = evidenceContext(request);
        return learningEvidenceService.recordApprovalDecisionOutcome(evidenceContext, request)
                .then(evidenceId -> applyEvolutionDecision(request, evidenceId))
                .map(ignored -> null);
    }

    private Promise<?> applyEvolutionDecision(ApprovalRequest request, String evidenceId) {
        String proposalId = evolutionProposalId(request);
        if (proposalId == null) {
            return Promise.of(evidenceId);
        }

        String priorTenant = null;
        try {
            priorTenant = TenantContext.getCurrentTenantId();
        } catch (RuntimeException ignored) {
            // Production request paths should already be scoped; the approval request is the fail-closed fallback.
        }
        TenantContext.setCurrentTenantId(request.tenantId());
        try {
            String reason = decisionReason(request, evidenceId);
            if (request.status() == ApprovalRequest.ApprovalStatus.APPROVED) {
                return evolutionService.approveProposal(proposalId, request.decidedBy(), reason);
            }
            if (request.status() == ApprovalRequest.ApprovalStatus.REJECTED) {
                return evolutionService.rejectProposal(proposalId, request.decidedBy(), reason);
            }
            return Promise.of(evidenceId);
        } finally {
            if (priorTenant == null) {
                TenantContext.clear();
            } else {
                TenantContext.setCurrentTenantId(priorTenant);
            }
        }
    }

    private static LearningEvidenceService.EvidenceContext evidenceContext(ApprovalRequest request) {
        requireNonBlank(request.tenantId(), "tenantId");
        requireNonBlank(request.projectId(), "projectId");
        requireNonBlank(request.id(), "approvalRequestId");

        ApprovalRequest.ApprovalContext approvalContext = request.context();
        String workspaceRef = approvalContext != null && nonBlank(approvalContext.workflowId())
                ? approvalContext.workflowId()
                : request.projectId();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("approvalRequestId", request.id());
        putIfPresent(metadata, "requestingAgentId", request.requestingAgentId());
        metadata.put("approvalStatus", request.status().name());
        if (approvalContext != null) {
            putIfPresent(metadata, "workflowId", approvalContext.workflowId());
            putIfPresent(metadata, "planId", approvalContext.planId());
            putIfPresent(metadata, "priorPlanId", approvalContext.priorPlanId());
            putIfPresent(metadata, "evolutionProposalId", approvalContext.evolutionProposalId());
            putIfPresent(metadata, "fromPhase", approvalContext.fromPhase());
            putIfPresent(metadata, "toPhase", approvalContext.toPhase());
        }

        return new LearningEvidenceService.EvidenceContext(
                request.tenantId(),
                workspaceRef,
                request.projectId(),
                request.id(),
                approvalContext != null ? approvalContext.workflowId() : null,
                metadata);
    }

    private static String evolutionProposalId(ApprovalRequest request) {
        if (request.context() == null || !nonBlank(request.context().evolutionProposalId())) {
            return null;
        }
        return request.context().evolutionProposalId();
    }

    private static String decisionReason(ApprovalRequest request, String evidenceId) {
        String blockReason = request.context() != null ? request.context().blockReason() : null;
        String reason = nonBlank(blockReason) ? blockReason : "Human approval decision";
        return reason + " (learningEvidenceId=" + evidenceId + ")";
    }

    private static boolean isTerminalDecision(ApprovalRequest request) {
        return request.status() == ApprovalRequest.ApprovalStatus.APPROVED
                || request.status() == ApprovalRequest.ApprovalStatus.REJECTED;
    }

    private static boolean nonBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static void putIfPresent(Map<String, Object> metadata, String key, Object value) {
        if (value != null) {
            metadata.put(key, value);
        }
    }

    private static void requireNonBlank(String value, String name) {
        if (!nonBlank(value)) {
            throw new IllegalArgumentException(name + " is required for approval decision evidence");
        }
    }
}
