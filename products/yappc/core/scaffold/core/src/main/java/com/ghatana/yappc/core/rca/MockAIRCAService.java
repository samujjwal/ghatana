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

import java.time.Instant;
import java.util.*;
import io.activej.promise.Promise;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Day 27: Mock AI RCA service implementation. Provides rule-based failure
 * analysis until real AI integration.
 *
 * @doc.type class
 * @doc.purpose Day 27: Mock AI RCA service implementation. Provides rule-based
 * failure analysis until real AI
 * @doc.layer platform
 * @doc.pattern Service
 */
public class MockAIRCAService implements AIRCAService {

    private static final Executor BLOCKING_EXECUTOR = Executors.newCachedThreadPool();


    private final Map<String, RCATemplate> rcaTemplates;

    public MockAIRCAService() {
        this.rcaTemplates = initializeRCATemplates();
    }

    @Override
    public Promise<RCAResult> analyzeFailure(NormalizedBuildLog buildLog) {
        return analyzeFailure(buildLog, null);
    }

    @Override
    public Promise<RCAResult> analyzeFailure(
            NormalizedBuildLog buildLog, RCAContext context) {
        return Promise.ofBlocking(BLOCKING_EXECUTOR, () -> performAnalysis(buildLog, context));
    }

    @Override
    public Promise<List<RCAResult>> getSimilarFailures(NormalizedBuildLog buildLog) {
        return Promise.ofBlocking(BLOCKING_EXECUTOR, 
                () -> {
                    // Mock implementation returns empty list
                    return new ArrayList<>();
                });
    }

    @Override
    public boolean isAvailable() {
        return true; // Mock service is always available
    }

    private RCAResult performAnalysis(NormalizedBuildLog buildLog, RCAContext context) {
        String analysisId = UUID.randomUUID().toString();

        // Analyze errors to determine root cause
        RCAResult.RootCause rootCause = determineRootCause(buildLog);
        String explanation = generateExplanation(buildLog, rootCause, context);
        List<RCAResult.FixSuggestion> suggestions
                = generateFixSuggestions(buildLog, rootCause, context);
        double confidence = calculateConfidence(buildLog, rootCause);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("analysisMethod", "rule-based");
        metadata.put("errorCount", buildLog.getErrors().size());
        metadata.put("warningCount", buildLog.getWarnings().size());

        return new RCAResult(
                analysisId,
                Instant.now(),
                buildLog,
                rootCause,
                explanation,
                suggestions,
                confidence,
                metadata);
    }

    private RCAResult.RootCause determineRootCause(NormalizedBuildLog buildLog) {
        if (buildLog.getStatus() == NormalizedBuildLog.BuildStatus.SUCCESS) {
            return RCAResult.RootCause.UNKNOWN;
        }

        // Check for common error patterns
        for (NormalizedBuildLog.BuildError error : buildLog.getErrors()) {
            String message = error.getMessage().toLowerCase();

            if (message.contains("compilation") || message.contains("cannot find symbol")) {
                return RCAResult.RootCause.COMPILATION_ERROR;
            }
            if (message.contains("dependency") || message.contains("could not resolve")) {
                return RCAResult.RootCause.DEPENDENCY_ISSUE;
            }
            if (message.contains("test") && message.contains("failed")) {
                return RCAResult.RootCause.TEST_FAILURE;
            }
            if (message.contains("permission") || message.contains("access denied")) {
                return RCAResult.RootCause.PERMISSION_DENIED;
            }
            if (message.contains("network") || message.contains("timeout")) {
                return RCAResult.RootCause.NETWORK_ERROR;
            }
        }

        // Check build tool specific patterns
        if (buildLog.getTool() == NormalizedBuildLog.BuildTool.GRADLE) {
            return analyzeGradleFailure(buildLog);
        }

        return RCAResult.RootCause.UNKNOWN;
    }

    private RCAResult.RootCause analyzeGradleFailure(NormalizedBuildLog buildLog) {
        String rawLog = buildLog.getRawLog().toLowerCase();

        if (rawLog.contains("build file")) {
            return RCAResult.RootCause.CONFIGURATION_ERROR;
        }
        if (rawLog.contains("out of memory")) {
            return RCAResult.RootCause.RESOURCE_EXHAUSTION;
        }

        return RCAResult.RootCause.UNKNOWN;
    }

    private String generateExplanation(
            NormalizedBuildLog buildLog, RCAResult.RootCause rootCause, RCAContext context) {
        RCATemplate template
                = rcaTemplates.getOrDefault(rootCause.name(), rcaTemplates.get("DEFAULT"));

        StringBuilder explanation = new StringBuilder();
        explanation.append(template.explanation);

        // Add specific details based on errors
        if (!buildLog.getErrors().isEmpty()) {
            explanation.append("\n\nSpecific errors found:\n");
            for (NormalizedBuildLog.BuildError error
                    : buildLog.getErrors().subList(0, Math.min(3, buildLog.getErrors().size()))) {
                explanation.append("- ").append(error.getMessage()).append("\n");
            }
        }

        // Add context-specific information
        if (context != null && context.getProjectType() != null) {
            explanation.append("\nProject Type: ").append(context.getProjectType());
        }

        return explanation.toString();
    }

    private List<RCAResult.FixSuggestion> generateFixSuggestions(
            NormalizedBuildLog buildLog, RCAResult.RootCause rootCause, RCAContext context) {
        List<RCAResult.FixSuggestion> suggestions = new ArrayList<>();
        RCATemplate template
                = rcaTemplates.getOrDefault(rootCause.name(), rcaTemplates.get("DEFAULT"));

        for (RCATemplate.SuggestionTemplate suggestionTemplate : template.suggestions) {
            suggestions.add(
                    new RCAResult.FixSuggestion(
                            suggestionTemplate.title,
                            suggestionTemplate.description,
                            suggestionTemplate.priority,
                            suggestionTemplate.category,
                            suggestionTemplate.commands,
                            new ArrayList<>(), // File changes would be more complex to generate
                            suggestionTemplate.effort));
        }

        return suggestions;
    }

    private double calculateConfidence(NormalizedBuildLog buildLog, RCAResult.RootCause rootCause) {
        double confidence = 0.5; // Base confidence

        // Higher confidence for known patterns
        if (rootCause != RCAResult.RootCause.UNKNOWN) {
            confidence += 0.3;
        }

        // Higher confidence with more error information
        if (!buildLog.getErrors().isEmpty()) {
            confidence += 0.2;
        }

        return Math.min(1.0, confidence);
    }

    private Map<String, RCATemplate> initializeRCATemplates() {
        Map<String, RCATemplate> templates = new HashMap<>();

        // Compilation error template
        templates.put(
                "COMPILATION_ERROR",
                new RCATemplate(
                        "The build failed due to compilation errors in your source code. This"
                        + " typically means there are syntax errors, missing imports, or type"
                        + " mismatches.",
                        Arrays.asList(
                                new RCATemplate.SuggestionTemplate(
                                        "Fix Compilation Errors",
                                        "Review and fix the compilation errors shown in the build"
                                        + " output",
                                        RCAResult.FixSuggestion.Priority.URGENT,
                                        RCAResult.FixSuggestion.Category.CODE_FIX,
                                        Arrays.asList("./gradlew compileJava"),
                                        RCAResult.FixSuggestion.EstimatedEffort.MEDIUM))));        // Dependency issue template
        templates.put(
                "DEPENDENCY_ISSUE",
                new RCATemplate(
                        "The build failed due to dependency resolution issues. This could be"
                        + " missing dependencies, version conflicts, or repository connectivity"
                        + " problems.",
                        Arrays.asList(
                                new RCATemplate.SuggestionTemplate(
                                        "Refresh Dependencies",
                                        "Clear dependency cache and refresh dependencies",
                                        RCAResult.FixSuggestion.Priority.HIGH,
                                        RCAResult.FixSuggestion.Category.DEPENDENCY_MANAGEMENT,
                                        Arrays.asList("./gradlew --refresh-dependencies"),
                                        RCAResult.FixSuggestion.EstimatedEffort.SMALL),
                                new RCATemplate.SuggestionTemplate(
                                        "Check Repository Configuration",
                                        "Verify that all required repositories are configured in"
                                        + " build.gradle",
                                        RCAResult.FixSuggestion.Priority.MEDIUM,
                                        RCAResult.FixSuggestion.Category.CONFIGURATION_CHANGE,
                                        Arrays.asList(),
                                        RCAResult.FixSuggestion.EstimatedEffort.MEDIUM))));

        // Default template
        templates.put(
                "DEFAULT",
                new RCATemplate(
                        "The build failed but the specific cause could not be determined"
                        + " automatically. Please review the build logs for more details.",
                        Arrays.asList(
                                new RCATemplate.SuggestionTemplate(
                                        "Review Build Logs",
                                        "Carefully examine the build output for error messages and"
                                        + " stack traces",
                                        RCAResult.FixSuggestion.Priority.HIGH,
                                        RCAResult.FixSuggestion.Category.CODE_FIX,
                                        Arrays.asList(),
                                        RCAResult.FixSuggestion.EstimatedEffort.LARGE))));

        return templates;
    }

    /**
     * Template for RCA responses
     */
    private static class RCATemplate {

        final String explanation;
        final List<SuggestionTemplate> suggestions;

        RCATemplate(String explanation, List<SuggestionTemplate> suggestions) {
            this.explanation = explanation;
            this.suggestions = suggestions;
        }

        static class SuggestionTemplate {

            final String title;
            final String description;
            final RCAResult.FixSuggestion.Priority priority;
            final RCAResult.FixSuggestion.Category category;
            final List<String> commands;
            final RCAResult.FixSuggestion.EstimatedEffort effort;

            SuggestionTemplate(
                    String title,
                    String description,
                    RCAResult.FixSuggestion.Priority priority,
                    RCAResult.FixSuggestion.Category category,
                    List<String> commands,
                    RCAResult.FixSuggestion.EstimatedEffort effort) {
                this.title = title;
                this.description = description;
                this.priority = priority;
                this.category = category;
                this.commands = commands;
                this.effort = effort;
            }
        }
    }
}
