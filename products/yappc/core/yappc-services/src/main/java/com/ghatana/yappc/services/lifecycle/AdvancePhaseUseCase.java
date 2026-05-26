/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Lifecycle Service
 */
package com.ghatana.yappc.services.lifecycle;

import com.ghatana.governance.PolicyEngine;
import com.ghatana.yappc.api.PhasePacket;
import com.ghatana.yappc.services.capability.CapabilityEvaluationService;
import com.ghatana.yappc.services.lifecycle.assessment.AIReadinessAssessor;
import com.ghatana.yappc.services.lifecycle.assessment.ProjectContext;
import com.ghatana.yappc.services.lifecycle.dlq.DlqPublisher;
import com.ghatana.yappc.services.phase.PhaseActionAuthorizationService;
import com.ghatana.yappc.storage.YappcArtifactRepository;
import io.activej.promise.Promise;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Core use case: validates and executes a YAPPC lifecycle phase transition.
 *
 * <p>Execution pipeline:
 * <ol>
 *   <li>Verify the requested {@code from → to} transition is declared in
 *       {@code lifecycle/transitions.yaml}.</li>
 *   <li>Check all required artifacts from the transition spec are present in the
 *       artifact store for the project.</li>
 *   <li>Run the AI + hard readiness assessment ({@link AIReadinessAssessor}) if configured.</li>
 *   <li>Evaluate the {@code phase_advance_policy} against the policy engine.</li>
 *   <li>If all gates pass, return {@link TransitionResult#success(String)}.</li>
 *   <li>Otherwise, return an appropriate {@link TransitionResult#blocked(String, String)}
 *       <em>and</em> publish the failed transition to the DLQ for audit/retry.</li>
 * </ol>
 *
 * <p>Artifact checks are fully async via ActiveJ Promise chaining.
 * DLQ publication is fire-and-forget — failures do not block the response.
 *
 * @doc.type class
 * @doc.purpose Core lifecycle phase transition use case with policy, artifact, and AI readiness gate enforcement
 * @doc.layer product
 * @doc.pattern Service
 * @doc.gaa.lifecycle act
 */
public class AdvancePhaseUseCase {

    private static final Logger log = LoggerFactory.getLogger(AdvancePhaseUseCase.class);

    private static final String PIPELINE_ID = "lifecycle-management-v1";
    private static final String NODE_ID     = "advance-phase";

    private final TransitionConfigLoader transitionConfig;
    private final PolicyEngine policyEngine;
    private final YappcArtifactRepository artifactRepository;
    private final DlqPublisher dlqPublisher;
    private final CapabilityEvaluationService capabilityEvaluationService;
    private final PhaseActionAuthorizationService phaseActionAuthorizationService;
    /** Optional AI readiness assessor; if null, AI gate is skipped. */
    private final @Nullable AIReadinessAssessor readinessAssessor;

    /**
     * Constructs the use case without an AI readiness assessor (AI gate skipped).
     *
     * @param transitionConfig   loaded transition rules from {@code transitions.yaml}
     * @param policyEngine       policy gate evaluator
     * @param artifactRepository artifact presence checker
     * @param dlqPublisher       DLQ sink for blocked / invalid transitions
     */
    public AdvancePhaseUseCase(
            TransitionConfigLoader transitionConfig,
            PolicyEngine policyEngine,
            YappcArtifactRepository artifactRepository,
            DlqPublisher dlqPublisher) {
        this(
                transitionConfig,
                policyEngine,
                artifactRepository,
                dlqPublisher,
                request -> Promise.of(CapabilityEvaluationService.CapabilityModel.allDenied(
                        "CapabilityEvaluationService is not configured")),
                new PhaseActionAuthorizationService(),
                null);
    }

    /**
     * Constructs the use case with an optional AI readiness assessor.
     *
     * @param transitionConfig   loaded transition rules from {@code transitions.yaml}
     * @param policyEngine       policy gate evaluator
     * @param artifactRepository artifact presence checker
     * @param dlqPublisher       DLQ sink for blocked / invalid transitions
     * @param readinessAssessor  AI readiness assessor; {@code null} to skip AI gate
     */
    public AdvancePhaseUseCase(
            TransitionConfigLoader transitionConfig,
            PolicyEngine policyEngine,
            YappcArtifactRepository artifactRepository,
            DlqPublisher dlqPublisher,
            @Nullable AIReadinessAssessor readinessAssessor) {
        this(
                transitionConfig,
                policyEngine,
                artifactRepository,
                dlqPublisher,
                request -> Promise.of(CapabilityEvaluationService.CapabilityModel.allDenied(
                        "CapabilityEvaluationService is not configured")),
                new PhaseActionAuthorizationService(),
                readinessAssessor);
    }

    /**
     * Constructs the use case with backend phase action authorization.
     *
     * @param transitionConfig loaded transition rules
     * @param policyEngine policy gate evaluator
     * @param artifactRepository artifact presence checker
     * @param dlqPublisher DLQ sink for blocked / invalid transitions
     * @param capabilityEvaluationService backend capability evaluator
     * @param phaseActionAuthorizationService phase action authorization contract service
     * @param readinessAssessor AI readiness assessor; {@code null} to skip AI gate
     */
    public AdvancePhaseUseCase(
            TransitionConfigLoader transitionConfig,
            PolicyEngine policyEngine,
            YappcArtifactRepository artifactRepository,
            DlqPublisher dlqPublisher,
            CapabilityEvaluationService capabilityEvaluationService,
            PhaseActionAuthorizationService phaseActionAuthorizationService,
            @Nullable AIReadinessAssessor readinessAssessor) {
        this.transitionConfig    = transitionConfig;
        this.policyEngine        = policyEngine;
        this.artifactRepository  = artifactRepository;
        this.dlqPublisher        = dlqPublisher;
        this.capabilityEvaluationService = capabilityEvaluationService;
        this.phaseActionAuthorizationService = phaseActionAuthorizationService;
        this.readinessAssessor   = readinessAssessor;
    }

    /**
     * Executes the phase transition for the given request.
     *
     * <p>Returns a {@link Promise} that resolves with a {@link TransitionResult}.
     * Never rejects — errors are encoded as blocked results with a suitable block code.
     *
     * @param request the transition request
     * @return {@link Promise} of {@link TransitionResult}
     */
    public Promise<TransitionResult> execute(TransitionRequest request) {
        log.info("AdvancePhaseUseCase: {} â†’ {} for project={} tenant={}",
            request.fromPhase(), request.toPhase(), request.projectId(), request.tenantId());

        return authorizePhaseAdvance(request)
                .then(authorizationResult -> authorizationResult
                        .map(result -> {
                            publishToDlq(request, result);
                            return Promise.of(result);
                        })
                        .orElseGet(() -> executeAuthorizedTransition(request)));
    }

    private Promise<Optional<TransitionResult>> authorizePhaseAdvance(TransitionRequest request) {
        CapabilityEvaluationService.CapabilityEvaluationRequest capabilityRequest =
                new CapabilityEvaluationService.CapabilityEvaluationRequest(
                        request.tenantId(),
                        request.requestedBy(),
                        request.workspaceId(),
                        request.projectId(),
                        null,
                        "phase:advance",
                        request.fromPhase());

        return capabilityEvaluationService.evaluate(capabilityRequest)
                .map(capabilities -> {
                    PhasePacket.PhaseReadiness readiness = new PhasePacket.PhaseReadiness(
                            true,
                            request.toPhase(),
                            List.of(),
                            1.0,
                            false);
                    List<PhasePacket.PhaseAction> actions = phaseActionAuthorizationService.determineAvailableActions(
                            capabilities,
                            request.tenantTier(),
                            request.enabledPhaseFlags(),
                            readiness,
                            List.of(),
                            List.of());
                    PhasePacket.PhaseAction advanceAction = actions.stream()
                            .filter(action -> "advance-phase".equals(action.actionId()))
                            .findFirst()
                            .orElseThrow();
                    if (advanceAction.enabled()) {
                        return Optional.<TransitionResult>empty();
                    }
                    return Optional.of(TransitionResult.blocked(
                            "PHASE_ACTION_UNAUTHORIZED",
                            advanceAction.disabledReason() != null
                                    ? advanceAction.disabledReason()
                                    : "phaseAction.disabled.unauthorized"));
                })
                .then((result, error) -> {
                    if (error == null) {
                        return Promise.of(result);
                    }
                    log.error(
                            "AdvancePhaseUseCase: capability authorization failed for project={} tenant={}",
                            request.projectId(),
                            request.tenantId(),
                            error);
                    return Promise.of(Optional.of(TransitionResult.blocked(
                            "PHASE_ACTION_AUTHORIZATION_ERROR",
                            "phaseAction.disabled.authorizationUnavailable")));
                });
    }

    private Promise<TransitionResult> executeAuthorizedTransition(TransitionRequest request) {
        log.info("AdvancePhaseUseCase: {} → {} for project={} tenant={}",
            request.fromPhase(), request.toPhase(), request.projectId(), request.tenantId());

        // ── Step 1: Validate the transition rule exists ───────────────────────
        Optional<TransitionSpec> maybeSpec =
            transitionConfig.findTransition(request.fromPhase(), request.toPhase());

        if (maybeSpec.isEmpty()) {
            log.warn("AdvancePhaseUseCase: invalid transition {} → {} for project={}",
                request.fromPhase(), request.toPhase(), request.projectId());
            TransitionResult result = TransitionResult.blocked(
                "INVALID_TRANSITION",
                "No transition rule found from '" + request.fromPhase() +
                "' to '" + request.toPhase() + "'");
            publishToDlq(request, result);
            return Promise.of(result);
        }

        TransitionSpec spec = maybeSpec.get();

        // ── Step 2: Check required artifacts ─────────────────────────────────
        return checkArtifacts(request, spec)
            .then(missingArtifacts -> {
                if (!missingArtifacts.isEmpty()) {
                    log.warn("AdvancePhaseUseCase: missing artifacts {} for project={}",
                        missingArtifacts, request.projectId());
                    TransitionResult result = TransitionResult.missingArtifacts(missingArtifacts);
                    publishToDlq(request, result);
                    return Promise.of(result);
                }

                // ── Step 3: AI readiness gate (optional) ─────────────────────
                if (readinessAssessor != null) {
                    // Build a minimal ProjectContext from the request; full context
                    // is populated by ProjectContextBuilder when wired in production.
                    ProjectContext minimalContext = new ProjectContext(
                            request.projectId(), request.tenantId(), request.fromPhase(),
                            0, 0.0, 0, -1, null, 0, 0);

                    return readinessAssessor.assess(
                            request.fromPhase(), request.toPhase(), minimalContext)
                        .then(report -> {
                            if (!report.ready()) {
                                log.info("AdvancePhaseUseCase: AI readiness BLOCKED for project={} blockers={}",
                                        request.projectId(), report.blockers());
                                TransitionResult result = TransitionResult.blocked(
                                        "AI_READINESS_GATE",
                                        "AI readiness check failed: " + String.join("; ", report.blockers()));
                                publishToDlq(request, result);
                                return Promise.of(result);
                            }
                            return evaluatePolicyGate(request);
                        });
                }

                // ── Step 4: Evaluate policy gate ─────────────────────────────
                return evaluatePolicyGate(request);
            });
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Checks all required artifacts for the transition spec.
     * Returns a Promise of the list of missing artifact IDs (empty = all present).
     */
    /**
     * Evaluates the policy gate for the given transition request.
     * Returns a {@link TransitionResult} indicating success or policy block.
     */
    private Promise<TransitionResult> evaluatePolicyGate(TransitionRequest request) {
        Map<String, Object> policyContext = Map.of(
            "from_phase",   request.fromPhase(),
            "to_phase",     request.toPhase(),
            "project_id",   request.projectId(),
            "tenant_id",    request.tenantId(),
            "requested_by", request.requestedBy() != null ? request.requestedBy() : "unknown"
        );

        return policyEngine.evaluate("phase_advance_policy", policyContext)
            .then(allowed -> {
                if (!allowed) {
                    log.info("AdvancePhaseUseCase: policy BLOCKED for project={}", request.projectId());
                    TransitionResult result = TransitionResult.blocked(
                        "POLICY_GATE",
                        "phase_advance_policy denied the transition from '" +
                        request.fromPhase() + "' to '" + request.toPhase() + "'");
                    publishToDlq(request, result);
                    return Promise.of(result);
                }

                // Transition approved
                log.info("AdvancePhaseUseCase: transition approved {} → {} for project={}",
                    request.fromPhase(), request.toPhase(), request.projectId());
                return Promise.of(TransitionResult.success(request.toPhase()));
            });
    }

    private Promise<List<String>> checkArtifacts(TransitionRequest request, TransitionSpec spec) {
        List<String> requiredArtifacts = spec.getRequiredArtifacts();
        if (requiredArtifacts.isEmpty()) {
            return Promise.of(List.of());
        }

        // Chain artifact checks: for each required artifact, check if any version exists
        Promise<List<String>> resultPromise = Promise.of(new ArrayList<>());

        for (String artifactId : requiredArtifacts) {
            String artifactPath = request.projectId() + "/" + artifactId;
            final String aid = artifactId;
            resultPromise = resultPromise.then(missingList ->
                artifactRepository.list(artifactPath)
                    .then(
                        versions -> {
                            if (versions.isEmpty()) {
                                List<String> updated = new ArrayList<>(missingList);
                                updated.add(aid);
                                return Promise.of(updated);
                            }
                            return Promise.of(missingList);
                        },
                        e -> {
                            // If listing fails, treat the artifact as missing
                            log.debug("Artifact check failed for {} — treating as missing: {}", artifactPath, e.getMessage());
                            List<String> updated = new ArrayList<>(missingList);
                            updated.add(aid);
                            return Promise.of(updated);
                        })
            );
        }

        return resultPromise;
    }

    /**
     * Fire-and-forget DLQ publication for blocked transitions.
     * Errors in publication are logged and swallowed — never propagated to the caller.
     */
    private void publishToDlq(TransitionRequest request, TransitionResult result) {
        Map<String, Object> payload = Map.of(
            "projectId",  request.projectId(),
            "fromPhase",  request.fromPhase(),
            "toPhase",    request.toPhase(),
            "blockCode",  result.blockCode() != null ? result.blockCode() : "UNKNOWN",
            "blockReason", result.blockReason() != null ? result.blockReason() : "",
            "missingArtifacts", result.missingArtifacts()
        );
        dlqPublisher.publish(
                request.tenantId(),
                PIPELINE_ID,
                NODE_ID,
                "PHASE_ADVANCE_BLOCKED",
                payload,
                result.blockCode(),
                request.projectId())
            .whenException(e ->
                log.warn("AdvancePhaseUseCase: DLQ publish failed for project={}: {}",
                    request.projectId(), e.getMessage()));
    }
}
