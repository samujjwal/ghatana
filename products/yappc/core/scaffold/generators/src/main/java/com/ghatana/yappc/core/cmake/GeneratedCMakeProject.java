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

package com.ghatana.yappc.core.cmake;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Generated CMake project configuration.
 
 * @doc.type record
 * @doc.purpose Immutable data carrier for generated c make project
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public record GeneratedCMakeProject(
        @JsonProperty("cmakeLists") String cmakeLists,
        @JsonProperty("gitignore") String gitignore,
        @JsonProperty("readme") String readme,
        @JsonProperty("warnings") List<String> warnings) {

    @JsonCreator
    public GeneratedCMakeProject {
    }
}
