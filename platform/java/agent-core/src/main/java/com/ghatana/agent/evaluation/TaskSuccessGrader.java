/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.evaluation;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Grader for task success dimension.
 *
 * @doc.type interface
 * @doc.purpose Task success grading
 * @doc.layer agent-core
 * @doc.pattern Strategy
 */
public interface TaskSuccessGrader {

    /**
     * Grades task success from execution data.
     *
     * @param executionData execution data including task description, expected output, actual output
     * @return task success grade
     */
    @NotNull
    TraceGrade.TaskSuccessGrade grade(@NotNull Map<String, Object> executionData);
}
