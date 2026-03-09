package com.ghatana.refactorer.performance;

import com.ghatana.refactorer.orchestrator.PolyfixOrchestrator;
import com.ghatana.refactorer.shared.PolyfixConfig;
import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.refactorer.shared.service.LanguageService;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Performance test for large codebase analysis. This test measures the performance of the tool when
 * analyzing large repositories.
 
 * @doc.type class
 * @doc.purpose Handles large repository test operations
 * @doc.layer core
 * @doc.pattern Test
*/
public class LargeRepositoryTest {
    private static final Logger LOG = LogManager.getLogger(LargeRepositoryTest.class);
    private static final String REPORT_DIR =
            System.getProperty("polyfix.performance.report.dir", "build/reports/performance");

    public static void main(String[] args) {
        try {
            new LargeRepositoryTest().run();
        } catch (Exception e) {
            LOG.error("Performance test failed", e);
            System.exit(1);
        }
    }

    public void run() throws Exception {
        LOG.info("Starting large repository performance test");

        // Create report directory
        Files.createDirectories(Path.of(REPORT_DIR));

        // Test with different repository sizes
        testRepository("small", 1_000);
        testRepository("medium", 10_000);
        testRepository("large", 100_000);

        LOG.info("Performance test completed. Reports available in: {}", REPORT_DIR);
    }

    private void testRepository(String size, int fileCount) throws Exception {
        LOG.info("Testing {} repository with ~{} files", size, fileCount);

        // Create a temporary test directory
        Path testDir = Files.createTempDirectory("polyfix-perf-" + size);
        PolyfixProjectContext context = null;
        try {
            // Generate test files
            generateTestFiles(testDir, fileCount);

            // Initialize orchestrator
            context = createTestContext(testDir);
            PolyfixOrchestrator orchestrator = new PolyfixOrchestrator();

            // Run tests
            TestResult result = runPerformanceTest(orchestrator, context);

            // Save results
            saveResults(size, result);

            LOG.info("Completed {} test: {}", size, result);
        } finally {
            // Clean up
            deleteDirectory(testDir.toFile());
            if (context != null && context.exec() != null) {
                context.exec().shutdownNow();
            }
        }
    }

    private void generateTestFiles(Path baseDir, int fileCount) throws IOException {
        LOG.info("Generating {} test files in {}", fileCount, baseDir);

        // Create source directories
        Path srcDir = baseDir.resolve("src/main/java/com/example");
        Files.createDirectories(srcDir);

        // Generate Java files
        for (int i = 0; i < fileCount; i++) {
            Path filePath = srcDir.resolve(String.format("TestClass%06d.java", i));
            String content = generateJavaClass(i, fileCount);
            Files.writeString(filePath, content);

            if ((i + 1) % 1000 == 0) {
                LOG.info("Generated {}/{} files", i + 1, fileCount);
            }
        }

        // Create a build.gradle file
        String buildGradle =
                """
        plugins {
            id 'java'
        }

        repositories {
            mavenCentral()
        }

        dependencies {
            implementation 'com.google.guava:guava:31.1-jre'
        }
        """;
        Files.writeString(baseDir.resolve("build.gradle"), buildGradle);

        LOG.info("Finished generating test files");
    }

    private String generateJavaClass(int index, int total) {
        return String.format(
                """
        package com.example;

        import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

        /**
         * Test class %d of %d
         */
        public class TestClass%06d {
            private static final String MESSAGE = "Hello from class %d";

            private final int id = %d;
            private String name;

            public TestClass%1$06d(String name) {
                this.name = name != null ? name : "default";
            }

            public String greet() {
                return String.format("%%s (id=%%d)", MESSAGE, id);
            }

            @Override
            public String toString() {
                return String.format("TestClass%%06d{name='%%s'}", id, name);
            }

            public static void main(String[] args) {
                TestClass%1$06d instance = new TestClass%1$06d("test");
                LOG.info("{}", instance.greet());
            }
        }
        """,
                index + 1, total, index, index);
    }

    private PolyfixProjectContext createTestContext(Path projectRoot) {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        PolyfixConfig config = createConfig();
        List<LanguageService> languageServices = new ArrayList<>();
        ServiceLoader.load(LanguageService.class).forEach(languageServices::add);
        return new PolyfixProjectContext(projectRoot, config, languageServices, executor, LOG);
    }

    private PolyfixConfig createConfig() {
        PolyfixConfig.Budgets budgets = new PolyfixConfig.Budgets(3, 10);
        PolyfixConfig.Policies policies = new PolyfixConfig.Policies(false, true, false, true);
        PolyfixConfig.Tools tools =
                new PolyfixConfig.Tools(
                        "node",
                        "eslint",
                        "tsc",
                        "prettier",
                        "ruff",
                        "black",
                        "mypy",
                        "shellcheck",
                        "shfmt",
                        "cargo",
                        "rustfmt",
                        "semgrep");
        return new PolyfixConfig(List.of("java"), List.of(), budgets, policies, tools);
    }

    private TestResult runPerformanceTest(
            PolyfixOrchestrator orchestrator, PolyfixProjectContext context) {
        TestResult result = new TestResult();

        // Measure memory before
        long startMemory = getUsedMemory();

        // Run analysis
        Instant start = Instant.now();
        var summary = orchestrator.run(context);
        Instant end = Instant.now();

        // Measure memory after
        long endMemory = getUsedMemory();

        // Calculate metrics
        result.duration = Duration.between(start, end);
        result.memoryUsed = endMemory - startMemory;
        result.fileCount = context.getSourceFiles().size();
        result.passes = summary.passes();
        result.editsApplied = summary.editsApplied();

        return result;
    }

    private long getUsedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private void saveResults(String size, TestResult result) throws IOException {
        // Save as properties
        Properties props = new Properties();
        props.setProperty("size", size);
        props.setProperty("fileCount", String.valueOf(result.fileCount));
        props.setProperty("durationMs", String.valueOf(result.duration.toMillis()));
        props.setProperty("memoryUsedBytes", String.valueOf(result.memoryUsed));
        props.setProperty("passes", String.valueOf(result.passes));
        props.setProperty("editsApplied", String.valueOf(result.editsApplied));

        Path reportFile =
                Paths.get(
                        REPORT_DIR,
                        String.format(
                                "performance-%s-%d.properties", size, System.currentTimeMillis()));

        try (var writer = new FileWriter(reportFile.toFile())) {
            props.store(writer, "Performance test results for " + size + " repository");
        }

        // Append to CSV for trend analysis
        Path csvFile = Paths.get(REPORT_DIR, "performance-trend.csv");
        boolean fileExists = Files.exists(csvFile);

        try (var writer = new BufferedWriter(new FileWriter(csvFile.toFile(), true))) {
            if (!fileExists) {
                writer.write(
                        "timestamp,size,fileCount,durationMs,memoryUsedBytes,passes,editsApplied\n");
            }

            writer.write(
                    String.format(
                            "%d,%s,%d,%d,%d,%d,%d\n",
                            System.currentTimeMillis(),
                            size,
                            result.fileCount,
                            result.duration.toMillis(),
                            result.memoryUsed,
                            result.passes,
                            result.editsApplied));
        }
    }

    private static boolean deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        return directoryToBeDeleted.delete();
    }

    private static class TestResult {
        Duration duration;
        long memoryUsed;
        int fileCount;
        int passes;
        int editsApplied;

        @Override
        public String toString() {
            return String.format(
                    "TestResult{duration=%dms, memory=%.2fMB, files=%d, passes=%d, edits=%d}",
                    duration.toMillis(),
                    memoryUsed / (1024.0 * 1024.0),
                    fileCount,
                    passes,
                    editsApplied);
        }
    }
}
