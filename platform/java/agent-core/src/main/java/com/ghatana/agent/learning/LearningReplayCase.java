/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning;

import java.util.Map;

/**
 * @doc.type record
 * @doc.purpose One offline replay/evaluation case for a learned candidate
 * @doc.layer agent-core
 * @doc.pattern Record
 */
/**
 * One offline replay/evaluation case for a learned candidate.
 */
public record LearningReplayCase(
        String caseId,
        Map<String, Object> input,
        Map<String, Object> expected,
        double weight
) {
    public LearningReplayCase {
        if (caseId == null || caseId.isBlank()) throw new IllegalArgumentException("caseId is required");
        input = input == null ? Map.of() : Map.copyOf(input);
        expected = expected == null ? Map.of() : Map.copyOf(expected);
        weight = weight <= 0.0 ? 1.0 : weight;
    }
}
