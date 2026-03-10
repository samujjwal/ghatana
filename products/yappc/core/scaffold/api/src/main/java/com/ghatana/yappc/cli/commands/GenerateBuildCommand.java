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

package com.ghatana.yappc.cli.commands;

import com.ghatana.yappc.core.buildgen.*;
import io.activej.eventloop.Eventloop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Day 28: CLI command for AI-powered build script generation */
@Command(
        name = "generate-build",
        description = "Generate optimized build scripts using AI analysis and best practices")
/**
 * GenerateBuildCommand component within the YAPPC platform.
 *
 * @doc.type class
 * @doc.purpose GenerateBuildCommand component within the YAPPC platform.
 * @doc.layer platform
 * @doc.pattern Component
 */
public class GenerateBuildCommand implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(GenerateBuildCommand.class);

    @Parameters(index = "0", description = "Target directory for generated build script")
    private String targetDirectory = ".";

    @Option(
            names = {"-t", "--type"},
            description = "Project type: java, kotlin, scala, groovy",
            defaultValue = "java")
    private String projectType;

    @Option(
            names = {"-b", "--build-tool"},
            description = "Build tool: gradle, maven",
            defaultValue = "gradle")
    private String buildTool;

    @Option(
            names = {"-j", "--java-version"},
            description = "Java version: 11, 17, 21",
            defaultValue = "21")
    private String javaVersion;

    @Option(
            names = {"-d", "--dependencies"},
            description = "Dependencies in group:name:version format",
            split = ",")
    private List<String> dependencies = new ArrayList<>();

    @Option(
            names = {"-p", "--plugins"},
            description = "Plugins in id:version format",
            split = ",")
    private List<String> plugins = new ArrayList<>();

    @Option(
            names = {"--test-framework"},
            description = "Test frameworks: junit5, testng, spock",
            split = ",")
    private List<String> testFrameworks = Arrays.asList("junit5");

    @Option(
            names = {"--quality-tools"},
            description = "Quality tools: spotless, jacoco, checkstyle",
            split = ",")
    private List<String> qualityTools = new ArrayList<>();

    @Option(
            names = {"-m", "--main-class"},
            description = "Main class for application projects")
    private String mainClass;

    @Option(
            names = {"--rca-log"},
            description = "Path to previous build failure log for RCA-informed optimizations")
    private String rcaLogPath;

    @Option(
            names = {"-f", "--force"},
            description = "Overwrite existing build files")
    private boolean force;

    @Option(
            names = {"-v", "--verbose"},
            description = "Show detailed generation information")
    private boolean verbose;

    @Option(
            names = {"--validate"},
            description = "Validate generated build script")
    private boolean validate;

    @Option(
            names = {"--dry-run"},
            description = "Generate script but don't write to files")
    private boolean dryRun;

    private AIBuildScriptGenerator generator;

    public GenerateBuildCommand() {
        // For now, using the Gradle generator directly
        // In production, this would use a service locator or dependency injection
        this.generator = new GradleGroovyDSLGenerator();
    }

    @Override
    public Integer call() throws Exception {
        try {
            // Validate inputs
            if (!generator.getSupportedBuildTools().contains(buildTool)) {
                log.error("Error: Unsupported build tool: {}", buildTool);
                log.error("Supported tools: {}", generator.getSupportedBuildTools());
                return 1;
            }

            if (!generator.getSupportedProjectTypes().contains(projectType)) {
                log.error("Error: Unsupported project type: {}", projectType);
                log.error("Supported types: {}", generator.getSupportedProjectTypes());
                return 1;
            }

            // Check target directory
            Path targetPath = Paths.get(targetDirectory);
            if (!Files.exists(targetPath)) {
                Files.createDirectories(targetPath);
            }

            // Check for existing build files
            Path buildFile = targetPath.resolve("build.gradle");
            if (Files.exists(buildFile) && !force) {
                log.error("Error: build.gradle already exists. Use -f/--force to overwrite.");
                return 1;
            }

            // Create build specification
            BuildScriptSpec spec = createBuildSpec();

            // Load RCA results if provided
            List<com.ghatana.yappc.core.rca.RCAResult> rcaResults = loadRCAResults();

            log.info("🔨 Generating {} build script for {} project...", buildTool, projectType);
            if (verbose) {
                log.info("Target directory: {}", targetPath.toAbsolutePath());
                log.info("Java version: {}", javaVersion);
                log.info("Dependencies: {}", dependencies.size());
                log.info("RCA optimizations: {}", rcaResults.size());
            }

            // Generate build script
            AtomicReference<GeneratedBuildScript> resultRef = new AtomicReference<>();
            Eventloop el1 = Eventloop.create();
            el1.post(() -> generator.generateBuildScript(spec, rcaResults).whenResult(resultRef::set));
            el1.run();
            GeneratedBuildScript result = resultRef.get();

            // Validate if requested
            if (validate) {
                log.info("🔍 Validating generated build script...");
                AtomicReference<BuildScriptValidation> valRef = new AtomicReference<>();
                Eventloop el2 = Eventloop.create();
                el2.post(() -> generator.validateBuildScript(result.getContent(), spec).whenResult(valRef::set));
                el2.run();
                BuildScriptValidation validation = valRef.get();

                if (!validation.isValid()) {
                    log.error("❌ Validation failed with {} errors", validation.getErrors().size());
                    for (BuildScriptValidation.ValidationIssue error : validation.getErrors()) {
                        log.error("  • {}", error.getMessage());
                    }
                    return 1;
                }

                if (!validation.getWarnings().isEmpty()) {
                    log.info("⚠️  Validation warnings:");
                    for (BuildScriptValidation.ValidationIssue warning : validation.getWarnings()) {
                        log.info("  • {}", warning.getMessage());
                    }
                }
            }

            // Output results
            if (dryRun) {
                outputDryRun(result);
            } else {
                writeFiles(result, targetPath);
                outputSummary(result, targetPath);
            }

            return 0;

        } catch (Exception e) {
            log.error("Error generating build script: {}", e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
            return 1;
        }
    }

    private BuildScriptSpec createBuildSpec() {
        BuildScriptSpec.Builder builder =
                BuildScriptSpec.builder()
                        .projectType(projectType)
                        .buildTool(buildTool)
                        .javaVersion(javaVersion)
                        .testFrameworks(testFrameworks)
                        .qualityTools(qualityTools)
                        .mainClass(mainClass);

        // Parse dependencies
        if (!dependencies.isEmpty()) {
            List<BuildScriptSpec.Dependency> deps = new ArrayList<>();
            for (String dep : dependencies) {
                String[] parts = dep.split(":");
                if (parts.length >= 3) {
                    deps.add(
                            new BuildScriptSpec.Dependency(
                                    parts[0], parts[1], parts[2], "implementation"));
                } else {
                    log.warn("Warning: Invalid dependency format: {} (expected group:name:version)", dep);
                }
            }
            builder.dependencies(deps);
        }

        // Parse plugins
        if (!plugins.isEmpty()) {
            List<BuildScriptSpec.Plugin> pluginList = new ArrayList<>();
            for (String plugin : plugins) {
                String[] parts = plugin.split(":");
                if (parts.length >= 2) {
                    pluginList.add(new BuildScriptSpec.Plugin(parts[0], parts[1]));
                } else {
                    pluginList.add(new BuildScriptSpec.Plugin(parts[0], null));
                }
            }
            builder.plugins(pluginList);
        }

        return builder.build();
    }

    private List<com.ghatana.yappc.core.rca.RCAResult> loadRCAResults() {
        // For now, return empty list
        // In production, this would parse RCA results from the log file
        // and integrate with the RCA system from Day 27
        return new ArrayList<>();
    }

    private void outputDryRun(GeneratedBuildScript result) {
        log.info("📄 Generated build script (dry-run mode):");
        log.info("=".repeat(60));
        log.info("{}", result.getContent());
        log.info("=".repeat(60));

        if (!result.getAdditionalFiles().isEmpty()) {
            log.info("\n📁 Additional files:");
            for (Map.Entry<String, String> entry : result.getAdditionalFiles().entrySet()) {
                log.info("\n{}:", entry.getKey());
                log.info("-".repeat(30));
                log.info("{}", entry.getValue());
            }
        }

        if (!result.getOptimizations().isEmpty()) {
            log.info("\n🚀 Applied optimizations:");
            for (GeneratedBuildScript.Optimization opt : result.getOptimizations()) {
                log.info("  • {} ({})", opt.getDescription(), opt.getImpact());
            }
        }

        if (!result.getWarnings().isEmpty()) {
            log.info("\n⚠️  Warnings:");
            for (String warning : result.getWarnings()) {
                log.info("  • {}", warning);
            }
        }
    }

    private void writeFiles(GeneratedBuildScript result, Path targetPath) throws IOException {
        // Write main build script
        Path buildFile = targetPath.resolve("build.gradle");
        Files.writeString(buildFile, result.getContent());

        // Write additional files
        for (Map.Entry<String, String> entry : result.getAdditionalFiles().entrySet()) {
            Path filePath = targetPath.resolve(entry.getKey());
            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, entry.getValue());
        }
    }

    private void outputSummary(GeneratedBuildScript result, Path targetPath) {
        log.info("✅ Build script generation completed!");
        log.info("");;
        log.info("📋 Generation Summary:");
        log.info("  • Build tool: {}", result.getBuildTool());
        log.info("  • Project type: {}", result.getProjectType());
        log.info("  • Generation ID: {}", result.getGenerationId());
        log.info("  • Files created: {}", (1 + result.getAdditionalFiles().size()));

        log.info("\n📁 Files created:");
        log.info("  • {}", targetPath.resolve("build.gradle").toAbsolutePath());
        for (String fileName : result.getAdditionalFiles().keySet()) {
            log.info("  • {}", targetPath.resolve(fileName).toAbsolutePath());
        }

        if (!result.getOptimizations().isEmpty()) {
            log.info("\n🚀 Applied optimizations:");
            for (GeneratedBuildScript.Optimization opt : result.getOptimizations()) {
                log.info("  • {} ({})", opt.getDescription(), opt.getImpact());
                if (verbose) {
                    log.info("    {}", opt.getDetails());
                }
            }
        }

        if (!result.getWarnings().isEmpty()) {
            log.info("\n⚠️  Warnings:");
            for (String warning : result.getWarnings()) {
                log.info("  • {}", warning);
            }
        }

        log.info("\n🏃 Next steps:");
        log.info("  1. Review the generated build.gradle file");
        log.info("  2. Run: ./gradlew build");
        log.info("  3. Add your source code to src/main/{}", projectType);
        if (!result.getWarnings().isEmpty()) {
            log.info("  4. Address any warnings shown above");
        }
    }
}
