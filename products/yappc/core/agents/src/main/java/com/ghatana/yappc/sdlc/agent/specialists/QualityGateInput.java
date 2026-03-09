package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.List;

/**
 * Input for Quality Gate Specialist.
 *
 * @doc.type record
 * @doc.purpose Input for quality gate validation
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record QualityGateInput(
    String buildId,
    double coverageThreshold,
    int complexityThreshold,
    int criticalIssuesThreshold,
    double actualCoverage,
    int actualComplexity,
    List<String> criticalIssues) {}
