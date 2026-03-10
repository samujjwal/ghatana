package com.ghatana.yappc.agent.specialists;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from SecurityTestsSpecialistAgent.
 *
 * @doc.type record
 * @doc.purpose Security test execution results with vulnerability details
 * @doc.layer product
 * @doc.pattern ValueObject
 * @doc.gaa.lifecycle act
 */
public record SecurityTestsOutput(
    @NotNull String testPlanId,
    @NotNull String executionId,
    int totalTests,
    int passed,
    int failed,
    @NotNull Map<String, Integer> vulnerabilitiesBySeverity,
    @NotNull List<Vulnerability> criticalVulnerabilities,
    @NotNull List<String> owaspTop10Findings,
    double securityScore,
    @NotNull Instant executedAt,
    boolean passedSecurityGate,
    String message) {

  /**
   * Vulnerability finding.
   *
   * @doc.type record
   * @doc.purpose Individual security vulnerability details
   * @doc.layer product
   * @doc.pattern ValueObject
   */
  public record Vulnerability(
      String id,
      String severity,
      String category,
      String description,
      String endpoint,
      String recommendation) {}
}
