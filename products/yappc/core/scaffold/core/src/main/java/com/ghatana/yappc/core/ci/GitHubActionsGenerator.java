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

/**
 * GitHub Actions CI/CD pipeline generator with security and quality gates. Implements comprehensive
 * GitHub Actions workflows with Polyfix integration.
 *
 * <p>Week 8 Day 37: GitHub Actions generator with security scanning and quality gates.
 *
 * @doc.type class
 * @doc.purpose GitHub Actions CI/CD pipeline generator with security and quality gates. Implements comprehensive
 * @doc.layer platform
 * @doc.pattern Generator
 */
public class GitHubActionsGenerator implements CIPipelineGenerator {

    @Override
    public GeneratedCIPipeline generatePipeline(CIPipelineSpec spec) {
        Map<String, String> pipelineFiles = new HashMap<>();
        List<String> secrets = new ArrayList<>();
        Map<String, String> environmentConfigs = new HashMap<>();
        List<String> requiredActions = new ArrayList<>();

        // Generate main workflow file
        String mainWorkflow = generateMainWorkflow(spec);
        pipelineFiles.put(".github/workflows/main.yml", mainWorkflow);

        // Generate security workflow
        if (spec.security() != null && spec.security().enableSecurityScanning()) {
            String securityWorkflow = generateSecurityWorkflow(spec);
            pipelineFiles.put(".github/workflows/security.yml", securityWorkflow);
            requiredActions.addAll(List.of("trivy-action", "dependency-review-action"));
        }

        // Generate release workflow
        String releaseWorkflow = generateReleaseWorkflow(spec);
        pipelineFiles.put(".github/workflows/release.yml", releaseWorkflow);

        // Generate Polyfix integration workflow
        String polyfixWorkflow = generatePolyfixWorkflow(spec);
        pipelineFiles.put(".github/workflows/polyfix.yml", polyfixWorkflow);

        // Generate environment configurations
        environmentConfigs.put("production.yml", generateEnvironmentConfig("production"));
        environmentConfigs.put("staging.yml", generateEnvironmentConfig("staging"));

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
                                "github-actions",
                                "workflowCount",
                                pipelineFiles.size(),
                                "securityEnabled",
                                spec.security() != null && spec.security().enableSecurityScanning(),
                                "matrixEnabled",
                                spec.matrix() != null))
                .build();
    }

    private String generateMainWorkflow(CIPipelineSpec spec) {
        StringBuilder workflow = new StringBuilder();

        // Workflow header
        workflow.append("name: ").append(spec.name()).append("\\n");
        workflow.append("\\n");

        // Triggers
        workflow.append("on:\\n");
        generateTriggers(spec.triggers(), workflow);
        workflow.append("\\n");

        // Environment variables
        if (!spec.environment().isEmpty()) {
            workflow.append("env:\\n");
            spec.environment()
                    .forEach(
                            (key, value) ->
                                    workflow.append("  ")
                                            .append(key)
                                            .append(": ")
                                            .append(value)
                                            .append("\\n"));
            workflow.append("\\n");
        }

        // Jobs
        workflow.append("jobs:\\n");

        // Generate matrix job if specified
        if (spec.matrix() != null) {
            generateMatrixJob(spec, workflow);
        } else {
            generateStandardJobs(spec, workflow);
        }

        return workflow.toString();
    }

    private void generateTriggers(List<CIPipelineSpec.CITrigger> triggers, StringBuilder workflow) {
        for (var trigger : triggers) {
            switch (trigger.type()) {
                case PUSH:
                    workflow.append("  push:\\n");
                    if (!trigger.branches().isEmpty()) {
                        workflow.append("    branches: ").append(trigger.branches()).append("\\n");
                    }
                    if (!trigger.paths().isEmpty()) {
                        workflow.append("    paths: ").append(trigger.paths()).append("\\n");
                    }
                    break;
                case PULL_REQUEST:
                    workflow.append("  pull_request:\\n");
                    if (!trigger.branches().isEmpty()) {
                        workflow.append("    branches: ").append(trigger.branches()).append("\\n");
                    }
                    break;
                case SCHEDULE:
                    if (trigger.schedule() != null) {
                        workflow.append("  schedule:\\n");
                        workflow.append("    - cron: '").append(trigger.schedule()).append("'\\n");
                    }
                    break;
                case WORKFLOW_DISPATCH:
                    workflow.append("  workflow_dispatch:\\n");
                    break;
                default:
                    break;
            }
        }
    }

    private void generateMatrixJob(CIPipelineSpec spec, StringBuilder workflow) {
        workflow.append("  test:\\n");
        workflow.append("    runs-on: ${{ matrix.os }}\\n");
        workflow.append("    strategy:\\n");
        workflow.append("      fail-fast: ").append(spec.matrix().failFast()).append("\\n");
        workflow.append("      matrix:\\n");

        if (!spec.matrix().operatingSystems().isEmpty()) {
            workflow.append("        os: ").append(spec.matrix().operatingSystems()).append("\\n");
        }
        if (!spec.matrix().languageVersions().isEmpty()) {
            workflow.append("        version: ")
                    .append(spec.matrix().languageVersions())
                    .append("\\n");
        }

        workflow.append("\\n");
        workflow.append("    steps:\\n");

        // Generate steps for matrix job
        generateStandardSteps(spec, workflow, true);
    }

    private void generateStandardJobs(CIPipelineSpec spec, StringBuilder workflow) {
        for (var stage : spec.stages()) {
            for (var job : stage.jobs()) {
                workflow.append("  ")
                        .append(job.name().replaceAll("[^a-zA-Z0-9_-]", "_"))
                        .append(":\\n");
                workflow.append("    runs-on: ").append(job.runsOn()).append("\\n");

                if (job.timeoutMinutes() > 0) {
                    workflow.append("    timeout-minutes: ")
                            .append(job.timeoutMinutes())
                            .append("\\n");
                }

                if (!stage.dependsOn().isEmpty()) {
                    workflow.append("    needs: ").append(stage.dependsOn()).append("\\n");
                }

                workflow.append("\\n");
                workflow.append("    steps:\\n");

                // Generate steps for job
                generateJobSteps(job, workflow);
            }
        }
    }

    private void generateStandardSteps(
            CIPipelineSpec spec, StringBuilder workflow, boolean matrixMode) {
        // Checkout step
        workflow.append("      - name: Checkout code\\n");
        workflow.append("        uses: actions/checkout@v4\\n");
        workflow.append("\\n");

        // Setup language step (example for Java)
        workflow.append("      - name: Setup JDK\\n");
        workflow.append("        uses: actions/setup-java@v4\\n");
        workflow.append("        with:\\n");
        if (matrixMode) {
            workflow.append("          java-version: ${{ matrix.version }}\\n");
        } else {
            workflow.append("          java-version: '21'\\n");
        }
        workflow.append("          distribution: 'temurin'\\n");
        workflow.append("\\n");

        // Cache dependencies
        workflow.append("      - name: Cache dependencies\\n");
        workflow.append("        uses: actions/cache@v4\\n");
        workflow.append("        with:\\n");
        workflow.append("          path: |\\n");
        workflow.append("            ~/.gradle/caches\\n");
        workflow.append("            ~/.gradle/wrapper\\n");
        workflow.append(
                "          key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*',"
                        + " '**/gradle-wrapper.properties') }}\\n");
        workflow.append("          restore-keys: |\\n");
        workflow.append("            ${{ runner.os }}-gradle-\\n");
        workflow.append("\\n");

        // Build step
        workflow.append("      - name: Build\\n");
        workflow.append("        run: ./gradlew build\\n");
        workflow.append("\\n");

        // Test step
        workflow.append("      - name: Test\\n");
        workflow.append("        run: ./gradlew test\\n");
        workflow.append("\\n");

        // Upload test results
        workflow.append("      - name: Upload test results\\n");
        workflow.append("        uses: actions/upload-artifact@v4\\n");
        workflow.append("        if: always()\\n");
        workflow.append("        with:\\n");
        workflow.append("          name: test-results\\n");
        workflow.append("          path: build/reports/tests/\\n");
    }

    private void generateJobSteps(CIPipelineSpec.CIJob job, StringBuilder workflow) {
        for (var step : job.steps()) {
            workflow.append("      - name: ").append(step.name()).append("\\n");

            switch (step.type()) {
                case CHECKOUT:
                    workflow.append("        uses: actions/checkout@v4\\n");
                    break;
                case SETUP_LANGUAGE:
                    generateLanguageSetup(step, workflow);
                    break;
                case RUN_COMMAND:
                    workflow.append("        run: ").append(step.command()).append("\\n");
                    break;
                case USE_ACTION:
                    workflow.append("        uses: ").append(step.action()).append("\\n");
                    if (!step.with().isEmpty()) {
                        workflow.append("        with:\\n");
                        step.with()
                                .forEach(
                                        (key, value) ->
                                                workflow.append("          ")
                                                        .append(key)
                                                        .append(": ")
                                                        .append(value)
                                                        .append("\\n"));
                    }
                    break;
                case CACHE:
                    generateCacheStep(step, workflow);
                    break;
                case ARTIFACT_UPLOAD:
                    generateArtifactUpload(step, workflow);
                    break;
                default:
                    workflow.append("        run: echo 'Step type not implemented: ")
                            .append(step.type())
                            .append("'\\n");
            }

            if (step.continueOnError()) {
                workflow.append("        continue-on-error: true\\n");
            }

            if (!step.environment().isEmpty()) {
                workflow.append("        env:\\n");
                step.environment()
                        .forEach(
                                (key, value) ->
                                        workflow.append("          ")
                                                .append(key)
                                                .append(": ")
                                                .append(value)
                                                .append("\\n"));
            }

            workflow.append("\\n");
        }
    }

    private void generateLanguageSetup(CIPipelineSpec.CIStep step, StringBuilder workflow) {
        String action = step.action();
        if (action == null) {
            action = "actions/setup-java@v4"; // default
        }

        workflow.append("        uses: ").append(action).append("\\n");
        workflow.append("        with:\\n");
        workflow.append("          java-version: '21'\\n");
        workflow.append("          distribution: 'temurin'\\n");
    }

    private void generateCacheStep(CIPipelineSpec.CIStep step, StringBuilder workflow) {
        workflow.append("        uses: actions/cache@v4\\n");
        workflow.append("        with:\\n");
        workflow.append("          path: ~/.gradle/caches\\n");
        workflow.append(
                "          key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*') }}\\n");
    }

    private void generateArtifactUpload(CIPipelineSpec.CIStep step, StringBuilder workflow) {
        workflow.append("        uses: actions/upload-artifact@v4\\n");
        workflow.append("        with:\\n");
        workflow.append("          name: build-artifacts\\n");
        workflow.append("          path: build/libs/\\n");
    }

    private String generateSecurityWorkflow(CIPipelineSpec spec) {
        return """
            name: Security Scan

            on:
              push:
                branches: [main]
              pull_request:
                branches: [main]
              schedule:
                - cron: '0 2 * * *'

            permissions:
              contents: read
              security-events: write

            jobs:
              security:
                runs-on: ubuntu-latest
                steps:
                  - name: Checkout code
                    uses: actions/checkout@v4

                  - name: Run Trivy vulnerability scanner
                    uses: aquasecurity/trivy-action@master
                    with:
                      scan-type: 'fs'
                      scan-ref: '.'
                      format: 'sarif'
                      output: 'trivy-results.sarif'

                  - name: Upload Trivy scan results to GitHub Security tab
                    uses: github/codeql-action/upload-sarif@v3
                    with:
                      sarif_file: 'trivy-results.sarif'

                  - name: Dependency Review
                    uses: actions/dependency-review-action@v4
                    with:
                      fail-on-severity: critical

                  - name: Secret Scan
                    uses: trufflesecurity/trufflehog@main
                    with:
                      path: ./
                      base: main
                      head: HEAD
            """;
    }

    private String generateReleaseWorkflow(CIPipelineSpec spec) {
        return """
            name: Release

            on:
              push:
                tags:
                  - 'v*.*.*'

            jobs:
              release:
                runs-on: ubuntu-latest
                permissions:
                  contents: write
                steps:
                  - name: Checkout code
                    uses: actions/checkout@v4

                  - name: Setup JDK
                    uses: actions/setup-java@v4
                    with:
                      java-version: '21'
                      distribution: 'temurin'

                  - name: Build release
                    run: ./gradlew build

                  - name: Create Release
                    uses: ncipollo/release-action@v1
                    with:
                      artifacts: "build/libs/*"
                      token: ${{ secrets.GITHUB_TOKEN }}
                      generateReleaseNotes: true
            """;
    }

    private String generatePolyfixWorkflow(CIPipelineSpec spec) {
        return """
            name: Polyfix Code Quality & Guardrails

            on:
              push:
                branches: [main, develop]
              pull_request:
                branches: [main, develop]
              workflow_call:
                inputs:
                  enforce-guardrails:
                    description: 'Enforce Polyfix guardrails (fail on violations)'
                    required: false
                    default: true
                    type: boolean

            permissions:
              contents: read
              issues: write
              pull-requests: write
              security-events: write

            jobs:
              polyfix-guardrails:
                runs-on: ubuntu-latest
                timeout-minutes: 15
                env:
                  POLYFIX_ENFORCE: ${{ inputs.enforce-guardrails || 'true' }}

                steps:
                  - name: Checkout code
                    uses: actions/checkout@v4
                    with:
                      fetch-depth: 0  # Full history for diff analysis

                  - name: Setup JDK
                    uses: actions/setup-java@v4
                    with:
                      java-version: '21'
                      distribution: 'temurin'
                      cache: gradle

                  - name: Validate Gradle wrapper
                    uses: gradle/wrapper-validation-action@v2

                  - name: Polyfix Pre-build Validation
                    id: prevalidation
                    run: |
                      echo "::group::Polyfix Pre-build Checks"

                      # Check for common anti-patterns
                      echo "Checking for prohibited patterns..."

                      # Prevent System.out.println in production code
                      if grep -r "System\\.out\\.println" src/main --include="*.java"; then
                        echo "::error::System.out.println found in production code. Use proper logging instead."
                        echo "polyfix-violation=system-out-println" >> $GITHUB_OUTPUT
                      fi

                      # Check for hardcoded secrets/tokens
                      if grep -rE "(password|token|secret|key).*=.*['\"][^'\"]{10,}['\"]" src/ --include="*.java" --include="*.properties"; then
                        echo "::error::Potential hardcoded secrets detected. Use environment variables or secret management."
                        echo "polyfix-violation=hardcoded-secrets" >> $GITHUB_OUTPUT
                      fi

                      # Check for TODO/FIXME without issue references
                      if grep -rE "(TODO|FIXME)(?!.*#[0-9]+)" src/ --include="*.java"; then
                        echo "::warning::TODO/FIXME found without issue reference. Link to GitHub issues."
                        echo "polyfix-warning=untracked-todos" >> $GITHUB_OUTPUT
                      fi

                      echo "::endgroup::"

                  - name: Run Polyfix Analysis
                    id: analysis
                    run: |
                      echo "::group::Polyfix Code Analysis"

                      # Create Polyfix analysis report directory
                      mkdir -p build/reports/polyfix

                      # Run comprehensive Polyfix checks
                      ./gradlew polyfixAnalyze --continue || echo "polyfix-analysis-warnings=true" >> $GITHUB_OUTPUT

                      # Generate complexity metrics
                      ./gradlew polyfixComplexity --continue || echo "polyfix-complexity-warnings=true" >> $GITHUB_OUTPUT

                      # Check for architectural violations
                      ./gradlew polyfixArchCheck --continue || echo "polyfix-arch-violations=true" >> $GITHUB_OUTPUT

                      echo "::endgroup::"

                  - name: Polyfix Refactoring Suggestions
                    id: suggestions
                    run: |
                      echo "::group::Polyfix Refactoring Analysis"

                      # Generate refactoring suggestions for PR
                      if [ "${{ github.event_name }}" = "pull_request" ]; then
                        ./gradlew polyfixSuggest \
                          --base-ref=origin/${{ github.base_ref }} \
                          --head-ref=HEAD \
                          --output=build/reports/polyfix/suggestions.md

                        # Check if suggestions file was created and has content
                        if [ -f "build/reports/polyfix/suggestions.md" ] && [ -s "build/reports/polyfix/suggestions.md" ]; then
                          echo "polyfix-suggestions-available=true" >> $GITHUB_OUTPUT
                        fi
                      fi

                      echo "::endgroup::"

                  - name: Enforce Guardrails
                    if: env.POLYFIX_ENFORCE == 'true'
                    run: |
                      echo "::group::Polyfix Guardrails Enforcement"

                      violations=0

                      # Check critical violations
                      if [ "${{ steps.prevalidation.outputs.polyfix-violation }}" != "" ]; then
                        echo "::error::Critical Polyfix violations detected!"
                        violations=$((violations + 1))
                      fi

                      # Check complexity thresholds
                      if [ -f "build/reports/polyfix/complexity.json" ]; then
                        # Parse complexity report and fail if thresholds exceeded
                        high_complexity=$(jq '.violations | length' build/reports/polyfix/complexity.json 2>/dev/null || echo "0")
                        if [ "$high_complexity" -gt "5" ]; then
                          echo "::error::Code complexity exceeds threshold (>5 violations). Refactoring required."
                          violations=$((violations + 1))
                        fi
                      fi

                      # Check test coverage requirements
                      if [ -f "build/reports/jacoco/test/jacocoTestReport.xml" ]; then
                        coverage=$(grep -o 'missed="[0-9]*"' build/reports/jacoco/test/jacocoTestReport.xml | head -1 | grep -o '[0-9]*')
                        total=$(grep -o 'covered="[0-9]*"' build/reports/jacoco/test/jacocoTestReport.xml | head -1 | grep -o '[0-9]*')
                        if [ "$total" -gt "0" ]; then
                          coverage_percent=$((100 * total / (total + coverage)))
                          if [ "$coverage_percent" -lt "80" ]; then
                            echo "::error::Test coverage below 80% threshold (current: ${coverage_percent}%)"
                            violations=$((violations + 1))
                          fi
                        fi
                      fi

                      if [ "$violations" -gt "0" ]; then
                        echo "::error::$violations Polyfix guardrail violations detected. Build failed."
                        exit 1
                      fi

                      echo "::notice::All Polyfix guardrails passed ✅"
                      echo "::endgroup::"

                  - name: Upload Polyfix Results
                    uses: actions/upload-artifact@v4
                    if: always()
                    with:
                      name: polyfix-analysis-${{ github.run_id }}
                      path: |
                        build/reports/polyfix/
                        build/reports/jacoco/
                        build/reports/tests/
                      retention-days: 30

                  - name: Comment PR with Suggestions
                    if: github.event_name == 'pull_request' && steps.suggestions.outputs.polyfix-suggestions-available == 'true'
                    uses: actions/github-script@v7
                    with:
                      script: |
                        const fs = require('fs');
                        const suggestionPath = 'build/reports/polyfix/suggestions.md';

                        if (fs.existsSync(suggestionPath)) {
                          const suggestions = fs.readFileSync(suggestionPath, 'utf8');

                          const body = `## 🔧 Polyfix Refactoring Suggestions

                          ${suggestions}

                          ---
                          *Generated by Polyfix CI Guardrails • [View full report](https://github.com/${{ github.repository }}/actions/runs/${{ github.run_id }})*`;

                          // Find existing Polyfix comment
                          const { data: comments } = await github.rest.issues.listComments({
                            owner: context.repo.owner,
                            repo: context.repo.repo,
                            issue_number: context.issue.number,
                          });

                          const polyfixComment = comments.find(comment =>
                            comment.body.includes('🔧 Polyfix Refactoring Suggestions')
                          );

                          if (polyfixComment) {
                            // Update existing comment
                            await github.rest.issues.updateComment({
                              owner: context.repo.owner,
                              repo: context.repo.repo,
                              comment_id: polyfixComment.id,
                              body: body
                            });
                          } else {
                            // Create new comment
                            await github.rest.issues.createComment({
                              owner: context.repo.owner,
                              repo: context.repo.repo,
                              issue_number: context.issue.number,
                              body: body
                            });
                          }
                        }
            """;
    }

    private String generateEnvironmentConfig(String environment) {
        return """
            name: %s
            url: https://%s.example.com
            protection_rules:
              required_reviewers: 1
              wait_timer: 0
              prevent_self_review: true
            """
                .formatted(environment, environment);
    }

    private List<String> extractRequiredSecrets(CIPipelineSpec spec) {
        List<String> secrets = new ArrayList<>(spec.secrets());

        // Add common secrets
        secrets.add("GITHUB_TOKEN");

        // Add security-related secrets
        if (spec.security() != null && spec.security().enableSecurityScanning()) {
            secrets.add("SECURITY_SCAN_TOKEN");
        }

        return secrets;
    }

    @Override
    public CIPipelineValidationResult validatePipeline(CIPipelineSpec spec) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> securityIssues = new ArrayList<>();

        // Validate basic spec
        if (spec.name() == null || spec.name().isBlank()) {
            errors.add("Pipeline name is required");
        }

        if (spec.stages().isEmpty()) {
            errors.add("At least one stage must be defined");
        }

        // Security validation
        if (spec.security() == null) {
            securityIssues.add("Security configuration is not defined");
        } else {
            if (!spec.security().enableSecurityScanning()) {
                warnings.add("Security scanning is disabled");
            }
            if (!spec.security().enableSecretsScanning()) {
                warnings.add("Secrets scanning is disabled");
            }
        }

        // Check for hardcoded secrets
        for (var stage : spec.stages()) {
            for (var job : stage.jobs()) {
                for (var step : job.steps()) {
                    if (step.environment().values().stream().anyMatch(this::looksLikeSecret)) {
                        securityIssues.add("Potential hardcoded secret in step: " + step.name());
                    }
                }
            }
        }

        double securityScore =
                securityIssues.isEmpty() ? 1.0 : Math.max(0.0, 1.0 - securityIssues.size() * 0.2);
        double qualityScore = errors.isEmpty() ? 1.0 : 0.0;

        return CIPipelineValidationResult.builder()
                .isValid(errors.isEmpty())
                .errors(errors)
                .warnings(warnings)
                .securityIssues(securityIssues)
                .securityScore(securityScore)
                .qualityScore(qualityScore)
                .build();
    }

    private boolean looksLikeSecret(String value) {
        return value != null
                && (value.matches(".*[A-Za-z0-9]{20,}.*")
                        || value.contains("password")
                        || value.contains("token")
                        || value.contains("key"));
    }

    @Override
    public CIPipelineImprovementSuggestions suggestImprovements(CIPipelineSpec spec) {
        List<String> securityEnhancements = new ArrayList<>();
        List<String> performanceOptimizations = new ArrayList<>();
        List<String> reliabilityImprovements = new ArrayList<>();

        // Security suggestions
        if (spec.security() == null || !spec.security().enableSecurityScanning()) {
            securityEnhancements.add("Enable security scanning with Trivy or Snyk");
        }
        if (spec.security() == null || !spec.security().enableSecretsScanning()) {
            securityEnhancements.add("Enable secrets scanning with TruffleHog");
        }

        // Performance suggestions
        if (spec.matrix() == null) {
            performanceOptimizations.add("Consider matrix builds for multi-platform testing");
        }

        // Check for caching
        boolean hasCaching =
                spec.stages().stream()
                        .flatMap(stage -> stage.jobs().stream())
                        .flatMap(job -> job.steps().stream())
                        .anyMatch(step -> step.type() == CIPipelineSpec.CIStep.CIStepType.CACHE);

        if (!hasCaching) {
            performanceOptimizations.add("Add dependency caching to speed up builds");
        }

        // Reliability suggestions
        if (spec.triggers().stream()
                .noneMatch(t -> t.type() == CIPipelineSpec.CITrigger.CITriggerType.SCHEDULE)) {
            reliabilityImprovements.add("Add scheduled builds to catch issues early");
        }

        return CIPipelineImprovementSuggestions.builder()
                .securityEnhancements(securityEnhancements)
                .performanceOptimizations(performanceOptimizations)
                .reliabilityImprovements(reliabilityImprovements)
                .improvementScore(0.8)
                .build();
    }

    @Override
    public CIPipelineRecommendationResult recommendPipeline(String projectPath) {
        // Simple project analysis - in practice would analyze project structure
        return CIPipelineRecommendationResult.builder()
                .projectType("java-gradle")
                .detectedLanguages(List.of("java"))
                .detectedFrameworks(List.of("gradle", "spring-boot"))
                .recommendedPlatform(CIPipelineSpec.CIPlatform.GITHUB_ACTIONS)
                .recommendations(
                        List.of(
                                new CIPipelineRecommendationResult.CIPipelineRecommendation(
                                        "gradle-ci",
                                        "Standard Gradle CI/CD pipeline",
                                        "Project uses Gradle build system",
                                        0.9,
                                        List.of(
                                                "Fast builds",
                                                "Parallel testing",
                                                "Dependency caching"),
                                        List.of("Requires Gradle wrapper"))))
                .platformScores(
                        Map.of(
                                "github-actions", 0.9,
                                "gitlab-ci", 0.7,
                                "azure-devops", 0.6))
                .confidenceScore(0.8)
                .build();
    }
}
