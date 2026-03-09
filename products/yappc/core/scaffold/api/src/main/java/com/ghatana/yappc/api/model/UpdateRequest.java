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
 * Request to update an existing project with pack changes.
 *
 * @doc.type record
 * @doc.purpose Project update request model
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public final class UpdateRequest {

    private final Path projectPath;
    private final boolean dryRun;
    private final boolean force;
    private final boolean backup;
    private final Map<String, Object> newVariables;

    private UpdateRequest(Builder builder) {
        this.projectPath = Objects.requireNonNull(builder.projectPath, "projectPath is required");
        this.dryRun = builder.dryRun;
        this.force = builder.force;
        this.backup = builder.backup;
        this.newVariables = Map.copyOf(builder.newVariables);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Path getProjectPath() {
        return projectPath;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public boolean isForce() {
        return force;
    }

    public boolean isBackup() {
        return backup;
    }

    public Map<String, Object> getNewVariables() {
        return newVariables;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UpdateRequest that = (UpdateRequest) o;
        return dryRun == that.dryRun &&
                force == that.force &&
                backup == that.backup &&
                Objects.equals(projectPath, that.projectPath) &&
                Objects.equals(newVariables, that.newVariables);
    }

    @Override
    public int hashCode() {
        return Objects.hash(projectPath, dryRun, force, backup, newVariables);
    }

    @Override
    public String toString() {
        return "UpdateRequest{" +
                "projectPath=" + projectPath +
                ", dryRun=" + dryRun +
                ", force=" + force +
                ", backup=" + backup +
                '}';
    }

    public static final class Builder {
        private Path projectPath;
        private boolean dryRun = false;
        private boolean force = false;
        private boolean backup = true;
        private final Map<String, Object> newVariables = new HashMap<>();

        private Builder() {}

        public Builder projectPath(Path projectPath) {
            this.projectPath = projectPath;
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

        public Builder backup(boolean backup) {
            this.backup = backup;
            return this;
        }

        public Builder newVariable(String key, Object value) {
            this.newVariables.put(key, value);
            return this;
        }

        public Builder newVariables(Map<String, Object> variables) {
            this.newVariables.putAll(variables);
            return this;
        }

        public UpdateRequest build() {
            return new UpdateRequest(this);
        }
    }
}
