/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.evaluation;

import com.ghatana.agent.context.version.VersionContext;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Grader for version compatibility dimension.
 *
 * @doc.type interface
 * @doc.purpose Version compatibility grading
 * @doc.layer agent-core
 * @doc.pattern Strategy
 */
public interface VersionCompatibilityGrader {

    /**
     * Grades version compatibility from execution data and version context.
     *
     * @param executionData execution data including version requirements
     * @param versionContext version context for compatibility checking
     * @return version compatibility grade
     */
    @NotNull
    TraceGrade.VersionCompatibilityGrade grade(
            @NotNull Map<String, Object> executionData,
            @NotNull VersionContext versionContext);
}
