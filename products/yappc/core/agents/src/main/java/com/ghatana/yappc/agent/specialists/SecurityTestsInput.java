package com.ghatana.yappc.agent.specialists;

import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Input for SecurityTestsSpecialistAgent.
 *
 * @doc.type record
 * @doc.purpose Input parameters for security testing
 * @doc.layer product
 * @doc.pattern ValueObject
 * @doc.gaa.lifecycle act
 */
public record SecurityTestsInput(
    @NotNull String testPlanId,
    @NotNull String deploymentUrl,
    @NotNull List<String> endpoints,
    @NotNull List<String> testTypes,
    @NotNull String environment,
    boolean includeAuthentication,
    boolean includePenetrationTests) {}
