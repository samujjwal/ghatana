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
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Day 26: Gradle build log normalizer. Parses Gradle output into standardized
 * format for RCA analysis.
 *
 * @doc.type class
 * @doc.purpose Day 26: Gradle build log normalizer. Parses Gradle output into
 * standardized format for RCA
 * @doc.layer platform
 * @doc.pattern Component
 */
public class GradleBuildLogNormalizer implements BuildLogNormalizer {

    // Gradle log patterns
    private static final Pattern TASK_PATTERN
            = Pattern.compile("> Task (:\\S+(?::\\S+)*) (\\w+)(?:\\s+(.+))?");

    private static final Pattern FAILURE_PATTERN
            = Pattern.compile("FAILURE: Build failed with an exception\\.");

    private static final Pattern ERROR_PATTERN
            = Pattern.compile("(.+?):(\\d+):(\\d+)?:?\\s+(error|warning):\\s+(.+)");

    private static final Pattern BUILD_RESULT_PATTERN
            = Pattern.compile("BUILD (SUCCESSFUL|FAILED) in (.+)");

    private static final Pattern TIME_PATTERN
            = Pattern.compile("(\\d{2}):(\\d{2}):(\\d{2})\\.\\d{3}");

    @Override
    public NormalizedBuildLog normalize(String rawLog) throws IOException {
        if (rawLog == null || rawLog.trim().isEmpty()) {
            throw new IOException("Raw log is empty or null");
        }

        List<String> lines = Arrays.asList(rawLog.split("\\r?\\n"));
        return parseGradleLog(lines, rawLog);
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

        // Check for Gradle-specific markers
        return rawLog.contains("Gradle")
                || rawLog.contains("> Task :")
                || rawLog.contains("BUILD SUCCESSFUL")
                || rawLog.contains("BUILD FAILED")
                || rawLog.contains("./gradlew");
    }

    @Override
    public NormalizedBuildLog.BuildTool getSupportedTool() {
        return NormalizedBuildLog.BuildTool.GRADLE;
    }

    private NormalizedBuildLog parseGradleLog(List<String> lines, String rawLog) {
        Instant startTime = extractStartTime(lines);
        Instant endTime = extractEndTime(lines);
        NormalizedBuildLog.BuildStatus status = extractStatus(lines);
        List<NormalizedBuildLog.BuildTask> tasks = extractTasks(lines);
        List<NormalizedBuildLog.BuildError> errors = extractErrors(lines);
        List<NormalizedBuildLog.BuildWarning> warnings = extractWarnings(lines);
        Map<String, String> environment = extractEnvironment(lines);

        return new NormalizedBuildLog(
                NormalizedBuildLog.BuildTool.GRADLE,
                startTime,
                endTime,
                status,
                tasks,
                errors,
                warnings,
                environment,
                rawLog);
    }

    private Instant extractStartTime(List<String> lines) {
        // Try to extract timestamp from first meaningful line
        for (String line : lines) {
            Matcher timeMatcher = TIME_PATTERN.matcher(line);
            if (timeMatcher.find()) {
                try {
                    LocalTime time
                            = LocalTime.parse(
                                    timeMatcher.group(0),
                                    DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
                    return Instant.now().minusSeconds(3600); // Approximation
                } catch (Exception e) {
                    // Continue searching
                }
            }
        }
        return Instant.now();
    }

    private Instant extractEndTime(List<String> lines) {
        // Look for BUILD result timestamp
        for (int i = lines.size() - 1; i >= 0; i--) {
            String line = lines.get(i);
            if (BUILD_RESULT_PATTERN.matcher(line).find()) {
                Matcher timeMatcher = TIME_PATTERN.matcher(line);
                if (timeMatcher.find()) {
                    try {
                        LocalTime time
                                = LocalTime.parse(
                                        timeMatcher.group(0),
                                        DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
                        return Instant.now(); // Approximation
                    } catch (Exception e) {
                        // Fall through to default
                    }
                }
            }
        }
        return Instant.now();
    }

    private NormalizedBuildLog.BuildStatus extractStatus(List<String> lines) {
        for (String line : lines) {
            Matcher matcher = BUILD_RESULT_PATTERN.matcher(line);
            if (matcher.find()) {
                String result = matcher.group(1);
                switch (result) {
                    case "SUCCESSFUL":
                        return NormalizedBuildLog.BuildStatus.SUCCESS;
                    case "FAILED":
                        return NormalizedBuildLog.BuildStatus.FAILURE;
                    default:
                        return NormalizedBuildLog.BuildStatus.UNKNOWN;
                }
            }
        }
        return NormalizedBuildLog.BuildStatus.UNKNOWN;
    }

    private List<NormalizedBuildLog.BuildTask> extractTasks(List<String> lines) {
        List<NormalizedBuildLog.BuildTask> tasks = new ArrayList<>();

        for (String line : lines) {
            Matcher matcher = TASK_PATTERN.matcher(line);
            if (matcher.find()) {
                String taskName = matcher.group(1);
                String status = matcher.group(2);
                String additional = matcher.group(3);

                NormalizedBuildLog.BuildTask.TaskStatus taskStatus
                        = parseTaskStatus(status, additional);

                tasks.add(
                        new NormalizedBuildLog.BuildTask(
                                taskName,
                                taskStatus,
                                0, // Duration not easily extractable from basic Gradle output
                                line));
            }
        }

        return tasks;
    }

    private NormalizedBuildLog.BuildTask.TaskStatus parseTaskStatus(
            String status, String additional) {
        if (additional == null) {
            return NormalizedBuildLog.BuildTask.TaskStatus.SUCCESS;
        }

        switch (additional.toUpperCase()) {
            case "FAILED":
                return NormalizedBuildLog.BuildTask.TaskStatus.FAILED;
            case "SKIPPED":
                return NormalizedBuildLog.BuildTask.TaskStatus.SKIPPED;
            case "UP-TO-DATE":
                return NormalizedBuildLog.BuildTask.TaskStatus.UP_TO_DATE;
            case "FROM-CACHE":
                return NormalizedBuildLog.BuildTask.TaskStatus.FROM_CACHE;
            case "NO-SOURCE":
                return NormalizedBuildLog.BuildTask.TaskStatus.NO_SOURCE;
            default:
                return NormalizedBuildLog.BuildTask.TaskStatus.SUCCESS;
        }
    }

    private List<NormalizedBuildLog.BuildError> extractErrors(List<String> lines) {
        List<NormalizedBuildLog.BuildError> errors = new ArrayList<>();

        for (String line : lines) {
            Matcher matcher = ERROR_PATTERN.matcher(line);
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

    private List<NormalizedBuildLog.BuildWarning> extractWarnings(List<String> lines) {
        List<NormalizedBuildLog.BuildWarning> warnings = new ArrayList<>();

        for (String line : lines) {
            Matcher matcher = ERROR_PATTERN.matcher(line);
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

    private Map<String, String> extractEnvironment(List<String> lines) {
        Map<String, String> environment = new HashMap<>();

        // Extract Gradle version, Java version, etc. from log
        for (String line : lines) {
            if (line.contains("Gradle")) {
                environment.put("gradle.version", extractGradleVersion(line));
            }
            if (line.contains("Java")) {
                environment.put("java.version", extractJavaVersion(line));
            }
        }

        return environment;
    }

    private String extractGradleVersion(String line) {
        // Extract version from lines like "Gradle 8.14"
        Pattern pattern = Pattern.compile("Gradle\\s+(\\d+\\.\\d+(?:\\.\\d+)?)");
        Matcher matcher = pattern.matcher(line);
        return matcher.find() ? matcher.group(1) : "unknown";
    }

    private String extractJavaVersion(String line) {
        // Extract version from lines containing Java version info
        Pattern pattern = Pattern.compile("Java\\s+(\\d+(?:\\.\\d+)*)");
        Matcher matcher = pattern.matcher(line);
        return matcher.find() ? matcher.group(1) : "unknown";
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
