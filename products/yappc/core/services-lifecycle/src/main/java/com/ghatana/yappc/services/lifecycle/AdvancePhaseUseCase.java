/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Lifecycle Service
 */
package com.ghatana.yappc.services.lifecycle;

import com.ghatana.governance.PolicyEngine;
import com.ghatana.yappc.storage.YappcArtifactRepository;
import io.activej.promise.Promise;
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
 *   <li>Evaluate the {@code phase_advance_policy} against the policy engine.</li>
 *   <li>If all gates pass, return {@link TransitionResult#success(String)}.</li>
 *   <li>Otherwise, return an appropriate {@link TransitionResult#blocked(String, String)}.</li>
 * </ol>
 *
 * <p>Artifact checks are fully async via ActiveJ Promise chaining.
 *
 * @doc.type class
 * @doc.purpose Core lifecycle phase transition use case with policy and artifact gate enforcement
 * @doc.layer product
 * @doc.pattern Service
 * @doc.gaa.lifecycle act
 */
public class AdvancePhaseUseCase {

    private static final Logger log = LoggerFactory.getLogger(AdvancePhaseUseCase.class);

    private final TransitionConfigLoader transitionConfig;
    private final PolicyEngine policyEngine;
    private final YappcArtifactRepository artifactRepository;

    /**
     * Constructs the use case with its required dependencies.
     *
     * @param transitionConfig   loaded transition rules from {@code transitions.yaml}
     * @param policyEngine       policy gate evaluator
     * @param artifactRepository artifact presence checker
     */
    public AdvancePhaseUseCase(
            TransitionConfigLoader transitionConfig,
            PolicyEngine policyEngine,
            YappcArtifactRepository artifactRepository) {
        this.transitionConfig    = transitionConfig;
        this.policyEngine        = policyEngine;
        this.artifactRepository  = artifactRepository;
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
        log.info("AdvancePhaseUseCase: {} → {} for project={} tenant={}",
            request.fromPhase(), request.toPhase(), request.projectId(), request.tenantId());

        // ── Step 1: Validate the transition rule exists ───────────────────────
        Optional<TransitionSpec> maybeSpec =
            transitionConfig.findTransition(request.fromPhase(), request.toPhase());

        if (maybeSpec.isEmpty()) {
            log.warn("AdvancePhaseUseCase: invalid transition {} → {} for project={}",
                request.fromPhase(), request.toPhase(), request.projectId());
            return Promise.of(TransitionResult.blocked(
                "INVALID_TRANSITION",
                "No transition rule found from '" + request.fromPhase() +
                "' to '" + request.toPhase() + "'"));
        }

        TransitionSpec spec = maybeSpec.get();

        // ── Step 2: Check required artifacts ─────────────────────────────────
        return checkArtifacts(request, spec)
            .then(missingArtifacts -> {
                if (!missingArtifacts.isEmpty()) {
                    log.warn("AdvancePhaseUseCase: missing artifacts {} for project={}",
                        missingArtifacts, request.projectId());
                    return Promise.of(TransitionResult.missingArtifacts(missingArtifacts));
                }

                // ── Step 3: Evaluate policy gate ─────────────────────────────
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
                            return Promise.of(TransitionResult.blocked(
                                "POLICY_GATE",
                                "phase_advance_policy denied the transition from '" +
                                request.fromPhase() + "' to '" + request.toPhase() + "'"));
                        }

                        // ── Step 4: Transition approved ───────────────────────
                        log.info("AdvancePhaseUseCase: transition approved {} → {} for project={}",
                            request.fromPhase(), request.toPhase(), request.projectId());
                        return Promise.of(TransitionResult.success(request.toPhase()));
                    });
            });
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Checks all required artifacts for the transition spec.
     * Returns a Promise of the list of missing artifact IDs (empty = all present).
     */
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
}
