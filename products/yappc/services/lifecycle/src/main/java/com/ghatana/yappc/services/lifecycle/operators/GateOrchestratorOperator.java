/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Lifecycle Service
 */
package com.ghatana.yappc.services.lifecycle.operators;

import com.ghatana.platform.domain.domain.event.Event;
import com.ghatana.platform.domain.domain.event.GEvent;
import com.ghatana.platform.types.identity.OperatorId;
import com.ghatana.platform.workflow.operator.AbstractOperator;
import com.ghatana.platform.workflow.operator.OperatorResult;
import com.ghatana.platform.workflow.operator.OperatorType;
import com.ghatana.governance.PolicyEngine;
import com.ghatana.yappc.services.lifecycle.ApprovalRequest;
import com.ghatana.yappc.services.lifecycle.HumanApprovalService;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Orchestrates quality gates for a validated lifecycle phase transition.
 *
 * <p><b>Pipeline Position</b><br>
 * Second operator in the {@code lifecycle-management-v1} AEP pipeline. Receives
 * {@code lifecycle.phase.transition.validated} events (emitted by
 * {@link PhaseTransitionValidatorOperator}) and:
 * <ul>
 *   <li>Runs a policy check via {@link YappcPolicyEngine} for the target stage</li>
 *   <li>If gate is already open ⟶ emits {@code lifecycle.gate.passed}</li>
 *   <li>If gate has unmet criteria ⟶ requests human approval via
 *       {@link HumanApprovalService} and emits {@code lifecycle.approval.requested}</li>
 *   <li>If policy denies ⟶ {@link OperatorResult#failed(String)}</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Orchestrates quality gates for lifecycle phase transitions
 * @doc.layer product
 * @doc.pattern Service
 * @doc.gaa.lifecycle reason
 */
public class GateOrchestratorOperator extends AbstractOperator {

    private static final Logger log = LoggerFactory.getLogger(GateOrchestratorOperator.class);

    /** Emitted when all gates pass automatically. */
    public static final String EVENT_GATE_PASSED      = "lifecycle.gate.passed";
    /** Emitted when human approval is requested to resolve unmet criteria. */
    public static final String EVENT_APPROVAL_REQUESTED = "lifecycle.approval.requested";
    /** Inbound event type from PhaseTransitionValidatorOperator. */
    public static final String EVENT_TRANSITION_VALIDATED =
            PhaseTransitionValidatorOperator.EVENT_TRANSITION_VALIDATED;

    private final PolicyEngine policyEngine;
    private final HumanApprovalService humanApprovalService;

    /**
     * Creates a {@code GateOrchestratorOperator}.
     *
     * @param policyEngine         evaluates lifecycle policies for the target stage
     * @param humanApprovalService issues human approval requests for unmet criteria
     */
    public GateOrchestratorOperator(
            PolicyEngine policyEngine,
            HumanApprovalService humanApprovalService) {
        super(
            OperatorId.of("yappc", "stream", "gate-orchestrator", "1.0.0"),
            OperatorType.STREAM,
            "Gate Orchestrator",
            "Orchestrates quality gates and human approval for lifecycle transitions",
            List.of("lifecycle.gate", "lifecycle.approval"),
            null
        );
        this.policyEngine          = Objects.requireNonNull(policyEngine, "policyEngine");
        this.humanApprovalService  = Objects.requireNonNull(humanApprovalService, "humanApprovalService");
    }

    @Override
    public Promise<OperatorResult> process(Event event) {
        Objects.requireNonNull(event, "event must not be null");

        String projectId   = payloadStr(event, "projectId");
        String fromPhase   = payloadStr(event, "fromPhase");
        String toPhase     = payloadStr(event, "toPhase");
        String tenantId    = payloadStr(event, "tenantId");
        String requestedBy = payloadStr(event, "requestedBy");
        boolean gateOpen   = Boolean.parseBoolean(payloadStr(event, "gateOpen"));

        // --- Policy check for the target stage ---
        String policyName = "lifecycle.stage." + toPhase;
        Map<String, Object> policyCtx = Map.of(
            "projectId",  projectId != null ? projectId : "",
            "fromPhase",  fromPhase != null ? fromPhase : "",
            "toPhase",    toPhase   != null ? toPhase   : "",
            "tenantId",   tenantId  != null ? tenantId  : ""
        );

        return policyEngine.evaluate(policyName, policyCtx)
            .then(policyPassed -> {
                if (!policyPassed) {
                    log.warn("Policy '{}' denied transition {}→{} for project={}",
                            policyName, fromPhase, toPhase, projectId);
                    return Promise.of(OperatorResult.failed("POLICY_DENIED: " + policyName));
                }

                if (gateOpen) {
                    // All criteria satisfied — proceed directly
                    Event gatePassedEvent = GEvent.builder()
                        .typeTenantVersion(tenantId, EVENT_GATE_PASSED, "v1")
                        .addPayload("projectId",   projectId)
                        .addPayload("fromPhase",   fromPhase)
                        .addPayload("toPhase",     toPhase)
                        .addPayload("tenantId",    tenantId)
                        .addPayload("requestedBy", requestedBy)
                        .addPayload("gateDecision", "AUTO_PASS")
                        .addPayload("decidedAt",   Instant.now().toString())
                        .build();

                    log.info("Gate passed (auto) for transition {}→{} project={}",
                            fromPhase, toPhase, projectId);
                    return Promise.of(OperatorResult.of(gatePassedEvent));
                }

                // Gate has unmet criteria — request human approval
                @SuppressWarnings("unchecked")
                List<String> unmetCriteria = (List<String>) event.getPayload("unmetCriteria");
                ApprovalRequest.ApprovalContext ctx = new ApprovalRequest.ApprovalContext(
                    fromPhase, toPhase, "Unmet entry criteria for stage " + toPhase,
                    unmetCriteria != null ? unmetCriteria : List.of(),
                    List.of()
                );

                return humanApprovalService.requestApproval(
                        tenantId, projectId, "gate-orchestrator-operator",
                        ApprovalRequest.ApprovalType.PHASE_ADVANCE, ctx)
                    .map(approvalRequest -> {
                        Event approvalEvent = GEvent.builder()
                            .typeTenantVersion(tenantId, EVENT_APPROVAL_REQUESTED, "v1")
                            .addPayload("projectId",    projectId)
                            .addPayload("fromPhase",    fromPhase)
                            .addPayload("toPhase",      toPhase)
                            .addPayload("tenantId",     tenantId)
                            .addPayload("requestedBy",  requestedBy)
                            .addPayload("approvalId",   approvalRequest.id())
                            .addPayload("requestedAt",  Instant.now().toString())
                            .build();
                        log.info("Human approval requested (id={}) for transition {}→{} project={}",
                                approvalRequest.id(), fromPhase, toPhase, projectId);
                        return OperatorResult.of(approvalEvent);
                    });
            });
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private static String payloadStr(Event event, String key) {
        Object v = event.getPayload(key);
        return v != null ? v.toString() : null;
    }

    @Override
    public Event toEvent() {
        return GEvent.builder()
                .type("operator.registered")
                .addPayload("operatorId", getId().toString())
                .addPayload("operatorName", getName())
                .addPayload("operatorType", getType().name())
                .addPayload("version", getVersion())
                .build();
    }
}
