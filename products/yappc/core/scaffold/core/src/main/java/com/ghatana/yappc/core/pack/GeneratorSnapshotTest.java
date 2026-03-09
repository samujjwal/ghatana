package com.ghatana.yappc.core.pack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Golden snapshot testing harness for pack generation validation. Compares rendered pack outputs
 * with stored fixtures to ensure deterministic generation and catch regressions.
 *
 * @doc.type class
 * @doc.purpose Golden snapshot testing harness for pack generation validation. Compares rendered pack outputs
 * @doc.layer platform
 * @doc.pattern Component
 */
public class GeneratorSnapshotTest {

    private final PackEngine packEngine;
    private final SnapshotTester snapshotTester;

    /**
 * Configuration for a snapshot test */
    public record SnapshotTestConfig(
            String packName,
            Path packPath,
            Map<String, Object> variables,
            boolean includeHooks,
            String testDescription) {}

    /**
 * Result of running snapshot tests */
    public record SnapshotTestSuite(
            int totalTests,
            int passedTests,
            int failedTests,
            java.util.List<SnapshotTestFailure> failures) {
        public boolean allPassed() {
            return failedTests == 0;
        }

        public double successRate() {
            return totalTests == 0 ? 1.0 : (double) passedTests / totalTests;
        }
    }

    /**
 * Details of a failed snapshot test */
    public record SnapshotTestFailure(
            String testName,
            String fileName,
            String reason,
            SnapshotTester.SnapshotTestResult result) {}

    public GeneratorSnapshotTest(PackEngine packEngine, SnapshotTester snapshotTester) {
        this.packEngine = packEngine;
        this.snapshotTester = snapshotTester;
    }

    /**
     * Run snapshot tests for a single pack configuration
     *
     * @param config The test configuration
     * @param outputDir Temporary directory for generation
     * @return Test suite results
     */
    public SnapshotTestSuite runSnapshotTest(SnapshotTestConfig config, Path outputDir)
            throws IOException, PackException {
        java.util.List<SnapshotTestFailure> failures = new java.util.ArrayList<>();
        int totalTests = 0;

        // Load and generate pack
        Pack pack = packEngine.loadPack(config.packPath);
        PackEngine.GenerationResult result;

        // Note: hooks functionality not yet implemented
        result = packEngine.generateFromPack(pack, outputDir, config.variables);

        // Test generated files against snapshots
        try (Stream<Path> files = Files.walk(outputDir)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                totalTests++;

                String relativePath = outputDir.relativize(file).toString().replace("\\", "/");
                String snapshotName = config.packName + "__" + relativePath.replace('/', '_');

                try {
                    String content = Files.readString(file);
                    SnapshotTester.SnapshotTestResult snapshotResult =
                            snapshotTester.assertSnapshot(snapshotName, content);

                    if (!snapshotResult.matches()) {
                        failures.add(
                                new SnapshotTestFailure(
                                        config.testDescription + " -> " + relativePath,
                                        relativePath,
                                        snapshotResult.matches() ? "Mismatch" : "Missing snapshot",
                                        snapshotResult));
                    }
                } catch (IOException e) {
                    failures.add(
                            new SnapshotTestFailure(
                                    config.testDescription + " -> " + relativePath,
                                    relativePath,
                                    "IO Error: " + e.getMessage(),
                                    null));
                }
            }
        }

        // Test generation result metadata as snapshot
        totalTests++;
        String metadataSnapshot = formatGenerationMetadata(result);
        String metadataSnapshotName = config.packName + "__generation_metadata";

        try {
            SnapshotTester.SnapshotTestResult metadataResult =
                    snapshotTester.assertSnapshot(metadataSnapshotName, metadataSnapshot);

            if (!metadataResult.matches()) {
                failures.add(
                        new SnapshotTestFailure(
                                config.testDescription + " -> Generation Metadata",
                                "generation_metadata",
                                "Metadata mismatch",
                                metadataResult));
            }
        } catch (IOException e) {
            failures.add(
                    new SnapshotTestFailure(
                            config.testDescription + " -> Generation Metadata",
                            "generation_metadata",
                            "Metadata IO Error: " + e.getMessage(),
                            null));
        }

        int passedTests = totalTests - failures.size();
        return new SnapshotTestSuite(totalTests, passedTests, failures.size(), failures);
    }

    /**
 * Run snapshot tests for multiple pack configurations */
    public SnapshotTestSuite runSnapshotTests(
            java.util.List<SnapshotTestConfig> configs, Path baseOutputDir)
            throws IOException, PackException {
        java.util.List<SnapshotTestFailure> allFailures = new java.util.ArrayList<>();
        int totalTests = 0;
        int totalPassed = 0;

        for (int i = 0; i < configs.size(); i++) {
            SnapshotTestConfig config = configs.get(i);
            Path outputDir = baseOutputDir.resolve("test_" + i + "_" + config.packName);
            Files.createDirectories(outputDir);

            SnapshotTestSuite suite = runSnapshotTest(config, outputDir);
            totalTests += suite.totalTests();
            totalPassed += suite.passedTests();
            allFailures.addAll(suite.failures());
        }

        return new SnapshotTestSuite(totalTests, totalPassed, allFailures.size(), allFailures);
    }

    /**
 * Create a formatted string representation of generation metadata for snapshotting */
    private String formatGenerationMetadata(PackEngine.GenerationResult result) {
        StringBuilder metadata = new StringBuilder();
        metadata.append("Generation Result Summary\\n");
        metadata.append("========================\\n");
        metadata.append(String.format("Successful: %s\\n", result.successful()));
        metadata.append(String.format("Files Generated: %d\\n", result.filesGenerated()));

        // Additional metadata from generation result
        if (result.metadata() != null && !result.metadata().isEmpty()) {
            metadata.append("\\nAdditional Metadata:\\n");
            metadata.append("-------------------\\n");
            result.metadata()
                    .forEach(
                            (key, value) ->
                                    metadata.append(String.format("%s: %s\\n", key, value)));
        }

        return metadata.toString();
    }

    /**
     * Update all snapshots for the given configurations (Useful when pack changes are intentional
     * and snapshots need updating)
     */
    public void updateSnapshots(java.util.List<SnapshotTestConfig> configs, Path baseOutputDir)
            throws IOException, PackException {
        // Create a new tester in update mode
        SnapshotTester updateTester =
                new DefaultSnapshotTester(
                        snapshotTester.getSnapshotDirectory(), true // Update mode
                        );

        GeneratorSnapshotTest updateTest = new GeneratorSnapshotTest(packEngine, updateTester);
        updateTest.runSnapshotTests(configs, baseOutputDir);
    }
}
