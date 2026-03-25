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

import com.ghatana.yappc.core.error.ErrorSeverity;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Day 26: Nx build log normalizer. Parses Nx output into standardized format
 * for RCA analysis.
 *
 * @doc.type class
 * @doc.purpose Day 26: Nx build log normalizer. Parses Nx output into
 * standardized format for RCA analysis.
 * @doc.layer platform
 * @doc.pattern Component
 */
public class NxBuildLogNormalizer implements BuildLogNormalizer {

    // Nx log patterns
    private static final Pattern NX_TASK_PATTERN
            = Pattern.compile("\\s*>\\s+nx run ([^:]+):([^\\s]+)(?:\\s+(.+))?");

    private static final Pattern NX_SUCCESS_PATTERN
            = Pattern.compile("Successfully ran target (.+) for project (.+)");

    private static final Pattern NX_ERROR_PATTERN
            = Pattern.compile("(.+?):(\\d+):(\\d+)?\\s+- (error|warning)\\s+(.+)");

    private static final Pattern TASK_COMPLETION_PATTERN
            = Pattern.compile("✓\\s+(.+)\\s+\\(\\d+.*\\)");

    @Override
    public NormalizedBuildLog normalize(String rawLog) throws IOException {
        if (rawLog == null || rawLog.trim().isEmpty()) {
            throw new IOException("Raw log is empty or null");
        }

        List<String> lines = Arrays.asList(rawLog.split("\\r?\\n"));
        return parseNxLog(lines, rawLog);
    }

    @Override
    public NormalizedBuildLog normalize(Path logFile) throws IOException {
        if (!Files.exists(logFile)) {
            throw new IOException("Log file does not exist: " + logFile);
        }

        String content = Files.readString(logFile);
        return normalize(content);
    }

    @Override
    public boolean canHandle(String rawLog) {
        if (rawLog == null || rawLog.trim().isEmpty()) {
            return false;
        }

        // Check for Nx-specific markers
        return rawLog.contains("nx run")
                || rawLog.contains("Nx ")
                || rawLog.contains("Successfully ran target")
                || rawLog.contains("nx.json")
                || rawLog.contains("@nx/");
    }

    @Override
    public NormalizedBuildLog.BuildTool getSupportedTool() {
        return NormalizedBuildLog.BuildTool.NX;
    }

    private NormalizedBuildLog parseNxLog(List<String> lines, String rawLog) {
        Instant startTime = Instant.now(); // Approximation
        Instant endTime = Instant.now(); // Approximation
        NormalizedBuildLog.BuildStatus status = extractNxStatus(lines);
        List<NormalizedBuildLog.BuildTask> tasks = extractNxTasks(lines);
        List<NormalizedBuildLog.BuildError> errors = extractNxErrors(lines);
        List<NormalizedBuildLog.BuildWarning> warnings = extractNxWarnings(lines);
        Map<String, String> environment = extractNxEnvironment(lines);

        return new NormalizedBuildLog(
                NormalizedBuildLog.BuildTool.NX,
                startTime,
                endTime,
                status,
                tasks,
                errors,
                warnings,
                environment,
                rawLog);
    }

    private NormalizedBuildLog.BuildStatus extractNxStatus(List<String> lines) {
        boolean hasErrors = false;
        boolean hasSuccessfulTasks = false;

        for (String line : lines) {
            if (line.contains("Successfully ran target")) {
                hasSuccessfulTasks = true;
            }
            if (line.contains("error") || line.contains("failed") || line.contains("Failed")) {
                hasErrors = true;
            }
        }

        if (hasErrors) {
            return NormalizedBuildLog.BuildStatus.FAILURE;
        } else if (hasSuccessfulTasks) {
            return NormalizedBuildLog.BuildStatus.SUCCESS;
        }

        return NormalizedBuildLog.BuildStatus.UNKNOWN;
    }

    private List<NormalizedBuildLog.BuildTask> extractNxTasks(List<String> lines) {
        List<NormalizedBuildLog.BuildTask> tasks = new ArrayList<>();

        for (String line : lines) {
            // Check for nx run commands
            Matcher runMatcher = NX_TASK_PATTERN.matcher(line);
            if (runMatcher.find()) {
                String project = runMatcher.group(1);
                String target = runMatcher.group(2);
                String taskName = project + ":" + target;

                tasks.add(
                        new NormalizedBuildLog.BuildTask(
                                taskName,
                                NormalizedBuildLog.BuildTask.TaskStatus.SUCCESS, // Default, updated later
                                0, // Duration not easily extractable
                                line));
            }

            // Check for successful completions
            Matcher successMatcher = NX_SUCCESS_PATTERN.matcher(line);
            if (successMatcher.find()) {
                String target = successMatcher.group(1);
                String project = successMatcher.group(2);
                String taskName = project + ":" + target;

                tasks.add(
                        new NormalizedBuildLog.BuildTask(
                                taskName,
                                NormalizedBuildLog.BuildTask.TaskStatus.SUCCESS,
                                0,
                                line));
            }
        }

        return tasks;
    }

    private List<NormalizedBuildLog.BuildError> extractNxErrors(List<String> lines) {
        List<NormalizedBuildLog.BuildError> errors = new ArrayList<>();

        for (String line : lines) {
            Matcher matcher = NX_ERROR_PATTERN.matcher(line);
            if (matcher.find() && "error".equals(matcher.group(4))) {
                String file = matcher.group(1);
                Integer lineNum = parseInteger(matcher.group(2));
                Integer column = parseInteger(matcher.group(3));
                String message = matcher.group(5);

                errors.add(
                        new NormalizedBuildLog.BuildError(
                                message,
                                file,
                                lineNum,
                                column,
                                null,
                                ErrorSeverity.ERROR));
            }
        }

        return errors;
    }

    private List<NormalizedBuildLog.BuildWarning> extractNxWarnings(List<String> lines) {
        List<NormalizedBuildLog.BuildWarning> warnings = new ArrayList<>();

        for (String line : lines) {
            Matcher matcher = NX_ERROR_PATTERN.matcher(line);
            if (matcher.find() && "warning".equals(matcher.group(4))) {
                String file = matcher.group(1);
                Integer lineNum = parseInteger(matcher.group(2));
                Integer column = parseInteger(matcher.group(3));
                String message = matcher.group(5);

                warnings.add(
                        new NormalizedBuildLog.BuildWarning(message, file, lineNum, column, null));
            }
        }

        return warnings;
    }

    private Map<String, String> extractNxEnvironment(List<String> lines) {
        Map<String, String> environment = new HashMap<>();

        // Extract Nx version and Node version from log
        for (String line : lines) {
            if (line.contains("Nx ")) {
                String version = extractVersion(line, "Nx\\s+(\\d+\\.\\d+(?:\\.\\d+)?)");
                if (version != null) {
                    environment.put("nx.version", version);
                }
            }
            if (line.contains("Node")) {
                String version = extractVersion(line, "Node\\s+(v?\\d+\\.\\d+(?:\\.\\d+)?)");
                if (version != null) {
                    environment.put("node.version", version);
                }
            }
        }

        return environment;
    }

    private String extractVersion(String line, String pattern) {
        Pattern versionPattern = Pattern.compile(pattern);
        Matcher matcher = versionPattern.matcher(line);
        return matcher.find() ? matcher.group(1) : null;
    }

    private Integer parseInteger(String str) {
        if (str == null || str.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
