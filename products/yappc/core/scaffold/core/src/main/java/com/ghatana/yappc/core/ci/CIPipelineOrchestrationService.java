/*
 * YAPPC - Yet Another Project/Package Creator
 * Copyright (c) 2025 Ghatana
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

package com.ghatana.yappc.core.ci;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * CI/CD orchestration service that coordinates multiple CI/CD generators. Provides unified
 * interface for multi-platform CI/CD pipeline generation.
 *
 * <p>Week 8 Day 37: CI/CD service orchestrator with multi-platform support and AI optimization.
 *
 * @doc.type class
 * @doc.purpose CI/CD orchestration service that coordinates multiple CI/CD generators. Provides unified
 * @doc.layer platform
 * @doc.pattern Service
 */
public class CIPipelineOrchestrationService {

    private static final Executor BLOCKING_EXECUTOR = Executors.newCachedThreadPool();


    private final Map<CIPipelineSpec.CIPlatform, CIPipelineGenerator> generators;

    public CIPipelineOrchestrationService() {
        this.generators = new HashMap<>();
        registerGenerators();
    }

    private void registerGenerators() {
        generators.put(CIPipelineSpec.CIPlatform.GITHUB_ACTIONS, new GitHubActionsGenerator());
        generators.put(CIPipelineSpec.CIPlatform.GITLAB_CI, new GitLabCIGenerator());
        // Additional generators can be added here
        // generators.put(CIPipelineSpec.CIPlatform.AZURE_DEVOPS, new AzureDevOpsGenerator());
        // generators.put(CIPipelineSpec.CIPlatform.JENKINS, new JenkinsGenerator());
    }

    /**
 * Generates CI/CD pipeline for specified platform. */
    public GeneratedCIPipeline generatePipeline(CIPipelineSpec spec) {
        var generator = generators.get(spec.platform());
        if (generator == null) {
            throw new UnsupportedOperationException("Platform not supported: " + spec.platform());
        }

        return generator.generatePipeline(spec);
    }

    /**
 * Generates CI/CD pipelines for multiple platforms. */
    public Promise<Map<CIPipelineSpec.CIPlatform, GeneratedCIPipeline>> generateMultiPlatformPipelines(
            CIPipelineSpec baseSpec, List<CIPipelineSpec.CIPlatform> platforms) {

        List<CIPipelineSpec.CIPlatform> platformList = new ArrayList<>(platforms);
        List<Promise<GeneratedCIPipeline>> promises = new ArrayList<>();

        // Generate pipelines concurrently
        for (var platform : platformList) {
            var platformSpec = baseSpec.toBuilder().platform(platform).build();
            promises.add(Promise.ofBlocking(BLOCKING_EXECUTOR, () -> generatePipeline(platformSpec)));
        }

        // Collect results when all complete
        return Promises.toList(promises)
                .map(results -> {
                    Map<CIPipelineSpec.CIPlatform, GeneratedCIPipeline> resultMap = new HashMap<>();
                    for (int i = 0; i < platformList.size(); i++) {
                        resultMap.put(platformList.get(i), results.get(i));
                    }
                    return resultMap;
                });
    }

    /**
 * Validates CI/CD pipeline specification. */
    public CIPipelineValidationResult validatePipeline(CIPipelineSpec spec) {
        var generator = generators.get(spec.platform());
        if (generator == null) {
            return CIPipelineValidationResult.builder()
                    .isValid(false)
                    .errors(List.of("Platform not supported: " + spec.platform()))
                    .warnings(List.of())
                    .securityIssues(List.of())
                    .securityScore(0.0)
                    .qualityScore(0.0)
                    .build();
        }

        return generator.validatePipeline(spec);
    }

    /**
 * Validates multiple platform specifications. */
    public Map<CIPipelineSpec.CIPlatform, CIPipelineValidationResult>
            validateMultiPlatformPipelines(
                    CIPipelineSpec baseSpec, List<CIPipelineSpec.CIPlatform> platforms) {

        Map<CIPipelineSpec.CIPlatform, CIPipelineValidationResult> results = new HashMap<>();

        for (var platform : platforms) {
            var platformSpec = baseSpec.toBuilder().platform(platform).build();
            results.put(platform, validatePipeline(platformSpec));
        }

        return results;
    }

    /**
 * Suggests improvements for CI/CD pipeline. */
    public CIPipelineImprovementSuggestions suggestImprovements(CIPipelineSpec spec) {
        var generator = generators.get(spec.platform());
        if (generator == null) {
            return CIPipelineImprovementSuggestions.builder()
                    .securityEnhancements(List.of("Platform not supported for improvements"))
                    .performanceOptimizations(List.of())
                    .reliabilityImprovements(List.of())
                    .improvementScore(0.0)
                    .build();
        }

        return generator.suggestImprovements(spec);
    }

    /**
 * Recommends optimal CI/CD platform and configuration for a project. */
    public CIPipelineRecommendationResult recommendPipeline(String projectPath) {
        // Analyze project and get recommendations from all generators
        Map<String, Double> platformScores = new HashMap<>();
        List<CIPipelineRecommendationResult.CIPipelineRecommendation> allRecommendations =
                new ArrayList<>();

        for (var entry : generators.entrySet()) {
            var platform = entry.getKey();
            var generator = entry.getValue();

            try {
                var result = generator.recommendPipeline(projectPath);
                platformScores.putAll(result.platformScores());
                allRecommendations.addAll(result.recommendations());
            } catch (Exception e) {
                // Log error and continue with other generators
                platformScores.put(platform.toString().toLowerCase(), 0.0);
            }
        }

        // Determine best platform
        var bestPlatform =
                platformScores.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(
                                entry -> {
                                    String platformName = entry.getKey();
                                    if (platformName.contains("github"))
                                        return CIPipelineSpec.CIPlatform.GITHUB_ACTIONS;
                                    if (platformName.contains("gitlab"))
                                        return CIPipelineSpec.CIPlatform.GITLAB_CI;
                                    return CIPipelineSpec.CIPlatform.GITHUB_ACTIONS; // default
                                })
                        .orElse(CIPipelineSpec.CIPlatform.GITHUB_ACTIONS);

        // Calculate confidence score
        double maxScore =
                platformScores.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);

        double confidenceScore = maxScore > 0.8 ? 0.9 : 0.7;

        return CIPipelineRecommendationResult.builder()
                .projectType("java-gradle")
                .detectedLanguages(List.of("java"))
                .detectedFrameworks(List.of("gradle"))
                .recommendedPlatform(bestPlatform)
                .recommendations(allRecommendations)
                .platformScores(platformScores)
                .confidenceScore(confidenceScore)
                .build();
    }

    /**
 * Generates comprehensive CI/CD report with cross-platform analysis. */
    public CIPipelineAnalysisReport generateAnalysisReport(CIPipelineSpec baseSpec) {
        var platforms =
                List.of(
                        CIPipelineSpec.CIPlatform.GITHUB_ACTIONS,
                        CIPipelineSpec.CIPlatform.GITLAB_CI);

        // Generate pipelines for all platforms
        var pipelines = generateMultiPlatformPipelines(baseSpec, platforms);

        // Validate all platforms
        var validations = validateMultiPlatformPipelines(baseSpec, platforms);

        // Get improvement suggestions for all platforms
        Map<CIPipelineSpec.CIPlatform, CIPipelineImprovementSuggestions> improvements =
                new HashMap<>();
        for (var platform : platforms) {
            var platformSpec = baseSpec.toBuilder().platform(platform).build();
            improvements.put(platform, suggestImprovements(platformSpec));
        }

        // Calculate aggregate metrics
        double avgSecurityScore =
                validations.values().stream()
                        .mapToDouble(CIPipelineValidationResult::securityScore)
                        .average()
                        .orElse(0.0);

        double avgQualityScore =
                validations.values().stream()
                        .mapToDouble(CIPipelineValidationResult::qualityScore)
                        .average()
                        .orElse(0.0);

        double avgImprovementScore =
                improvements.values().stream()
                        .mapToDouble(CIPipelineImprovementSuggestions::improvementScore)
                        .average()
                        .orElse(0.0);

        return CIPipelineAnalysisReport.builder()
                .baseSpec(baseSpec)
                .generatedPipelines(pipelines)
                .validationResults(validations)
                .improvementSuggestions(improvements)
                .aggregateMetrics(
                        Map.of(
                                "avgSecurityScore", avgSecurityScore,
                                "avgQualityScore", avgQualityScore,
                                "avgImprovementScore", avgImprovementScore,
                                "supportedPlatforms", platforms.size()))
                .build();
    }

    /**
 * Optimizes CI/CD pipeline using AI-powered analysis. */
    public OptimizedCIPipelineResult optimizePipeline(CIPipelineSpec spec) {
        // Generate baseline pipeline
        var baseline = generatePipeline(spec);

        // Validate and get improvement suggestions
        var validation = validatePipeline(spec);
        var suggestions = suggestImprovements(spec);

        // Apply AI-powered optimizations
        var optimizedSpec = applyAIOptimizations(spec, suggestions, validation);

        // Generate optimized pipeline
        var optimized = generatePipeline(optimizedSpec);

        // Calculate improvement metrics
        var improvementMetrics = calculateImprovementMetrics(baseline, optimized, validation);

        return OptimizedCIPipelineResult.builder()
                .originalSpec(spec)
                .optimizedSpec(optimizedSpec)
                .originalPipeline(baseline)
                .optimizedPipeline(optimized)
                .validationResult(validation)
                .improvementSuggestions(suggestions)
                .improvementMetrics(improvementMetrics)
                .optimizationScore(calculateOptimizationScore(improvementMetrics))
                .build();
    }

    private CIPipelineSpec applyAIOptimizations(
            CIPipelineSpec spec,
            CIPipelineImprovementSuggestions suggestions,
            CIPipelineValidationResult validation) {

        var builder = spec.toBuilder();

        // Apply security enhancements
        if (suggestions.securityEnhancements().contains("Enable security scanning")) {
            var securityConfig =
                    CIPipelineSpec.CISecurityConfig.builder()
                            .enableSecurityScanning(true)
                            .enableSecretsScanning(true)
                            .enableDependencyScanning(true)
                            .scanTools(List.of("trivy", "snyk", "trufflehog"))
                            .blockOnCritical(true)
                            .build();
            builder.security(securityConfig);
        }

        // Apply performance optimizations
        if (suggestions.performanceOptimizations().contains("matrix builds")) {
            var matrix =
                    CIPipelineSpec.CIMatrix.builder()
                            .operatingSystems(
                                    List.of("ubuntu-latest", "windows-latest", "macos-latest"))
                            .languageVersions(List.of("17", "21"))
                            .failFast(false)
                            .build();
            builder.matrix(matrix);
        }

        return builder.build();
    }

    private Map<String, Double> calculateImprovementMetrics(
            GeneratedCIPipeline baseline,
            GeneratedCIPipeline optimized,
            CIPipelineValidationResult validation) {

        return Map.of(
                "securityImprovement",
                validation.securityScore(),
                "qualityImprovement",
                validation.qualityScore(),
                "fileCountChange",
                (double) (optimized.pipelineFiles().size() - baseline.pipelineFiles().size()),
                "secretsReduction",
                (double)
                        Math.max(
                                0,
                                baseline.generatedSecrets().size()
                                        - optimized.generatedSecrets().size()));
    }

    private double calculateOptimizationScore(Map<String, Double> metrics) {
        return metrics.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    /**
 * Gets list of supported CI/CD platforms. */
    public List<CIPipelineSpec.CIPlatform> getSupportedPlatforms() {
        return new ArrayList<>(generators.keySet());
    }

    /**
 * Checks if platform is supported. */
    public boolean isPlatformSupported(CIPipelineSpec.CIPlatform platform) {
        return generators.containsKey(platform);
    }
}
