/*
 * Copyright (c) 2025 Ghatana Platform Contributors
 */

package com.ghatana.yappc.core.cmake;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**

 * @doc.type record

 * @doc.purpose Immutable data carrier for c make improvement suggestions

 * @doc.layer core

 * @doc.pattern ValueObject

 */

public record CMakeImprovementSuggestions(
        @JsonProperty("modernizationHints") List<String> modernizationHints,
        @JsonProperty("performanceHints") List<String> performanceHints,
        @JsonProperty("bestPractices") List<String> bestPractices,
        @JsonProperty("securityIssues") List<String> securityIssues) {

    @JsonCreator
    public CMakeImprovementSuggestions {
    }
}
