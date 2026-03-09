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

package com.ghatana.yappc.api.model;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Request to create a new project from a pack.
 *
 * @doc.type record
 * @doc.purpose Project creation request model
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public final class CreateRequest {

    private final String projectName;
    private final String packName;
    private final Path outputPath;
    private final Map<String, Object> variables;
    private final boolean overwrite;
    private final boolean dryRun;
    private final boolean skipValidation;

    private CreateRequest(Builder builder) {
        this.projectName = Objects.requireNonNull(builder.projectName, "projectName is required");
        this.packName = Objects.requireNonNull(builder.packName, "packName is required");
        this.outputPath = builder.outputPath != null ? builder.outputPath : Paths.get(System.getProperty("user.dir"));
        this.variables = Map.copyOf(builder.variables);
        this.overwrite = builder.overwrite;
        this.dryRun = builder.dryRun;
        this.skipValidation = builder.skipValidation;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getProjectName() {
        return projectName;
    }

    public String getPackName() {
        return packName;
    }

    public Path getOutputPath() {
        return outputPath;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public boolean isOverwrite() {
        return overwrite;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public boolean isSkipValidation() {
        return skipValidation;
    }

    /**
     * Get the full project path (outputPath + projectName).
     */
    public Path getProjectPath() {
        return outputPath.resolve(projectName);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CreateRequest that = (CreateRequest) o;
        return overwrite == that.overwrite &&
                dryRun == that.dryRun &&
                skipValidation == that.skipValidation &&
                Objects.equals(projectName, that.projectName) &&
                Objects.equals(packName, that.packName) &&
                Objects.equals(outputPath, that.outputPath) &&
                Objects.equals(variables, that.variables);
    }

    @Override
    public int hashCode() {
        return Objects.hash(projectName, packName, outputPath, variables, overwrite, dryRun, skipValidation);
    }

    @Override
    public String toString() {
        return "CreateRequest{" +
                "projectName='" + projectName + '\'' +
                ", packName='" + packName + '\'' +
                ", outputPath=" + outputPath +
                ", variables=" + variables +
                ", overwrite=" + overwrite +
                ", dryRun=" + dryRun +
                ", skipValidation=" + skipValidation +
                '}';
    }

    public static final class Builder {
        private String projectName;
        private String packName;
        private Path outputPath;
        private final Map<String, Object> variables = new HashMap<>();
        private boolean overwrite = false;
        private boolean dryRun = false;
        private boolean skipValidation = false;

        private Builder() {}

        public Builder projectName(String projectName) {
            this.projectName = projectName;
            return this;
        }

        public Builder packName(String packName) {
            this.packName = packName;
            return this;
        }

        public Builder outputPath(Path outputPath) {
            this.outputPath = outputPath;
            return this;
        }

        public Builder variable(String key, Object value) {
            this.variables.put(key, value);
            return this;
        }

        public Builder variables(Map<String, Object> variables) {
            this.variables.putAll(variables);
            return this;
        }

        public Builder overwrite(boolean overwrite) {
            this.overwrite = overwrite;
            return this;
        }

        public Builder dryRun(boolean dryRun) {
            this.dryRun = dryRun;
            return this;
        }

        public Builder skipValidation(boolean skipValidation) {
            this.skipValidation = skipValidation;
            return this;
        }

        public CreateRequest build() {
            return new CreateRequest(this);
        }
    }
}
