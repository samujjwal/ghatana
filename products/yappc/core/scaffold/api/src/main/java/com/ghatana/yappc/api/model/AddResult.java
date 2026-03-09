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
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Result of adding a feature to a project.
 *
 * @doc.type record
 * @doc.purpose Feature addition result model
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public final class AddResult {

    private final boolean success;
    private final Path projectPath;
    private final String feature;
    private final String type;
    private final List<String> filesCreated;
    private final List<String> filesModified;
    private final List<String> dependenciesAdded;
    private final List<String> warnings;
    private final String errorMessage;
    private final Instant addedAt;
    private final long durationMs;

    private AddResult(Builder builder) {
        this.success = builder.success;
        this.projectPath = builder.projectPath;
        this.feature = builder.feature;
        this.type = builder.type;
        this.filesCreated = builder.filesCreated != null ? List.copyOf(builder.filesCreated) : List.of();
        this.filesModified = builder.filesModified != null ? List.copyOf(builder.filesModified) : List.of();
        this.dependenciesAdded = builder.dependenciesAdded != null ? List.copyOf(builder.dependenciesAdded) : List.of();
        this.warnings = builder.warnings != null ? List.copyOf(builder.warnings) : List.of();
        this.errorMessage = builder.errorMessage;
        this.addedAt = builder.addedAt != null ? builder.addedAt : Instant.now();
        this.durationMs = builder.durationMs;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static AddResult success(Path projectPath, String feature, String type, 
                                    List<String> filesCreated, List<String> dependenciesAdded) {
        return builder()
                .success(true)
                .projectPath(projectPath)
                .feature(feature)
                .type(type)
                .filesCreated(filesCreated)
                .dependenciesAdded(dependenciesAdded)
                .build();
    }

    public static AddResult failure(String errorMessage) {
        return builder()
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }

    public boolean isSuccess() {
        return success;
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

    public List<String> getFilesCreated() {
        return filesCreated;
    }

    public List<String> getFilesModified() {
        return filesModified;
    }

    public List<String> getDependenciesAdded() {
        return dependenciesAdded;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getAddedAt() {
        return addedAt;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public int getTotalFilesAffected() {
        return filesCreated.size() + filesModified.size();
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AddResult addResult = (AddResult) o;
        return success == addResult.success &&
                durationMs == addResult.durationMs &&
                Objects.equals(projectPath, addResult.projectPath) &&
                Objects.equals(feature, addResult.feature) &&
                Objects.equals(type, addResult.type) &&
                Objects.equals(filesCreated, addResult.filesCreated) &&
                Objects.equals(filesModified, addResult.filesModified) &&
                Objects.equals(dependenciesAdded, addResult.dependenciesAdded);
    }

    @Override
    public int hashCode() {
        return Objects.hash(success, projectPath, feature, type, filesCreated, filesModified, dependenciesAdded, durationMs);
    }

    @Override
    public String toString() {
        return "AddResult{" +
                "success=" + success +
                ", feature='" + feature + '\'' +
                ", type='" + type + '\'' +
                ", filesCreated=" + filesCreated.size() +
                ", filesModified=" + filesModified.size() +
                ", dependenciesAdded=" + dependenciesAdded.size() +
                '}';
    }

    public static final class Builder {
        private boolean success;
        private Path projectPath;
        private String feature;
        private String type;
        private List<String> filesCreated;
        private List<String> filesModified;
        private List<String> dependenciesAdded;
        private List<String> warnings;
        private String errorMessage;
        private Instant addedAt;
        private long durationMs;

        private Builder() {}

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

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

        public Builder filesCreated(List<String> filesCreated) {
            this.filesCreated = filesCreated;
            return this;
        }

        public Builder filesModified(List<String> filesModified) {
            this.filesModified = filesModified;
            return this;
        }

        public Builder dependenciesAdded(List<String> dependenciesAdded) {
            this.dependenciesAdded = dependenciesAdded;
            return this;
        }

        public Builder warnings(List<String> warnings) {
            this.warnings = warnings;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder addedAt(Instant addedAt) {
            this.addedAt = addedAt;
            return this;
        }

        public Builder durationMs(long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        public AddResult build() {
            return new AddResult(this);
        }
    }
}
