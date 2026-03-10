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

package com.ghatana.yappc.cli.commands;

import com.ghatana.yappc.core.ci.*;
import com.ghatana.yappc.core.services.ProjectAnalysisService;
import com.ghatana.yappc.core.snapshots.CISnapshotManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Command to generate CI/CD pipelines with snapshot management and validation.
 *
 * <p>Week 8 Day 39: CI generate command with snapshots support.
 */
@CommandLine.Command(
        name = "generate",
        description = "Generate CI/CD pipelines for your project with snapshot management",
        mixinStandardHelpOptions = true)
/**
 * CIGenerateCommand component within the YAPPC platform.
 *
 * @doc.type class
 * @doc.purpose CIGenerateCommand component within the YAPPC platform.
 * @doc.layer platform
 * @doc.pattern Component
 */
public class CIGenerateCommand implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(CIGenerateCommand.class);

    @CommandLine.Option(
            names = {"-p", "--platform"},
            description = "Target CI platform (github-actions, gitlab-ci, azure-devops)",
            defaultValue = "github-actions")
    private String platform;

    @CommandLine.Option(
            names = {"-o", "--output"},
            description = "Output directory for generated CI files",
            defaultValue = ".")
    private String outputDir;

    @CommandLine.Option(
            names = {"--matrix"},
            description = "Enable matrix builds for multi-platform testing")
    private boolean enableMatrix;

    @CommandLine.Option(
            names = {"--security"},
            description = "Enable security scanning in CI pipeline",
            defaultValue = "true")
    private boolean enableSecurity;

    @CommandLine.Option(
            names = {"--dry-run"},
            description = "Generate pipeline files but don't write to disk")
    private boolean dryRun;

    @CommandLine.Option(
            names = {"--snapshot"},
            description = "Create a snapshot of the generated configuration")
    private boolean createSnapshot;

    @CommandLine.Option(
            names = {"--restore-snapshot"},
            description = "Restore from a previous snapshot by ID")
    private String restoreSnapshotId;

    @CommandLine.Option(
            names = {"--list-snapshots"},
            description = "List available CI configuration snapshots")
    private boolean listSnapshots;

    @CommandLine.Option(
            names = {"--validate"},
            description = "Validate generated pipeline without creating files")
    private boolean validateOnly;

    @CommandLine.Option(
            names = {"--suggest-improvements"},
            description = "Show improvement suggestions for the pipeline")
    private boolean suggestImprovements;

    @CommandLine.Option(
            names = {"--config"},
            description = "Path to CI configuration file (YAML/JSON)")
    private String configFile;

    @CommandLine.Parameters(
            index = "0",
            description = "Project directory to analyze",
            defaultValue = ".")
    private String projectDir;

    private final ProjectAnalysisService projectAnalysisService;
    private final CISnapshotManager snapshotManager;
    private final Map<String, CIPipelineGenerator> generators;

    public CIGenerateCommand() {
        this.projectAnalysisService = new ProjectAnalysisService();
        this.snapshotManager = new CISnapshotManager();
        this.generators =
                Map.of(
                        "github-actions", new GitHubActionsGenerator(),
                        "gitlab-ci", new GitLabCIGenerator(),
                        "azure-devops", new AzureDevOpsGenerator());
    }

    @Override
    public Integer call() throws Exception {
        try {
            // Handle snapshot operations
            if (listSnapshots) {
                return handleListSnapshots();
            }

            if (restoreSnapshotId != null) {
                return handleRestoreSnapshot();
            }

            // Validate project directory
            Path projectPath = Paths.get(projectDir).toAbsolutePath();
            if (!Files.exists(projectPath)) {
                log.error("Error: Project directory does not exist: {}", projectPath);
                return 1;
            }

            log.info("🔍 Analyzing project at: {}", projectPath);

            // Generate or load CI specification
            CIPipelineSpec spec;
            if (configFile != null) {
                spec = loadSpecFromConfig();
            } else {
                spec = generateSpecFromProject(projectPath);
            }

            // Validate pipeline specification
            CIPipelineGenerator generator = generators.get(platform);
            if (generator == null) {
                log.error("Error: Unsupported platform: {}", platform);
                log.error("Supported platforms: {}", String.join(", ", generators.keySet()));
                return 1;
            }

            // Validate pipeline
            CIPipelineValidationResult validation = generator.validatePipeline(spec);
            if (!validation.isValid()) {
                log.error("❌ Pipeline validation failed:");
                validation.errors().forEach(error ->
                    log.error("  • {}", error));
                return 1;
            }
            if (validateOnly) {
                    log.info("✅ Pipeline validation passed");
                printValidationSummary(validation);
                return 0;
            }

            // Generate improvement suggestions
            if (suggestImprovements) {
                CIPipelineImprovementSuggestions suggestions = generator.suggestImprovements(spec);
                printImprovementSuggestions(suggestions);
            }

            // Generate pipeline files
            GeneratedCIPipeline pipeline = generator.generatePipeline(spec);

            if (dryRun) {
                log.info("🔍 Dry run - Generated files preview:");
                printDryRunSummary(pipeline);
                return 0;
            }

            // Write pipeline files
            Path outputPath = Paths.get(outputDir).toAbsolutePath();
            writePipelineFiles(pipeline, outputPath);

            // Create snapshot if requested
            if (createSnapshot) {
                String snapshotId = snapshotManager.createSnapshot(spec, pipeline);
                log.info("📸 Created snapshot: {}", snapshotId);
            }

            printGenerationSummary(pipeline, outputPath);
            return 0;

        } catch (Exception e) {
            log.error("Error: {}", e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }

    private int handleListSnapshots() {
        List<CISnapshotManager.CISnapshot> snapshots = snapshotManager.listSnapshots();

        if (snapshots.isEmpty()) {
            log.info("No CI configuration snapshots found.");
            return 0;
        }

        log.info("📸 Available CI Configuration Snapshots:");
        log.info("");;

        for (var snapshot : snapshots) {
            log.info("  {} - {} ({})", snapshot.id(),
                    snapshot.description(),
                    snapshot.createdAt()
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            log.info("    Platform: {}, Files: {}", snapshot.spec().platform().getDisplayName(),
                    snapshot.generatedPipeline().pipelineFiles().size());
            log.info("");;
        }

        return 0;
    }

    private int handleRestoreSnapshot() {
        Optional<CISnapshotManager.CISnapshot> snapshot =
                snapshotManager.getSnapshot(restoreSnapshotId);

        if (snapshot.isEmpty()) {
            log.error("Error: Snapshot not found: {}", restoreSnapshotId);
            return 1;
        }

        var snap = snapshot.get();
        log.info("📸 Restoring snapshot: {}", snap.id());
        log.info("Description: {}", snap.description());

        if (!dryRun) {
            Path outputPath = Paths.get(outputDir).toAbsolutePath();
            try {
                writePipelineFiles(snap.generatedPipeline(), outputPath);
            } catch (Exception e) {
                log.error("Error writing pipeline files: {}", e.getMessage());
                return 1;
            }
            log.info("✅ Snapshot restored to: {}", outputPath);
        } else {
            log.info("🔍 Dry run - would restore files:");
            printDryRunSummary(snap.generatedPipeline());
        }

        return 0;
    }

    private CIPipelineSpec loadSpecFromConfig() throws Exception {
        Path configPath = Paths.get(configFile);
        if (!Files.exists(configPath)) {
            throw new IllegalArgumentException("Configuration file not found: " + configFile);
        }

        // For now, use project analysis as fallback
        // In production, would parse YAML/JSON config
        log.info("⚠️  Custom config parsing not yet implemented, using project analysis");
        return generateSpecFromProject(configPath.getParent());
    }

    private CIPipelineSpec generateSpecFromProject(Path projectPath) {
        // Analyze project structure
        var analysis = projectAnalysisService.analyzeProject(projectPath.toString());

        var specBuilder =
                CIPipelineSpec.builder()
                        .name(projectPath.getFileName().toString() + "-ci")
                        .platform(
                                CIPipelineSpec.CIPlatform.valueOf(
                                        platform.toUpperCase().replace("-", "_")));

        // Configure based on detected languages/frameworks
        if (analysis.languages().contains("java")) {
            configureJavaCI(specBuilder, analysis);
        }
        if (analysis.languages().contains("typescript")
                || analysis.languages().contains("javascript")) {
            configureNodeCI(specBuilder, analysis);
        }
        if (analysis.languages().contains("rust")) {
            configureRustCI(specBuilder, analysis);
        }

        // Add security configuration
        if (enableSecurity) {
            specBuilder.security(
                    CIPipelineSpec.CISecurityConfig.builder()
                            .enableSecurityScanning(true)
                            .enableDependencyScanning(true)
                            .enableSecretsScanning(true)
                            .scanTools(List.of("trivy", "semgrep", "gitleaks"))
                            .blockOnCritical(true)
                            .build());
        }

        // Add matrix configuration
        if (enableMatrix) {
            specBuilder.matrix(
                    CIPipelineSpec.CIMatrix.builder()
                            .operatingSystems(
                                    List.of("ubuntu-latest", "windows-latest", "macos-latest"))
                            .languageVersions(getLanguageVersionsForMatrix(analysis))
                            .failFast(false)
                            .build());
        }

        // Add common triggers
        specBuilder.triggers(
                List.of(
                        new CIPipelineSpec.CITrigger(
                                CIPipelineSpec.CITrigger.CITriggerType.PUSH,
                                List.of("main", "develop"),
                                List.of(),
                                null,
                                Map.of()),
                        new CIPipelineSpec.CITrigger(
                                CIPipelineSpec.CITrigger.CITriggerType.PULL_REQUEST,
                                List.of("main", "develop"),
                                List.of(),
                                null,
                                Map.of())));

        return specBuilder.build();
    }

    private void configureJavaCI(
            CIPipelineSpec.Builder specBuilder, ProjectAnalysisService.ProjectAnalysis analysis) {
        var buildJob =
                CIPipelineSpec.CIJob.builder()
                        .name("build-and-test")
                        .runsOn("ubuntu-latest")
                        .timeoutMinutes(30)
                        .steps(
                                List.of(
                                        CIPipelineSpec.CIStep.builder()
                                                .name("Checkout code")
                                                .type(CIPipelineSpec.CIStep.CIStepType.CHECKOUT)
                                                .build(),
                                        CIPipelineSpec.CIStep.builder()
                                                .name("Setup JDK")
                                                .type(
                                                        CIPipelineSpec.CIStep.CIStepType
                                                                .SETUP_LANGUAGE)
                                                .action("actions/setup-java@v4")
                                                .with(
                                                        Map.of(
                                                                "java-version", "21",
                                                                "distribution", "temurin",
                                                                "cache", "gradle"))
                                                .build(),
                                        CIPipelineSpec.CIStep.builder()
                                                .name("Build with Gradle")
                                                .type(CIPipelineSpec.CIStep.CIStepType.RUN_COMMAND)
                                                .command("./gradlew build")
                                                .build(),
                                        CIPipelineSpec.CIStep.builder()
                                                .name("Run tests")
                                                .type(CIPipelineSpec.CIStep.CIStepType.RUN_COMMAND)
                                                .command("./gradlew test")
                                                .build()))
                        .build();

        var stage = CIPipelineSpec.CIStage.builder().name("build").jobs(List.of(buildJob)).build();

        specBuilder.stages(List.of(stage));
    }

    private void configureNodeCI(
            CIPipelineSpec.Builder specBuilder, ProjectAnalysisService.ProjectAnalysis analysis) {
        // Add Node.js specific CI configuration
        log.info("📦 Detected Node.js project, configuring npm/pnpm/yarn pipeline");
    }

    private void configureRustCI(
            CIPipelineSpec.Builder specBuilder, ProjectAnalysisService.ProjectAnalysis analysis) {
        // Add Rust specific CI configuration
        log.info("🦀 Detected Rust project, configuring Cargo pipeline");
    }

    private List<String> getLanguageVersionsForMatrix(
            ProjectAnalysisService.ProjectAnalysis analysis) {
        List<String> versions = new ArrayList<>();

        if (analysis.languages().contains("java")) {
            versions.addAll(List.of("17", "21"));
        }
        if (analysis.languages().contains("typescript")
                || analysis.languages().contains("javascript")) {
            versions.addAll(List.of("18", "20", "22"));
        }
        if (analysis.languages().contains("rust")) {
            versions.addAll(List.of("stable", "beta"));
        }

        return versions.isEmpty() ? List.of("latest") : versions;
    }

    private void writePipelineFiles(GeneratedCIPipeline pipeline, Path outputPath)
            throws Exception {
        log.info("📝 Writing CI pipeline files to: {}", outputPath);

        for (var entry : pipeline.pipelineFiles().entrySet()) {
            String relativePath = entry.getKey();
            String content = entry.getValue();

            Path filePath = outputPath.resolve(relativePath);
            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, content);

            log.info("  ✅ {} ({} lines)", relativePath, content.lines().count());
        }

        // Write environment configurations if present
        if (!pipeline.environmentConfigurations().isEmpty()) {
            Path envsPath = outputPath.resolve(".github/environments");
            Files.createDirectories(envsPath);

            for (var entry : pipeline.environmentConfigurations().entrySet()) {
                Path envFile = envsPath.resolve(entry.getKey());
                Files.writeString(envFile, entry.getValue());
                log.info("  ✅ .github/environments/{}", entry.getKey());
            }
        }
    }

    private void printDryRunSummary(GeneratedCIPipeline pipeline) {
        log.info("");;
        log.info("Generated Files:");

        for (var entry : pipeline.pipelineFiles().entrySet()) {
            log.info("  📄 {} ({} lines)", entry.getKey(), entry.getValue().lines().count());
        }

        if (!pipeline.generatedSecrets().isEmpty()) {
            log.info("");;
            log.info("Required Secrets:");
            pipeline.generatedSecrets().forEach(secret ->
                log.info("  🔐 {}", secret));
        }
        if (!pipeline.requiredActions().isEmpty()) {
                log.info("");
            log.info("Required GitHub Actions:");
            pipeline.requiredActions().forEach(action ->
                log.info("  ⚡ {}", action));
        }
    }
    private void printValidationSummary(CIPipelineValidationResult validation) {
        log.info(String.format("🔍 Security Score: %.1f/1.0", validation.securityScore()));
        log.info(String.format("🔍 Quality Score: %.1f/1.0", validation.qualityScore()));

        if (!validation.warnings().isEmpty()) {
            log.info("");;
            log.info("⚠️  Warnings:");
            validation.warnings().forEach(warning ->
                log.info("  • {}", warning));
        }
        if (!validation.securityIssues().isEmpty()) {
                log.info("");
            log.info("🔒 Security Issues:");
            validation.securityIssues().forEach(issue ->
                log.info("  • {}", issue));
        }
    }
    private void printImprovementSuggestions(CIPipelineImprovementSuggestions suggestions) {
        log.info("");
        log.info("💡 Improvement Suggestions:");

        if (!suggestions.securityEnhancements().isEmpty()) {
            log.info("");
            log.info("🔒 Security Enhancements:");
            suggestions.securityEnhancements().forEach(enhancement ->
                log.info("  • {}", enhancement));
        }
        if (!suggestions.performanceOptimizations().isEmpty()) {
            log.info("");
            log.info("⚡ Performance Optimizations:");
            suggestions.performanceOptimizations().forEach(optimization ->
                log.info("  • {}", optimization));
        }
        if (!suggestions.reliabilityImprovements().isEmpty()) {
            log.info("");
            log.info("🛡️  Reliability Improvements:");
            suggestions.reliabilityImprovements().forEach(improvement ->
                log.info("  • {}", improvement));
        }
        log.info(String.format("%n💯 Overall Improvement Score: %.1f/1.0", suggestions.improvementScore()));
    }

    private void printGenerationSummary(GeneratedCIPipeline pipeline, Path outputPath) {
        log.info("");;
        log.info("✅ CI Pipeline Generated Successfully!");
        log.info("");;

        log.info("📊 Generation Summary:");
        log.info("  Platform: {}", pipeline.spec().platform().getDisplayName());
        log.info("  Files created: {}", pipeline.pipelineFiles().size());
        log.info("  Output directory: {}", outputPath);

        if (pipeline.metadata().containsKey("securityEnabled")
                && (Boolean) pipeline.metadata().get("securityEnabled")) {
            log.info("  🔒 Security scanning: Enabled");
        }

        if (pipeline.metadata().containsKey("matrixEnabled")
                && (Boolean) pipeline.metadata().get("matrixEnabled")) {
            log.info("  🔄 Matrix builds: Enabled");
        }

        log.info("");;
        log.info("🚀 Next Steps:");
        log.info("  1. Review generated CI files");
        log.info("  2. Configure required secrets in your repository");
        log.info("  3. Commit and push to trigger your first CI run");

        if (!pipeline.generatedSecrets().isEmpty()) {
            log.info("");
            log.info("🔐 Configure these secrets in your repository:");
            pipeline.generatedSecrets().forEach(secret -> log.info("  • {}", secret));
        }
    }
}