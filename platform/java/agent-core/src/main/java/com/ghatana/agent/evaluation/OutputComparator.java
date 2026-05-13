/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.evaluation;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Comparator for evaluating agent execution outputs against expected outputs.
 *
 * @doc.type interface
 * @doc.purpose Output comparison for evaluation
 * @doc.layer agent-core
 * @doc.pattern Strategy
 */
public interface OutputComparator {

    /**
     * Compares actual output against expected output with tolerance configuration.
     *
     * @param expected expected output
     * @param actual actual output
     * @param toleranceConfig tolerance configuration for comparison
     * @return comparison result
     */
    @NotNull
    OutputComparisonResult compare(
            @NotNull Object expected,
            @NotNull Object actual,
            @NotNull Map<String, String> toleranceConfig);
}
