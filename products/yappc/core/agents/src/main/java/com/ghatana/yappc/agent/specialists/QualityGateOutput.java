package com.ghatana.yappc.agent.specialists;

import java.util.List;

/**
 * Output from Quality Gate Specialist.
 *
 * @doc.type record
 * @doc.purpose Result of quality gate validation
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record QualityGateOutput(
    String buildId,
    boolean passed,
    List<String> failures,
    String recommendation,
    boolean allowPublish) {}
