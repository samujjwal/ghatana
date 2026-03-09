package com.ghatana.yappc.core.pack;

import static org.junit.jupiter.api.Assertions.*;

import com.ghatana.yappc.core.template.SimpleTemplateEngine;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for CI snapshot testing runner 
 * @doc.type class
 * @doc.purpose Handles ci snapshot runner test operations
 * @doc.layer core
 * @doc.pattern Test
*/
class CiSnapshotRunnerTest {

    private CiSnapshotRunner ciRunner;
    private Path snapshotDir;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        snapshotDir = tempDir.resolve("ci_snapshots");
        PackEngine packEngine = new DefaultPackEngine(new SimpleTemplateEngine());
        ciRunner = new CiSnapshotRunner(packEngine);
    }

    @Test
    void testCiRunnerWithBasePack(@TempDir Path tempDir) throws Exception {
        var basePackResource = getClass().getClassLoader().getResource("packs/base");
        assertNotNull(basePackResource, "Base pack resource should exist");

        CiSnapshotRunner.PackTestCase testCase =
                new CiSnapshotRunner.PackTestCase(
                        "base",
                        basePackResource.getPath(),
                        Map.of("projectName", "ci-base-test", "author", "CI Test"),
                        false,
                        List.of(".gitignore", "README.md") // Expected files
                        );

        CiSnapshotRunner.CiTestMatrix matrix =
                new CiSnapshotRunner.CiTestMatrix(
                        List.of(testCase),
                        false, // Don't update snapshots
                        snapshotDir.toString(),
                        false // Don't fail on mismatch for this test
                        );

        Path outputDir = tempDir.resolve("ci_output");
        CiSnapshotRunner.CiTestResult result = ciRunner.runCiTests(matrix, outputDir);

        assertNotNull(result);
        assertTrue(result.executionTimeSeconds() > 0, "Should record execution time");
        assertTrue(result.totalTests() > 0, "Should execute tests");

        // Print results for debugging
        CiSnapshotRunner.printCiResults(result);
    }

    @Test
    void testStandardMatrix() {
        // Test generation of standard CI matrix
        String packsDir = "src/test/resources/packs";
        String snapshotsDir = "snapshots";

        CiSnapshotRunner.CiTestMatrix matrix =
                CiSnapshotRunner.generateStandardMatrix(packsDir, snapshotsDir);

        assertNotNull(matrix);
        assertFalse(matrix.testCases().isEmpty(), "Should generate test cases");
        assertEquals(snapshotsDir, matrix.snapshotDirectory());
        assertTrue(matrix.failOnMismatch(), "Should fail on mismatch in CI");
        assertFalse(matrix.updateSnapshots(), "Should not update snapshots by default");

        // Verify all expected packs are included
        List<String> packNames =
                matrix.testCases().stream().map(CiSnapshotRunner.PackTestCase::packName).toList();

        assertTrue(packNames.contains("base"), "Should include base pack");
        assertTrue(packNames.contains("java-service"), "Should include Java service pack");
        assertTrue(packNames.contains("react-vite"), "Should include React Vite pack");
        assertTrue(packNames.contains("react-nextjs"), "Should include Next.js pack");
    }

    @Test
    void testCiRunnerWithMultiplePacks(@TempDir Path tempDir) throws Exception {
        var basePackResource = getClass().getClassLoader().getResource("packs/base");
        var javaPackResource =
                getClass().getClassLoader().getResource("packs/java-service-activej-gradle");

        assertNotNull(basePackResource, "Base pack resource should exist");
        assertNotNull(javaPackResource, "Java pack resource should exist");

        List<CiSnapshotRunner.PackTestCase> testCases =
                List.of(
                        new CiSnapshotRunner.PackTestCase(
                                "base",
                                basePackResource.getPath(),
                                Map.of("projectName", "multi-base", "author", "Multi Test"),
                                false,
                                List.of(".gitignore")),
                        new CiSnapshotRunner.PackTestCase(
                                "java-service",
                                javaPackResource.getPath(),
                                Map.of(
                                        "serviceName",
                                        "MultiService",
                                        "packageName",
                                        "com.multi.test"),
                                false,
                                List.of("build.gradle", "settings.gradle")));

        CiSnapshotRunner.CiTestMatrix matrix =
                new CiSnapshotRunner.CiTestMatrix(testCases, false, snapshotDir.toString(), false);

        Path outputDir = tempDir.resolve("multi_output");
        CiSnapshotRunner.CiTestResult result = ciRunner.runCiTests(matrix, outputDir);

        assertNotNull(result);
        assertTrue(result.totalTests() > 0, "Should execute tests for multiple packs");
        assertEquals(2, testCases.size(), "Should test both packs");

        // Print detailed results
        System.out.println("Multi-pack CI test completed:");
        System.out.printf("- Total tests: %d\\n", result.totalTests());
        System.out.printf(
                "- Success rate: %.1f%%\\n",
                result.totalTests() > 0
                        ? (double) result.passedTests() / result.totalTests() * 100
                        : 0);
    }

    @Test
    void testResultFormatting() {
        // Test CI result formatting
        CiSnapshotRunner.CiTestResult successResult =
                new CiSnapshotRunner.CiTestResult(true, 10, 10, 0, List.of(), 1.5);

        CiSnapshotRunner.CiTestResult failureResult =
                new CiSnapshotRunner.CiTestResult(
                        false, 10, 8, 2, List.of("Test error 1", "Test error 2"), 2.3);

        // These should not throw exceptions
        assertDoesNotThrow(() -> CiSnapshotRunner.printCiResults(successResult));
        assertDoesNotThrow(() -> CiSnapshotRunner.printCiResults(failureResult));

        assertTrue(successResult.success());
        assertFalse(failureResult.success());
        assertEquals(1.5, successResult.executionTimeSeconds());
        assertEquals(2, failureResult.errorMessages().size());
    }
}
