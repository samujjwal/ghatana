package com.ghatana.yappc.agent.specialists;

import java.util.List;

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
    String testFramework) {}
