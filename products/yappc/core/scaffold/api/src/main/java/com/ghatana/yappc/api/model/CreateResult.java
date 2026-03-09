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
 * Result of project creation.
 *
 * @doc.type record
 * @doc.purpose Project creation result model
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public final class CreateResult {

    private final boolean success;
    private final Path projectPath;
    private final String packName;
    private final String packVersion;
    private final List<String> filesCreated;
    private final List<String> warnings;
    private final String errorMessage;
    private final Instant createdAt;
    private final long durationMs;

    private CreateResult(Builder builder) {
        this.success = builder.success;
        this.projectPath = builder.projectPath;
        this.packName = builder.packName;
        this.packVersion = builder.packVersion;
        this.filesCreated = builder.filesCreated != null ? List.copyOf(builder.filesCreated) : List.of();
        this.warnings = builder.warnings != null ? List.copyOf(builder.warnings) : List.of();
        this.errorMessage = builder.errorMessage;
        this.createdAt = builder.createdAt != null ? builder.createdAt : Instant.now();
        this.durationMs = builder.durationMs;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static CreateResult success(Path projectPath, String packName, List<String> filesCreated) {
        return builder()
                .success(true)
                .projectPath(projectPath)
                .packName(packName)
                .filesCreated(filesCreated)
                .build();
    }

    public static CreateResult failure(String errorMessage) {
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

    public String getPackName() {
        return packName;
    }

    public String getPackVersion() {
        return packVersion;
    }

    public List<String> getFilesCreated() {
        return filesCreated;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public int getFileCount() {
        return filesCreated.size();
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CreateResult that = (CreateResult) o;
        return success == that.success &&
                durationMs == that.durationMs &&
                Objects.equals(projectPath, that.projectPath) &&
                Objects.equals(packName, that.packName) &&
                Objects.equals(packVersion, that.packVersion) &&
                Objects.equals(filesCreated, that.filesCreated) &&
                Objects.equals(warnings, that.warnings) &&
                Objects.equals(errorMessage, that.errorMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(success, projectPath, packName, packVersion, filesCreated, warnings, errorMessage, durationMs);
    }

    @Override
    public String toString() {
        return "CreateResult{" +
                "success=" + success +
                ", projectPath=" + projectPath +
                ", packName='" + packName + '\'' +
                ", filesCreated=" + filesCreated.size() +
                ", warnings=" + warnings.size() +
                ", durationMs=" + durationMs +
                '}';
    }

    public static final class Builder {
        private boolean success;
        private Path projectPath;
        private String packName;
        private String packVersion;
        private List<String> filesCreated;
        private List<String> warnings;
        private String errorMessage;
        private Instant createdAt;
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

        public Builder packName(String packName) {
            this.packName = packName;
            return this;
        }

        public Builder packVersion(String packVersion) {
            this.packVersion = packVersion;
            return this;
        }

        public Builder filesCreated(List<String> filesCreated) {
            this.filesCreated = filesCreated;
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

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder durationMs(long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        public CreateResult build() {
            return new CreateResult(this);
        }
    }
}
