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

package com.ghatana.yappc.core.python;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Generated Python project scaffold with source files and directory structure.
 *
 * @doc.type record
 * @doc.purpose Python project scaffold structure
 * @doc.layer platform
 * @doc.pattern Data Transfer Object
 */
public record PythonProjectScaffold(
        @JsonProperty("sourceFiles") Map<String, String> sourceFiles,
        @JsonProperty("testFiles") Map<String, String> testFiles,
        @JsonProperty("configFiles") Map<String, String> configFiles,
        @JsonProperty("directories") List<String> directories) {

    @JsonCreator
    public PythonProjectScaffold {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Map<String, String> sourceFiles = Map.of();
        private Map<String, String> testFiles = Map.of();
        private Map<String, String> configFiles = Map.of();
        private List<String> directories = List.of();

        public Builder sourceFiles(Map<String, String> sourceFiles) {
            this.sourceFiles = sourceFiles;
            return this;
        }

        public Builder testFiles(Map<String, String> testFiles) {
            this.testFiles = testFiles;
            return this;
        }

        public Builder configFiles(Map<String, String> configFiles) {
            this.configFiles = configFiles;
            return this;
        }

        public Builder directories(List<String> directories) {
            this.directories = directories;
            return this;
        }

        public PythonProjectScaffold build() {
            return new PythonProjectScaffold(
                    sourceFiles,
                    testFiles,
                    configFiles,
                    directories);
        }
    }
}
