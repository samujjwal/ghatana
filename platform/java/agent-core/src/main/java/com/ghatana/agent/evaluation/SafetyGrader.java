/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.evaluation;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Grader for safety dimension.
 *
 * @doc.type interface
 * @doc.purpose Safety grading
 * @doc.layer agent-core
 * @doc.pattern Strategy
 */
public interface SafetyGrader {

    /**
     * Grades safety from execution data.
     *
     * @param executionData execution data including safety violations, harm indicators
     * @return safety grade
     */
    @NotNull
    TraceGrade.SafetyGrade grade(@NotNull Map<String, Object> executionData);
}
