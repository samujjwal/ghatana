/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Lifecycle Service — Project Context
 */
package com.ghatana.yappc.services.lifecycle.assessment;

/**
 * Snapshot of a project's current state used to evaluate readiness for phase transition.
 *
 * <p>Aggregated by {@link ProjectContextBuilder} from requirements, code, test metrics,
 * knowledge-graph decisions, and agent statuses at assessment time.
 *
 * @param projectId             unique project identifier
 * @param tenantId              tenant owning the project
 * @param currentPhase          current lifecycle phase (e.g. {@code "intent"})
 * @param requirementCount      total number of requirements added to the project
 * @param averageClarityScore   average clarity score across all requirements [0.0, 1.0];
 *                              {@code 0.0} if no requirements or score unavailable
 * @param codeCommitCount       number of code commits in the project repository
 * @param testCoveragePercent   latest reported test coverage percentage [0, 100];
 *                              {@code -1} if not yet measured
 * @param buildPassing          whether the last recorded build status is passing;
 *                              {@code null} if no build has been run
 * @param decisionCount         number of architectural/design decisions recorded in the KG
 * @param activeAgentCount      number of currently running/scheduled agents for this project
 *
 * @doc.type class
 * @doc.purpose Aggregated project snapshot for readiness evaluation
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ProjectContext(
        String projectId,
        String tenantId,
        String currentPhase,
        int requirementCount,
        double averageClarityScore,
        int codeCommitCount,
        int testCoveragePercent,
        Boolean buildPassing,
        int decisionCount,
        int activeAgentCount) {

    /**
     * Returns {@code true} if requirements have been added.
     */
    public boolean hasRequirements() {
        return requirementCount > 0;
    }

    /**
     * Returns {@code true} if the average clarity score meets or exceeds the given threshold.
     *
     * @param threshold minimum acceptable clarity score (0.0 – 1.0)
     */
    public boolean meetsClarity(double threshold) {
        return averageClarityScore >= threshold;
    }

    /**
     * Returns {@code true} if test coverage exceeds the given percentage.
     *
     * @param minPercent minimum coverage percentage (0 – 100)
     */
    public boolean hasCoverage(int minPercent) {
        return testCoveragePercent >= minPercent;
    }
}
