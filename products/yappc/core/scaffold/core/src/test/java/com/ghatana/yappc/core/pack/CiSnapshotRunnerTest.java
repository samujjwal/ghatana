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
    void setUp(@TempDir Path tempDir) { // GH-90000
        snapshotDir = tempDir.resolve("ci_snapshots");
        PackEngine packEngine = new DefaultPackEngine(new SimpleTemplateEngine()); // GH-90000
        ciRunner = new CiSnapshotRunner(packEngine); // GH-90000
    }

    @Test
    void testCiRunnerWithBasePack(@TempDir Path tempDir) throws Exception { // GH-90000
        var basePackResource = getClass().getClassLoader().getResource("packs/base");
        assertNotNull(basePackResource, "Base pack resource should exist"); // GH-90000

        CiSnapshotRunner.PackTestCase testCase =
                new CiSnapshotRunner.PackTestCase( // GH-90000
                        "base",
                        basePackResource.getPath(), // GH-90000
                        Map.of("projectName", "ci-base-test", "author", "CI Test"), // GH-90000
                        false,
                        List.of(".gitignore", "README.md") // Expected files // GH-90000
                        );

        CiSnapshotRunner.CiTestMatrix matrix =
                new CiSnapshotRunner.CiTestMatrix( // GH-90000
                        List.of(testCase), // GH-90000
                        false, // Don't update snapshots
                        snapshotDir.toString(), // GH-90000
                        false // Don't fail on mismatch for this test
                        );

        Path outputDir = tempDir.resolve("ci_output");
        CiSnapshotRunner.CiTestResult result = ciRunner.runCiTests(matrix, outputDir); // GH-90000

        assertNotNull(result); // GH-90000
        assertTrue(result.executionTimeSeconds() > 0, "Should record execution time"); // GH-90000
        assertTrue(result.totalTests() > 0, "Should execute tests"); // GH-90000

        // Print results for debugging
        CiSnapshotRunner.printCiResults(result); // GH-90000
    }

    @Test
    void testStandardMatrix() { // GH-90000
        // Test generation of standard CI matrix
        String packsDir = "src/test/resources/packs";
        String snapshotsDir = "snapshots";

        CiSnapshotRunner.CiTestMatrix matrix =
                CiSnapshotRunner.generateStandardMatrix(packsDir, snapshotsDir); // GH-90000

        assertNotNull(matrix); // GH-90000
        assertFalse(matrix.testCases().isEmpty(), "Should generate test cases"); // GH-90000
        assertEquals(snapshotsDir, matrix.snapshotDirectory()); // GH-90000
        assertTrue(matrix.failOnMismatch(), "Should fail on mismatch in CI"); // GH-90000
        assertFalse(matrix.updateSnapshots(), "Should not update snapshots by default"); // GH-90000

        // Verify all expected packs are included
        List<String> packNames =
                matrix.testCases().stream().map(CiSnapshotRunner.PackTestCase::packName).toList(); // GH-90000

        assertTrue(packNames.contains("base"), "Should include base pack");
        assertTrue(packNames.contains("java-service"), "Should include Java service pack");
        assertTrue(packNames.contains("react-vite"), "Should include React Vite pack");
        assertTrue(packNames.contains("react-nextjs"), "Should include Next.js pack");
    }

    @Test
    void testCiRunnerWithMultiplePacks(@TempDir Path tempDir) throws Exception { // GH-90000
        var basePackResource = getClass().getClassLoader().getResource("packs/base");
        var javaPackResource =
                getClass().getClassLoader().getResource("packs/java-service-activej-gradle");

        assertNotNull(basePackResource, "Base pack resource should exist"); // GH-90000
        assertNotNull(javaPackResource, "Java pack resource should exist"); // GH-90000

        List<CiSnapshotRunner.PackTestCase> testCases =
                List.of( // GH-90000
                        new CiSnapshotRunner.PackTestCase( // GH-90000
                                "base",
                                basePackResource.getPath(), // GH-90000
                                Map.of("projectName", "multi-base", "author", "Multi Test"), // GH-90000
                                false,
                                List.of(".gitignore")),
                        new CiSnapshotRunner.PackTestCase( // GH-90000
                                "java-service",
                                javaPackResource.getPath(), // GH-90000
                                Map.of( // GH-90000
                                        "serviceName",
                                        "MultiService",
                                        "packageName",
                                        "com.multi.test"),
                                false,
                                List.of("build.gradle", "settings.gradle"))); // GH-90000

        CiSnapshotRunner.CiTestMatrix matrix =
                new CiSnapshotRunner.CiTestMatrix(testCases, false, snapshotDir.toString(), false); // GH-90000

        Path outputDir = tempDir.resolve("multi_output");
        CiSnapshotRunner.CiTestResult result = ciRunner.runCiTests(matrix, outputDir); // GH-90000

        assertNotNull(result); // GH-90000
        assertTrue(result.totalTests() > 0, "Should execute tests for multiple packs"); // GH-90000
        assertEquals(2, testCases.size(), "Should test both packs"); // GH-90000

        // Print detailed results
        System.out.println("Multi-pack CI test completed:");
        System.out.printf("- Total tests: %d\\n", result.totalTests()); // GH-90000
        System.out.printf( // GH-90000
                "- Success rate: %.1f%%\\n",
                result.totalTests() > 0 // GH-90000
                        ? (double) result.passedTests() / result.totalTests() * 100 // GH-90000
                        : 0);
    }

    @Test
    void testResultFormatting() { // GH-90000
        // Test CI result formatting
        CiSnapshotRunner.CiTestResult successResult =
                new CiSnapshotRunner.CiTestResult(true, 10, 10, 0, List.of(), 1.5); // GH-90000

        CiSnapshotRunner.CiTestResult failureResult =
                new CiSnapshotRunner.CiTestResult( // GH-90000
                        false, 10, 8, 2, List.of("Test error 1", "Test error 2"), 2.3); // GH-90000

        // These should not throw exceptions
        assertDoesNotThrow(() -> CiSnapshotRunner.printCiResults(successResult)); // GH-90000
        assertDoesNotThrow(() -> CiSnapshotRunner.printCiResults(failureResult)); // GH-90000

        assertTrue(successResult.success()); // GH-90000
        assertFalse(failureResult.success()); // GH-90000
        assertEquals(1.5, successResult.executionTimeSeconds()); // GH-90000
        assertEquals(2, failureResult.errorMessages().size()); // GH-90000
    }
}
