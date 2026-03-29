/*
 * Copyright (c) 2026 Ghatana
 */
package com.ghatana.yappc.services.intent;

import java.util.List;
import java.util.Map;

/**
 * Analysis result for a structured intent specification.
 *
 * @doc.type class
 * @doc.purpose Value object carrying the analysis of a captured intent
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record IntentAnalysis(
        boolean feasible,
        String estimatedComplexity,
        List<String> requiredPhases,
        Map<String, String> recommendations) {
}
