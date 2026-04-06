package com.ghatana.yappc.agents.testing;

import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Input for Generate Tests Specialist.
 *
 * @doc.type record
 * @doc.purpose Input for test code generation
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record GenerateTestsInput(
    String testPlanId,
    List<String> testCases,
    String testType,
    String targetLanguage,
        String testFramework,
        String className,
        String classSource,
        List<String> requirements) {

    public GenerateTestsInput {
        requirements = requirements == null ? List.of() : List.copyOf(requirements);
        testCases = testCases == null ? List.of() : List.copyOf(testCases);
    }

    public GenerateTestsInput(
            String testPlanId,
            List<String> testCases,
            String testType,
            String targetLanguage,
            String testFramework) {
        this(testPlanId, testCases, testType, targetLanguage, testFramework, null, null, List.of());
    }

    public @NotNull String resolvedClassName() {
        if (className != null && !className.isBlank()) {
            return className;
        }
        return testPlanId == null || testPlanId.isBlank() ? "GeneratedSubject" : testPlanId;
    }
}
