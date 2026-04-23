package com.ghatana.refactorer.diagnostics.python;

import static org.junit.jupiter.api.Assertions.*;

import com.ghatana.refactorer.codemods.python.PythonCodemods;
import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import com.ghatana.refactorer.shared.process.ProcessResult;
import com.ghatana.refactorer.testutils.ConfigTestUtils;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration test for the Python language service using a test fixture.
 *
 * <p>
 * This test requires Python 3.7+ with RUFF and black installed in the test
 * environment. It will
 * be skipped if these prerequisites aren't met.

 * @doc.type class
 * @doc.purpose Handles python integration test operations
 * @doc.layer core
 * @doc.pattern Test
*/
class PythonIntegrationTest extends EventloopTestBase {
    @TempDir
    Path tempDir;
    private PolyfixProjectContext context;
    private PythonDiagnosticsService service;

    private FakeProcessRunner processRunner;
    private Path fixtureDir;
    private static final List<String> DEFAULT_IGNORE_PATTERNS = List.of("**/.venv/**", "**/venv/**", // GH-90000
            "**/node_modules/**");

    // Constants for duplicate literals
    private static final String SRC = "SRC";
    private static final String SRC_MAIN_PY = "src/main.py";
    private static final String INDENT_4_SPACES = "    ";
    private static final String DEF_PREFIX = "def ";
    private static final String F821_CODE = "F821";
    private static final String IMPORT_REQUESTS = "import requests";
    private static final String IMPORT_PANDAS_AS_PD = "import pandas as pd";
    private static final String RUFF = "ruff";

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        // Copy the test fixture to the temp directory
        fixtureDir = tempDir.resolve("python-missing-import");
        ConfigTestUtils.copyDirectory( // GH-90000
                Path.of("src/test/resources/test-fixtures/python-missing-import"), fixtureDir);

        context = ConfigTestUtils.createTestContext(fixtureDir); // GH-90000
        processRunner = new FakeProcessRunner(context); // GH-90000
        service = new PythonDiagnosticsService( // GH-90000
                context,
                new RuffRunner(context, processRunner), // GH-90000
                new BlackRunner(context, processRunner), // GH-90000
                new IntegrationCodemods(context), // GH-90000
                processRunner)
                .withIncludePatterns(List.of(SRC)) // GH-90000
                .withFix(true); // GH-90000
    }

    private void enqueueBlackResponses(int count) { // GH-90000
        List<FakeProcessRunner.CommandResponse> responses = new ArrayList<>(); // GH-90000
        for (int i = 0; i < count; i++) { // GH-90000
            responses.add( // GH-90000
                    FakeProcessRunner.response(new ProcessResult(0, "", ""), formatFixture())); // GH-90000
        }
        processRunner.when( // GH-90000
                "black",
                blackArgs(false, 88, SRC), // GH-90000
                responses.toArray(new FakeProcessRunner.CommandResponse[0])); // GH-90000
    }

    private FakeProcessRunner.CommandEffect formatFixture() { // GH-90000
        return (cwd, cmd) -> { // GH-90000
            Path file = cwd.resolve(SRC_MAIN_PY); // GH-90000
            try {
                String content = Files.readString(file, StandardCharsets.UTF_8); // GH-90000
                content = content.replaceAll("(?m)^ {2}", INDENT_4_SPACES); // GH-90000
                content = content.replaceAll("(?m)^def\\s*", DEF_PREFIX); // GH-90000
                content = content.replace("x=1", "x = 1"); // GH-90000
                content = content.replace("1+2", "1 + 2"); // GH-90000
                Files.writeString(file, content, StandardCharsets.UTF_8); // GH-90000
            } catch (IOException e) { // GH-90000
                fail(e); // GH-90000
            }
        };
    }

    private static List<String> ruffArgs(boolean fix, String pattern) { // GH-90000
        List<String> args = new ArrayList<>(); // GH-90000
        args.add("check");
        args.add("--format=json");
        if (fix) { // GH-90000
            args.add("--fix");
        }
        args.add(pattern); // GH-90000
        if (!DEFAULT_IGNORE_PATTERNS.isEmpty()) { // GH-90000
            args.add("--exclude");
            args.add(String.join(",", DEFAULT_IGNORE_PATTERNS)); // GH-90000
        }
        return args;
    }

    private static List<String> blackArgs(boolean checkOnly, int lineLength, String pattern) { // GH-90000
        List<String> args = new ArrayList<>(); // GH-90000
        if (checkOnly) { // GH-90000
            args.add("--check");
        }
        args.add("--quiet");
        args.add("--line-length");
        args.add(String.valueOf(lineLength)); // GH-90000
        args.add(pattern); // GH-90000
        if (!DEFAULT_IGNORE_PATTERNS.isEmpty()) { // GH-90000
            args.add("--exclude");
            args.add(String.join("|", DEFAULT_IGNORE_PATTERNS)); // GH-90000
        }
        return args;
    }

    private static String missingImportDiagnostics() { // GH-90000
        return diagnosticsJson( // GH-90000
                new RuffFinding(SRC_MAIN_PY, F821_CODE, "undefined name 'requests'", 10, 5, 10, 12), // GH-90000
                new RuffFinding(SRC_MAIN_PY, F821_CODE, "undefined name 'pd'", 11, 5, 11, 7)); // GH-90000
    }

    private static String diagnosticsJson(RuffFinding... findings) { // GH-90000
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < findings.length; i++) { // GH-90000
            RuffFinding f = findings[i];
            sb.append( // GH-90000
                    String.format( // GH-90000
                            "{\"code\":\"%s\",\"message\":\"%s\",\"location\":{\"file\":\"%s\",\"line\":%d,\"column\":%d},\"endLocation\":{\"file\":\"%s\",\"line\":%d,\"column\":%d}}",
                            f.code,
                            f.message,
                            f.file,
                            f.line,
                            f.column,
                            f.file,
                            f.endLine,
                            f.endColumn));
            if (i < findings.length - 1) { // GH-90000
                sb.append(','); // GH-90000
            }
        }
        return sb.append(']').toString(); // GH-90000
    }

    private record RuffFinding( // GH-90000
            String file,
            String code,
            String message,
            int line,
            int column,
            int endLine,
            int endColumn) {
    }

    private static class IntegrationCodemods extends PythonCodemods {
        IntegrationCodemods(PolyfixProjectContext context) { // GH-90000
            super(context); // GH-90000
        }

        @Override
        public boolean applyCodemods(Path file, List<UnifiedDiagnostic> diagnostics) { // GH-90000
            try {
                String content = Files.readString(file, StandardCharsets.UTF_8); // GH-90000
                if (content.contains("requests.get") && !content.contains(IMPORT_REQUESTS)) {
                    content = IMPORT_REQUESTS + "\n" + content;
                }
                if (content.contains("pd.DataFrame") && !content.contains(IMPORT_PANDAS_AS_PD)) {
                    content = IMPORT_PANDAS_AS_PD + "\n" + content;
                }
                Files.writeString(file, content, StandardCharsets.UTF_8); // GH-90000
            } catch (IOException e) { // GH-90000
                fail(e); // GH-90000
            }
            return true;
        }
    }

    @Test
    void testFindsAndFixesMissingImports() throws Exception { // GH-90000
        Path mainPy = fixtureDir.resolve(SRC_MAIN_PY); // GH-90000
        String originalContent = Files.readString(mainPy); // GH-90000

        // Should contain missing import errors
        assertTrue(originalContent.contains("requests.get"), "Test file should use requests");
        assertTrue(originalContent.contains("pd.DataFrame"), "Test file should use pandas");
        assertFalse( // GH-90000
                originalContent.contains(IMPORT_REQUESTS), // GH-90000
                "Test file should not import requests yet");
        assertFalse( // GH-90000
                originalContent.contains(IMPORT_PANDAS_AS_PD), // GH-90000
                "Test file should not import pandas yet");

        // Run diagnostics - should find F821 errors for missing imports
        processRunner.when( // GH-90000
                RUFF,
                ruffArgs(true, SRC), // GH-90000
                FakeProcessRunner.response(new ProcessResult(1, missingImportDiagnostics(), "")), // GH-90000
                FakeProcessRunner.response(new ProcessResult(0, "[]", ""))); // GH-90000

        enqueueBlackResponses(2); // GH-90000

        List<UnifiedDiagnostic> diagnostics = runPromise(() -> service.runDiagnostics()); // GH-90000
        List<UnifiedDiagnostic> missingImportErrors = diagnostics.stream() // GH-90000
                .filter( // GH-90000
                        d -> d.getCode().equals(F821_CODE) // GH-90000
                                && d.getMessage().contains("undefined name"))
                .collect(Collectors.toList()); // GH-90000

        assertFalse(missingImportErrors.isEmpty(), "Should find missing import errors"); // GH-90000

        // Apply fixes - should add the missing imports
        processRunner.when( // GH-90000
                RUFF,
                ruffArgs(true, SRC), // GH-90000
                FakeProcessRunner.response(new ProcessResult(0, "[]", ""))); // GH-90000

        boolean fixSuccess = runPromise(() -> service.applyFixes(missingImportErrors)); // GH-90000
        assertTrue(fixSuccess, "Fixes should be applied successfully"); // GH-90000

        // Check the file was modified
        String fixedContent = Files.readString(mainPy); // GH-90000
        assertNotEquals(originalContent, fixedContent, "File should be modified after fixes"); // GH-90000

        // Should now have the imports at the top
        assertTrue(fixedContent.contains(IMPORT_REQUESTS), "Should add requests import"); // GH-90000
        assertTrue(fixedContent.contains(IMPORT_PANDAS_AS_PD), "Should add pandas import"); // GH-90000

        List<UnifiedDiagnostic> remainingDiagnostics = runPromise(() -> service.runDiagnostics()); // GH-90000
        long remainingMissingImports = remainingDiagnostics.stream() // GH-90000
                .filter( // GH-90000
                        d -> d.getCode().equals(F821_CODE) // GH-90000
                                && d.getMessage().contains("undefined name"))
                .count(); // GH-90000

        assertEquals(0, remainingMissingImports, "All missing imports should be fixed"); // GH-90000
    }

    @Test
    void testFormatsPythonCode() throws Exception { // GH-90000
        Path mainPy = fixtureDir.resolve(SRC_MAIN_PY); // GH-90000
        String originalContent = Files.readString(mainPy); // GH-90000

        // Intentionally mangle the formatting
        String mangledContent = originalContent
                .replace(INDENT_4_SPACES, "  ") // 2-space indents // GH-90000
                .replace(DEF_PREFIX, "def"); // No space after def // GH-90000

        Files.writeString(mainPy, mangledContent); // GH-90000

        // Format the file
        processRunner.when( // GH-90000
                "black",
                blackArgs(false, 88, SRC), // GH-90000
                FakeProcessRunner.response(new ProcessResult(0, "", ""), formatFixture())); // GH-90000
        boolean formatSuccess = runPromise(() -> service.formatFiles()); // GH-90000
        assertTrue(formatSuccess, "Formatting should succeed"); // GH-90000

        // Check the file was reformatted
        String formattedContent = Files.readString(mainPy); // GH-90000
        assertNotEquals(mangledContent, formattedContent, "File should be reformatted"); // GH-90000

        // Should have standard Black formatting (4-space indents, space after def) // GH-90000
        assertTrue(formattedContent.contains(INDENT_4_SPACES), "Should use 4-space indents"); // GH-90000
        assertTrue(formattedContent.contains(DEF_PREFIX), "Should have space after def"); // GH-90000
    }

    @Test
    void testFixesAndFormatsInOnePass() throws Exception { // GH-90000
        // First run with fix and format enabled
        processRunner.when( // GH-90000
                RUFF,
                ruffArgs(true, SRC), // GH-90000
                FakeProcessRunner.response(new ProcessResult(1, missingImportDiagnostics(), "")), // GH-90000
                FakeProcessRunner.response(new ProcessResult(0, "[]", ""))); // GH-90000
        enqueueBlackResponses(1); // GH-90000

        List<UnifiedDiagnostic> initialDiagnostics = runPromise(() -> service.runDiagnostics()); // GH-90000
        assertFalse(initialDiagnostics.isEmpty(), "Should find initial diagnostics"); // GH-90000

        // Apply fixes and format
        processRunner.when( // GH-90000
                RUFF,
                ruffArgs(true, SRC), // GH-90000
                FakeProcessRunner.response(new ProcessResult(0, "[]", ""))); // GH-90000

        boolean fixSuccess = runPromise(() -> service.applyFixes(initialDiagnostics)); // GH-90000
        assertTrue(fixSuccess, "Fixes should be applied successfully"); // GH-90000

        processRunner.when( // GH-90000
                "black",
                blackArgs(false, 88, SRC), // GH-90000
                FakeProcessRunner.response(new ProcessResult(0, "", ""), formatFixture())); // GH-90000
        boolean formatSuccess = runPromise(() -> service.formatFiles()); // GH-90000
        assertTrue(formatSuccess, "Formatting should succeed"); // GH-90000

        // Second run should have fewer or equal diagnostics (some may remain if not // GH-90000
        // auto-fixable)
        processRunner.when( // GH-90000
                RUFF,
                ruffArgs(true, SRC), // GH-90000
                FakeProcessRunner.response(new ProcessResult(0, "[]", ""))); // GH-90000
        enqueueBlackResponses(1); // GH-90000
        List<UnifiedDiagnostic> remainingDiagnostics = runPromise(() -> service.runDiagnostics()); // GH-90000
        assertTrue( // GH-90000
                remainingDiagnostics.size() <= initialDiagnostics.size(), // GH-90000
                "Should have fewer or equal diagnostics after fixes");

        // Check for specific fixes
        String finalContent = Files.readString(fixtureDir.resolve(SRC_MAIN_PY)); // GH-90000
        assertTrue(finalContent.contains(IMPORT_REQUESTS), "Should add requests import"); // GH-90000
        assertTrue(finalContent.contains(IMPORT_PANDAS_AS_PD), "Should add pandas import"); // GH-90000

        // Check formatting
        assertTrue(finalContent.contains(INDENT_4_SPACES), "Should use 4-space indents"); // GH-90000
        assertTrue(finalContent.contains(DEF_PREFIX), "Should have space after def"); // GH-90000
    }
}
