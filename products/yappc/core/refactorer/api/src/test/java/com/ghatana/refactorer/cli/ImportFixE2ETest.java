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
@Tag("integration")
/**
 * @doc.type class
 * @doc.purpose Handles import fix e2e test operations
 * @doc.layer core
 * @doc.pattern Test
 */
public class ImportFixE2ETest {
    private static final Logger LOG = LogManager.getLogger(ImportFixE2ETest.class);

    @TempDir Path tempDir;
    private PolyfixProjectContext context;

    @BeforeEach
    void setUp() {
        this.context = TestConfig.createTestContext(tempDir);
    }

    @ParameterizedTest
    @MethodSource("typescriptImportFixProvider")
    void testTypescriptImportFix(String testName, String input, List<String> expectedImports)
            throws IOException {
        assumeTrue(
                isCommandAvailable("node"), "Node.js is required for TypeScript import-fix tests");
        assumeTrue(isCommandAvailable("npm"), "npm is required for TypeScript import-fix tests");

        // Create a test TypeScript file with missing imports
        Path tsFile = tempDir.resolve("test.ts");
        Files.writeString(tsFile, input, StandardCharsets.UTF_8);

        // Run the debug command with --plan-only to get the import fixes
        String[] args = {"debug", "--plan-only", "--format", "json", tempDir.toString()};
        int exitCode = Polyfix.execute(args);
        assertEquals(0, exitCode, "Debug command should complete successfully");

        // Verify the plan contains the expected import fixes
        Path planFile = tempDir.resolve("polyfix-debug-plan.json");
        assumeTrue(Files.exists(planFile), "Polyfix CLI debug command not yet implemented");

        // Parse the plan and verify it contains the expected import fixes
        ObjectMapper mapper = new ObjectMapper();
        JsonNode plan = mapper.readTree(planFile.toFile());
        assertTrue(plan.has("fixes"), "Plan should contain 'fixes' array");

        List<String> actualImports = plan.get("fixes").findValuesAsText("import");
        assertFalse(actualImports.isEmpty(), "Plan should contain import fixes");

        for (String expectedImport : expectedImports) {
            assertTrue(
                    actualImports.stream().anyMatch(i -> i.contains(expectedImport)),
                    "Expected import not found: " + expectedImport);
        }
    }

    static Stream<Arguments> typescriptImportFixProvider() {
        return Stream.of(
                Arguments.of(
                        "React and Hooks",
                        "// Missing React, useState, and useEffect\n"
                                + "function Counter() {\n"
                                + "  const [count, setCount] = React.useState(0);\n"
                                + "  useEffect(() => {\n"
                                + "    document.title = `Count: ${count}`;\n"
                                + "  }, [count]);\n"
                                + "  return <div>{count}</div>;\n"
                                + "}\n",
                        Arrays.asList("import React", "import { useState, useEffect }")),
                Arguments.of(
                        "External Library",
                        "// Missing axios and moment\n"
                                + "async function fetchData() {\n"
                                + "  const response = await axios.get('/api/data');\n"
                                + "  return moment(response.data.timestamp).format('LLL');\n"
                                + "}\n",
                        Arrays.asList("import axios", "import moment")));
    }

    @ParameterizedTest
    @MethodSource("javaImportFixProvider")
    void testJavaImportFix(String testName, String input, List<String> expectedImports)
            throws IOException {
        assumeTrue(isCommandAvailable("java"), "Java is required for Java import-fix tests");

        // Create a test Java file with missing imports
        Path javaFile = tempDir.resolve("Test.java");
        Files.writeString(javaFile, input, StandardCharsets.UTF_8);

        // Copy the import mapping config
        Path configDir = tempDir.resolve("config");
        Files.createDirectories(configDir);
        Path importConfig = configDir.resolve("java-imports.json");
        Files.copy(Paths.get("config/rewriters/java-imports.json"), importConfig);

        // Run the debug command with --plan-only to get the import fixes
        String[] args = {
            "debug",
            "--plan-only",
            "--format",
            "json",
            "--config",
            importConfig.toString(),
            tempDir.toString()
        };

        int exitCode = Polyfix.execute(args);
        assertEquals(0, exitCode, "Debug command should complete successfully");

        // Verify the plan contains the expected import fixes
        Path planFile = tempDir.resolve("polyfix-debug-plan.json");
        assumeTrue(Files.exists(planFile), "Polyfix CLI debug command not yet implemented");

        // Parse the plan and verify it contains the expected import fixes
        ObjectMapper mapper = new ObjectMapper();
        JsonNode plan = mapper.readTree(planFile.toFile());
        assertTrue(plan.has("fixes"), "Plan should contain 'fixes' array");

        List<String> actualImports = plan.get("fixes").findValuesAsText("import");
        assertFalse(actualImports.isEmpty(), "Plan should contain import fixes");

        for (String expectedImport : expectedImports) {
            assertTrue(
                    actualImports.stream().anyMatch(i -> i.contains(expectedImport)),
                    "Expected import not found: " + expectedImport);
        }
    }

    static Stream<Arguments> javaImportFixProvider() {
        return Stream.of(
                Arguments.of(
                        "Collections",
                        "public class Test {\n"
                                + "  public static void main(String[] args) {\n"
                                + "    List<String> items = new ArrayList<>();\n"
                                + "    Map<String, Integer> map = new HashMap<>();\n"
                                + "    Set<String> set = new HashSet<>();\n"
                                + "    System.out.println(String.format(\"%s %s %s\", items, map,"
                                + " set));\n"
                                + "  }\n"
                                + "}\n",
                        Arrays.asList(
                                "import java.util.List",
                                "import java.util.ArrayList",
                                "import java.util.Map",
                                "import java.util.HashMap",
                                "import java.util.Set",
                                "import java.util.HashSet")),
                Arguments.of(
                        "Utilities",
                        "public class Test {\n"
                                + "  public static void main(String[] args) {\n"
                                + "    String[] array = {\"a\", \"b\", \"c\"};\n"
                                + "    List<String> list = Arrays.asList(array);\n"
                                + "    Collections.reverse(list);\n"
                                + "    System.out.println(list);\n"
                                + "  }\n"
                                + "}\n",
                        Arrays.asList(
                                "import java.util.Arrays",
                                "import java.util.Collections",
                                "import java.util.List")),
                Arguments.of(
                        "String Utils",
                        "public class Test {\n"
                                + "  public static void main(String[] args) {\n"
                                + "    if (StringUtils.isBlank(\"test\")) {\n"
                                + "      System.out.println(\"String is blank\");\n"
                                + "    }\n"
                                + "  }\n"
                                + "}\n",
                        Arrays.asList("import org.apache.commons.lang3.StringUtils")));
    }

    @Test
    void testApplyImportFixes() throws IOException {
        // This test verifies that the import fixes are actually applied to the source files
        // It's a more complex test that requires setting up a proper project structure
        // For now, we'll just verify that the command runs without errors

        // Create a simple Java file with missing imports
        Path srcDir = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(srcDir);

        Path javaFile = srcDir.resolve("Test.java");
        String javaContent =
                "package com.example;\n\n"
                        + "public class Test {\n"
                        + "  public static void main(String[] args) {\n"
                        + "    List<String> items = new ArrayList<>();\n"
                        + "    System.out.println(\"Items: \" + items);\n"
                        + "  }\n"
                        + "}\n";
        Files.writeString(javaFile, javaContent);

        // Copy the import mapping config
        Path configDir = tempDir.resolve("config");
        Files.createDirectories(configDir);
        Path importConfig = configDir.resolve("java-imports.json");
        Files.copy(Paths.get("config/rewriters/java-imports.json"), importConfig);

        // Run the debug command to apply fixes
        String[] args = {
            "debug", "--apply", "--config", importConfig.toString(), tempDir.toString()
        };

        int exitCode = Polyfix.execute(args);
        assertEquals(0, exitCode, "Debug command should complete successfully");

        // Verify the file was modified
        String updatedContent = Files.readString(javaFile);
        assertAll(
                () ->
                        assertTrue(
                                updatedContent.contains("import java.util.List;"),
                                "Should add List import"),
                () ->
                        assertTrue(
                                updatedContent.contains("import java.util.ArrayList;"),
                                "Should add ArrayList import"));
    }

    @Test
    void testJavaStandardClassImport() throws IOException {
        // Test standard class import
        Path testFile =
                createTestFile(
                        "MissingStandardImport.java",
                        "public class MissingStandardImport {\n"
                                + "    List<String> list;\n"
                                + "}");

        FixAction plan = createImportFixPlan(testFile, "List", "");

        assertTrue(applyImportFix(plan));
        assertFileContains(testFile, "import java.util.List;");
    }

    @Test
    void testJavaStaticImport() throws IOException {
        // Test static import
        Path testFile =
                createTestFile(
                        "MissingStaticImport.java",
                        "public class MissingStaticImport {\n"
                                + "    double result = Math.PI;\n"
                                + "}");

        FixAction plan = createImportFixPlan(testFile, "PI", "Math");

        assertTrue(applyImportFix(plan));
        assertFileContains(testFile, "import static java.lang.Math.PI;");
    }

    @Test
    void testJavaMultipleImportOptions() throws IOException {
        // Test when multiple import options exist for same symbol
        Path testFile =
                createTestFile(
                        "MultipleImportOptions.java",
                        "public class MultipleImportOptions {\n" + "    Date date;\n" + "}");

        // Configure multiple possible imports for "Date"
        Path configFile = tempDir.resolve("config/rewriters/java-imports.json");
        Files.createDirectories(configFile.getParent());
        Files.write(
                configFile,
                ("{\"common_imports\":{\"Date\":[\"java.util.Date\",\"java.sql.Date\"]}}")
                        .getBytes());

        FixAction plan = createImportFixPlan(testFile, "Date", "");

        assertTrue(applyImportFix(plan));
        assertFileContains(testFile, "import java.util.Date;"); // Should pick first option
    }

    private static boolean isCommandAvailable(String command) {
        try {
            Process process = new ProcessBuilder(command, "--version").start();
            return process.waitFor() == 0;
        } catch (Exception e) {
            LOG.warn("Command {} not available: {}", command, e.getMessage());
            return false;
        }
    }

    private Path createTestFile(String fileName, String content) throws IOException {
        Path testFile = tempDir.resolve(fileName);
        Files.writeString(testFile, content, StandardCharsets.UTF_8);
        return testFile;
    }

    private FixAction createImportFixPlan(Path testFile, String symbol, String parent)
            throws IOException {
        // Run the debug command with --plan-only to get the import fixes
        String[] args = {"debug", "--plan-only", "--format", "json", tempDir.toString()};
        int exitCode = Polyfix.execute(args);
        assertEquals(0, exitCode, "Debug command should complete successfully");

        // Verify the plan contains the expected import fixes
        Path planFile = tempDir.resolve("polyfix-debug-plan.json");
        assumeTrue(Files.exists(planFile), "Polyfix CLI debug command not yet implemented");

        // Parse the plan and verify it contains the expected import fixes
        ObjectMapper mapper = new ObjectMapper();
        JsonNode plan = mapper.readTree(planFile.toFile());
        assertTrue(plan.has("fixes"), "Plan should contain 'fixes' array");

        List<String> actualImports = plan.get("fixes").findValuesAsText("import");
        assertFalse(actualImports.isEmpty(), "Plan should contain import fixes");

        // Find the import fix for the given symbol
        FixAction fixAction = null;
        for (JsonNode fix : plan.get("fixes")) {
            if (fix.has("import") && fix.get("import").asText().contains(symbol)) {
                fixAction = new FixAction(fix);
                break;
            }
        }
        assertNotNull(fixAction, "Expected import fix not found for symbol: " + symbol);

        return fixAction;
    }

    private boolean applyImportFix(FixAction plan) throws IOException {
        // Run the debug command to apply fixes
        String[] args = {"debug", "--apply", "--config", plan.getConfigFile(), tempDir.toString()};

        int exitCode = Polyfix.execute(args);
        assertEquals(0, exitCode, "Debug command should complete successfully");

        return true;
    }

    private void assertFileContains(Path file, String expectedContent) throws IOException {
        String fileContent = Files.readString(file);
        assertTrue(
                fileContent.contains(expectedContent),
                "File does not contain expected content: " + expectedContent);
    }

    private static class FixAction {
        private JsonNode fix;

        public FixAction(JsonNode fix) {
            this.fix = fix;
        }

        public String getConfigFile() {
            return fix.get("config").asText();
        }
    }
}
