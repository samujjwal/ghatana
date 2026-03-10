package com.ghatana.yappc.core.pack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for running snapshot tests in CI environments. Provides functionality to run
 * comprehensive pack validation and integration with CI matrix testing.
 *
 * @doc.type class
 * @doc.purpose Utility class for running snapshot tests in CI environments. Provides functionality to run
 * @doc.layer platform
 * @doc.pattern Runner
 */
public class CiSnapshotRunner {

    private static final Logger log = LoggerFactory.getLogger(CiSnapshotRunner.class);

    /**
 * CI test configuration that can be loaded from external configuration */
    public record CiTestMatrix(
            List<PackTestCase> testCases,
            boolean updateSnapshots,
            String snapshotDirectory,
            boolean failOnMismatch) {}

    /**
 * Individual test case in CI matrix */
    public record PackTestCase(
            String packName,
            String packPath,
            Map<String, Object> variables,
            boolean includeHooks,
            List<String> expectedFiles) {}

    /**
 * CI test execution result */
    public record CiTestResult(
            boolean success,
            int totalTests,
            int passedTests,
            int failedTests,
            List<String> errorMessages,
            double executionTimeSeconds) {}

    private final PackEngine packEngine;

    public CiSnapshotRunner(PackEngine packEngine) {
        this.packEngine = packEngine;
    }

    /**
 * Run CI snapshot tests based on matrix configuration */
    public CiTestResult runCiTests(CiTestMatrix matrix, Path outputDir) throws IOException {
        long startTime = System.currentTimeMillis();

        Path snapshotDir = Path.of(matrix.snapshotDirectory());
        SnapshotTester snapshotTester =
                new DefaultSnapshotTester(snapshotDir, matrix.updateSnapshots());
        GeneratorSnapshotTest snapshotTest = new GeneratorSnapshotTest(packEngine, snapshotTester);

        List<GeneratorSnapshotTest.SnapshotTestConfig> configs =
                matrix.testCases().stream()
                        .map(
                                testCase ->
                                        new GeneratorSnapshotTest.SnapshotTestConfig(
                                                testCase.packName(),
                                                Path.of(testCase.packPath()),
                                                testCase.variables(),
                                                testCase.includeHooks(),
                                                "CI Test: " + testCase.packName()))
                        .toList();

        List<String> errorMessages = new java.util.ArrayList<>();
        boolean success = true;
        int totalTests = 0;
        int passedTests = 0;

        try {
            GeneratorSnapshotTest.SnapshotTestSuite result =
                    snapshotTest.runSnapshotTests(configs, outputDir);

            totalTests = result.totalTests();
            passedTests = result.passedTests();

            if (!result.allPassed() && matrix.failOnMismatch()) {
                success = false;
                for (GeneratorSnapshotTest.SnapshotTestFailure failure : result.failures()) {
                    errorMessages.add(
                            String.format(
                                    "SNAPSHOT MISMATCH: %s - %s",
                                    failure.testName(), failure.reason()));
                }
            }

            // Validate expected files were generated
            for (PackTestCase testCase : matrix.testCases()) {
                if (!testCase.expectedFiles().isEmpty()) {
                    Path testOutputDir = outputDir.resolve("test_" + testCase.packName());
                    for (String expectedFile : testCase.expectedFiles()) {
                        Path expectedPath = testOutputDir.resolve(expectedFile);
                        if (!Files.exists(expectedPath)) {
                            success = false;
                            errorMessages.add(
                                    String.format(
                                            "MISSING EXPECTED FILE: %s in %s",
                                            expectedFile, testCase.packName()));
                        }
                    }
                }
            }

        } catch (PackException e) {
            success = false;
            errorMessages.add("PACK ERROR: " + e.getMessage());
        } catch (Exception e) {
            success = false;
            errorMessages.add("UNEXPECTED ERROR: " + e.getMessage());
        }

        long endTime = System.currentTimeMillis();
        double executionTimeSeconds = (endTime - startTime) / 1000.0;

        return new CiTestResult(
                success,
                totalTests,
                passedTests,
                totalTests - passedTests,
                errorMessages,
                executionTimeSeconds);
    }

    /**
 * Generate a CI test matrix for all available packs */
    public static CiTestMatrix generateStandardMatrix(
            String packsDirectory, String snapshotDirectory) {
        List<PackTestCase> testCases =
                List.of(
                        // Base pack test
                        new PackTestCase(
                                "base",
                                packsDirectory + "/base",
                                Map.of("projectName", "ci-test", "author", "CI System"),
                                false,
                                List.of(".gitignore", "README.md", ".editorconfig")),

                        // Java service pack test
                        new PackTestCase(
                                "java-service",
                                packsDirectory + "/java-service-activej-gradle",
                                Map.of(
                                        "serviceName", "CiTestService",
                                        "packageName", "com.ci.test",
                                        "className", "CiTestServiceApplication",
                                        "port", 8080,
                                        "enableOtel", true,
                                        "enableDocker", true,
                                        "javaVersion", "21"),
                                true, // Include hooks
                                List.of(
                                        "build.gradle",
                                        "settings.gradle",
                                        "Dockerfile",
                                        "src/main/java")),

                        // React pack test
                        new PackTestCase(
                                "react-vite",
                                packsDirectory + "/ts-react-vite",
                                Map.of(
                                        "projectName", "ci-react-test",
                                        "description", "CI React Test Application",
                                        "author", "CI System",
                                        "enableRouter", true,
                                        "enableDocker", false,
                                        "cssFramework", "vanilla"),
                                true, // Include hooks
                                List.of(
                                        "package.json",
                                        "tsconfig.json",
                                        "vite.config.ts",
                                        "src/main.tsx",
                                        "src/App.tsx")),

                        // Next.js pack test
                        new PackTestCase(
                                "react-nextjs",
                                packsDirectory + "/ts-react-nextjs",
                                Map.of(
                                        "projectName", "ci-nextjs-test",
                                        "description", "CI Next.js Test Application",
                                        "author", "CI System",
                                        "enableTypeScript", true,
                                        "enableApp", true),
                                false,
                                List.of("package.json", "tsconfig.json", "next.config.js")));

        return new CiTestMatrix(
                testCases,
                false, // Don't update snapshots in CI by default
                snapshotDirectory,
                true // Fail on mismatch in CI
                );
    }

    /**
 * Print CI test results in a format suitable for CI logs */
    public static void printCiResults(CiTestResult result) {
        log.info("=== CI SNAPSHOT TEST RESULTS ===");
        log.info("Status: {}", result.success() ? "PASS" : "FAIL");
        log.info("Tests: {} total, {} passed, {} failed", result.totalTests(), result.passedTests(), result.failedTests());
        log.info(String.format("Execution Time: %.2f seconds", result.executionTimeSeconds()));

        if (!result.errorMessages().isEmpty()) {
            log.info("\\nERRORS:");
            for (String error : result.errorMessages()) {
                log.info("  {}", error);
            }
        }

        log.info("================================");
    }
}
