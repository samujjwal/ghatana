/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Lifecycle Service
 */
package com.ghatana.yappc.services.lifecycle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Evaluates lifecycle stage entry and exit criteria against a set of verified conditions.
 *
 * <p>Each YAPPC lifecycle stage ({@link StageSpec}) declares human-readable criteria
 * strings. At runtime, a caller provides a {@link Map} of {@code criterionKey → Boolean}
 * verdicts. The {@code GateEvaluator} matches supplied verdicts against each criterion
 * string using case-insensitive containment, and reports:
 * <ul>
 *   <li>How many criteria were satisfied</li>
 *   <li>Which criteria remain unmet (for UI feedback and audit)</li>
 *   <li>Whether all required criteria passed (i.e., the gate is open)</li>
 * </ul>
 *
 * <h2>Key design decisions</h2>
 * <ul>
 *   <li>Criterion matching is <em>keyword-based</em>: a verdict key is considered to
 *       match a criterion if any word from the key appears in the criterion text.</li>
 *   <li>Unmatched criteria are treated as <em>not satisfied</em> (fail-closed).</li>
 *   <li>An empty criterion list means the gate is trivially open (no constraints).</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Evaluates lifecycle gate criteria against supplied verdicts
 * @doc.layer product
 * @doc.pattern Service
 * @doc.gaa.lifecycle reason
 */
public class GateEvaluator {

    private static final Logger log = LoggerFactory.getLogger(GateEvaluator.class);

    /**
     * Result returned by a gate evaluation.
     *
     * @param open            {@code true} if all criteria are satisfied
     * @param satisfiedCount  number of criteria that passed
     * @param totalCount      total number of criteria
     * @param unmetCriteria   list of criterion texts that were not satisfied
     */
    public record GateResult(
            boolean open,
            int satisfiedCount,
            int totalCount,
            List<String> unmetCriteria) {

        /** Returns {@code true} if every criterion was satisfied. */
        public boolean isFullySatisfied() {
            return open;
        }

        /** Returns the fraction of criteria satisfied in {@code [0.0, 1.0]}. */
        public double satisfactionRatio() {
            return totalCount == 0 ? 1.0 : (double) satisfiedCount / totalCount;
        }

        @Override
        public String toString() {
            return "GateResult{open=" + open +
                    ", " + satisfiedCount + "/" + totalCount + " criteria met" +
                    (unmetCriteria.isEmpty() ? "" : ", unmet=" + unmetCriteria) + "}";
        }
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Evaluates the <em>entry</em> criteria for the given stage.
     *
     * @param stage    stage whose entry criteria to check
     * @param verdicts map from condition key to verified boolean
     * @return evaluation result
     */
    public GateResult evaluateEntry(StageSpec stage, Map<String, Boolean> verdicts) {
        Objects.requireNonNull(stage, "stage must not be null");
        Objects.requireNonNull(verdicts, "verdicts must not be null");
        return evaluate("entry", stage.getId(), stage.getEntryCriteria(), verdicts);
    }

    /**
     * Evaluates the <em>exit</em> criteria for the given stage.
     *
     * @param stage    stage whose exit criteria to check
     * @param verdicts map from condition key to verified boolean
     * @return evaluation result
     */
    public GateResult evaluateExit(StageSpec stage, Map<String, Boolean> verdicts) {
        Objects.requireNonNull(stage, "stage must not be null");
        Objects.requireNonNull(verdicts, "verdicts must not be null");
        return evaluate("exit", stage.getId(), stage.getExitCriteria(), verdicts);
    }

    /**
     * Evaluates whether all artifact IDs declared by the stage are present in the
     * supplied set of available artifact IDs.
     *
     * @param stage              stage whose artifact requirements to check
     * @param availableArtifacts set of artifact IDs that have been produced
     * @return evaluation result
     */
    public GateResult evaluateArtifacts(StageSpec stage, Set<String> availableArtifacts) {
        Objects.requireNonNull(stage, "stage must not be null");
        Objects.requireNonNull(availableArtifacts, "availableArtifacts must not be null");

        List<String> required = stage.getArtifacts();
        if (required.isEmpty()) {
            return new GateResult(true, 0, 0, List.of());
        }

        List<String> unmet = required.stream()
                .filter(artifact -> !availableArtifacts.contains(artifact))
                .collect(Collectors.toList());

        boolean open = unmet.isEmpty();
        int satisfied = required.size() - unmet.size();

        log.debug("GateEvaluator artifact check for stage '{}': {}/{} artifacts present",
                stage.getId(), satisfied, required.size());

        return new GateResult(open, satisfied, required.size(), unmet);
    }

    // ─── Internal ─────────────────────────────────────────────────────────────

    private GateResult evaluate(
            String gateType,
            String stageId,
            List<String> criteria,
            Map<String, Boolean> verdicts) {

        if (criteria.isEmpty()) {
            log.debug("GateEvaluator: stage '{}' has no {} criteria — gate trivially open",
                    stageId, gateType);
            return new GateResult(true, 0, 0, List.of());
        }

        List<String> unmet = new ArrayList<>();
        int satisfied = 0;

        for (String criterion : criteria) {
            if (isSatisfied(criterion, verdicts)) {
                satisfied++;
            } else {
                unmet.add(criterion);
            }
        }

        boolean open = unmet.isEmpty();
        log.debug("GateEvaluator: stage '{}' {} gate — {}/{} criteria met (open={})",
                stageId, gateType, satisfied, criteria.size(), open);

        return new GateResult(open, satisfied, criteria.size(), Collections.unmodifiableList(unmet));
    }

    /**
     * Returns {@code true} if the criterion is satisfied by any verdict in the map.
     *
     * <p>A verdict key satisfies a criterion when:
     * <ol>
     *   <li>The criterion text contains the key (case-insensitive), AND</li>
     *   <li>The corresponding verdict value is {@code Boolean.TRUE}.</li>
     * </ol>
     *
     * <p>If no verdict key matches the criterion, it is treated as <em>not satisfied</em>.
     */
    private boolean isSatisfied(String criterion, Map<String, Boolean> verdicts) {
        String lowerCriterion = criterion.toLowerCase();
        for (Map.Entry<String, Boolean> entry : verdicts.entrySet()) {
            String lowerKey = entry.getKey().toLowerCase();
            if (lowerCriterion.contains(lowerKey) && Boolean.TRUE.equals(entry.getValue())) {
                return true;
            }
        }
        return false;
    }
}
