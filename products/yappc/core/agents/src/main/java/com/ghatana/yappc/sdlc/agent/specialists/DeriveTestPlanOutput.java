package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.List;
import java.util.Map;

/**
 * Output from Derive Test Plan Specialist.
 *
 * @doc.type record
 * @doc.purpose Comprehensive test plan
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record DeriveTestPlanOutput(
    String requirementsId,
    String testPlanId,
    List<String> unitTestCases,
    List<String> integrationTestCases,
    List<String> performanceTestScenarios,
    List<String> securityTestScenarios,
    Map<String, String> testStrategy,
    int estimatedTestCount) {}
