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
 * Result of updating a project.
 *
 * @doc.type record
 * @doc.purpose Project update result model
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public final class UpdateResult {

    private final boolean success;
    private final Path projectPath;
    private final String fromVersion;
    private final String toVersion;
    private final List<String> filesUpdated;
    private final List<String> filesAdded;
    private final List<String> filesRemoved;
    private final List<String> filesSkipped;
    private final List<String> conflicts;
    private final List<String> warnings;
    private final String backupPath;
    private final String errorMessage;
    private final Instant updatedAt;
    private final long durationMs;

    private UpdateResult(Builder builder) {
        this.success = builder.success;
        this.projectPath = builder.projectPath;
        this.fromVersion = builder.fromVersion;
        this.toVersion = builder.toVersion;
        this.filesUpdated = builder.filesUpdated != null ? List.copyOf(builder.filesUpdated) : List.of();
        this.filesAdded = builder.filesAdded != null ? List.copyOf(builder.filesAdded) : List.of();
        this.filesRemoved = builder.filesRemoved != null ? List.copyOf(builder.filesRemoved) : List.of();
        this.filesSkipped = builder.filesSkipped != null ? List.copyOf(builder.filesSkipped) : List.of();
        this.conflicts = builder.conflicts != null ? List.copyOf(builder.conflicts) : List.of();
        this.warnings = builder.warnings != null ? List.copyOf(builder.warnings) : List.of();
        this.backupPath = builder.backupPath;
        this.errorMessage = builder.errorMessage;
        this.updatedAt = builder.updatedAt != null ? builder.updatedAt : Instant.now();
        this.durationMs = builder.durationMs;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static UpdateResult success(Path projectPath, String fromVersion, String toVersion,
                                        List<String> filesUpdated) {
        return builder()
                .success(true)
                .projectPath(projectPath)
                .fromVersion(fromVersion)
                .toVersion(toVersion)
                .filesUpdated(filesUpdated)
                .build();
    }

    public static UpdateResult failure(String errorMessage) {
        return builder()
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }

    public static UpdateResult noUpdate(Path projectPath, String version) {
        return builder()
                .success(true)
                .projectPath(projectPath)
                .fromVersion(version)
                .toVersion(version)
                .build();
    }

    public boolean isSuccess() {
        return success;
    }

    public Path getProjectPath() {
        return projectPath;
    }

    public String getFromVersion() {
        return fromVersion;
    }

    public String getToVersion() {
        return toVersion;
    }

    public List<String> getFilesUpdated() {
        return filesUpdated;
    }

    public List<String> getFilesAdded() {
        return filesAdded;
    }

    public List<String> getFilesRemoved() {
        return filesRemoved;
    }

    public List<String> getFilesSkipped() {
        return filesSkipped;
    }

    public List<String> getConflicts() {
        return conflicts;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public String getBackupPath() {
        return backupPath;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public int getTotalFilesAffected() {
        return filesUpdated.size() + filesAdded.size() + filesRemoved.size();
    }

    public boolean hasConflicts() {
        return !conflicts.isEmpty();
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    public boolean isVersionChanged() {
        return fromVersion != null && toVersion != null && !fromVersion.equals(toVersion);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UpdateResult that = (UpdateResult) o;
        return success == that.success &&
                Objects.equals(projectPath, that.projectPath) &&
                Objects.equals(fromVersion, that.fromVersion) &&
                Objects.equals(toVersion, that.toVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(success, projectPath, fromVersion, toVersion);
    }

    @Override
    public String toString() {
        return "UpdateResult{" +
                "success=" + success +
                ", projectPath=" + projectPath +
                ", fromVersion='" + fromVersion + '\'' +
                ", toVersion='" + toVersion + '\'' +
                ", filesUpdated=" + filesUpdated.size() +
                ", filesAdded=" + filesAdded.size() +
                ", conflicts=" + conflicts.size() +
                '}';
    }

    public static final class Builder {
        private boolean success;
        private Path projectPath;
        private String fromVersion;
        private String toVersion;
        private List<String> filesUpdated;
        private List<String> filesAdded;
        private List<String> filesRemoved;
        private List<String> filesSkipped;
        private List<String> conflicts;
        private List<String> warnings;
        private String backupPath;
        private String errorMessage;
        private Instant updatedAt;
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

        public Builder fromVersion(String fromVersion) {
            this.fromVersion = fromVersion;
            return this;
        }

        public Builder toVersion(String toVersion) {
            this.toVersion = toVersion;
            return this;
        }

        public Builder filesUpdated(List<String> filesUpdated) {
            this.filesUpdated = filesUpdated;
            return this;
        }

        public Builder filesAdded(List<String> filesAdded) {
            this.filesAdded = filesAdded;
            return this;
        }

        public Builder filesRemoved(List<String> filesRemoved) {
            this.filesRemoved = filesRemoved;
            return this;
        }

        public Builder filesSkipped(List<String> filesSkipped) {
            this.filesSkipped = filesSkipped;
            return this;
        }

        public Builder conflicts(List<String> conflicts) {
            this.conflicts = conflicts;
            return this;
        }

        public Builder warnings(List<String> warnings) {
            this.warnings = warnings;
            return this;
        }

        public Builder backupPath(String backupPath) {
            this.backupPath = backupPath;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public Builder durationMs(long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        public UpdateResult build() {
            return new UpdateResult(this);
        }
    }
}
