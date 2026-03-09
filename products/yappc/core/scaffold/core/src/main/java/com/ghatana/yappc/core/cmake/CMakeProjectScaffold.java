/*
 * Copyright (c) 2025 Ghatana Platform Contributors
 */

package com.ghatana.yappc.core.cmake;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**

 * @doc.type record

 * @doc.purpose Immutable data carrier for c make project scaffold

 * @doc.layer core

 * @doc.pattern ValueObject

 */

public record CMakeProjectScaffold(
        @JsonProperty("sourceFiles") Map<String, String> sourceFiles,
        @JsonProperty("headerFiles") Map<String, String> headerFiles,
        @JsonProperty("testFiles") Map<String, String> testFiles,
        @JsonProperty("directories") List<String> directories) {

    @JsonCreator
    public CMakeProjectScaffold {
    }
}
