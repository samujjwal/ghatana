/*
 * Copyright (c) 2025 Ghatana Platform Contributors
 */

package com.ghatana.yappc.core.cmake;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**

 * @doc.type record

 * @doc.purpose Immutable data carrier for c make validation result

 * @doc.layer core

 * @doc.pattern ValueObject

 */

public record CMakeValidationResult(
        @JsonProperty("valid") boolean valid,
        @JsonProperty("errors") List<String> errors,
        @JsonProperty("warnings") List<String> warnings,
        @JsonProperty("recommendations") List<String> recommendations) {

    @JsonCreator
    public CMakeValidationResult {
    }

    public static CMakeValidationResult success() {
        return new CMakeValidationResult(true, List.of(), List.of(), List.of());
    }

    public static CMakeValidationResult failure(List<String> errors) {
        return new CMakeValidationResult(false, errors, List.of(), List.of());
    }
}
