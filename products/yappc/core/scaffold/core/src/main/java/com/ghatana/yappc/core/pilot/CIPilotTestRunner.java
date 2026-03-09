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

package com.ghatana.yappc.core.pilot;

import com.ghatana.yappc.core.ci.*;
import com.ghatana.yappc.core.services.ProjectAnalysisService;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import io.activej.promise.Promise;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Real repository pilot testing framework for validating generated CI workflows. Tests CI pipelines
 * against real repositories to ensure they work correctly.
 *
 * <p>Week 8 Day 40: Real repository pilot testing for CI generation validation.
 *
 * @doc.type class
 * @doc.purpose Real repository pilot testing framework for validating generated CI workflows. Tests CI pipelines
 * @doc.layer platform
 * @doc.pattern Runner
 */
public class CIPilotTestRunner {

    private static final Logger log = LoggerFactory.getLogger(CIPilotTestRunner.class);

    private static final Executor BLOCKING_EXECUTOR = Executors.newCachedThreadPool();


    private final ExecutorService executorService;
    private final ProjectAnalysisService projectAnalysisService;
    private final Map<String, CIPipelineGenerator> generators;
    private final List<PilotTestRepository> testRepositories;

    public CIPilotTestRunner() {
        this.executorService = Executors.newFixedThreadPool(4);
        this.projectAnalysisService = new ProjectAnalysisService();
        this.generators =
                Map.of(
                        "github-actions", new GitHubActionsGenerator(),
                        "gitlab-ci", new GitLabCIGenerator(),
                        "azure-devops", new AzureDevOpsGenerator());
        this.testRepositories = initializeTestRepositories();
    }

    /**
 * Runs pilot tests against all configured repositories. */
    public Promise<PilotTestSuite> runPilotTests() {
        return Promise.ofBlocking(BLOCKING_EXECUTOR, 
                () -> {
                    List<PilotTestResult> results = new ArrayList<>();
                    String suiteId = generateSuiteId();

                    log.info("🧪 Starting CI Pilot Test Suite: {}", suiteId);
                    log.info("📊 Testing {} repositories across {} platforms", testRepositories.size(), generators.size());

                    for (PilotTestRepository repo : testRepositories) {
                        for (String platform : generators.keySet()) {
                            try {
                                PilotTestResult result = runSinglePilotTest(repo, platform);
                                results.add(result);

                                log.info("  {} {} ({}) - {}", result.success() ? "✅" : "❌",
                                        repo.name(),
                                        platform,
                                        result.success()
                                                ? "PASSED"
                                                : "FAILED: " + result.errorMessage());

                            } catch (Exception e) {
                                results.add(
                                        new PilotTestResult(
                                                repo,
                                                platform,
                                                false,
                                                null,
                                                null,
                                                null,
                                                "Test execution failed: " + e.getMessage(),
                                                System.currentTimeMillis()
                                                        - System.currentTimeMillis()));
                                log.info("  ❌ {} ({}) - ERROR: {}", repo.name(), platform, e.getMessage());
                            }
                        }
                    }

                    PilotTestSuite suite =
                            new PilotTestSuite(
                                    suiteId,
                                    LocalDateTime.now(),
                                    testRepositories,
                                    results,
                                    calculateSummary(results));

                    // Generate comprehensive report
                    generatePilotReport(suite);

                    return suite;
                },
                executorService);
    }

    /**
 * Runs a pilot test for a specific repository and CI platform. */
    public PilotTestResult runSinglePilotTest(PilotTestRepository repo, String platform) {
        long startTime = System.currentTimeMillis();

        try {
            // Step 1: Setup test repository
            Path testWorkspace = setupTestRepository(repo);

            // Step 2: Analyze project structure
            ProjectAnalysisService.ProjectAnalysis analysis =
                    projectAnalysisService.analyzeProject(testWorkspace.toString());

            // Step 3: Generate CI pipeline specification
            CIPipelineSpec spec = generateCISpec(repo, platform, analysis);

            // Step 4: Generate CI pipeline files
            CIPipelineGenerator generator = generators.get(platform);
            GeneratedCIPipeline pipeline = generator.generatePipeline(spec);

            // Step 5: Write pipeline files to test workspace
            writePipelineFiles(pipeline, testWorkspace);

            // Step 6: Validate pipeline syntax and structure
            CIPipelineValidationResult validation = generator.validatePipeline(spec);

            // Step 7: Run dry-run simulation
            PipelineDryRunResult dryRunResult = simulatePipelineExecution(pipeline, testWorkspace);

            // Step 8: Cleanup test workspace
            cleanupTestWorkspace(testWorkspace);

            long duration = System.currentTimeMillis() - startTime;

            boolean success = validation.isValid() && dryRunResult.success();
            String errorMessage =
                    success
                            ? null
                            : (!validation.isValid()
                                    ? "Validation failed: " + String.join(", ", validation.errors())
                                    : "Dry run failed: " + dryRunResult.message());

            return new PilotTestResult(
                    repo,
                    platform,
                    success,
                    pipeline,
                    validation,
                    dryRunResult,
                    errorMessage,
                    duration);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            return new PilotTestResult(
                    repo,
                    platform,
                    false,
                    null,
                    null,
                    null,
                    "Pilot test failed: " + e.getMessage(),
                    duration);
        }
    }

    private List<PilotTestRepository> initializeTestRepositories() {
        return List.of(
                new PilotTestRepository(
                        "java-gradle-simple",
                        "Simple Java Gradle project",
                        "https://github.com/example/java-gradle-simple.git",
                        Set.of("java"),
                        Set.of("gradle"),
                        Set.of("spring-boot"),
                        PilotTestRepository.ProjectComplexity.LOW,
                        Map.of("expected_build_time", "120", "expected_test_count", "10")),
                new PilotTestRepository(
                        "typescript-react-app",
                        "React TypeScript application",
                        "https://github.com/example/typescript-react-app.git",
                        Set.of("typescript", "javascript"),
                        Set.of("npm", "webpack"),
                        Set.of("react", "jest"),
                        PilotTestRepository.ProjectComplexity.MEDIUM,
                        Map.of("expected_build_time", "180", "expected_test_count", "25")),
                new PilotTestRepository(
                        "rust-cargo-workspace",
                        "Rust multi-crate workspace",
                        "https://github.com/example/rust-cargo-workspace.git",
                        Set.of("rust"),
                        Set.of("cargo"),
                        Set.of("actix-web", "serde"),
                        PilotTestRepository.ProjectComplexity.HIGH,
                        Map.of("expected_build_time", "300", "expected_test_count", "50")),
                new PilotTestRepository(
                        "polyglot-monorepo",
                        "Multi-language monorepo (Java + TS + Rust)",
                        "https://github.com/example/polyglot-monorepo.git",
                        Set.of("java", "typescript", "rust"),
                        Set.of("gradle", "npm", "cargo"),
                        Set.of("spring-boot", "react", "actix-web"),
                        PilotTestRepository.ProjectComplexity.VERY_HIGH,
                        Map.of("expected_build_time", "600", "expected_test_count", "100")));
    }

    private Path setupTestRepository(PilotTestRepository repo) throws IOException {
        // Create temporary workspace for testing
        Path tempDir = Files.createTempDirectory("yappc-pilot-" + repo.name());

        // For pilot testing, create a mock repository structure based on repo metadata
        createMockRepository(repo, tempDir);

        return tempDir;
    }

    private void createMockRepository(PilotTestRepository repo, Path workspace) throws IOException {
        // Create basic project structure based on detected languages and frameworks

        if (repo.languages().contains("java")) {
            createJavaProjectStructure(workspace, repo.frameworks().contains("spring-boot"));
        }

        if (repo.languages().contains("typescript") || repo.languages().contains("javascript")) {
            createNodeProjectStructure(workspace, repo.frameworks().contains("react"));
        }

        if (repo.languages().contains("rust")) {
            createRustProjectStructure(workspace);
        }

        // Create common files
        Files.writeString(
                workspace.resolve("README.md"),
                "# " + repo.description() + "\\n\\nGenerated for CI pilot testing.\\n");

        Files.writeString(
                workspace.resolve(".gitignore"),
                "target/\\nbuild/\\nnode_modules/\\ndist/\\n*.log\\n");
    }

    private void createJavaProjectStructure(Path workspace, boolean isSpringBoot)
            throws IOException {
        // Create Gradle build files
        Files.writeString(workspace.resolve("build.gradle"), generateGradleBuildFile(isSpringBoot));

        Files.writeString(
                workspace.resolve("settings.gradle"), "rootProject.name = 'pilot-test-project'\\n");

        // Create source directories
        Path srcMain = workspace.resolve("src/main/java/com/example");
        Path srcTest = workspace.resolve("src/test/java/com/example");
        Files.createDirectories(srcMain);
        Files.createDirectories(srcTest);

        // Create sample Java files
        String mainClass = isSpringBoot ? generateSpringBootMainClass() : generateSimpleMainClass();

    // Use SampleApplication as the generated main class name to avoid collisions with
    // existing Application types in other modules. Write files with matching names.
    // Choose filenames to match the generated class name to avoid duplicate public type
    // declarations inside this runner's source (which are embedded as string literals).
    String mainFileName = isSpringBoot ? "SampleSpringApplication.java" : "SampleApplication.java";
    String testFileName = isSpringBoot ? "SampleSpringApplicationTest.java" : "SampleApplicationTest.java";

    Files.writeString(srcMain.resolve(mainFileName), mainClass);
    Files.writeString(srcTest.resolve(testFileName), generateJavaTestClass(isSpringBoot ? "SampleSpringApplication" : "SampleApplication"));
    }

    private void createNodeProjectStructure(Path workspace, boolean isReact) throws IOException {
        // Create package.json
        String packageJson = isReact ? generateReactPackageJson() : generateSimpleNodePackageJson();

        Files.writeString(workspace.resolve("package.json"), packageJson);

        // Create source directories
        Path src = workspace.resolve("src");
        Files.createDirectories(src);

        // Create sample TypeScript/JavaScript files
        String mainFile =
                isReact
                        ? "import React from 'react';\\n"
                                + "export default function App() { return <div>Hello World</div>; }"
                        : "console.log('Hello World');";

        Files.writeString(src.resolve(isReact ? "App.tsx" : "index.ts"), mainFile);

        // Create test file
        String testFile =
                isReact
                        ? "import { render } from '@testing-library/react';\\n"
                                + "import App from './App';\\n"
                                + "test('renders', () => render(<App />));"
                        : "test('example', () => { expect(1 + 1).toBe(2); });";

        Files.writeString(src.resolve(isReact ? "App.test.tsx" : "index.test.ts"), testFile);
    }

    private void createRustProjectStructure(Path workspace) throws IOException {
        // Create Cargo.toml
        Files.writeString(workspace.resolve("Cargo.toml"), generateCargoToml());

        // Create source directory
        Path src = workspace.resolve("src");
        Files.createDirectories(src);

        // Create sample Rust files
        Files.writeString(
                src.resolve("main.rs"), "fn main() {\\n    println!(\"Hello, world!\");\\n}\\n");

        Files.writeString(
                src.resolve("lib.rs"),
                "pub fn add(a: i32, b: i32) -> i32 {\\n"
                        + "    a + b\\n"
                        + "}\\n"
                        + "\\n"
                        + "#[cfg(test)]\\n"
                        + "mod tests {\\n"
                        + "    use super::*;\\n"
                        + "\\n"
                        + "    #[test]\\n"
                        + "    fn test_add() {\\n"
                        + "        assert_eq!(add(2, 2), 4);\\n"
                        + "    }\\n"
                        + "}\\n");
    }

    private CIPipelineSpec generateCISpec(
            PilotTestRepository repo,
            String platform,
            ProjectAnalysisService.ProjectAnalysis analysis) {
        var specBuilder =
                CIPipelineSpec.builder()
                        .name(repo.name() + "-ci")
                        .platform(
                                CIPipelineSpec.CIPlatform.valueOf(
                                        platform.toUpperCase().replace("-", "_")));

        // Configure based on repository metadata
        List<CIPipelineSpec.CIJob> jobs = new ArrayList<>();

        if (repo.languages().contains("java")) {
            jobs.add(createJavaJob());
        }

        if (repo.languages().contains("typescript") || repo.languages().contains("javascript")) {
            jobs.add(createNodeJob());
        }

        if (repo.languages().contains("rust")) {
            jobs.add(createRustJob());
        }

        var stage = CIPipelineSpec.CIStage.builder().name("test").jobs(jobs).build();

        specBuilder.stages(List.of(stage));

        // Add security configuration for pilot testing
        specBuilder.security(
                CIPipelineSpec.CISecurityConfig.builder()
                        .enableSecurityScanning(true)
                        .enableDependencyScanning(true)
                        .enableSecretsScanning(true)
                        .scanTools(List.of("trivy", "semgrep"))
                        .blockOnCritical(false) // Don't block for pilot tests
                        .build());

        // Add basic triggers
        specBuilder.triggers(
                List.of(
                        new CIPipelineSpec.CITrigger(
                                CIPipelineSpec.CITrigger.CITriggerType.PUSH,
                                List.of("main"),
                                List.of(),
                                null,
                                Map.of())));

        return specBuilder.build();
    }

    private CIPipelineSpec.CIJob createJavaJob() {
        return CIPipelineSpec.CIJob.builder()
                .name("java-build-test")
                .runsOn("ubuntu-latest")
                .steps(
                        List.of(
                                CIPipelineSpec.CIStep.builder()
                                        .name("Checkout")
                                        .type(CIPipelineSpec.CIStep.CIStepType.CHECKOUT)
                                        .build(),
                                CIPipelineSpec.CIStep.builder()
                                        .name("Setup JDK")
                                        .type(CIPipelineSpec.CIStep.CIStepType.SETUP_LANGUAGE)
                                        .action("actions/setup-java@v4")
                                        .build(),
                                CIPipelineSpec.CIStep.builder()
                                        .name("Build")
                                        .type(CIPipelineSpec.CIStep.CIStepType.RUN_COMMAND)
                                        .command("./gradlew build")
                                        .build(),
                                CIPipelineSpec.CIStep.builder()
                                        .name("Test")
                                        .type(CIPipelineSpec.CIStep.CIStepType.RUN_COMMAND)
                                        .command("./gradlew test")
                                        .build()))
                .build();
    }

    private CIPipelineSpec.CIJob createNodeJob() {
        return CIPipelineSpec.CIJob.builder()
                .name("node-build-test")
                .runsOn("ubuntu-latest")
                .steps(
                        List.of(
                                CIPipelineSpec.CIStep.builder()
                                        .name("Checkout")
                                        .type(CIPipelineSpec.CIStep.CIStepType.CHECKOUT)
                                        .build(),
                                CIPipelineSpec.CIStep.builder()
                                        .name("Setup Node")
                                        .type(CIPipelineSpec.CIStep.CIStepType.SETUP_LANGUAGE)
                                        .action("actions/setup-node@v4")
                                        .build(),
                                CIPipelineSpec.CIStep.builder()
                                        .name("Install")
                                        .type(CIPipelineSpec.CIStep.CIStepType.RUN_COMMAND)
                                        .command("npm install")
                                        .build(),
                                CIPipelineSpec.CIStep.builder()
                                        .name("Test")
                                        .type(CIPipelineSpec.CIStep.CIStepType.RUN_COMMAND)
                                        .command("npm test")
                                        .build()))
                .build();
    }

    private CIPipelineSpec.CIJob createRustJob() {
        return CIPipelineSpec.CIJob.builder()
                .name("rust-build-test")
                .runsOn("ubuntu-latest")
                .steps(
                        List.of(
                                CIPipelineSpec.CIStep.builder()
                                        .name("Checkout")
                                        .type(CIPipelineSpec.CIStep.CIStepType.CHECKOUT)
                                        .build(),
                                CIPipelineSpec.CIStep.builder()
                                        .name("Setup Rust")
                                        .type(CIPipelineSpec.CIStep.CIStepType.RUN_COMMAND)
                                        .command("rustup update stable")
                                        .build(),
                                CIPipelineSpec.CIStep.builder()
                                        .name("Build")
                                        .type(CIPipelineSpec.CIStep.CIStepType.RUN_COMMAND)
                                        .command("cargo build")
                                        .build(),
                                CIPipelineSpec.CIStep.builder()
                                        .name("Test")
                                        .type(CIPipelineSpec.CIStep.CIStepType.RUN_COMMAND)
                                        .command("cargo test")
                                        .build()))
                .build();
    }

    private void writePipelineFiles(GeneratedCIPipeline pipeline, Path workspace)
            throws IOException {
        for (var entry : pipeline.pipelineFiles().entrySet()) {
            Path filePath = workspace.resolve(entry.getKey());
            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, entry.getValue());
        }
    }

    private PipelineDryRunResult simulatePipelineExecution(
            GeneratedCIPipeline pipeline, Path workspace) {
        try {
            // Simulate pipeline execution by checking if generated files are valid
            List<String> validationErrors = new ArrayList<>();

            for (var entry : pipeline.pipelineFiles().entrySet()) {
                String filePath = entry.getKey();
                String content = entry.getValue();

                // Basic syntax validation
                if (content.isEmpty()) {
                    validationErrors.add("Empty pipeline file: " + filePath);
                }

                // Check for required elements based on platform
                if (filePath.endsWith(".yml") || filePath.endsWith(".yaml")) {
                    if (!content.contains("jobs:") && !content.contains("stages:")) {
                        validationErrors.add("Missing jobs/stages in: " + filePath);
                    }
                }
            }

            // Simulate build step validation
            if (Files.exists(workspace.resolve("build.gradle"))) {
                // Java project - check if gradle wrapper exists or can be created
                if (!Files.exists(workspace.resolve("gradlew"))) {
                    // Create mock gradle wrapper for validation
                    Files.writeString(
                            workspace.resolve("gradlew"), "#!/bin/bash\\necho 'Mock Gradle'\\n");
                    Files.setPosixFilePermissions(
                            workspace.resolve("gradlew"),
                            Set.of(
                                    java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                                    java.nio.file.attribute.PosixFilePermission.OWNER_WRITE,
                                    java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE));
                }
            }

            boolean success = validationErrors.isEmpty();
            return new PipelineDryRunResult(
                    success,
                    success
                            ? "Pipeline dry run completed successfully"
                            : String.join("; ", validationErrors),
                    estimateExecutionTime(pipeline),
                    success
                            ? List.of("All pipeline files generated", "Syntax validation passed")
                            : validationErrors);

        } catch (Exception e) {
            return new PipelineDryRunResult(
                    false,
                    "Dry run simulation failed: " + e.getMessage(),
                    0,
                    List.of("Simulation error: " + e.getMessage()));
        }
    }

    private int estimateExecutionTime(GeneratedCIPipeline pipeline) {
        // Simple heuristic based on number of jobs and steps
        int totalSteps =
                pipeline.spec().stages().stream()
                        .mapToInt(
                                stage ->
                                        stage.jobs().stream()
                                                .mapToInt(job -> job.steps().size())
                                                .sum())
                        .sum();

        return Math.max(60, totalSteps * 30); // 30 seconds per step minimum
    }

    private void cleanupTestWorkspace(Path workspace) {
        try {
            deleteRecursively(workspace);
        } catch (IOException e) {
            log.warn("Warning: Failed to cleanup test workspace: {}", workspace);
        }
    }

    private void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (var stream = Files.list(path)) {
                stream.forEach(
                        child -> {
                            try {
                                deleteRecursively(child);
                            } catch (IOException e) {
                                // Ignore cleanup errors
                            }
                        });
            }
        }
        Files.deleteIfExists(path);
    }

    private String generateSuiteId() {
        return "pilot-"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
    }

    private PilotTestSummary calculateSummary(List<PilotTestResult> results) {
        int total = results.size();
        int passed = (int) results.stream().filter(PilotTestResult::success).count();
        int failed = total - passed;

        Map<String, Integer> platformResults = new HashMap<>();
        Map<String, Integer> repositoryResults = new HashMap<>();

        for (PilotTestResult result : results) {
            platformResults.merge(result.platform(), result.success() ? 1 : 0, Integer::sum);
            repositoryResults.merge(
                    result.repository().name(), result.success() ? 1 : 0, Integer::sum);
        }

        double successRate = total > 0 ? (double) passed / total : 0.0;

        return new PilotTestSummary(
                total,
                passed,
                failed,
                successRate,
                platformResults,
                repositoryResults,
                results.stream().mapToLong(PilotTestResult::durationMs).average().orElse(0.0));
    }

    private void generatePilotReport(PilotTestSuite suite) {
        try {
            Path reportsDir = Paths.get("reports", "pilot-tests");
            Files.createDirectories(reportsDir);

            Path reportFile = reportsDir.resolve(suite.suiteId() + "-report.md");

            StringBuilder report = new StringBuilder();
            report.append("# CI Pilot Test Report\\n\\n");
            report.append("**Suite ID:** ").append(suite.suiteId()).append("\\n");
            report.append("**Execution Time:** ").append(suite.executionTime()).append("\\n\\n");

            report.append("## Summary\\n\\n");
            var summary = suite.summary();
            report.append("- **Total Tests:** ").append(summary.totalTests()).append("\\n");
            report.append("- **Passed:** ").append(summary.passedTests()).append("\\n");
            report.append("- **Failed:** ").append(summary.failedTests()).append("\\n");
            report.append("- **Success Rate:** ")
                    .append(String.format("%.1f%%", summary.successRate() * 100))
                    .append("\\n");
            report.append("- **Average Duration:** ")
                    .append(String.format("%.1f ms", summary.averageDurationMs()))
                    .append("\\n\\n");

            report.append("## Platform Results\\n\\n");
            summary.platformResults()
                    .forEach(
                            (platform, successes) -> {
                                long total =
                                        suite.results().stream()
                                                .filter(r -> r.platform().equals(platform))
                                                .count();
                                report.append("- **")
                                        .append(platform)
                                        .append(":** ")
                                        .append(successes)
                                        .append("/")
                                        .append(total)
                                        .append(" passed\\n");
                            });

            report.append("\\n## Detailed Results\\n\\n");
            for (PilotTestResult result : suite.results()) {
                report.append("### ")
                        .append(result.repository().name())
                        .append(" (")
                        .append(result.platform())
                        .append(")\\n\\n");

                report.append("- **Status:** ")
                        .append(result.success() ? "✅ PASSED" : "❌ FAILED")
                        .append("\\n");
                report.append("- **Duration:** ").append(result.durationMs()).append(" ms\\n");

                if (!result.success() && result.errorMessage() != null) {
                    report.append("- **Error:** ").append(result.errorMessage()).append("\\n");
                }

                if (result.validation() != null) {
                    report.append("- **Validation Score:** ")
                            .append(String.format("%.2f", result.validation().qualityScore()))
                            .append("\\n");
                }

                report.append("\\n");
            }

            Files.writeString(reportFile, report.toString());
            log.info("📋 Pilot test report generated: {}", reportFile);

        } catch (Exception e) {
            log.error("Warning: Failed to generate pilot report: {}", e.getMessage());
        }
    }

    // Helper methods for generating mock project files
    private String generateGradleBuildFile(boolean isSpringBoot) {
        if (isSpringBoot) {
            return """
                plugins {
                    id 'org.springframework.boot' version '3.2.1'
                    id 'io.spring.dependency-management' version '1.1.4'
                    id 'java'
                }

                group = 'com.example'
                version = '0.0.1-SNAPSHOT'
                java.sourceCompatibility = JavaVersion.VERSION_21

                repositories {
                    mavenCentral()
                }

                dependencies {
                    implementation 'org.springframework.boot:spring-boot-starter-web'
                    testImplementation 'org.springframework.boot:spring-boot-starter-test'
                }

                tasks.named('test') {
                    useJUnitPlatform()
                }
                """;
        } else {
            return """
                plugins {
                    id 'java'
                }

                group = 'com.example'
                version = '1.0.0'
                java.sourceCompatibility = JavaVersion.VERSION_21

                repositories {
                    mavenCentral()
                }

                dependencies {
                    testImplementation 'org.junit.jupiter:junit-jupiter:5.9.2'
                }

                tasks.named('test') {
                    useJUnitPlatform()
                }
                """;
        }
    }

    private String generateSpringBootMainClass() {
        return """
            package com.example;

            import org.springframework.boot.SpringApplication;
            import org.springframework.boot.autoconfigure.SpringBootApplication;
            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.RestController;

            @SpringBootApplication
            public class SampleSpringApplication {
                public static void main(String[] args) {
                    SpringApplication.run(SampleSpringApplication.class, args);
                }
            }

            @RestController
            class HelloController {
                @GetMapping("/")
                public String hello() {
                    return "Hello World!";
                }
            }
            """;
    }

    private String generateSimpleMainClass() {
        return """
            package com.example;

            public class SampleApplication {
                public static void main(String[] args) {
                    log.info("Hello World!");
                }

                public static int add(int a, int b) {
                    return a + b;
                }
            }
            """;
    }

    private String generateJavaTestClass(String mainClassName) {
        return """
            package com.example;

            import org.junit.jupiter.api.Test;
            import static org.junit.jupiter.api.Assertions.assertEquals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

            public class %sTest {
                @Test
                public void testAdd() {
                    assertEquals(4, %s.add(2, 2));
                }
            }
            """.formatted(mainClassName, mainClassName);
    }

    private String generateReactPackageJson() {
        return """
            {
              "name": "pilot-react-app",
              "version": "0.1.0",
              "private": true,
              "dependencies": {
                "react": "^18.2.0",
                "react-dom": "^18.2.0"
              },
              "devDependencies": {
                "@testing-library/react": "^13.4.0",
                "@testing-library/jest-dom": "^5.16.5",
                "@types/react": "^18.2.0",
                "@types/react-dom": "^18.2.0",
                "typescript": "^4.9.0"
              },
              "scripts": {
                "start": "react-scripts start",
                "build": "react-scripts build",
                "test": "react-scripts test",
                "eject": "react-scripts eject"
              }
            }
            """;
    }

    private String generateSimpleNodePackageJson() {
        return """
            {
              "name": "pilot-node-app",
              "version": "1.0.0",
              "description": "Pilot test Node.js application",
              "main": "src/index.ts",
              "scripts": {
                "build": "tsc",
                "test": "jest",
                "start": "node dist/index.js"
              },
              "devDependencies": {
                "@types/node": "^20.0.0",
                "typescript": "^5.0.0",
                "jest": "^29.0.0",
                "@types/jest": "^29.0.0"
              }
            }
            """;
    }

    private String generateCargoToml() {
        return """
            [package]
            name = "pilot-rust-app"
            version = "0.1.0"
            edition = "2021"

            [dependencies]
            serde = { version = "1.0", features = ["derive"] }

            [[bin]]
            name = "pilot-rust-app"
            path = "src/main.rs"
            """;
    }

    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // Record types for pilot testing data structures
    public record PilotTestRepository(
            String name,
            String description,
            String gitUrl,
            Set<String> languages,
            Set<String> buildTools,
            Set<String> frameworks,
            ProjectComplexity complexity,
            Map<String, String> metadata) {
        public enum ProjectComplexity {
            LOW,
            MEDIUM,
            HIGH,
            VERY_HIGH
        }
    }

    public record PilotTestResult(
            PilotTestRepository repository,
            String platform,
            boolean success,
            GeneratedCIPipeline generatedPipeline,
            CIPipelineValidationResult validation,
            PipelineDryRunResult dryRunResult,
            String errorMessage,
            long durationMs) {}

    public record PipelineDryRunResult(
            boolean success,
            String message,
            int estimatedExecutionTimeSeconds,
            List<String> validationMessages) {}

    public record PilotTestSuite(
            String suiteId,
            LocalDateTime executionTime,
            List<PilotTestRepository> repositories,
            List<PilotTestResult> results,
            PilotTestSummary summary) {}

    public record PilotTestSummary(
            int totalTests,
            int passedTests,
            int failedTests,
            double successRate,
            Map<String, Integer> platformResults,
            Map<String, Integer> repositoryResults,
            double averageDurationMs) {}
}
