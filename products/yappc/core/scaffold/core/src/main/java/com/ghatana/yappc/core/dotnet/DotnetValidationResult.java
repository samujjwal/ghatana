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

package com.ghatana.yappc.core.dotnet;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * .NET build specification validation result.
 *
 * @doc.type record
 * @doc.purpose Validation result for .NET build specs
 * @doc.layer platform
 * @doc.pattern Data Transfer Object
 */
public record DotnetValidationResult(
        @JsonProperty("valid") boolean valid,
        @JsonProperty("errors") List<String> errors,
        @JsonProperty("warnings") List<String> warnings,
        @JsonProperty("recommendations") List<String> recommendations) {

    @JsonCreator
    public DotnetValidationResult {
    }

    public static DotnetValidationResult success() {
        return new DotnetValidationResult(true, List.of(), List.of(), List.of());
    }

    public static DotnetValidationResult failure(List<String> errors) {
        return new DotnetValidationResult(false, errors, List.of(), List.of());
    }
}
