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
 * .NET project analysis result.
 *
 * @doc.type record
 * @doc.purpose Analysis result for .NET projects
 * @doc.layer platform
 * @doc.pattern Data Transfer Object
 */
public record DotnetAnalysisResult(
        @JsonProperty("projectType") DotnetBuildSpec.DotnetProjectType projectType,
        @JsonProperty("targetFramework") String targetFramework,
        @JsonProperty("detectedPackages") List<String> detectedPackages,
        @JsonProperty("hasTests") boolean hasTests,
        @JsonProperty("hasNullableEnabled") boolean hasNullableEnabled,
        @JsonProperty("codeQualityScore") int codeQualityScore,
        @JsonProperty("suggestions") List<String> suggestions) {

    @JsonCreator
    public DotnetAnalysisResult {
    }
}
