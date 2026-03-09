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
 * Information about an existing YAPPC-managed project.
 *
 * @doc.type record
 * @doc.purpose Project information model
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public final class ProjectInfo {

    private final Path projectPath;
    private final String projectName;
    private final String packName;
    private final String packVersion;
    private final String language;
    private final String platform;
    private final List<String> addedFeatures;
    private final Map<String, Object> variables;
    private final Instant createdAt;
    private final Instant lastModifiedAt;
    private final boolean updateAvailable;

    private ProjectInfo(Builder builder) {
        this.projectPath = builder.projectPath;
        this.projectName = builder.projectName;
        this.packName = builder.packName;
        this.packVersion = builder.packVersion;
        this.language = builder.language;
        this.platform = builder.platform;
        this.addedFeatures = builder.addedFeatures != null ? List.copyOf(builder.addedFeatures) : List.of();
        this.variables = builder.variables != null ? Map.copyOf(builder.variables) : Map.of();
        this.createdAt = builder.createdAt;
        this.lastModifiedAt = builder.lastModifiedAt;
        this.updateAvailable = builder.updateAvailable;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Path getProjectPath() {
        return projectPath;
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

    public String getLanguage() {
        return language;
    }

    public String getPlatform() {
        return platform;
    }

    public List<String> getAddedFeatures() {
        return addedFeatures;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastModifiedAt() {
        return lastModifiedAt;
    }

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public boolean hasFeature(String feature) {
        return addedFeatures.contains(feature);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProjectInfo that = (ProjectInfo) o;
        return Objects.equals(projectPath, that.projectPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(projectPath);
    }

    @Override
    public String toString() {
        return "ProjectInfo{" +
                "projectPath=" + projectPath +
                ", projectName='" + projectName + '\'' +
                ", packName='" + packName + '\'' +
                ", language='" + language + '\'' +
                ", features=" + addedFeatures.size() +
                '}';
    }

    public static final class Builder {
        private Path projectPath;
        private String projectName;
        private String packName;
        private String packVersion;
        private String language;
        private String platform;
        private List<String> addedFeatures;
        private Map<String, Object> variables;
        private Instant createdAt;
        private Instant lastModifiedAt;
        private boolean updateAvailable;

        private Builder() {}

        public Builder projectPath(Path projectPath) {
            this.projectPath = projectPath;
            return this;
        }

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

        public Builder language(String language) {
            this.language = language;
            return this;
        }

        public Builder platform(String platform) {
            this.platform = platform;
            return this;
        }

        public Builder addedFeatures(List<String> addedFeatures) {
            this.addedFeatures = addedFeatures;
            return this;
        }

        public Builder variables(Map<String, Object> variables) {
            this.variables = variables;
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

        public Builder updateAvailable(boolean updateAvailable) {
            this.updateAvailable = updateAvailable;
            return this;
        }

        public ProjectInfo build() {
            return new ProjectInfo(this);
        }
    }
}
