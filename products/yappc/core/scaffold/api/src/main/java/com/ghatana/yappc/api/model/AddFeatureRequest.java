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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Request to add a feature to an existing project.
 *
 * @doc.type record
 * @doc.purpose Feature addition request model
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public final class AddFeatureRequest {

    private final Path projectPath;
    private final String feature;
    private final String type;
    private final Map<String, Object> variables;
    private final boolean dryRun;
    private final boolean force;

    private AddFeatureRequest(Builder builder) {
        this.projectPath = Objects.requireNonNull(builder.projectPath, "projectPath is required");
        this.feature = Objects.requireNonNull(builder.feature, "feature is required");
        this.type = builder.type;
        this.variables = Map.copyOf(builder.variables);
        this.dryRun = builder.dryRun;
        this.force = builder.force;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Path getProjectPath() {
        return projectPath;
    }

    public String getFeature() {
        return feature;
    }

    public String getType() {
        return type;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public boolean isForce() {
        return force;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AddFeatureRequest that = (AddFeatureRequest) o;
        return dryRun == that.dryRun &&
                force == that.force &&
                Objects.equals(projectPath, that.projectPath) &&
                Objects.equals(feature, that.feature) &&
                Objects.equals(type, that.type) &&
                Objects.equals(variables, that.variables);
    }

    @Override
    public int hashCode() {
        return Objects.hash(projectPath, feature, type, variables, dryRun, force);
    }

    @Override
    public String toString() {
        return "AddFeatureRequest{" +
                "projectPath=" + projectPath +
                ", feature='" + feature + '\'' +
                ", type='" + type + '\'' +
                ", dryRun=" + dryRun +
                ", force=" + force +
                '}';
    }

    public static final class Builder {
        private Path projectPath;
        private String feature;
        private String type;
        private final Map<String, Object> variables = new HashMap<>();
        private boolean dryRun = false;
        private boolean force = false;

        private Builder() {}

        public Builder projectPath(Path projectPath) {
            this.projectPath = projectPath;
            return this;
        }

        public Builder feature(String feature) {
            this.feature = feature;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
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

        public Builder dryRun(boolean dryRun) {
            this.dryRun = dryRun;
            return this;
        }

        public Builder force(boolean force) {
            this.force = force;
            return this;
        }

        public AddFeatureRequest build() {
            return new AddFeatureRequest(this);
        }
    }
}
