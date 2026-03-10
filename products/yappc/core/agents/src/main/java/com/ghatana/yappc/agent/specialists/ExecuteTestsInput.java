package com.ghatana.yappc.agent.specialists;

import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Input for ExecuteTestsSpecialistAgent.
 *
 * @doc.type record
 * @doc.purpose Input parameters for test execution
 * @doc.layer product
 * @doc.pattern ValueObject
 * @doc.gaa.lifecycle perceive
 */
public record ExecuteTestsInput(
    @NotNull String testPlanId,
    @NotNull List<String> testFiles,
    @NotNull String testType,
    @NotNull String environment,
    int parallelism,
    int timeoutMinutes,
    boolean failFast) {}
