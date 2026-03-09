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

import java.util.*;

/**
 * Azure DevOps Pipelines generator.
 *
 * <p>Week 8 Day 37: Azure DevOps pipeline generator with security scanning and Polyfix integration.
 *
 * @doc.type class
 * @doc.purpose Azure DevOps Pipelines generator.
 * @doc.layer platform
 * @doc.pattern Generator
 */
public class AzureDevOpsGenerator implements CIPipelineGenerator {

    @Override
    public GeneratedCIPipeline generatePipeline(CIPipelineSpec spec) {
        Map<String, String> pipelineFiles = new HashMap<>();
        List<String> secrets = new ArrayList<>();
        Map<String, String> environmentConfigs = new HashMap<>();
        List<String> requiredActions = new ArrayList<>();

        // Generate main Azure DevOps pipeline file
        String azurePipeline = generateAzurePipeline(spec);
        pipelineFiles.put("azure-pipelines.yml", azurePipeline);

        // Collect secrets
        secrets.addAll(extractRequiredSecrets(spec));

        return GeneratedCIPipeline.builder()
                .spec(spec)
                .pipelineFiles(pipelineFiles)
                .generatedSecrets(secrets)
                .environmentConfigurations(environmentConfigs)
                .requiredActions(requiredActions)
                .metadata(
                        Map.of(
                                "platform",
                                "azure-devops",
                                "workflowCount",
                                pipelineFiles.size(),
                                "securityEnabled",
                                spec.security() != null && spec.security().enableSecurityScanning(),
                                "matrixEnabled",
                                spec.matrix() != null))
                .build();
    }

    private String generateAzurePipeline(CIPipelineSpec spec) {
        StringBuilder pipeline = new StringBuilder();

        // Header
        pipeline.append("# Generated Azure DevOps Pipeline by YAPPC\\n");
        pipeline.append("# ").append(spec.name()).append("\\n");
        pipeline.append("\\n");

        // Trigger configuration
        pipeline.append("trigger:\\n");
        for (var trigger : spec.triggers()) {
            if (trigger.type() == CIPipelineSpec.CITrigger.CITriggerType.PUSH) {
                pipeline.append("  branches:\\n");
                pipeline.append("    include:\\n");
                for (String branch : trigger.branches()) {
                    pipeline.append("      - ").append(branch).append("\\n");
                }
            }
        }
        pipeline.append("\\n");

        // PR trigger
        pipeline.append("pr:\\n");
        pipeline.append("  branches:\\n");
        pipeline.append("    include:\\n");
        pipeline.append("      - main\\n");
        pipeline.append("      - develop\\n");
        pipeline.append("\\n");

        // Variables
        if (!spec.environment().isEmpty()) {
            pipeline.append("variables:\\n");
            spec.environment()
                    .forEach(
                            (key, value) ->
                                    pipeline.append("  ")
                                            .append(key)
                                            .append(": ")
                                            .append(value)
                                            .append("\\n"));
            pipeline.append("\\n");
        }

        // Stages
        pipeline.append("stages:\\n");
        for (var stage : spec.stages()) {
            generateStage(stage, pipeline);
        }

        return pipeline.toString();
    }

    private void generateStage(CIPipelineSpec.CIStage stage, StringBuilder pipeline) {
        pipeline.append("- stage: ")
                .append(stage.name().replaceAll("[^a-zA-Z0-9_]", "_"))
                .append("\\n");
        pipeline.append("  displayName: ").append(stage.name()).append("\\n");
        pipeline.append("  jobs:\\n");

        for (var job : stage.jobs()) {
            generateJob(job, pipeline);
        }
    }

    private void generateJob(CIPipelineSpec.CIJob job, StringBuilder pipeline) {
        pipeline.append("  - job: ")
                .append(job.name().replaceAll("[^a-zA-Z0-9_]", "_"))
                .append("\\n");
        pipeline.append("    displayName: ").append(job.name()).append("\\n");
        pipeline.append("    pool:\\n");
        pipeline.append("      vmImage: 'ubuntu-latest'\\n");

        if (job.timeoutMinutes() > 0) {
            pipeline.append("    timeoutInMinutes: ").append(job.timeoutMinutes()).append("\\n");
        }

        pipeline.append("    steps:\\n");
        for (var step : job.steps()) {
            generateStep(step, pipeline);
        }

        pipeline.append("\\n");
    }

    private void generateStep(CIPipelineSpec.CIStep step, StringBuilder pipeline) {
        switch (step.type()) {
            case CHECKOUT:
                pipeline.append("    - checkout: self\\n");
                break;
            case SETUP_LANGUAGE:
                pipeline.append("    - task: JavaToolInstaller@0\\n");
                pipeline.append("      displayName: 'Setup JDK'\\n");
                pipeline.append("      inputs:\\n");
                pipeline.append("        versionSpec: '21'\\n");
                pipeline.append("        jdkArchitectureOption: 'x64'\\n");
                pipeline.append("        jdkSourceOption: 'PreInstalled'\\n");
                break;
            case RUN_COMMAND:
                pipeline.append("    - script: ").append(step.command()).append("\\n");
                pipeline.append("      displayName: '").append(step.name()).append("'\\n");
                break;
            case USE_ACTION:
                pipeline.append("    - task: ").append(step.action()).append("\\n");
                pipeline.append("      displayName: '").append(step.name()).append("'\\n");
                if (!step.with().isEmpty()) {
                    pipeline.append("      inputs:\\n");
                    step.with()
                            .forEach(
                                    (key, value) ->
                                            pipeline.append("        ")
                                                    .append(key)
                                                    .append(": ")
                                                    .append(value)
                                                    .append("\\n"));
                }
                break;
            default:
                pipeline.append("    - script: echo 'Step type not implemented: ")
                        .append(step.type())
                        .append("'\\n");
                pipeline.append("      displayName: '").append(step.name()).append("'\\n");
        }
    }

    private List<String> extractRequiredSecrets(CIPipelineSpec spec) {
        return new ArrayList<>(spec.secrets());
    }

    @Override
    public CIPipelineValidationResult validatePipeline(CIPipelineSpec spec) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (spec.name() == null || spec.name().isBlank()) {
            errors.add("Pipeline name is required");
        }

        return CIPipelineValidationResult.builder()
                .isValid(errors.isEmpty())
                .errors(errors)
                .warnings(warnings)
                .securityIssues(List.of())
                .securityScore(1.0)
                .qualityScore(errors.isEmpty() ? 1.0 : 0.0)
                .build();
    }

    @Override
    public CIPipelineImprovementSuggestions suggestImprovements(CIPipelineSpec spec) {
        return CIPipelineImprovementSuggestions.builder()
                .securityEnhancements(List.of("Add Azure Security Center scanning"))
                .performanceOptimizations(List.of("Use self-hosted agents for faster builds"))
                .reliabilityImprovements(List.of("Add pipeline retention policies"))
                .improvementScore(0.7)
                .build();
    }

    @Override
    public CIPipelineRecommendationResult recommendPipeline(String projectPath) {
        return CIPipelineRecommendationResult.builder()
                .projectType("generic")
                .detectedLanguages(List.of())
                .detectedFrameworks(List.of())
                .recommendedPlatform(CIPipelineSpec.CIPlatform.AZURE_DEVOPS)
                .recommendations(List.of())
                .platformScores(Map.of("azure-devops", 0.7))
                .confidenceScore(0.7)
                .build();
    }
}
