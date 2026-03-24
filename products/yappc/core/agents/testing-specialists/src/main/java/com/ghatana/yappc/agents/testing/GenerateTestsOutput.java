package com.ghatana.yappc.agents.testing;

import java.util.List;

/**
 * Output from Generate Tests Specialist.
 *
 * @doc.type record
 * @doc.purpose Generated test code
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record GenerateTestsOutput(
    String testPlanId,
    List<String> generatedTestFiles,
    int totalTests,
    int linesOfTestCode,
    String testType,
    double estimatedCoverage) {}
