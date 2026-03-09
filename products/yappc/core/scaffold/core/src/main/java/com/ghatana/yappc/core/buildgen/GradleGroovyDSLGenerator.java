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
package com.ghatana.yappc.core.buildgen;

import com.ghatana.platform.domain.domain.Severity;
import com.ghatana.yappc.core.rca.RCAResult;
import java.time.Instant;
import java.util.*;
import io.activej.promise.Promise;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Day 28: Gradle Groovy DSL generator implementation. Template-based build
 * script generation with AI-informed optimizations.
 *
 * @doc.type class
 * @doc.purpose Day 28: Gradle Groovy DSL generator implementation.
 * Template-based build script generation with
 * @doc.layer platform
 * @doc.pattern Generator
 */
public class GradleGroovyDSLGenerator implements AIBuildScriptGenerator {

    private static final Executor BLOCKING_EXECUTOR = Executors.newCachedThreadPool();


    private final Map<String, String> templates;
    private final Map<String, List<String>> rcaOptimizations;

    public GradleGroovyDSLGenerator() {
        this.templates = initializeTemplates();
        this.rcaOptimizations = initializeRCAOptimizations();
    }

    @Override
    public Promise<GeneratedBuildScript> generateBuildScript(BuildScriptSpec spec) {
        return generateBuildScript(spec, new ArrayList<>());
    }

    @Override
    public Promise<GeneratedBuildScript> generateBuildScript(
            BuildScriptSpec spec, List<RCAResult> rcaResults) {
        return Promise.ofBlocking(BLOCKING_EXECUTOR, 
                () -> {
                    String generationId = UUID.randomUUID().toString();
                    Instant timestamp = Instant.now();

                    // Generate main build.gradle content
                    String content = generateGradleBuildScript(spec, rcaResults);

                    // Generate additional files
                    Map<String, String> additionalFiles = generateAdditionalFiles(spec);

                    // Apply optimizations based on RCA results
                    List<GeneratedBuildScript.Optimization> optimizations
                    = applyRCAOptimizations(spec, rcaResults);

                    // Generate warnings
                    List<String> warnings = generateWarnings(spec);

                    // Metadata
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("generator", "GradleGroovyDSLGenerator");
                    metadata.put("rcaResultsCount", rcaResults.size());
                    metadata.put("templateVersion", "1.0");

                    return new GeneratedBuildScript(
                            generationId,
                            timestamp,
                            spec.getBuildTool(),
                            spec.getProjectType(),
                            content,
                            additionalFiles,
                            optimizations,
                            warnings,
                            metadata);
                });
    }

    @Override
    public Promise<BuildScriptImprovement> suggestImprovements(
            String existingScript, BuildScriptSpec spec) {
        return Promise.ofBlocking(BLOCKING_EXECUTOR, 
                () -> {
                    String improvementId = UUID.randomUUID().toString();
                    Instant timestamp = Instant.now();

                    List<BuildScriptImprovement.ImprovementSuggestion> suggestions
                    = analyzeExistingScript(existingScript, spec);
                    String improvedScript = applyImprovements(existingScript, suggestions);

                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("analyzer", "GradleGroovyDSLGenerator");
                    metadata.put("suggestionsCount", suggestions.size());

                    return new BuildScriptImprovement(
                            improvementId,
                            timestamp,
                            existingScript,
                            improvedScript,
                            suggestions,
                            metadata);
                });
    }

    @Override
    public Promise<BuildScriptValidation> validateBuildScript(
            String script, BuildScriptSpec spec) {
        return Promise.ofBlocking(BLOCKING_EXECUTOR, 
                () -> {
                    String validationId = UUID.randomUUID().toString();
                    Instant timestamp = Instant.now();

                    List<BuildScriptValidation.ValidationIssue> errors = new ArrayList<>();
                    List<BuildScriptValidation.ValidationIssue> warnings = new ArrayList<>();
                    List<BuildScriptValidation.ValidationIssue> suggestions = new ArrayList<>();

                    validateSyntax(script, errors, warnings);
                    validateBestPractices(script, spec, warnings, suggestions);
                    validateDependencies(script, spec, errors, warnings);

                    boolean isValid = errors.isEmpty();

                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("validator", "GradleGroovyDSLGenerator");
                    metadata.put("linesValidated", script.split("\n").length);

                    return new BuildScriptValidation(
                            validationId,
                            timestamp,
                            isValid,
                            errors,
                            warnings,
                            suggestions,
                            metadata);
                });
    }

    @Override
    public boolean isAvailable() {
        return true; // Template-based generator is always available
    }

    @Override
    public List<String> getSupportedBuildTools() {
        return Arrays.asList("gradle");
    }

    @Override
    public List<String> getSupportedProjectTypes() {
        return Arrays.asList("java", "kotlin", "scala", "groovy");
    }

    private String generateGradleBuildScript(BuildScriptSpec spec, List<RCAResult> rcaResults) {
        StringBuilder script = new StringBuilder();

        // Header comment
        script.append("/*\n");
        script.append(" * Generated Gradle build script\n");
        script.append(" * Project: ").append(spec.getProjectType()).append("\n");
        script.append(" * Generated at: ").append(Instant.now()).append("\n");
        if (!rcaResults.isEmpty()) {
            script.append(" * RCA-informed optimizations: ")
                    .append(rcaResults.size())
                    .append(" failures analyzed\n");
        }
        script.append(" */\n\n");

        // Plugins block
        generatePluginsBlock(script, spec, rcaResults);

        // Java toolchain configuration
        generateJavaConfiguration(script, spec);

        // Repositories block
        generateRepositoriesBlock(script, spec);

        // Dependencies block
        generateDependenciesBlock(script, spec);

        // Test configuration
        generateTestConfiguration(script, spec, rcaResults);

        // Quality tools configuration
        generateQualityConfiguration(script, spec);

        // Custom tasks
        generateCustomTasks(script, spec);

        // RCA-informed configurations
        generateRCAOptimizedConfigurations(script, spec, rcaResults);

        return script.toString();
    }

    private void generatePluginsBlock(
            StringBuilder script, BuildScriptSpec spec, List<RCAResult> rcaResults) {
        script.append("plugins {\n");

        // Core plugins based on project type
        switch (spec.getProjectType()) {
            case "java" ->
                script.append("    id 'java'\n");
            case "kotlin" -> {
                script.append("    id 'java'\n");
                script.append("    id 'org.jetbrains.kotlin.jvm' version '1.9.22'\n");
            }
            case "scala" -> {
                script.append("    id 'java'\n");
                script.append("    id 'scala'\n");
            }
            case "groovy" -> {
                script.append("    id 'java'\n");
                script.append("    id 'groovy'\n");
            }
        }

        // Application plugin if main class specified
        if (spec.getMainClass() != null) {
            script.append("    id 'application'\n");
        }

        // Quality plugins
        if (spec.getQualityTools() != null) {
            for (String tool : spec.getQualityTools()) {
                switch (tool) {
                    case "spotless" ->
                        script.append("    id 'com.diffplug.spotless' version '6.23.3'\n");
                    case "jacoco" ->
                        script.append("    id 'jacoco'\n");
                    case "checkstyle" ->
                        script.append("    id 'checkstyle'\n");
                }
            }
        }

        // Additional plugins from spec
        if (spec.getPlugins() != null) {
            for (BuildScriptSpec.Plugin plugin : spec.getPlugins()) {
                if (plugin.getVersion() != null) {
                    script.append("    id '")
                            .append(plugin.getId())
                            .append("' version '")
                            .append(plugin.getVersion())
                            .append("'\n");
                } else {
                    script.append("    id '").append(plugin.getId()).append("'\n");
                }
            }
        }

        script.append("}\n\n");
    }

    private void generateJavaConfiguration(StringBuilder script, BuildScriptSpec spec) {
        if (spec.getJavaVersion() != null) {
            script.append("java {\n");
            script.append("    toolchain {\n");
            script.append("        languageVersion = JavaLanguageVersion.of(")
                    .append(spec.getJavaVersion())
                    .append(")\n");
            script.append("    }\n");
            script.append("}\n\n");
        }

        if (spec.getMainClass() != null) {
            script.append("application {\n");
            script.append("    mainClass = '").append(spec.getMainClass()).append("'\n");
            script.append("}\n\n");
        }
    }

    private void generateRepositoriesBlock(StringBuilder script, BuildScriptSpec spec) {
        script.append("repositories {\n");
        script.append("    mavenCentral()\n");

        // Add custom repositories
        if (spec.getRepositories() != null) {
            for (BuildScriptSpec.Repository repo : spec.getRepositories()) {
                if ("maven".equals(repo.getType())) {
                    script.append("    maven {\n");
                    script.append("        name = '").append(repo.getName()).append("'\n");
                    script.append("        url = '").append(repo.getUrl()).append("'\n");
                    script.append("    }\n");
                }
            }
        }

        script.append("}\n\n");
    }

    private void generateDependenciesBlock(StringBuilder script, BuildScriptSpec spec) {
        script.append("dependencies {\n");

        if (spec.getDependencies() != null) {
            for (BuildScriptSpec.Dependency dep : spec.getDependencies()) {
                script.append("    ").append(dep.getScope()).append(" '");
                script.append(dep.getGroup())
                        .append(":")
                        .append(dep.getName())
                        .append(":")
                        .append(dep.getVersion());
                script.append("'\n");
            }
        }

        // Add test dependencies based on frameworks
        if (spec.getTestFrameworks() != null) {
            for (String framework : spec.getTestFrameworks()) {
                switch (framework) {
                    case "junit5" -> {
                        script.append(
                                "    testImplementation"
                                + " 'org.junit.jupiter:junit-jupiter-api:5.10.1'\n");
                        script.append(
                                "    testRuntimeOnly"
                                + " 'org.junit.jupiter:junit-jupiter-engine:5.10.1'\n");
                    }
                    case "testng" ->
                        script.append("    testImplementation 'org.testng:testng:7.8.0'\n");
                    case "spock" -> {
                        script.append(
                                "    testImplementation"
                                + " 'org.spockframework:spock-core:2.3-groovy-4.0'\n");
                        script.append(
                                "    testImplementation 'org.apache.groovy:groovy-all:4.0.16'\n");
                    }
                }
            }
        }

        script.append("}\n\n");
    }

    private void generateTestConfiguration(
            StringBuilder script, BuildScriptSpec spec, List<RCAResult> rcaResults) {
        script.append("test {\n");

        // Test framework configuration
        if (spec.getTestFrameworks() != null && spec.getTestFrameworks().contains("junit5")) {
            script.append("    useJUnitPlatform()\n");
        }

        // RCA-informed test optimizations
        boolean hasTestFailures
                = rcaResults.stream()
                        .anyMatch(rca -> rca.getRootCause() == RCAResult.RootCause.TEST_FAILURE);

        if (hasTestFailures) {
            script.append("    // RCA-informed test configuration\n");
            script.append("    testLogging {\n");
            script.append("        events 'passed', 'skipped', 'failed'\n");
            script.append("        showStandardStreams = false\n");
            script.append("    }\n");
            script.append(
                    "    maxParallelForks = Runtime.runtime.availableProcessors().intdiv(2) ?:"
                    + " 1\n");
        }

        script.append("}\n\n");
    }

    private void generateQualityConfiguration(StringBuilder script, BuildScriptSpec spec) {
        if (spec.getQualityTools() != null) {
            for (String tool : spec.getQualityTools()) {
                switch (tool) {
                    case "spotless" -> {
                        script.append("spotless {\n");
                        script.append("    java {\n");
                        script.append("        googleJavaFormat()\n");
                        script.append("        removeUnusedImports()\n");
                        script.append("        trimTrailingWhitespace()\n");
                        script.append("        endWithNewline()\n");
                        script.append("    }\n");
                        script.append("}\n\n");
                    }
                    case "jacoco" -> {
                        script.append("jacocoTestReport {\n");
                        script.append("    reports {\n");
                        script.append("        xml.required = true\n");
                        script.append("        html.required = true\n");
                        script.append("    }\n");
                        script.append("}\n\n");
                    }
                }
            }
        }
    }

    private void generateCustomTasks(StringBuilder script, BuildScriptSpec spec) {
        if (spec.getCustomTasks() != null) {
            for (BuildScriptSpec.CustomTask task : spec.getCustomTasks()) {
                script.append("task ")
                        .append(task.getName())
                        .append("(type: ")
                        .append(task.getType())
                        .append(") {\n");
                if (task.getDescription() != null) {
                    script.append("    description = '")
                            .append(task.getDescription())
                            .append("'\n");
                }
                if (task.getGroup() != null) {
                    script.append("    group = '").append(task.getGroup()).append("'\n");
                }
                if (task.getDependsOn() != null && !task.getDependsOn().isEmpty()) {
                    script.append("    dependsOn ")
                            .append(String.join(", ", task.getDependsOn()))
                            .append("\n");
                }
                script.append("}\n\n");
            }
        }
    }

    private void generateRCAOptimizedConfigurations(
            StringBuilder script, BuildScriptSpec spec, List<RCAResult> rcaResults) {
        if (rcaResults.isEmpty()) {
            return;
        }

        script.append("// RCA-informed optimizations\n");

        boolean hasDependencyIssues
                = rcaResults.stream()
                        .anyMatch(
                                rca -> rca.getRootCause() == RCAResult.RootCause.DEPENDENCY_ISSUE);

        if (hasDependencyIssues) {
            script.append("configurations.all {\n");
            script.append("    resolutionStrategy {\n");
            script.append("        cacheDynamicVersionsFor 10, 'minutes'\n");
            script.append("        cacheChangingModulesFor 4, 'hours'\n");
            script.append("    }\n");
            script.append("}\n\n");
        }

        boolean hasResourceIssues
                = rcaResults.stream()
                        .anyMatch(
                                rca
                                -> rca.getRootCause()
                                == RCAResult.RootCause.RESOURCE_EXHAUSTION);

        if (hasResourceIssues) {
            script.append("gradle.projectsEvaluated {\n");
            script.append("    tasks.withType(JavaCompile) {\n");
            script.append("        options.fork = true\n");
            script.append("        options.forkOptions.jvmArgs = ['-Xmx1g']\n");
            script.append("    }\n");
            script.append("}\n\n");
        }
    }

    private Map<String, String> generateAdditionalFiles(BuildScriptSpec spec) {
        Map<String, String> files = new HashMap<>();

        // gradle.properties
        StringBuilder gradleProps = new StringBuilder();
        gradleProps.append("# Gradle properties\n");
        gradleProps.append("org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8\n");
        gradleProps.append("org.gradle.parallel=true\n");
        gradleProps.append("org.gradle.caching=true\n");
        gradleProps.append("org.gradle.configuration-cache=true\n");
        files.put("gradle.properties", gradleProps.toString());

        // gradlew wrapper (properties reference)
        files.put(
                "gradle/wrapper/gradle-wrapper.properties",
                "distributionBase=GRADLE_USER_HOME\n"
                + "distributionPath=wrapper/dists\n"
                + "distributionUrl=https\\://services.gradle.org/distributions/gradle-8.14-bin.zip\n"
                + "zipStoreBase=GRADLE_USER_HOME\n"
                + "zipStorePath=wrapper/dists\n");

        return files;
    }

    private List<GeneratedBuildScript.Optimization> applyRCAOptimizations(
            BuildScriptSpec spec, List<RCAResult> rcaResults) {
        List<GeneratedBuildScript.Optimization> optimizations = new ArrayList<>();

        for (RCAResult rca : rcaResults) {
            switch (rca.getRootCause()) {
                case DEPENDENCY_ISSUE ->
                    optimizations.add(
                            new GeneratedBuildScript.Optimization(
                                    "dependency-resolution",
                                    "Added dependency resolution caching",
                                    "performance",
                                    "Configured resolution strategy to cache dynamic versions"
                                    + " and changing modules"));
                case RESOURCE_EXHAUSTION ->
                    optimizations.add(
                            new GeneratedBuildScript.Optimization(
                                    "memory-optimization",
                                    "Increased compiler memory allocation",
                                    "reliability",
                                    "Set JVM heap size for compilation tasks to prevent"
                                    + " out-of-memory errors"));
                case TEST_FAILURE ->
                    optimizations.add(
                            new GeneratedBuildScript.Optimization(
                                    "test-configuration",
                                    "Enhanced test logging and parallel execution",
                                    "maintainability",
                                    "Improved test output visibility and performance"));
            }
        }

        return optimizations;
    }

    private List<String> generateWarnings(BuildScriptSpec spec) {
        List<String> warnings = new ArrayList<>();

        if (spec.getJavaVersion() == null) {
            warnings.add("No Java version specified - using system default");
        }

        if (spec.getDependencies() == null || spec.getDependencies().isEmpty()) {
            warnings.add("No dependencies specified - consider adding required libraries");
        }

        if (spec.getTestFrameworks() == null || spec.getTestFrameworks().isEmpty()) {
            warnings.add("No test framework specified - consider adding JUnit 5 or TestNG");
        }

        return warnings;
    }

    private List<BuildScriptImprovement.ImprovementSuggestion> analyzeExistingScript(
            String script, BuildScriptSpec spec) {
        List<BuildScriptImprovement.ImprovementSuggestion> suggestions = new ArrayList<>();

        if (!script.contains("toolchain")) {
            suggestions.add(
                    new BuildScriptImprovement.ImprovementSuggestion(
                            "java-configuration",
                            "Use Java Toolchain",
                            "Configure Java toolchain for better version management",
                            BuildScriptImprovement.Priority.HIGH,
                            "maintainability",
                            BuildScriptImprovement.EstimatedEffort.SMALL));
        }

        if (!script.contains("useJUnitPlatform")) {
            suggestions.add(
                    new BuildScriptImprovement.ImprovementSuggestion(
                            "test-configuration",
                            "Enable JUnit Platform",
                            "Add useJUnitPlatform() to test configuration",
                            BuildScriptImprovement.Priority.MEDIUM,
                            "functionality",
                            BuildScriptImprovement.EstimatedEffort.SMALL));
        }

        return suggestions;
    }

    private String applyImprovements(
            String script, List<BuildScriptImprovement.ImprovementSuggestion> suggestions) {
        // For now, return the original script
        // In a real implementation, this would apply the suggested improvements
        return script;
    }

    private void validateSyntax(
            String script,
            List<BuildScriptValidation.ValidationIssue> errors,
            List<BuildScriptValidation.ValidationIssue> warnings) {
        String[] lines = script.split("\n");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();

            // Check for common syntax issues
            if (line.contains("implementation(") && !line.contains(")")) {
                errors.add(
                        new BuildScriptValidation.ValidationIssue(
                                "syntax",
                                "Unclosed parenthesis in dependency declaration",
                                i + 1,
                                null,
                                Severity.ERROR,
                                "SYNTAX_001"));
            }

            if (line.startsWith("compile ")) {
                warnings.add(
                        new BuildScriptValidation.ValidationIssue(
                                "deprecation",
                                "Use 'implementation' instead of deprecated 'compile'",
                                i + 1,
                                null,
                                Severity.WARNING,
                                "DEPRECATION_001"));
            }
        }
    }

    private void validateBestPractices(
            String script,
            BuildScriptSpec spec,
            List<BuildScriptValidation.ValidationIssue> warnings,
            List<BuildScriptValidation.ValidationIssue> suggestions) {
        if (!script.contains("toolchain")) {
            suggestions.add(
                    new BuildScriptValidation.ValidationIssue(
                            "best-practice",
                            "Consider using Java toolchain for better version management",
                            null,
                            null,
                            Severity.INFO,
                            "BEST_PRACTICE_001"));
        }
    }

    private void validateDependencies(
            String script,
            BuildScriptSpec spec,
            List<BuildScriptValidation.ValidationIssue> errors,
            List<BuildScriptValidation.ValidationIssue> warnings) {
        if (spec.getDependencies() != null) {
            for (BuildScriptSpec.Dependency dep : spec.getDependencies()) {
                if (dep.getVersion().contains("+")) {
                    warnings.add(
                            new BuildScriptValidation.ValidationIssue(
                                    "dependency",
                                    "Dynamic version '"
                                    + dep.getVersion()
                                    + "' may cause build reproducibility issues",
                                    null,
                                    null,
                                    Severity.WARNING,
                                    "DEPENDENCY_001"));
                }
            }
        }
    }

    private Map<String, String> initializeTemplates() {
        // Template initialization would go here
        return new HashMap<>();
    }

    private Map<String, List<String>> initializeRCAOptimizations() {
        Map<String, List<String>> optimizations = new HashMap<>();

        optimizations.put(
                "DEPENDENCY_ISSUE",
                Arrays.asList(
                        "Add dependency resolution strategy",
                        "Configure repository order",
                        "Enable dependency verification"));

        optimizations.put(
                "RESOURCE_EXHAUSTION",
                Arrays.asList(
                        "Increase JVM heap size",
                        "Enable parallel execution",
                        "Configure build cache"));

        optimizations.put(
                "TEST_FAILURE",
                Arrays.asList(
                        "Add test logging configuration",
                        "Configure test parallelization",
                        "Add test retry mechanism"));

        return optimizations;
    }
}
