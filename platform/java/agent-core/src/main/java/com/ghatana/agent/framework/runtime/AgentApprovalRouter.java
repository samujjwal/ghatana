/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.runtime;

import com.ghatana.agent.AgentResult;
import com.ghatana.agent.AgentResultStatus;
import com.ghatana.agent.framework.governance.ActionIntent;
import com.ghatana.agent.framework.governance.PolicyDecision;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Risk-aware approval router that evaluates {@link ActionIntent actions}
 * against governance {@link PolicyDecision decisions} and routes to approval
 * when required.
 *
 * <p>This replaces confidence-only approval gating with a full risk-aware
 * governance pipeline: action classification → policy evaluation → approval routing.
 *
 * @param <O> the agent output type
 *
 * @doc.type class
 * @doc.purpose Risk-aware action routing through governance policy evaluation
 * @doc.layer framework
 * @doc.pattern Strategy, Mediator
 * @doc.gaa.lifecycle act
 */
public final class AgentApprovalRouter<O> {

    private static final Logger log = LoggerFactory.getLogger(AgentApprovalRouter.class);

    private final PolicyEvaluator policyEvaluator;
    private final ApprovalRequestHandler<O> approvalHandler;
    private final ApprovalResumeHandler<O> resumeHandler;

    /**
     * Evaluates a policy decision for an action intent.
     */
    @FunctionalInterface
    public interface PolicyEvaluator {
        @NotNull Promise<PolicyDecision> evaluate(@NotNull ActionIntent intent);
    }

    /**
     * Handles creation and tracking of approval requests.
     */
    @FunctionalInterface
    public interface ApprovalRequestHandler<O> {
        @NotNull Promise<AgentResult<O>> requestApproval(
                @NotNull ApprovalRequest request,
                @NotNull AgentResult<O> pendingResult);
    }

    /**
     * Handles resuming agent execution after approval is granted.
     */
    @FunctionalInterface
    public interface ApprovalResumeHandler<O> {
        @NotNull Promise<AgentResult<O>> resumeAfterApproval(
                @NotNull String requestId,
                @NotNull ApprovalDecision decision,
                @NotNull String reviewerId,
                @NotNull String reviewerNote);
    }

    public AgentApprovalRouter(
            @NotNull PolicyEvaluator policyEvaluator,
            @NotNull ApprovalRequestHandler<O> approvalHandler) {
        this(policyEvaluator, approvalHandler, null);
    }

    public AgentApprovalRouter(
            @NotNull PolicyEvaluator policyEvaluator,
            @NotNull ApprovalRequestHandler<O> approvalHandler,
            @Nullable ApprovalResumeHandler<O> resumeHandler) {
        this.policyEvaluator = Objects.requireNonNull(policyEvaluator);
        this.approvalHandler = Objects.requireNonNull(approvalHandler);
        this.resumeHandler = resumeHandler;
    }

    /**
     * Routes an agent result through governance evaluation.
     *
     * <p>Non-privileged actions pass through immediately. Privileged actions
     * undergo policy evaluation and may be allowed, denied, or routed for approval.
     *
     * @param result the agent's computed result
     * @param intent the action intent describing what the agent wants to do
     * @return the original result (if allowed), a DENIED result, or a PENDING_APPROVAL result
     */
    @NotNull
    public Promise<AgentResult<O>> route(
            @NotNull AgentResult<O> result,
            @NotNull ActionIntent intent) {

        Objects.requireNonNull(result);
        Objects.requireNonNull(intent);

        // Failed results pass through — governance only gates successful actions
        if (result.isFailed()) {
            return Promise.of(result);
        }

        // Non-privileged actions pass through
        if (!intent.isPrivileged()) {
            log.debug("AgentApprovalRouter: action {} is non-privileged, passing through",
                    intent.actionClass());
            return Promise.of(result);
        }

        // Evaluate policy for privileged actions
        return policyEvaluator.evaluate(intent)
                .then(decision -> handleDecision(result, intent, decision));
    }

    private Promise<AgentResult<O>> handleDecision(
            AgentResult<O> result,
            ActionIntent intent,
            PolicyDecision decision) {

        log.info("AgentApprovalRouter: policy decision={} for agent={} action={}",
                decision.decision(), intent.agentId(), intent.actionClass());

        return switch (decision.decision()) {
            case ALLOW, ALLOW_WITH_MONITORING -> Promise.of(result);

            case DENY -> Promise.of(AgentResult.<O>builder()
                    .confidence(0.0)
                    .status(AgentResultStatus.DENIED)
                    .agentId(result.getAgentId())
                    .explanation("Action denied by governance policy: "
                            + String.join("; ", decision.reasons()))
                    .build());

            case ESCALATE -> Promise.of(AgentResult.<O>builder()
                    .confidence(0.0)
                    .status(AgentResultStatus.PENDING_APPROVAL)
                    .agentId(result.getAgentId())
                    .explanation("Action escalated for review: "
                            + String.join("; ", decision.reasons()))
                    .build());

            case ALLOW_WITH_APPROVAL -> {
                ApprovalRequest request = new ApprovalRequest(
                        UUID.randomUUID().toString(),
                        intent.traceId(),
                        intent,
                        buildActionSummary(intent),
                        buildRiskSummary(intent, decision),
                        String.join("; ", decision.reasons()),
                        decision.requiredApprovals(),
                        decision.expiresAt(),
                        ApprovalStatus.PENDING,
                        Instant.now());
                yield approvalHandler.requestApproval(request, result);
            }

            case ALLOW_WITH_COMPENSATION -> Promise.of(result);
        };
    }

    private String buildActionSummary(ActionIntent intent) {
        return "Agent '%s' wants to perform %s on %s (target: %s)"
                .formatted(intent.agentId(), intent.actionClass(),
                        intent.targetType(), intent.targetId());
    }

    private String buildRiskSummary(ActionIntent intent, PolicyDecision decision) {
        return "Criticality: %s, Reversibility: %s, Policies: %s"
                .formatted(intent.criticality(), intent.reversibilityClass(),
                        String.join(", ", decision.policyRefsApplied()));
    }

    /**
     * Resumes agent execution after an approval decision is made.
     *
     * <p>AGENTS-P1-001: Provides a mechanism to resume agent execution when
     * a pending approval is granted or rejected. This enables the full approval
     * lifecycle: request → pending → decision → resume.
     *
     * @param requestId the approval request ID
     * @param decision the approval decision (APPROVED or REJECTED)
     * @param reviewerId the ID of the reviewer who made the decision
     * @param reviewerNote optional notes from the reviewer
     * @return promise completing with the resumed agent result
     */
    @NotNull
    public Promise<AgentResult<O>> resumeAfterApproval(
            @NotNull String requestId,
            @NotNull ApprovalDecision decision,
            @NotNull String reviewerId,
            @NotNull String reviewerNote) {
        
        if (resumeHandler == null) {
            log.warn("AgentApprovalRouter: resumeAfterApproval called but no resumeHandler configured");
            return Promise.of(AgentResult.<O>builder()
                    .confidence(0.0)
                    .status(AgentResultStatus.FAILED)
                    .explanation("Approval resume handler not configured")
                    .build());
        }

        log.info("AgentApprovalRouter: resuming after approval decision={} requestId={} reviewerId={}",
                decision, requestId, reviewerId);

        return resumeHandler.resumeAfterApproval(requestId, decision, reviewerId, reviewerNote);
    }
}
