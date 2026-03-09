package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for AnalyzeTestResultsSpecialistAgent.
 *
 * @doc.type record
 * @doc.purpose Input parameters for test results analysis
 * @doc.layer product
 * @doc.pattern ValueObject
 * @doc.gaa.lifecycle reason
 */
public record AnalyzeTestResultsInput(
    @NotNull String executionId,
    @NotNull String testPlanId,
    int totalTests,
    int passed,
    int failed,
    int skipped,
    int flaky,
    double durationSeconds,
    double coverageThreshold,
    double actualCoverage,
    @NotNull Map<String, ExecuteTestsOutput.TestFileResult> testFileResults) {}
