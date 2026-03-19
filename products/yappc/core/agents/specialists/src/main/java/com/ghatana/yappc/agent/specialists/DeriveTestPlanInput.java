package com.ghatana.yappc.agent.specialists;

import java.util.List;

/**
 * Input for Derive Test Plan Specialist.
 *
 * @doc.type record
 * @doc.purpose Input for test plan derivation
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record DeriveTestPlanInput(
    String requirementsId,
    List<String> functionalRequirements,
    List<String> nonFunctionalRequirements,
    List<String> contracts,
    List<String> components,
    String coverageTarget) {}
