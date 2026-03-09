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
import java.util.Map;
import java.util.Objects;

/**
 * Represents the persisted state of a YAPPC-managed project.
 *
 * @doc.type record
 * @doc.purpose Project state tracking model
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public final class ProjectState {

    private final String projectName;
    private final String packName;
    private final String packVersion;
    private final String yappcVersion;
    private final Map<String, Object> variables;
    private final List<FeatureRecord> addedFeatures;
    private final List<String> generatedFiles;
    private final Instant createdAt;
    private final Instant lastModifiedAt;

    private ProjectState(Builder builder) {
        this.projectName = builder.projectName;
        this.packName = builder.packName;
        this.packVersion = builder.packVersion;
        this.yappcVersion = builder.yappcVersion;
        this.variables = builder.variables != null ? Map.copyOf(builder.variables) : Map.of();
        this.addedFeatures = builder.addedFeatures != null ? List.copyOf(builder.addedFeatures) : List.of();
        this.generatedFiles = builder.generatedFiles != null ? List.copyOf(builder.generatedFiles) : List.of();
        this.createdAt = builder.createdAt;
        this.lastModifiedAt = builder.lastModifiedAt;
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

    public String getPackVersion() {
        return packVersion;
    }

    public String getYappcVersion() {
        return yappcVersion;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public List<FeatureRecord> getAddedFeatures() {
        return addedFeatures;
    }

    public List<String> getGeneratedFiles() {
        return generatedFiles;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastModifiedAt() {
        return lastModifiedAt;
    }

    public boolean hasFeature(String featureName) {
        return addedFeatures.stream()
                .anyMatch(f -> f.name().equals(featureName));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProjectState that = (ProjectState) o;
        return Objects.equals(projectName, that.projectName) &&
                Objects.equals(packName, that.packName) &&
                Objects.equals(packVersion, that.packVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(projectName, packName, packVersion);
    }

    @Override
    public String toString() {
        return "ProjectState{" +
                "projectName='" + projectName + '\'' +
                ", packName='" + packName + '\'' +
                ", packVersion='" + packVersion + '\'' +
                ", features=" + addedFeatures.size() +
                ", files=" + generatedFiles.size() +
                '}';
    }

    /**
     * Record of an added feature.
     */
    public record FeatureRecord(
            String name,
            String type,
            Instant addedAt,
            List<String> filesAdded
    ) {}

    public static final class Builder {
        private String projectName;
        private String packName;
        private String packVersion;
        private String yappcVersion;
        private Map<String, Object> variables;
        private List<FeatureRecord> addedFeatures;
        private List<String> generatedFiles;
        private Instant createdAt;
        private Instant lastModifiedAt;

        private Builder() {}

        public Builder projectName(String projectName) {
            this.projectName = projectName;
            return this;
        }

        public Builder packName(String packName) {
            this.packName = packName;
            return this;
        }

        public Builder packVersion(String packVersion) {
            this.packVersion = packVersion;
            return this;
        }

        public Builder yappcVersion(String yappcVersion) {
            this.yappcVersion = yappcVersion;
            return this;
        }

        public Builder variables(Map<String, Object> variables) {
            this.variables = variables;
            return this;
        }

        public Builder addedFeatures(List<FeatureRecord> addedFeatures) {
            this.addedFeatures = addedFeatures;
            return this;
        }

        public Builder generatedFiles(List<String> generatedFiles) {
            this.generatedFiles = generatedFiles;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder lastModifiedAt(Instant lastModifiedAt) {
            this.lastModifiedAt = lastModifiedAt;
            return this;
        }

        public ProjectState build() {
            return new ProjectState(this);
        }
    }
}
