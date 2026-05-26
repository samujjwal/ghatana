/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Lifecycle Service — Phase Gate Validator
 */
package com.ghatana.yappc.services.lifecycle.gate;

import com.ghatana.yappc.domain.PhaseType;
import com.ghatana.yappc.services.lifecycle.GateEvaluator;
import com.ghatana.yappc.services.lifecycle.StageConfigLoader;
import com.ghatana.yappc.services.lifecycle.StageSpec;
import com.ghatana.yappc.services.metrics.BusinessMetrics;
import com.ghatana.yappc.storage.YappcArtifactRepository;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Validates whether a YAPPC project satisfies all gate criteria for a target lifecycle phase.
 *
 * <p>This validates YAPPC creator/SDLC phase gates. It does not execute or replace
 * Kernel Product Lifecycle delivery gates. YAPPC may display Kernel gates but must not execute them.
 *
 * <p>The validator runs three checks in sequence:
 * <ol>
 *   <li><strong>Entry criteria</strong> — keyword-based matching via {@link GateEvaluator}
 *       against caller-supplied condition verdicts.</li>
 *   <li><strong>Artifact presence</strong> — verifies every required artifact declared in
 *       the target {@link StageSpec} is present in the {@link YappcArtifactRepository}.</li>
 *   <li><strong>Exit criteria of the prior stage</strong> — the stage immediately before
 *       the target must have its exit criteria satisfied before a forward transition is
 *       allowed.</li>
 * </ol>
 *
 * <p>The combined result is returned as a {@link ValidationResult} that indicates whether
 * all gates are open and provides actionable diagnostics for any that are not.
 *
 * <p><b>Usage</b></p>
 * <pre>{@code
 * PhaseGateValidator validator = new PhaseGateValidator(stageLoader, gateEvaluator, artifactRepo);
 *
 * Map<String, Boolean> conditions = Map.of(
 *     "requirements_reviewed", true,
 *     "design_approved",       false
 * );
 *
 * validator.validate("project-42", PhaseType.SHAPE, conditions)
 *          .whenResult(result -> {
 *              if (result.isAllClear()) {
 *                  advanceToNextPhase();
 *              } else {
 *                  showBlockers(result.blockers());
 *              }
 *          });
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Validates phase-gate criteria before a YAPPC lifecycle phase transition
 * @doc.layer product
 * @doc.pattern Service
 */
public final class PhaseGateValidator {

    private static final Logger log = LoggerFactory.getLogger(PhaseGateValidator.class);

    private final StageConfigLoader      stageConfig;
    private final GateEvaluator          gateEvaluator;
    private final YappcArtifactRepository artifactRepository;
    @Nullable
    private final BusinessMetrics         metrics;

    /**
     * Constructs the validator without metrics instrumentation.
     *
     * @param stageConfig        loaded lifecycle stage definitions
     * @param gateEvaluator      low-level criterion evaluator
     * @param artifactRepository artifact presence checker
     */
    public PhaseGateValidator(
            @NotNull StageConfigLoader       stageConfig,
            @NotNull GateEvaluator           gateEvaluator,
            @NotNull YappcArtifactRepository  artifactRepository) {
        this(stageConfig, gateEvaluator, artifactRepository, null);
    }

    /**
     * Constructs the validator with optional metrics instrumentation.
     *
     * @param stageConfig        loaded lifecycle stage definitions
     * @param gateEvaluator      low-level criterion evaluator
     * @param artifactRepository artifact presence checker
     * @param metrics            business metrics publisher, or {@code null} to disable instrumentation
     */
    public PhaseGateValidator(
            @NotNull  StageConfigLoader       stageConfig,
            @NotNull  GateEvaluator           gateEvaluator,
            @NotNull  YappcArtifactRepository  artifactRepository,
            @Nullable BusinessMetrics          metrics) {
        this.stageConfig         = Objects.requireNonNull(stageConfig,         "stageConfig");
        this.gateEvaluator       = Objects.requireNonNull(gateEvaluator,       "gateEvaluator");
        this.artifactRepository  = Objects.requireNonNull(artifactRepository,  "artifactRepository");
        this.metrics             = metrics;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Validates all gates for a project transitioning to {@code targetPhase}.
     *
     * @param projectId   the project being transitioned
     * @param targetPhase the lifecycle phase the project wants to enter
     * @param conditions  map of {@code conditionKey → satisfied} verdicts supplied by the caller
     * @return promise resolving to a {@link ValidationResult} with full gate diagnostics
     */
    public Promise<ValidationResult> validate(
            @NotNull String projectId,
            @NotNull PhaseType targetPhase,
            @NotNull Map<String, Boolean> conditions) {
        return validate(projectId, targetPhase, PhaseGateContext.fromConditions(conditions));
    }

    /**
     * Validates all gates for a project transitioning to {@code targetPhase}.
     *
     * @param projectId   the project being transitioned
     * @param targetPhase the lifecycle phase the project wants to enter
     * @param context typed gate context with artifacts, evidence, governance, health, flags, and condition verdicts
     * @return promise resolving to a {@link ValidationResult} with full gate diagnostics
     */
    public Promise<ValidationResult> validate(
            @NotNull String projectId,
            @NotNull PhaseType targetPhase,
            @NotNull PhaseGateContext context) {

        Objects.requireNonNull(projectId,    "projectId");
        Objects.requireNonNull(targetPhase,  "targetPhase");
        Objects.requireNonNull(context,      "context");

        long startMs = System.currentTimeMillis();
        String stageId = targetPhase.name().toLowerCase();

        Optional<StageSpec> stageOpt = stageConfig.findById(stageId);
        if (stageOpt.isEmpty()) {
            log.warn("No stage spec found for phase '{}'; treating as open gate", stageId);
            emitGateMetrics(projectId, targetPhase, true, startMs);
            return Promise.of(ValidationResult.allClear(targetPhase));
        }

        StageSpec stage = stageOpt.get();
        Map<String, Boolean> conditions = context.conditionVerdicts();

        // 1. Entry criteria evaluation (synchronous — no I/O)
        GateEvaluator.GateResult entryResult = gateEvaluator.evaluateEntry(stage, conditions);
        List<String> blockers = new ArrayList<>();
        if (!entryResult.isFullySatisfied()) {
            entryResult.unmetCriteria().forEach(c -> blockers.add("entry-criterion: " + c));
        }

        // 2. Exit criteria of the prior stage (synchronous)
        Optional<StageSpec> priorOpt = priorStage(stage);
        if (priorOpt.isPresent()) {
            GateEvaluator.GateResult exitResult =
                    gateEvaluator.evaluateExit(priorOpt.get(), conditions);
            if (!exitResult.isFullySatisfied()) {
                exitResult.unmetCriteria().forEach(c ->
                        blockers.add("prior-exit-criterion: " + c));
            }
        }

        // 3. Artifact presence check (async)
        List<String> requiredArtifacts = stage.getArtifacts() != null ? stage.getArtifacts() : List.of();
        if (requiredArtifacts.isEmpty()) {
            boolean clear = blockers.isEmpty();
            log.debug("Phase gate validation for {}/{}: {} blockers", projectId, targetPhase, blockers.size());
            ValidationResult syncResult = new ValidationResult(targetPhase, clear, List.copyOf(blockers));
            emitGateMetrics(projectId, targetPhase, clear, startMs);
            return Promise.of(syncResult);
        }

        List<Promise<Optional<String>>> artifactChecks = requiredArtifacts.stream()
                .map(artifactKey -> checkArtifact(projectId, targetPhase, artifactKey))
                .toList();

        return Promises.toList(artifactChecks)
                .map(results -> {
                    for (int i = 0; i < results.size(); i++) {
                        if (results.get(i).isEmpty()) {
                            blockers.add("missing-artifact: " + requiredArtifacts.get(i));
                        }
                    }
                    boolean clear = blockers.isEmpty();
                    log.debug("Phase gate validation for {}/{}: {} blockers", projectId, targetPhase, blockers.size());
                    ValidationResult result = new ValidationResult(targetPhase, clear, List.copyOf(blockers));
                    emitGateMetrics(projectId, targetPhase, clear, startMs);
                    return result;
                });
    }

    /**
     * Quickly checks whether all gates for a single phase are open with no conditions.
     * Useful for UI status indicators that show which phases are already satisfied.
     *
     * @param projectId   the project to check
     * @param targetPhase the phase to evaluate
     * @return promise resolving to {@code true} if no artifact blockers are found
     */
    public Promise<Boolean> isArtifactGateOpen(@NotNull String projectId, @NotNull PhaseType targetPhase) {
        return validate(projectId, targetPhase, Collections.emptyMap())
                .map(r -> r.artifactBlockers().isEmpty());
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private void emitGateMetrics(String projectId, PhaseType phase, boolean clear, long startMs) {
        if (metrics != null) {
            metrics.recordPhaseGateValidation(
                    projectId,
                    phase.name(),
                    clear ? "PASS" : "BLOCK",
                    System.currentTimeMillis() - startMs);
        }
    }

    private Promise<Optional<String>> checkArtifact(String projectId, PhaseType phase, String artifactKey) {
        return artifactRepository.listVersions(projectId, phase)
                .<Optional<String>>map(versions -> {
                    boolean found = versions.stream()
                            .anyMatch(v -> v.contains(artifactKey));
                    return found ? Optional.of(artifactKey) : Optional.empty();
                })
                .whenException(ex ->
                        log.warn("Artifact check failed for project={} phase={} key={}: {}",
                                projectId, phase, artifactKey, ex.getMessage()));
    }

    private Optional<StageSpec> priorStage(StageSpec current) {
        return stageConfig.getAll().stream()
                .filter(s -> s.getOrder() == current.getOrder() - 1)
                .findFirst();
    }

    // ── Result types ──────────────────────────────────────────────────────────

    /**
     * Result of a full phase-gate validation run.
     *
     * @param targetPhase  the phase that was validated
     * @param allClear     {@code true} when every gate is satisfied
     * @param blockers     human-readable descriptions of unsatisfied gates
     */
    public record ValidationResult(
            PhaseType targetPhase,
            boolean   allClear,
            List<String> blockers) {

        /**
         * Filters blockers that relate specifically to artifact presence.
         */
        public List<String> artifactBlockers() {
            return blockers.stream()
                    .filter(b -> b.startsWith("missing-artifact:"))
                    .toList();
        }

        /**
         * Filters blockers that relate to entry/exit criteria.
         */
        public List<String> criteriaBlockers() {
            return blockers.stream()
                    .filter(b -> b.startsWith("entry-criterion:") || b.startsWith("prior-exit-criterion:"))
                    .toList();
        }

        /** Convenience factory for a fully-green result. */
        static ValidationResult allClear(PhaseType phase) {
            return new ValidationResult(phase, true, List.of());
        }
    }

    /**
     * Typed phase-gate context used by YAPPC readiness and transition validation.
     *
     * @doc.type record
     * @doc.purpose Carries typed gate inputs before reducing them to GateEvaluator condition verdicts
     * @doc.layer product
     * @doc.pattern DTO
     */
    public record PhaseGateContext(
            Set<String> requiredArtifactIds,
            Set<String> completedArtifactIds,
            boolean evidenceAvailable,
            boolean policyAllowed,
            boolean previewHealthy,
            boolean generationHealthy,
            boolean runtimeHealthy,
            Set<String> enabledFlags,
            Map<String, Boolean> conditionVerdicts
    ) {
        public PhaseGateContext {
            requiredArtifactIds = Set.copyOf(requiredArtifactIds);
            completedArtifactIds = Set.copyOf(completedArtifactIds);
            enabledFlags = Set.copyOf(enabledFlags);
            conditionVerdicts = Map.copyOf(conditionVerdicts);
        }

        /**
         * Creates a typed context from legacy condition verdicts.
         *
         * @param conditions condition verdict map
         * @return typed phase gate context
         */
        public static PhaseGateContext fromConditions(@NotNull Map<String, Boolean> conditions) {
            return new PhaseGateContext(
                    Set.of(),
                    Set.of(),
                    conditions.getOrDefault("evidence.available", false),
                    conditions.getOrDefault("policyAllowed", false),
                    conditions.getOrDefault("previewHealthy", false),
                    conditions.getOrDefault("generationHealthy", false),
                    conditions.getOrDefault("runtimeHealthy", false),
                    Set.of(),
                    conditions);
        }
    }
}
