/*
 * Copyright (c) 2025 Ghatana Platform Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.yappc.core.go;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Validation result for Go build specification.
 *
 * @doc.type record
 * @doc.purpose Validation result for Go module configuration
 * @doc.layer platform
 * @doc.pattern Data Transfer Object
 */
public record GoValidationResult(
        @JsonProperty("valid") boolean valid,
        @JsonProperty("errors") List<String> errors,
        @JsonProperty("warnings") List<String> warnings,
        @JsonProperty("suggestions") List<String> suggestions,
        @JsonProperty("summary") String summary) {

    public static GoValidationResult success() {
        return new GoValidationResult(true, List.of(), List.of(), List.of(), "Validation passed");
    }

    public static GoValidationResult failure(List<String> errors) {
        return new GoValidationResult(false, errors, List.of(), List.of(), 
                "Validation failed with " + errors.size() + " error(s)");
    }
}
