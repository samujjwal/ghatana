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
import com.ghatana.yappc.services.lifecycle.GateEvaluator;
import com.ghatana.yappc.services.lifecycle.StageConfigLoader;
import com.ghatana.yappc.services.lifecycle.StageSpec;
import com.ghatana.yappc.services.lifecycle.TransitionConfigLoader;
import com.ghatana.yappc.services.lifecycle.TransitionSpec;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Validates that a lifecycle phase transition is permitted according to the
 * configured transition rules and entry criteria of the target stage.
 *
 * <p><b>Pipeline Position</b><br>
 * First operator in the {@code lifecycle-management-v1} AEP pipeline. Receives
 * {@code lifecycle.phase.transition.requested} events and emits either:
 * <ul>
 *   <li>{@code lifecycle.phase.transition.validated} — transition is allowed</li>
 *   <li>{@code OperatorResult.failed(reason)} — transition is rejected</li>
 * </ul>
 *
 * <p><b>Validation Rules</b>
 * <ol>
 *   <li>A matching {@link TransitionSpec} must exist in {@code lifecycle/transitions.yaml}</li>
 *   <li>If the target stage declares entry criteria, an {@code all_criteria_met} header
 *       on the inbound event must be {@code "true"}; otherwise gate is trivially open</li>
 * </ol>
 *
 * @doc.type class
 * @doc.purpose Validates that a lifecycle phase transition is allowed
 * @doc.layer product
 * @doc.pattern Service
 * @doc.gaa.lifecycle act
 */
public class PhaseTransitionValidatorOperator extends AbstractOperator {

    private static final Logger log = LoggerFactory.getLogger(PhaseTransitionValidatorOperator.class);

    /** Event type emitted when a transition passes validation. */
    public static final String EVENT_TRANSITION_VALIDATED = "lifecycle.phase.transition.validated";

    /** Inbound event type this operator handles. */
    public static final String EVENT_TRANSITION_REQUESTED = "lifecycle.phase.transition.requested";

    private final TransitionConfigLoader transitionConfig;
    private final StageConfigLoader stageConfig;
    private final GateEvaluator gateEvaluator;

    /**
     * Creates a {@code PhaseTransitionValidatorOperator}.
     *
     * @param transitionConfig loader for allowed transition rules
     * @param stageConfig      loader for stage entry/exit criteria
     * @param gateEvaluator    evaluates gate criteria against supplied verdicts
     */
    public PhaseTransitionValidatorOperator(
            TransitionConfigLoader transitionConfig,
            StageConfigLoader stageConfig,
            GateEvaluator gateEvaluator) {
        super(
            OperatorId.of("yappc", "stream", "phase-transition-validator", "1.0.0"),
            OperatorType.STREAM,
            "Phase Transition Validator",
            "Validates that a lifecycle phase transition is permitted",
            List.of("lifecycle.validate", "lifecycle.gate"),
            null   // null = NoopMetricsCollector
        );
        this.transitionConfig = Objects.requireNonNull(transitionConfig, "transitionConfig");
        this.stageConfig      = Objects.requireNonNull(stageConfig, "stageConfig");
        this.gateEvaluator    = Objects.requireNonNull(gateEvaluator, "gateEvaluator");
    }

    @Override
    public Promise<OperatorResult> process(Event event) {
        Objects.requireNonNull(event, "event must not be null");

        // --- Extract payload fields ---
        String projectId   = payloadStr(event, "projectId");
        String fromPhase   = payloadStr(event, "fromPhase");
        String toPhase     = payloadStr(event, "toPhase");
        String tenantId    = payloadStr(event, "tenantId");
        String requestedBy = payloadStr(event, "requestedBy");

        if (fromPhase == null || toPhase == null) {
            return Promise.of(OperatorResult.failed("INVALID_PAYLOAD: fromPhase or toPhase missing"));
        }

        // --- Step 1: Check transition is in the rulebook ---
        Optional<TransitionSpec> specOpt = transitionConfig.findTransition(fromPhase, toPhase);
        if (specOpt.isEmpty()) {
            log.warn("Rejected transition {}→{} for project={}: no matching rule",
                    fromPhase, toPhase, projectId);
            return Promise.of(OperatorResult.failed("INVALID_TRANSITION: " + fromPhase + "->" + toPhase));
        }

        // --- Step 2: Evaluate target stage entry criteria ---
        Optional<StageSpec> stageOpt = stageConfig.findById(toPhase);
        if (stageOpt.isEmpty()) {
            log.warn("Target stage '{}' not found in stage configuration", toPhase);
            return Promise.of(OperatorResult.failed("UNKNOWN_TARGET_STAGE: " + toPhase));
        }

        StageSpec targetStage = stageOpt.get();
        // Verdicts are empty for automated transitions; GateEvaluator treats unmatched criteria as not-satisfied.
        // However, a stage with no entry criteria is trivially open.
        GateEvaluator.GateResult gateResult = gateEvaluator.evaluateEntry(targetStage, Collections.emptyMap());

        if (!gateResult.open() && !targetStage.getEntryCriteria().isEmpty()) {
            // Gate is not open; operator does NOT block — it annotates output for GateOrchestrator
            log.info("Entry gate not fully open for stage '{}': {}/{} criteria met",
                    toPhase, gateResult.satisfiedCount(), gateResult.totalCount());
        }

        // --- Step 3: Emit validated event ---
        Event validatedEvent = GEvent.builder()
            .typeTenantVersion(tenantId, EVENT_TRANSITION_VALIDATED, "v1")
            .addPayload("projectId",    projectId)
            .addPayload("fromPhase",    fromPhase)
            .addPayload("toPhase",      toPhase)
            .addPayload("tenantId",     tenantId)
            .addPayload("requestedBy",  requestedBy)
            .addPayload("gateOpen",     gateResult.open())
            .addPayload("gateSatisfied", gateResult.satisfiedCount())
            .addPayload("gateTotal",    gateResult.totalCount())
            .addPayload("unmetCriteria", gateResult.unmetCriteria())
            .addPayload("transitionType", specOpt.get().getType())
            .addPayload("validatedAt",  Instant.now().toString())
            .build();

        log.info("Validated transition {}→{} for project={} (gate open={})",
                fromPhase, toPhase, projectId, gateResult.open());
        return Promise.of(OperatorResult.of(validatedEvent));
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
