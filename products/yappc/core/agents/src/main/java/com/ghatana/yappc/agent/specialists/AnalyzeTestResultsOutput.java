package com.ghatana.yappc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from AnalyzeTestResultsSpecialistAgent.
 *
 * @doc.type record
 * @doc.purpose Detailed analysis of test execution results
 * @doc.layer product
 * @doc.pattern ValueObject
 * @doc.gaa.lifecycle reason
 */
public record AnalyzeTestResultsOutput(
    @NotNull String executionId,
    @NotNull String testPlanId,
    double passRate,
    double failureRate,
    double coverageAchieved,
    boolean coverageMetThreshold,
    @NotNull List<String> slowestTests,
    @NotNull List<String> flakyTests,
    @NotNull List<String> criticalFailures,
    @NotNull Map<String, Integer> failuresByCategory,
    @NotNull QualityAssessment qualityAssessment,
    @NotNull List<String> recommendations,
    boolean readyForRelease,
    String summary) {

  /**
   * Overall quality assessment.
   *
   * @doc.type record
   * @doc.purpose Assessment of test suite quality
   * @doc.layer product
   * @doc.pattern ValueObject
   */
  public record QualityAssessment(
      String grade, double score, String stability, String performance, String coverage) {}
}
