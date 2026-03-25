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
package com.ghatana.yappc.core.polyfix;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import io.activej.promise.Promise;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/**
 * Day 29: Mock Polyfix validator implementation. Simulates Polyfix codemod
 * validation until real integration is available.
 *
 * @doc.type class
 * @doc.purpose Day 29: Mock Polyfix validator implementation. Simulates Polyfix
 * codemod validation until real
 * @doc.layer platform
 * @doc.pattern Service
 */
public class MockPolyfixValidatorService implements PolyfixValidatorService {

    private static final Executor BLOCKING_EXECUTOR = Executors.newCachedThreadPool();


    private final Map<String, List<CodemodInfo>> projectTypeCodemods;
    private final Map<String, MockCodemodRunner> codemodRunners;

    public MockPolyfixValidatorService() {
        this.projectTypeCodemods = initializeProjectTypeCodemods();
        this.codemodRunners = initializeCodemodRunners();
    }

    @Override
    public Promise<PolyfixValidationResult> validateProject(
            String targetPath, String projectType) {
        return Promise.ofBlocking(BLOCKING_EXECUTOR, 
                () -> {
                    try {
                        return performProjectValidation(targetPath, projectType);
                    } catch (Exception e) {
                        throw new RuntimeException(
                                "Failed to validate project: " + e.getMessage(), e);
                    }
                });
    }

    @Override
    public Promise<PolyfixValidationResult> validateFiles(
            List<String> filePaths, String projectType) {
        return Promise.ofBlocking(BLOCKING_EXECUTOR, 
                () -> {
                    try {
                        return performFileValidation(filePaths, projectType);
                    } catch (Exception e) {
                        throw new RuntimeException(
                                "Failed to validate files: " + e.getMessage(), e);
                    }
                });
    }

    @Override
    public List<CodemodInfo> getAvailableCodemods(String projectType) {
        return projectTypeCodemods.getOrDefault(projectType, new ArrayList<>());
    }

    @Override
    public Promise<PolyfixValidationResult> runSpecificCodemods(
            String targetPath, List<String> codemodNames) {
        return Promise.ofBlocking(BLOCKING_EXECUTOR, 
                () -> {
                    try {
                        return performSpecificCodemodValidation(targetPath, codemodNames);
                    } catch (Exception e) {
                        throw new RuntimeException(
                                "Failed to run specific codemods: " + e.getMessage(), e);
                    }
                });
    }

    @Override
    public boolean isAvailable() {
        return true; // Mock service is always available
    }

    @Override
    public List<String> getSupportedProjectTypes() {
        return Arrays.asList("java", "kotlin", "scala", "typescript", "javascript");
    }

    private PolyfixValidationResult performProjectValidation(String targetPath, String projectType)
            throws IOException {
        String validationId = UUID.randomUUID().toString();
        Instant timestamp = Instant.now();
        long startTime = System.currentTimeMillis();

        Path projectPath = Paths.get(targetPath);
        if (!Files.exists(projectPath)) {
            throw new IOException("Target path does not exist: " + targetPath);
        }

        // Discover files to analyze
        List<String> filesToAnalyze = discoverProjectFiles(projectPath, projectType);

        // Get applicable codemods
        List<CodemodInfo> availableCodemods = getAvailableCodemods(projectType);

        // Apply codemods
        List<PolyfixValidationResult.AppliedCodemod> appliedCodemods = new ArrayList<>();
        List<PolyfixValidationResult.ValidationIssue> issues = new ArrayList<>();

        int totalFilesModified = 0;
        int totalChangesApplied = 0;

        for (CodemodInfo codemodInfo : availableCodemods) {
            MockCodemodRunner runner = codemodRunners.get(codemodInfo.getName());
            if (runner != null) {
                MockCodemodResult result = runner.runOnProject(projectPath, filesToAnalyze);

                appliedCodemods.add(
                        new PolyfixValidationResult.AppliedCodemod(
                                codemodInfo.getName(),
                                codemodInfo.getVersion(),
                                codemodInfo.getDescription(),
                                result.getFilesModified(),
                                result.getChangesApplied(),
                                result.getExecutionTimeMs(),
                                mapCodemodStatus(result.getStatus()),
                                result.getErrors()));

                totalFilesModified += result.getFilesModified();
                totalChangesApplied += result.getChangesApplied();

                // Add any issues found
                issues.addAll(result.getValidationIssues());
            }
        }

        // Generate suggestions
        List<PolyfixValidationResult.CodemodSuggestion> suggestions
                = generateSuggestions(projectPath, projectType);

        // Calculate metrics
        long executionTime = System.currentTimeMillis() - startTime;
        PolyfixValidationResult.ValidationMetrics metrics
                = new PolyfixValidationResult.ValidationMetrics(
                        filesToAnalyze.size(),
                        totalFilesModified,
                        totalChangesApplied,
                        executionTime,
                        calculateCodeQualityScore(issues),
                        calculateCoverage(filesToAnalyze.size(), totalFilesModified));

        // Determine overall status
        PolyfixValidationResult.ValidationStatus status
                = determineValidationStatus(appliedCodemods, issues);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("validator", "MockPolyfixValidatorService");
        metadata.put("projectType", projectType);
        metadata.put("availableCodemods", availableCodemods.size());

        return new PolyfixValidationResult(
                validationId,
                timestamp,
                targetPath,
                status,
                appliedCodemods,
                issues,
                suggestions,
                metrics,
                metadata);
    }

    private PolyfixValidationResult performFileValidation(
            List<String> filePaths, String projectType) {
        String validationId = UUID.randomUUID().toString();
        Instant timestamp = Instant.now();
        long startTime = System.currentTimeMillis();

        List<PolyfixValidationResult.AppliedCodemod> appliedCodemods = new ArrayList<>();
        List<PolyfixValidationResult.ValidationIssue> issues = new ArrayList<>();

        // Mock validation for specific files
        int totalChanges = 0;
        int filesModified = 0;

        for (String filePath : filePaths) {
            Path file = Paths.get(filePath);
            if (Files.exists(file)) {
                // Simulate codemod application
                String fileExtension = getFileExtension(file);
                if (isSupported(fileExtension, projectType)) {
                    filesModified++;
                    totalChanges += simulateFileChanges(file);
                }
            } else {
                issues.add(
                        new PolyfixValidationResult.ValidationIssue(
                                "file-not-found",
                                PolyfixValidationResult.Severity.ERROR,
                                "File not found: " + filePath,
                                filePath,
                                null,
                                null,
                                "FILE_001"));
            }
        }

        if (filesModified > 0) {
            appliedCodemods.add(
                    new PolyfixValidationResult.AppliedCodemod(
                            "file-specific-validation",
                            "1.0.0",
                            "Applied validation to specific files",
                            filesModified,
                            totalChanges,
                            System.currentTimeMillis() - startTime,
                            PolyfixValidationResult.AppliedCodemod.CodemodStatus.SUCCESS,
                            new ArrayList<>()));
        }

        long executionTime = System.currentTimeMillis() - startTime;
        PolyfixValidationResult.ValidationMetrics metrics
                = new PolyfixValidationResult.ValidationMetrics(
                        filePaths.size(), filesModified, totalChanges, executionTime, 0.85, 75.0);

        PolyfixValidationResult.ValidationStatus status
                = issues.stream()
                        .anyMatch(
                                i
                                -> i.getSeverity()
                                == PolyfixValidationResult.Severity.ERROR)
                        ? PolyfixValidationResult.ValidationStatus.FAILED
                        : PolyfixValidationResult.ValidationStatus.PASSED;

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("validator", "MockPolyfixValidatorService");
        metadata.put("mode", "file-specific");

        return new PolyfixValidationResult(
                validationId,
                timestamp,
                "file-validation",
                status,
                appliedCodemods,
                issues,
                new ArrayList<>(),
                metrics,
                metadata);
    }

    private PolyfixValidationResult performSpecificCodemodValidation(
            String targetPath, List<String> codemodNames) throws IOException {
        String validationId = UUID.randomUUID().toString();
        Instant timestamp = Instant.now();
        long startTime = System.currentTimeMillis();

        Path projectPath = Paths.get(targetPath);
        List<PolyfixValidationResult.AppliedCodemod> appliedCodemods = new ArrayList<>();
        List<PolyfixValidationResult.ValidationIssue> issues = new ArrayList<>();

        int totalFilesModified = 0;
        int totalChangesApplied = 0;

        for (String codemodName : codemodNames) {
            MockCodemodRunner runner = codemodRunners.get(codemodName);
            if (runner != null) {
                List<String> projectFiles
                        = discoverProjectFiles(projectPath, "java"); // Default to java
                MockCodemodResult result = runner.runOnProject(projectPath, projectFiles);

                appliedCodemods.add(
                        new PolyfixValidationResult.AppliedCodemod(
                                codemodName,
                                "1.0.0",
                                "Specific codemod execution: " + codemodName,
                                result.getFilesModified(),
                                result.getChangesApplied(),
                                result.getExecutionTimeMs(),
                                mapCodemodStatus(result.getStatus()),
                                result.getErrors()));

                totalFilesModified += result.getFilesModified();
                totalChangesApplied += result.getChangesApplied();
                issues.addAll(result.getValidationIssues());
            } else {
                issues.add(
                        new PolyfixValidationResult.ValidationIssue(
                                "codemod-not-found",
                                PolyfixValidationResult.Severity.ERROR,
                                "Codemod not available: " + codemodName,
                                null,
                                null,
                                null,
                                "CODEMOD_001"));
            }
        }

        long executionTime = System.currentTimeMillis() - startTime;
        PolyfixValidationResult.ValidationMetrics metrics
                = new PolyfixValidationResult.ValidationMetrics(
                        100, totalFilesModified, totalChangesApplied, executionTime, 0.88, 80.0);

        PolyfixValidationResult.ValidationStatus status
                = determineValidationStatus(appliedCodemods, issues);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("validator", "MockPolyfixValidatorService");
        metadata.put("mode", "specific-codemods");
        metadata.put("requestedCodemods", codemodNames);

        return new PolyfixValidationResult(
                validationId,
                timestamp,
                targetPath,
                status,
                appliedCodemods,
                issues,
                new ArrayList<>(),
                metrics,
                metadata);
    }

    private List<String> discoverProjectFiles(Path projectPath, String projectType)
            throws IOException {
        Set<String> extensions = getFileExtensions(projectType);
        List<String> files = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(projectPath)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> extensions.contains(getFileExtension(path)))
                    .forEach(path -> files.add(path.toString()));
        }

        return files;
    }

    private Set<String> getFileExtensions(String projectType) {
        return switch (projectType.toLowerCase()) {
            case "java" ->
                Set.of("java", "gradle", "properties");
            case "kotlin" ->
                Set.of("kt", "kts", "gradle", "properties");
            case "scala" ->
                Set.of("scala", "sbt", "properties");
            case "typescript" ->
                Set.of("ts", "tsx", "json", "js");
            case "javascript" ->
                Set.of("js", "jsx", "json");
            default ->
                Set.of("java", "gradle", "properties");
        };
    }

    private String getFileExtension(Path file) {
        String fileName = file.getFileName().toString();
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(lastDot + 1) : "";
    }

    private boolean isSupported(String extension, String projectType) {
        return getFileExtensions(projectType).contains(extension);
    }

    private int simulateFileChanges(Path file) {
        // Simulate changes based on file type
        String extension = getFileExtension(file);
        return switch (extension) {
            case "java", "kt", "scala" ->
                3; // 3 changes per source file
            case "gradle", "sbt" ->
                2; // 2 changes per build file
            case "properties", "json" ->
                1; // 1 change per config file
            default ->
                0;
        };
    }

    private List<PolyfixValidationResult.CodemodSuggestion> generateSuggestions(
            Path projectPath, String projectType) {
        List<PolyfixValidationResult.CodemodSuggestion> suggestions = new ArrayList<>();

        // Generate context-aware suggestions
        if ("java".equals(projectType)) {
            suggestions.add(
                    new PolyfixValidationResult.CodemodSuggestion(
                            "modernize-collections",
                            PolyfixValidationResult.Priority.MEDIUM,
                            "Modernize collection usage patterns",
                            "Use of modern collection methods can improve code readability",
                            "Better code maintainability and performance"));

            suggestions.add(
                    new PolyfixValidationResult.CodemodSuggestion(
                            "fix-deprecated-apis",
                            PolyfixValidationResult.Priority.HIGH,
                            "Replace deprecated API usage",
                            "Deprecated APIs may be removed in future versions",
                            "Future-proof code and avoid breaking changes"));
        }

        return suggestions;
    }

    private PolyfixValidationResult.ValidationStatus determineValidationStatus(
            List<PolyfixValidationResult.AppliedCodemod> appliedCodemods,
            List<PolyfixValidationResult.ValidationIssue> issues) {

        boolean hasErrors
                = issues.stream().anyMatch(issue -> issue.getSeverity() == PolyfixValidationResult.Severity.ERROR);

        boolean hasWarnings
                = issues.stream().anyMatch(issue -> issue.getSeverity() == PolyfixValidationResult.Severity.WARNING);

        boolean hasFailedCodemods
                = appliedCodemods.stream()
                        .anyMatch(
                                codemod
                                -> codemod.getStatus()
                                == PolyfixValidationResult.AppliedCodemod.CodemodStatus.FAILED);

        if (hasErrors || hasFailedCodemods) {
            return PolyfixValidationResult.ValidationStatus.FAILED;
        } else if (hasWarnings) {
            return PolyfixValidationResult.ValidationStatus.PASSED_WITH_WARNINGS;
        } else if (appliedCodemods.isEmpty()) {
            return PolyfixValidationResult.ValidationStatus.SKIPPED;
        } else {
            return PolyfixValidationResult.ValidationStatus.PASSED;
        }
    }

    private double calculateCodeQualityScore(List<PolyfixValidationResult.ValidationIssue> issues) {
        if (issues.isEmpty()) {
            return 1.0;
        }

        long errorCount = issues.stream().filter(i -> i.getSeverity() == PolyfixValidationResult.Severity.ERROR).count();
        long warningCount
                = issues.stream().filter(i -> i.getSeverity() == PolyfixValidationResult.Severity.WARNING).count();

        double penalty = (errorCount * 0.1) + (warningCount * 0.05);
        return Math.max(0.0, 1.0 - penalty);
    }

    private double calculateCoverage(int totalFiles, int modifiedFiles) {
        return totalFiles > 0 ? (double) modifiedFiles / totalFiles * 100 : 0.0;
    }

    private PolyfixValidationResult.AppliedCodemod.CodemodStatus mapCodemodStatus(String status) {
        return switch (status.toLowerCase()) {
            case "success" ->
                PolyfixValidationResult.AppliedCodemod.CodemodStatus.SUCCESS;
            case "partial" ->
                PolyfixValidationResult.AppliedCodemod.CodemodStatus.PARTIAL;
            case "failed" ->
                PolyfixValidationResult.AppliedCodemod.CodemodStatus.FAILED;
            case "skipped" ->
                PolyfixValidationResult.AppliedCodemod.CodemodStatus.SKIPPED;
            default ->
                PolyfixValidationResult.AppliedCodemod.CodemodStatus.SUCCESS;
        };
    }

    private Map<String, List<CodemodInfo>> initializeProjectTypeCodemods() {
        Map<String, List<CodemodInfo>> codemods = new HashMap<>();

        // Java codemods
        List<CodemodInfo> javaCodemods
                = Arrays.asList(
                        new CodemodInfo(
                                "fix-deprecated-apis",
                                "1.0.0",
                                "Replace deprecated API usage with modern alternatives",
                                Arrays.asList("java"),
                                "modernization"),
                        new CodemodInfo(
                                "modernize-collections",
                                "1.0.0",
                                "Update collection usage to use modern patterns",
                                Arrays.asList("java"),
                                "modernization"),
                        new CodemodInfo(
                                "optimize-imports",
                                "1.0.0",
                                "Organize and optimize import statements",
                                Arrays.asList("java"),
                                "cleanup"),
                        new CodemodInfo(
                                "gradle-best-practices",
                                "1.0.0",
                                "Apply Gradle build script best practices",
                                Arrays.asList("gradle"),
                                "build-optimization"));
        codemods.put("java", javaCodemods);

        // Kotlin codemods
        List<CodemodInfo> kotlinCodemods
                = Arrays.asList(
                        new CodemodInfo(
                                "kotlin-idiomatic",
                                "1.0.0",
                                "Apply idiomatic Kotlin patterns",
                                Arrays.asList("kt"),
                                "modernization"),
                        new CodemodInfo(
                                "coroutines-migration",
                                "1.0.0",
                                "Migrate to Kotlin coroutines",
                                Arrays.asList("kt"),
                                "modernization"));
        codemods.put("kotlin", kotlinCodemods);

        return codemods;
    }

    private Map<String, MockCodemodRunner> initializeCodemodRunners() {
        Map<String, MockCodemodRunner> runners = new HashMap<>();

        runners.put("fix-deprecated-apis", new MockCodemodRunner("fix-deprecated-apis"));
        runners.put("modernize-collections", new MockCodemodRunner("modernize-collections"));
        runners.put("optimize-imports", new MockCodemodRunner("optimize-imports"));
        runners.put("gradle-best-practices", new MockCodemodRunner("gradle-best-practices"));
        runners.put("kotlin-idiomatic", new MockCodemodRunner("kotlin-idiomatic"));

        return runners;
    }

    /**
     * Mock codemod runner for simulation
     */
    private static class MockCodemodRunner {

        @SuppressWarnings("unused")
        private final String name;

        public MockCodemodRunner(String name) {
            this.name = name;
        }

        public MockCodemodResult runOnProject(Path projectPath, List<String> files) {
            long startTime = System.currentTimeMillis();

            // Simulate codemod execution
            int filesModified = Math.min(files.size(), 5 + (int) (Math.random() * 10));
            int changesApplied = filesModified * (2 + (int) (Math.random() * 5));
            long executionTime
                    = System.currentTimeMillis() - startTime + (long) (Math.random() * 1000);

            List<String> errors = new ArrayList<>();
            List<PolyfixValidationResult.ValidationIssue> issues = new ArrayList<>();

            // Simulate occasional issues
            if (Math.random() < 0.1) { // 10% chance of issues
                issues.add(
                        new PolyfixValidationResult.ValidationIssue(
                                "style-violation",
                                PolyfixValidationResult.Severity.WARNING,
                                "Code style violation detected",
                                files.get(0),
                                15,
                                10,
                                "STYLE_001"));
            }

            String status = Math.random() < 0.05 ? "failed" : "success"; // 5% failure rate

            return new MockCodemodResult(
                    filesModified, changesApplied, executionTime, status, errors, issues);
        }
    }

    /**
     * Mock codemod execution result
     */
    private static class MockCodemodResult {

        private final int filesModified;
        private final int changesApplied;
        private final long executionTimeMs;
        private final String status;
        private final List<String> errors;
        private final List<PolyfixValidationResult.ValidationIssue> validationIssues;

        public MockCodemodResult(
                int filesModified,
                int changesApplied,
                long executionTimeMs,
                String status,
                List<String> errors,
                List<PolyfixValidationResult.ValidationIssue> validationIssues) {
            this.filesModified = filesModified;
            this.changesApplied = changesApplied;
            this.executionTimeMs = executionTimeMs;
            this.status = status;
            this.errors = errors;
            this.validationIssues = validationIssues;
        }

        public int getFilesModified() {
            return filesModified;
        }

        public int getChangesApplied() {
            return changesApplied;
        }

        public long getExecutionTimeMs() {
            return executionTimeMs;
        }

        public String getStatus() {
            return status;
        }

        public List<String> getErrors() {
            return errors;
        }

        public List<PolyfixValidationResult.ValidationIssue> getValidationIssues() {
            return validationIssues;
        }
    }
}
