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
package com.ghatana.yappc.core.rca;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ghatana.yappc.core.error.ErrorSeverity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * Day 26: Normalized build log representation for RCA analysis. Converts
 * various build tool outputs into a consistent format.
 *
 * @doc.type class
 * @doc.purpose Day 26: Normalized build log representation for RCA analysis.
 * Converts various build tool outputs
 * @doc.layer platform
 * @doc.pattern Component
 */
public class NormalizedBuildLog {

    @JsonProperty("tool")
    private BuildTool tool;

    @JsonProperty("startTime")
    private Instant startTime;

    @JsonProperty("endTime")
    private Instant endTime;

    @JsonProperty("status")
    private BuildStatus status;

    @JsonProperty("tasks")
    private List<BuildTask> tasks;

    @JsonProperty("errors")
    private List<BuildError> errors;

    @JsonProperty("warnings")
    private List<BuildWarning> warnings;

    @JsonProperty("environment")
    private Map<String, String> environment;

    @JsonProperty("rawLog")
    private String rawLog;

    // Constructor
    public NormalizedBuildLog() {
    }

    public NormalizedBuildLog(
            BuildTool tool,
            Instant startTime,
            Instant endTime,
            BuildStatus status,
            List<BuildTask> tasks,
            List<BuildError> errors,
            List<BuildWarning> warnings,
            Map<String, String> environment,
            String rawLog) {
        this.tool = tool;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
        this.tasks = tasks;
        this.errors = errors;
        this.warnings = warnings;
        this.environment = environment;
        this.rawLog = rawLog;
    }

    // Getters and setters
    public BuildTool getTool() {
        return tool;
    }

    public void setTool(BuildTool tool) {
        this.tool = tool;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }

    public BuildStatus getStatus() {
        return status;
    }

    public void setStatus(BuildStatus status) {
        this.status = status;
    }

    public List<BuildTask> getTasks() {
        return tasks;
    }

    public void setTasks(List<BuildTask> tasks) {
        this.tasks = tasks;
    }

    public List<BuildError> getErrors() {
        return errors;
    }

    public void setErrors(List<BuildError> errors) {
        this.errors = errors;
    }

    public List<BuildWarning> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<BuildWarning> warnings) {
        this.warnings = warnings;
    }

    public Map<String, String> getEnvironment() {
        return environment;
    }

    public void setEnvironment(Map<String, String> environment) {
        this.environment = environment;
    }

    public String getRawLog() {
        return rawLog;
    }

    public void setRawLog(String rawLog) {
        this.rawLog = rawLog;
    }

    /**
     * Supported build tools for normalization
     */
    public enum BuildTool {
        GRADLE("gradle"),
        NX("nx"),
        PNPM("pnpm"),
        CARGO("cargo"),
        MAVEN("maven"),
        NPM("npm"),
        UNKNOWN("unknown");

        private final String name;

        BuildTool(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public static BuildTool fromString(String name) {
            for (BuildTool tool : BuildTool.values()) {
                if (tool.name.equalsIgnoreCase(name)) {
                    return tool;
                }
            }
            return UNKNOWN;
        }
    }

    /**
     * Build execution status
     */
    public enum BuildStatus {
        SUCCESS,
        FAILURE,
        CANCELLED,
        TIMEOUT,
        UNKNOWN
    }

    /**
     * Individual build task representation
     */
    public static class BuildTask {

        @JsonProperty("name")
        private String name;

        @JsonProperty("status")
        private TaskStatus status;

        @JsonProperty("duration")
        private long durationMs;

        @JsonProperty("output")
        private String output;

        public BuildTask() {
        }

        public BuildTask(String name, TaskStatus status, long durationMs, String output) {
            this.name = name;
            this.status = status;
            this.durationMs = durationMs;
            this.output = output;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public TaskStatus getStatus() {
            return status;
        }

        public void setStatus(TaskStatus status) {
            this.status = status;
        }

        public long getDurationMs() {
            return durationMs;
        }

        public void setDurationMs(long durationMs) {
            this.durationMs = durationMs;
        }

        public String getOutput() {
            return output;
        }

        public void setOutput(String output) {
            this.output = output;
        }

        public enum TaskStatus {
            SUCCESS,
            FAILED,
            SKIPPED,
            UP_TO_DATE,
            FROM_CACHE,
            NO_SOURCE
        }
    }

    /**
     * Build error representation
     */
    public static class BuildError {

        @JsonProperty("message")
        private String message;

        @JsonProperty("file")
        private String file;

        @JsonProperty("line")
        private Integer line;

        @JsonProperty("column")
        private Integer column;

        @JsonProperty("code")
        private String code;

        @JsonProperty("severity")
        private ErrorSeverity severity;

        public BuildError() {
        }

        public BuildError(
                String message,
                String file,
                Integer line,
                Integer column,
                String code,
                ErrorSeverity severity) {
            this.message = message;
            this.file = file;
            this.line = line;
            this.column = column;
            this.code = code;
            this.severity = severity;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getFile() {
            return file;
        }

        public void setFile(String file) {
            this.file = file;
        }

        public Integer getLine() {
            return line;
        }

        public void setLine(Integer line) {
            this.line = line;
        }

        public Integer getColumn() {
            return column;
        }

        public void setColumn(Integer column) {
            this.column = column;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public ErrorSeverity getSeverity() {
            return severity;
        }

        public void setSeverity(ErrorSeverity severity) {
            this.severity = severity;
        }
    }

    /**
     * Build warning representation
     */
    public static class BuildWarning extends BuildError {

        public BuildWarning() {
            super();
            setSeverity(ErrorSeverity.WARNING);
        }

        public BuildWarning(
                String message, String file, Integer line, Integer column, String code) {
            super(message, file, line, column, code, ErrorSeverity.WARNING);
        }
    }
}
