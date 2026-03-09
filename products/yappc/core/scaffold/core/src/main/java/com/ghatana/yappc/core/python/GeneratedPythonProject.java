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

/**
 * Generated Python project configuration.
 *
 * @doc.type record
 * @doc.purpose Generated Python project with pyproject.toml
 * @doc.layer platform
 * @doc.pattern Data Transfer Object
 */
public record GeneratedPythonProject(
        @JsonProperty("pyprojectToml") String pyprojectToml,
        @JsonProperty("requirementsTxt") String requirementsTxt,
        @JsonProperty("devRequirementsTxt") String devRequirementsTxt,
        @JsonProperty("readmeMd") String readmeMd,
        @JsonProperty("gitignore") String gitignore,
        @JsonProperty("warnings") List<String> warnings) {

    @JsonCreator
    public GeneratedPythonProject {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String pyprojectToml;
        private String requirementsTxt = "";
        private String devRequirementsTxt = "";
        private String readmeMd = "";
        private String gitignore = "";
        private List<String> warnings = List.of();

        public Builder pyprojectToml(String pyprojectToml) {
            this.pyprojectToml = pyprojectToml;
            return this;
        }

        public Builder requirementsTxt(String requirementsTxt) {
            this.requirementsTxt = requirementsTxt;
            return this;
        }

        public Builder devRequirementsTxt(String devRequirementsTxt) {
            this.devRequirementsTxt = devRequirementsTxt;
            return this;
        }

        public Builder readmeMd(String readmeMd) {
            this.readmeMd = readmeMd;
            return this;
        }

        public Builder gitignore(String gitignore) {
            this.gitignore = gitignore;
            return this;
        }

        public Builder warnings(List<String> warnings) {
            this.warnings = warnings;
            return this;
        }

        public GeneratedPythonProject build() {
            return new GeneratedPythonProject(
                    pyprojectToml,
                    requirementsTxt,
                    devRequirementsTxt,
                    readmeMd,
                    gitignore,
                    warnings);
        }
    }
}
