package com.ghatana.evaluation.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Quality metrics collected during an evaluation.
 * This class captures metrics related to code quality, test coverage, and documentation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QualityMetrics {

    /**
     * The test coverage percentage (0-100).
     */
    private Double testCoveragePercent;

    /**
     * The number of unit tests.
     */
    private Integer unitTestCount;

    /**
     * The number of integration tests.
     */
    private Integer integrationTestCount;

    /**
     * The number of end-to-end tests.
     */
    private Integer e2eTestCount;

    /**
     * The number of tests that passed.
     */
    private Integer testsPassed;

    /**
     * The number of tests that failed.
     */
    private Integer testsFailed;

    /**
     * The number of tests that were skipped.
     */
    private Integer testsSkipped;

    /**
     * The code complexity score.
     */
    private Double codeComplexityScore;

    /**
     * The code duplication percentage (0-100).
     */
    private Double codeDuplicationPercent;

    /**
     * The documentation coverage percentage (0-100).
     */
    private Double documentationCoveragePercent;

    /**
     * The number of code smells detected.
     */
    private Integer codeSmellCount;

    /**
     * The number of bugs detected.
     */
    private Integer bugCount;

    /**
     * The number of vulnerabilities detected.
     */
    private Integer vulnerabilityCount;

    /**
     * The maintainability index (0-100).
     */
    private Double maintainabilityIndex;

    /**
     * The technical debt in minutes.
     */
    private Integer technicalDebtMinutes;

    /**
     * Additional quality metrics.
     */
    private Map<String, Object> additionalMetrics;
}
