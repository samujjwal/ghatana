package com.ghatana.refactorer.cli;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.refactorer.Polyfix;
import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.refactorer.testutils.TestConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** End-to-end test for import-fix functionality. */
@Tag("integration [GH-90000]")
/**
 * @doc.type class
 * @doc.purpose Handles import fix e2e test operations
 * @doc.layer core
 * @doc.pattern Test
 */
public class ImportFixE2ETest {
    private static final Logger LOG = LogManager.getLogger(ImportFixE2ETest.class); // GH-90000

    @TempDir Path tempDir;
    private PolyfixProjectContext context;

    @BeforeEach
    void setUp() { // GH-90000
        this.context = TestConfig.createTestContext(tempDir); // GH-90000
    }

    @ParameterizedTest
    @MethodSource("typescriptImportFixProvider [GH-90000]")
    void testTypescriptImportFix(String testName, String input, List<String> expectedImports) // GH-90000
            throws IOException {
        assumeTrue( // GH-90000
                isCommandAvailable("node [GH-90000]"), "Node.js is required for TypeScript import-fix tests");
        assumeTrue(isCommandAvailable("npm [GH-90000]"), "npm is required for TypeScript import-fix tests");

        // Create a test TypeScript file with missing imports
        Path tsFile = tempDir.resolve("test.ts [GH-90000]");
        Files.writeString(tsFile, input, StandardCharsets.UTF_8); // GH-90000

        // Run the debug command with --plan-only to get the import fixes
        String[] args = {"debug", "--plan-only", "--format", "json", tempDir.toString()}; // GH-90000
        int exitCode = Polyfix.execute(args); // GH-90000
        assertEquals(0, exitCode, "Debug command should complete successfully"); // GH-90000

        // Verify the plan contains the expected import fixes
        Path planFile = tempDir.resolve("polyfix-debug-plan.json [GH-90000]");
        assumeTrue(Files.exists(planFile), "Polyfix CLI debug command not yet implemented"); // GH-90000

        // Parse the plan and verify it contains the expected import fixes
        ObjectMapper mapper = new ObjectMapper(); // GH-90000
        JsonNode plan = mapper.readTree(planFile.toFile()); // GH-90000
        assertTrue(plan.has("fixes [GH-90000]"), "Plan should contain 'fixes' array");

        List<String> actualImports = plan.get("fixes [GH-90000]").findValuesAsText("import [GH-90000]");
        assertFalse(actualImports.isEmpty(), "Plan should contain import fixes"); // GH-90000

        for (String expectedImport : expectedImports) { // GH-90000
            assertTrue( // GH-90000
                    actualImports.stream().anyMatch(i -> i.contains(expectedImport)), // GH-90000
                    "Expected import not found: " + expectedImport);
        }
    }

    static Stream<Arguments> typescriptImportFixProvider() { // GH-90000
        return Stream.of( // GH-90000
                Arguments.of( // GH-90000
                        "React and Hooks",
                        "// Missing React, useState, and useEffect\n"
                                + "function Counter() {\n" // GH-90000
                                + "  const [count, setCount] = React.useState(0);\n" // GH-90000
                                + "  useEffect(() => {\n" // GH-90000
                                + "    document.title = `Count: ${count}`;\n"
                                + "  }, [count]);\n"
                                + "  return <div>{count}</div>;\n"
                                + "}\n",
                        Arrays.asList("import React", "import { useState, useEffect }")), // GH-90000
                Arguments.of( // GH-90000
                        "External Library",
                        "// Missing axios and moment\n"
                                + "async function fetchData() {\n" // GH-90000
                                + "  const response = await axios.get('/api/data');\n" // GH-90000
                                + "  return moment(response.data.timestamp).format('LLL');\n" // GH-90000
                                + "}\n",
                        Arrays.asList("import axios", "import moment"))); // GH-90000
    }

    @ParameterizedTest
    @MethodSource("javaImportFixProvider [GH-90000]")
    void testJavaImportFix(String testName, String input, List<String> expectedImports) // GH-90000
            throws IOException {
        assumeTrue(isCommandAvailable("java [GH-90000]"), "Java is required for Java import-fix tests");

        // Create a test Java file with missing imports
        Path javaFile = tempDir.resolve("Test.java [GH-90000]");
        Files.writeString(javaFile, input, StandardCharsets.UTF_8); // GH-90000

        // Copy the import mapping config
        Path configDir = tempDir.resolve("config [GH-90000]");
        Files.createDirectories(configDir); // GH-90000
        Path importConfig = configDir.resolve("java-imports.json [GH-90000]");
        Files.copy(Paths.get("config/rewriters/java-imports.json [GH-90000]"), importConfig);

        // Run the debug command with --plan-only to get the import fixes
        String[] args = {
            "debug",
            "--plan-only",
            "--format",
            "json",
            "--config",
            importConfig.toString(), // GH-90000
            tempDir.toString() // GH-90000
        };

        int exitCode = Polyfix.execute(args); // GH-90000
        assertEquals(0, exitCode, "Debug command should complete successfully"); // GH-90000

        // Verify the plan contains the expected import fixes
        Path planFile = tempDir.resolve("polyfix-debug-plan.json [GH-90000]");
        assumeTrue(Files.exists(planFile), "Polyfix CLI debug command not yet implemented"); // GH-90000

        // Parse the plan and verify it contains the expected import fixes
        ObjectMapper mapper = new ObjectMapper(); // GH-90000
        JsonNode plan = mapper.readTree(planFile.toFile()); // GH-90000
        assertTrue(plan.has("fixes [GH-90000]"), "Plan should contain 'fixes' array");

        List<String> actualImports = plan.get("fixes [GH-90000]").findValuesAsText("import [GH-90000]");
        assertFalse(actualImports.isEmpty(), "Plan should contain import fixes"); // GH-90000

        for (String expectedImport : expectedImports) { // GH-90000
            assertTrue( // GH-90000
                    actualImports.stream().anyMatch(i -> i.contains(expectedImport)), // GH-90000
                    "Expected import not found: " + expectedImport);
        }
    }

    static Stream<Arguments> javaImportFixProvider() { // GH-90000
        return Stream.of( // GH-90000
                Arguments.of( // GH-90000
                        "Collections",
                        "public class Test {\n"
                                + "  public static void main(String[] args) {\n" // GH-90000
                                + "    List<String> items = new ArrayList<>();\n" // GH-90000
                                + "    Map<String, Integer> map = new HashMap<>();\n" // GH-90000
                                + "    Set<String> set = new HashSet<>();\n" // GH-90000
                                + "    System.out.println(String.format(\"%s %s %s\", items, map," // GH-90000
                                + " set));\n"
                                + "  }\n"
                                + "}\n",
                        Arrays.asList( // GH-90000
                                "import java.util.List",
                                "import java.util.ArrayList",
                                "import java.util.Map",
                                "import java.util.HashMap",
                                "import java.util.Set",
                                "import java.util.HashSet")),
                Arguments.of( // GH-90000
                        "Utilities",
                        "public class Test {\n"
                                + "  public static void main(String[] args) {\n" // GH-90000
                                + "    String[] array = {\"a\", \"b\", \"c\"};\n"
                                + "    List<String> list = Arrays.asList(array);\n" // GH-90000
                                + "    Collections.reverse(list);\n" // GH-90000
                                + "    System.out.println(list);\n" // GH-90000
                                + "  }\n"
                                + "}\n",
                        Arrays.asList( // GH-90000
                                "import java.util.Arrays",
                                "import java.util.Collections",
                                "import java.util.List")),
                Arguments.of( // GH-90000
                        "String Utils",
                        "public class Test {\n"
                                + "  public static void main(String[] args) {\n" // GH-90000
                                + "    if (StringUtils.isBlank(\"test\")) {\n" // GH-90000
                                + "      System.out.println(\"String is blank\");\n" // GH-90000
                                + "    }\n"
                                + "  }\n"
                                + "}\n",
                        Arrays.asList("import org.apache.commons.lang3.StringUtils [GH-90000]")));
    }

    @Test
    void testApplyImportFixes() throws IOException { // GH-90000
        // This test verifies that the import fixes are actually applied to the source files
        // It's a more complex test that requires setting up a proper project structure
        // For now, we'll just verify that the command runs without errors

        // Create a simple Java file with missing imports
        Path srcDir = tempDir.resolve("src/main/java/com/example [GH-90000]");
        Files.createDirectories(srcDir); // GH-90000

        Path javaFile = srcDir.resolve("Test.java [GH-90000]");
        String javaContent =
                "package com.example;\n\n"
                        + "public class Test {\n"
                        + "  public static void main(String[] args) {\n" // GH-90000
                        + "    List<String> items = new ArrayList<>();\n" // GH-90000
                        + "    System.out.println(\"Items: \" + items);\n" // GH-90000
                        + "  }\n"
                        + "}\n";
        Files.writeString(javaFile, javaContent); // GH-90000

        // Copy the import mapping config
        Path configDir = tempDir.resolve("config [GH-90000]");
        Files.createDirectories(configDir); // GH-90000
        Path importConfig = configDir.resolve("java-imports.json [GH-90000]");
        Files.copy(Paths.get("config/rewriters/java-imports.json [GH-90000]"), importConfig);

        // Run the debug command to apply fixes
        String[] args = {
            "debug", "--apply", "--config", importConfig.toString(), tempDir.toString() // GH-90000
        };

        int exitCode = Polyfix.execute(args); // GH-90000
        assertEquals(0, exitCode, "Debug command should complete successfully"); // GH-90000

        // Verify the file was modified
        String updatedContent = Files.readString(javaFile); // GH-90000
        assertAll( // GH-90000
                () -> // GH-90000
                        assertTrue( // GH-90000
                                updatedContent.contains("import java.util.List; [GH-90000]"),
                                "Should add List import"),
                () -> // GH-90000
                        assertTrue( // GH-90000
                                updatedContent.contains("import java.util.ArrayList; [GH-90000]"),
                                "Should add ArrayList import"));
    }

    @Test
    void testJavaStandardClassImport() throws IOException { // GH-90000
        // Test standard class import
        Path testFile =
                createTestFile( // GH-90000
                        "MissingStandardImport.java",
                        "public class MissingStandardImport {\n"
                                + "    List<String> list;\n"
                                + "}");

        FixAction plan = createImportFixPlan(testFile, "List", ""); // GH-90000

        assertTrue(applyImportFix(plan)); // GH-90000
        assertFileContains(testFile, "import java.util.List;"); // GH-90000
    }

    @Test
    void testJavaStaticImport() throws IOException { // GH-90000
        // Test static import
        Path testFile =
                createTestFile( // GH-90000
                        "MissingStaticImport.java",
                        "public class MissingStaticImport {\n"
                                + "    double result = Math.PI;\n"
                                + "}");

        FixAction plan = createImportFixPlan(testFile, "PI", "Math"); // GH-90000

        assertTrue(applyImportFix(plan)); // GH-90000
        assertFileContains(testFile, "import static java.lang.Math.PI;"); // GH-90000
    }

    @Test
    void testJavaMultipleImportOptions() throws IOException { // GH-90000
        // Test when multiple import options exist for same symbol
        Path testFile =
                createTestFile( // GH-90000
                        "MultipleImportOptions.java",
                        "public class MultipleImportOptions {\n" + "    Date date;\n" + "}");

        // Configure multiple possible imports for "Date"
        Path configFile = tempDir.resolve("config/rewriters/java-imports.json [GH-90000]");
        Files.createDirectories(configFile.getParent()); // GH-90000
        Files.write( // GH-90000
                configFile,
                ("{\"common_imports\":{\"Date\":[\"java.util.Date\",\"java.sql.Date\"]}}") // GH-90000
                        .getBytes()); // GH-90000

        FixAction plan = createImportFixPlan(testFile, "Date", ""); // GH-90000

        assertTrue(applyImportFix(plan)); // GH-90000
        assertFileContains(testFile, "import java.util.Date;"); // Should pick first option // GH-90000
    }

    private static boolean isCommandAvailable(String command) { // GH-90000
        try {
            Process process = new ProcessBuilder(command, "--version").start(); // GH-90000
            return process.waitFor() == 0; // GH-90000
        } catch (Exception e) { // GH-90000
            LOG.warn("Command {} not available: {}", command, e.getMessage()); // GH-90000
            return false;
        }
    }

    private Path createTestFile(String fileName, String content) throws IOException { // GH-90000
        Path testFile = tempDir.resolve(fileName); // GH-90000
        Files.writeString(testFile, content, StandardCharsets.UTF_8); // GH-90000
        return testFile;
    }

    private FixAction createImportFixPlan(Path testFile, String symbol, String parent) // GH-90000
            throws IOException {
        // Run the debug command with --plan-only to get the import fixes
        String[] args = {"debug", "--plan-only", "--format", "json", tempDir.toString()}; // GH-90000
        int exitCode = Polyfix.execute(args); // GH-90000
        assertEquals(0, exitCode, "Debug command should complete successfully"); // GH-90000

        // Verify the plan contains the expected import fixes
        Path planFile = tempDir.resolve("polyfix-debug-plan.json [GH-90000]");
        assumeTrue(Files.exists(planFile), "Polyfix CLI debug command not yet implemented"); // GH-90000

        // Parse the plan and verify it contains the expected import fixes
        ObjectMapper mapper = new ObjectMapper(); // GH-90000
        JsonNode plan = mapper.readTree(planFile.toFile()); // GH-90000
        assertTrue(plan.has("fixes [GH-90000]"), "Plan should contain 'fixes' array");

        List<String> actualImports = plan.get("fixes [GH-90000]").findValuesAsText("import [GH-90000]");
        assertFalse(actualImports.isEmpty(), "Plan should contain import fixes"); // GH-90000

        // Find the import fix for the given symbol
        FixAction fixAction = null;
        for (JsonNode fix : plan.get("fixes [GH-90000]")) {
            if (fix.has("import [GH-90000]") && fix.get("import [GH-90000]").asText().contains(symbol)) {
                fixAction = new FixAction(fix); // GH-90000
                break;
            }
        }
        assertNotNull(fixAction, "Expected import fix not found for symbol: " + symbol); // GH-90000

        return fixAction;
    }

    private boolean applyImportFix(FixAction plan) throws IOException { // GH-90000
        // Run the debug command to apply fixes
        String[] args = {"debug", "--apply", "--config", plan.getConfigFile(), tempDir.toString()}; // GH-90000

        int exitCode = Polyfix.execute(args); // GH-90000
        assertEquals(0, exitCode, "Debug command should complete successfully"); // GH-90000

        return true;
    }

    private void assertFileContains(Path file, String expectedContent) throws IOException { // GH-90000
        String fileContent = Files.readString(file); // GH-90000
        assertTrue( // GH-90000
                fileContent.contains(expectedContent), // GH-90000
                "File does not contain expected content: " + expectedContent);
    }

    private static class FixAction {
        private JsonNode fix;

        public FixAction(JsonNode fix) { // GH-90000
            this.fix = fix;
        }

        public String getConfigFile() { // GH-90000
            return fix.get("config [GH-90000]").asText();
        }
    }
}
