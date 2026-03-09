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

package com.ghatana.yappc.core.telemetry.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.time.Instant;

/**
 * Day 19: Telemetry event model for anonymized usage data collection. Designed for privacy
 * compliance per Doc1 §6 Non-Negotiables.
 */
@JsonDeserialize(builder = TelemetryEvent.Builder.class)
/**
 * TelemetryEvent component within the YAPPC platform.
 *
 * @doc.type class
 * @doc.purpose TelemetryEvent component within the YAPPC platform.
 * @doc.layer platform
 * @doc.pattern Component
 */
public class TelemetryEvent {

    private final String eventType;
    private final String command;
    private final boolean success;
    private final long durationMs;
    private final Instant timestamp;
    private final String projectType;
    private final String language;
    private final String framework;
    private final String packName;
    private final String errorType;
    private final String userId;
    private final String workspacePath;

    @JsonCreator
    TelemetryEvent(
            @JsonProperty("eventType") String eventType,
            @JsonProperty("command") String command,
            @JsonProperty("success") boolean success,
            @JsonProperty("durationMs") long durationMs,
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("projectType") String projectType,
            @JsonProperty("language") String language,
            @JsonProperty("framework") String framework,
            @JsonProperty("packName") String packName,
            @JsonProperty("errorType") String errorType,
            @JsonProperty("userId") String userId,
            @JsonProperty("workspacePath") String workspacePath) {
        this.eventType = eventType;
        this.command = command;
        this.success = success;
        this.durationMs = durationMs;
        this.timestamp = timestamp;
        this.projectType = projectType;
        this.language = language;
        this.framework = framework;
        this.packName = packName;
        this.errorType = errorType;
        this.userId = userId;
        this.workspacePath = workspacePath;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getEventType() {
        return eventType;
    }

    public String getCommand() {
        return command;
    }

    public boolean isSuccess() {
        return success;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getProjectType() {
        return projectType;
    }

    public String getLanguage() {
        return language;
    }

    public String getFramework() {
        return framework;
    }

    public String getPackName() {
        return packName;
    }

    public String getErrorType() {
        return errorType;
    }

    public String getUserId() {
        return userId;
    }

    public String getWorkspacePath() {
        return workspacePath;
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
        private String eventType;
        private String command;
        private boolean success;
        private long durationMs;
        private Instant timestamp = Instant.now();
        private String projectType;
        private String language;
        private String framework;
        private String packName;
        private String errorType;
        private String userId;
        private String workspacePath;

        public Builder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder command(String command) {
            this.command = command;
            return this;
        }

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder durationMs(long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder projectType(String projectType) {
            this.projectType = projectType;
            return this;
        }

        public Builder language(String language) {
            this.language = language;
            return this;
        }

        public Builder framework(String framework) {
            this.framework = framework;
            return this;
        }

        public Builder packName(String packName) {
            this.packName = packName;
            return this;
        }

        public Builder errorType(String errorType) {
            this.errorType = errorType;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder workspacePath(String workspacePath) {
            this.workspacePath = workspacePath;
            return this;
        }

        public TelemetryEvent build() {
            return new TelemetryEvent(
                    eventType,
                    command,
                    success,
                    durationMs,
                    timestamp,
                    projectType,
                    language,
                    framework,
                    packName,
                    errorType,
                    userId,
                    workspacePath);
        }
    }
}
