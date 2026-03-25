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
import java.util.Map;

/**
 * Generated Go project scaffold with source files and directory structure.
 *
 * @doc.type record
 * @doc.purpose Generated Go project scaffold with source files and directory structure
 * @doc.layer platform
 * @doc.pattern Data Transfer Object
 */
public record GoProjectScaffold(
        @JsonProperty("sourceFiles") Map<String, String> sourceFiles,
        @JsonProperty("testFiles") Map<String, String> testFiles,
        @JsonProperty("internalFiles") Map<String, String> internalFiles,
        @JsonProperty("pkgFiles") Map<String, String> pkgFiles,
        @JsonProperty("cmdFiles") Map<String, String> cmdFiles,
        @JsonProperty("directories") List<String> directories) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Map<String, String> sourceFiles = Map.of();
        private Map<String, String> testFiles = Map.of();
        private Map<String, String> internalFiles = Map.of();
        private Map<String, String> pkgFiles = Map.of();
        private Map<String, String> cmdFiles = Map.of();
        private List<String> directories = List.of();

        public Builder sourceFiles(Map<String, String> sourceFiles) {
            this.sourceFiles = sourceFiles;
            return this;
        }

        public Builder testFiles(Map<String, String> testFiles) {
            this.testFiles = testFiles;
            return this;
        }

        public Builder internalFiles(Map<String, String> internalFiles) {
            this.internalFiles = internalFiles;
            return this;
        }

        public Builder pkgFiles(Map<String, String> pkgFiles) {
            this.pkgFiles = pkgFiles;
            return this;
        }

        public Builder cmdFiles(Map<String, String> cmdFiles) {
            this.cmdFiles = cmdFiles;
            return this;
        }

        public Builder directories(List<String> directories) {
            this.directories = directories;
            return this;
        }

        public GoProjectScaffold build() {
            return new GoProjectScaffold(
                    sourceFiles,
                    testFiles,
                    internalFiles,
                    pkgFiles,
                    cmdFiles,
                    directories);
        }
    }
}
