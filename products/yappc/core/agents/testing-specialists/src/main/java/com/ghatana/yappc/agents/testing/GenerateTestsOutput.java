package com.ghatana.yappc.agents.testing;

import java.util.List;
import java.util.Map;

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
        double estimatedCoverage,
        Map<String, String> generatedSources,
        List<String> implementedScenarios,
        String framework) {

    public GenerateTestsOutput {
        generatedTestFiles = generatedTestFiles == null ? List.of() : List.copyOf(generatedTestFiles);
        generatedSources = generatedSources == null ? Map.of() : Map.copyOf(generatedSources);
        implementedScenarios = implementedScenarios == null ? List.of() : List.copyOf(implementedScenarios);
    }

    public GenerateTestsOutput(
            String testPlanId,
            List<String> generatedTestFiles,
            int totalTests,
            int linesOfTestCode,
            String testType,
            double estimatedCoverage) {
        this(
                testPlanId,
                generatedTestFiles,
                totalTests,
                linesOfTestCode,
                testType,
                estimatedCoverage,
                Map.of(),
                List.of(),
                "unknown");
    }
}
