/*
 * Copyright (c) 2025 Ghatana Platform Contributors
 */

package com.ghatana.yappc.core.cmake;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**

 * @doc.type record

 * @doc.purpose Immutable data carrier for c make analysis result

 * @doc.layer core

 * @doc.pattern ValueObject

 */

public record CMakeAnalysisResult(
        @JsonProperty("projectType") CMakeBuildSpec.CMakeProjectType projectType,
        @JsonProperty("cmakeVersion") String cmakeVersion,
        @JsonProperty("cxxStandard") String cxxStandard,
        @JsonProperty("detectedTargets") List<String> detectedTargets,
        @JsonProperty("detectedDependencies") List<String> detectedDependencies,
        @JsonProperty("hasTests") boolean hasTests,
        @JsonProperty("suggestions") List<String> suggestions) {

    @JsonCreator
    public CMakeAnalysisResult {
    }
}
