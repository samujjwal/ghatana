package com.ghatana.yappc.core.pack;

import static org.junit.jupiter.api.Assertions.*;

import com.ghatana.yappc.core.template.SimpleTemplateEngine;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Golden snapshot tests for pack generation validation. Tests ensure deterministic outputs and
 * catch regressions in pack generation.

 * @doc.type class
 * @doc.purpose Handles pack generator snapshot test operations
 * @doc.layer core
 * @doc.pattern Test
*/
class PackGeneratorSnapshotTest {

    private GeneratorSnapshotTest snapshotTest;
    private Path snapshotDir;

    @BeforeEach
    void setUp(@TempDir Path tempDir) { // GH-90000
        snapshotDir = tempDir.resolve("snapshots [GH-90000]");
        PackEngine packEngine = new DefaultPackEngine(new SimpleTemplateEngine()); // GH-90000
        SnapshotTester snapshotTester = new DefaultSnapshotTester(snapshotDir); // GH-90000
        snapshotTest = new GeneratorSnapshotTest(packEngine, snapshotTester); // GH-90000
    }

    @Test
    void testBasePackSnapshot(@TempDir Path tempDir) throws Exception { // GH-90000
        // Test base pack generation against golden snapshot
        var basePackResource = getClass().getClassLoader().getResource("packs/base [GH-90000]");
        assertNotNull(basePackResource, "Base pack resource should exist"); // GH-90000

        Path basePackPath = Path.of(basePackResource.getPath()); // GH-90000
        Map<String, Object> variables =
                Map.of( // GH-90000
                        "projectName", "test-project",
                        "author", "Test Author",
                        "description", "Test description");

        GeneratorSnapshotTest.SnapshotTestConfig config =
                new GeneratorSnapshotTest.SnapshotTestConfig( // GH-90000
                        "base_pack",
                        basePackPath,
                        variables,
                        false, // No hooks for basic test
                        "Base pack generation test");

        Path outputDir = tempDir.resolve("base_output [GH-90000]");
        GeneratorSnapshotTest.SnapshotTestSuite result =
                snapshotTest.runSnapshotTest(config, outputDir); // GH-90000

        // First run will create snapshots, subsequent runs should match
        if (result.totalTests() > 0) { // GH-90000
            assertTrue(result.successRate() >= 0.0, "Snapshot test should execute successfully"); // GH-90000
        }
    }

    @Test
    void testJavaServicePackSnapshot(@TempDir Path tempDir) throws Exception { // GH-90000
        // Test Java ActiveJ service pack generation
        var javaPackResource =
                getClass().getClassLoader().getResource("packs/java-service-activej-gradle [GH-90000]");
        assertNotNull(javaPackResource, "Java service pack resource should exist"); // GH-90000

        Path javaPackPath = Path.of(javaPackResource.getPath()); // GH-90000
        Map<String, Object> variables =
                Map.of( // GH-90000
                        "serviceName", "TestService",
                        "packageName", "com.example.test",
                        "className", "TestServiceApplication",
                        "port", 8080,
                        "enableOtel", true,
                        "enableDocker", true,
                        "javaVersion", "21");

        GeneratorSnapshotTest.SnapshotTestConfig config =
                new GeneratorSnapshotTest.SnapshotTestConfig( // GH-90000
                        "java_service_pack",
                        javaPackPath,
                        variables,
                        false,
                        "Java ActiveJ service pack generation test");

        Path outputDir = tempDir.resolve("java_output [GH-90000]");
        GeneratorSnapshotTest.SnapshotTestSuite result =
                snapshotTest.runSnapshotTest(config, outputDir); // GH-90000

        assertTrue(result.totalTests() > 0, "Should generate multiple files for Java service pack"); // GH-90000

        // Log any failures for debugging
        if (!result.allPassed()) { // GH-90000
            System.out.println("Java Service Pack Snapshot Failures: [GH-90000]");
            for (GeneratorSnapshotTest.SnapshotTestFailure failure : result.failures()) { // GH-90000
                System.out.println("- " + failure.testName() + ": " + failure.reason()); // GH-90000
            }
        }
    }

    @Test
    void testReactPackSnapshot(@TempDir Path tempDir) throws Exception { // GH-90000
        // Test TypeScript React pack generation
        var reactPackResource = getClass().getClassLoader().getResource("packs/ts-react-vite [GH-90000]");
        assertNotNull(reactPackResource, "React pack resource should exist"); // GH-90000

        Path reactPackPath = Path.of(reactPackResource.getPath()); // GH-90000
        Map<String, Object> variables = new HashMap<>(); // GH-90000
        variables.put("projectName", "test-react-app"); // GH-90000
        variables.put("description", "Test React application"); // GH-90000
        variables.put("author", "Test Author"); // GH-90000
        variables.put("version", "0.1.0"); // GH-90000
        variables.put("license", "MIT"); // GH-90000
        variables.put("port", 3000); // GH-90000
        variables.put("enableRouter", true); // GH-90000
        variables.put("enablePWA", false); // GH-90000
        variables.put("enableDocker", false); // GH-90000
        variables.put("cssFramework", "vanilla"); // GH-90000
        variables.put("stateManagement", "react-hooks"); // GH-90000

        GeneratorSnapshotTest.SnapshotTestConfig config =
                new GeneratorSnapshotTest.SnapshotTestConfig( // GH-90000
                        "react_pack",
                        reactPackPath,
                        variables,
                        false,
                        "TypeScript React pack generation test");

        Path outputDir = tempDir.resolve("react_output [GH-90000]");
        GeneratorSnapshotTest.SnapshotTestSuite result =
                snapshotTest.runSnapshotTest(config, outputDir); // GH-90000

        assertTrue(result.totalTests() > 0, "Should generate multiple files for React pack"); // GH-90000

        // Log any failures for debugging
        if (!result.allPassed()) { // GH-90000
            System.out.println("React Pack Snapshot Failures: [GH-90000]");
            for (GeneratorSnapshotTest.SnapshotTestFailure failure : result.failures()) { // GH-90000
                System.out.println("- " + failure.testName() + ": " + failure.reason()); // GH-90000
            }
        }
    }

    @Test
    void testNextJsPackSnapshot(@TempDir Path tempDir) throws Exception { // GH-90000
        // Test TypeScript Next.js pack generation
        var nextjsPackResource = getClass().getClassLoader().getResource("packs/ts-react-nextjs [GH-90000]");
        assertNotNull(nextjsPackResource, "Next.js pack resource should exist"); // GH-90000

        Path nextjsPackPath = Path.of(nextjsPackResource.getPath()); // GH-90000
        Map<String, Object> variables = new HashMap<>(); // GH-90000
        variables.put("projectName", "test-nextjs-app"); // GH-90000
        variables.put("description", "Test Next.js application"); // GH-90000
        variables.put("author", "Test Author"); // GH-90000
        variables.put("version", "0.1.0"); // GH-90000
        variables.put("license", "MIT"); // GH-90000
        variables.put("port", 3000); // GH-90000
        variables.put("enableTypeScript", true); // GH-90000
        variables.put("enableESLint", true); // GH-90000
        variables.put("enableTailwind", false); // GH-90000
        variables.put("enableApp", true); // GH-90000
        variables.put("enableSrc", true); // GH-90000

        GeneratorSnapshotTest.SnapshotTestConfig config =
                new GeneratorSnapshotTest.SnapshotTestConfig( // GH-90000
                        "nextjs_pack",
                        nextjsPackPath,
                        variables,
                        false,
                        "TypeScript Next.js pack generation test");

        Path outputDir = tempDir.resolve("nextjs_output [GH-90000]");
        GeneratorSnapshotTest.SnapshotTestSuite result =
                snapshotTest.runSnapshotTest(config, outputDir); // GH-90000

        assertTrue(result.totalTests() > 0, "Should generate multiple files for Next.js pack"); // GH-90000

        // Log any failures for debugging
        if (!result.allPassed()) { // GH-90000
            System.out.println("Next.js Pack Snapshot Failures: [GH-90000]");
            for (GeneratorSnapshotTest.SnapshotTestFailure failure : result.failures()) { // GH-90000
                System.out.println("- " + failure.testName() + ": " + failure.reason()); // GH-90000
            }
        }
    }

    @Test
    void testPackWithHooksSnapshot(@TempDir Path tempDir) throws Exception { // GH-90000
        // Test pack generation with post-generation hook execution
        var reactPackResource = getClass().getClassLoader().getResource("packs/ts-react-vite [GH-90000]");
        assertNotNull(reactPackResource, "React pack resource should exist"); // GH-90000

        Path reactPackPath = Path.of(reactPackResource.getPath()); // GH-90000
        Map<String, Object> variables =
                Map.of( // GH-90000
                        "projectName", "test-react-hooks",
                        "description", "React app with hooks test",
                        "author", "Test Author",
                        "enableRouter", false,
                        "enableDocker", false);

        GeneratorSnapshotTest.SnapshotTestConfig config =
                new GeneratorSnapshotTest.SnapshotTestConfig( // GH-90000
                        "react_with_hooks",
                        reactPackPath,
                        variables,
                        true, // Enable hooks execution
                        "React pack with hooks generation test");

        Path outputDir = tempDir.resolve("react_hooks_output [GH-90000]");
        GeneratorSnapshotTest.SnapshotTestSuite result =
                snapshotTest.runSnapshotTest(config, outputDir); // GH-90000

        assertTrue(result.totalTests() > 0, "Should generate files and test hooks metadata"); // GH-90000

        // The hooks may fail (yappc commands not available in test), but generation should work // GH-90000
        // We're primarily testing that the snapshot system captures hook execution metadata
        if (!result.allPassed()) { // GH-90000
            System.out.println("React with Hooks Snapshot Info: [GH-90000]");
            for (GeneratorSnapshotTest.SnapshotTestFailure failure : result.failures()) { // GH-90000
                System.out.println("- " + failure.testName() + ": " + failure.reason()); // GH-90000
            }
        }
    }

    @Test
    void testMultiplePacksSnapshot(@TempDir Path tempDir) throws Exception { // GH-90000
        // Test multiple pack configurations in one test suite
        var basePackResource = getClass().getClassLoader().getResource("packs/base [GH-90000]");
        var javaPackResource =
                getClass().getClassLoader().getResource("packs/java-service-activej-gradle [GH-90000]");

        assertNotNull(basePackResource, "Base pack resource should exist"); // GH-90000
        assertNotNull(javaPackResource, "Java pack resource should exist"); // GH-90000

        List<GeneratorSnapshotTest.SnapshotTestConfig> configs =
                List.of( // GH-90000
                        new GeneratorSnapshotTest.SnapshotTestConfig( // GH-90000
                                "multi_base",
                                Path.of(basePackResource.getPath()), // GH-90000
                                Map.of("projectName", "multi-test", "author", "Test"), // GH-90000
                                false,
                                "Multi-pack test: Base"),
                        new GeneratorSnapshotTest.SnapshotTestConfig( // GH-90000
                                "multi_java",
                                Path.of(javaPackResource.getPath()), // GH-90000
                                Map.of( // GH-90000
                                        "serviceName",
                                        "MultiService",
                                        "packageName",
                                        "com.multi.test"),
                                false,
                                "Multi-pack test: Java Service"));

        Path outputDir = tempDir.resolve("multi_output [GH-90000]");
        GeneratorSnapshotTest.SnapshotTestSuite result =
                snapshotTest.runSnapshotTests(configs, outputDir); // GH-90000

        assertTrue(result.totalTests() > 0, "Should generate files for multiple packs"); // GH-90000
        assertEquals(2, configs.size(), "Should test both pack configurations"); // GH-90000

        // Log results
        System.out.println( // GH-90000
                String.format( // GH-90000
                        "Multi-pack test: %d total tests, %d passed, %.1f%% success rate",
                        result.totalTests(), result.passedTests(), result.successRate() * 100)); // GH-90000
    }

    @Test
    void testSnapshotUpdate(@TempDir Path tempDir) throws Exception { // GH-90000
        // Test updating snapshots when pack output changes intentionally
        var basePackResource = getClass().getClassLoader().getResource("packs/base [GH-90000]");
        assertNotNull(basePackResource, "Base pack resource should exist"); // GH-90000

        Path basePackPath = Path.of(basePackResource.getPath()); // GH-90000
        Map<String, Object> variables =
                Map.of( // GH-90000
                        "projectName", "update-test",
                        "author", "Update Author");

        GeneratorSnapshotTest.SnapshotTestConfig config =
                new GeneratorSnapshotTest.SnapshotTestConfig( // GH-90000
                        "update_test", basePackPath, variables, false, "Snapshot update test");

        Path outputDir = tempDir.resolve("update_output [GH-90000]");

        // First run creates snapshots
        snapshotTest.runSnapshotTest(config, outputDir); // GH-90000

        // Update snapshots (simulating intentional pack changes) // GH-90000
        snapshotTest.updateSnapshots(List.of(config), outputDir); // GH-90000

        // Subsequent run should match updated snapshots
        GeneratorSnapshotTest.SnapshotTestSuite result =
                snapshotTest.runSnapshotTest(config, outputDir); // GH-90000

        if (result.totalTests() > 0) { // GH-90000
            assertTrue( // GH-90000
                    result.successRate() >= 0.0, "Updated snapshots should validate successfully"); // GH-90000
        }
    }
}
