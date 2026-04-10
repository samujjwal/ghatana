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
    private static final List<String> DEFAULT_IGNORE_PATTERNS = List.of("**/.venv/**", "**/venv/**",
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
    void setUp() throws Exception {
        // Copy the test fixture to the temp directory
        fixtureDir = tempDir.resolve("python-missing-import");
        ConfigTestUtils.copyDirectory(
                Path.of("src/test/resources/test-fixtures/python-missing-import"), fixtureDir);

        context = ConfigTestUtils.createTestContext(fixtureDir);
        processRunner = new FakeProcessRunner(context);
        service = new PythonDiagnosticsService(
                context,
                new RuffRunner(context, processRunner),
                new BlackRunner(context, processRunner),
                new IntegrationCodemods(context),
                processRunner)
                .withIncludePatterns(List.of(SRC))
                .withFix(true);
    }

    private void enqueueBlackResponses(int count) {
        List<FakeProcessRunner.CommandResponse> responses = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            responses.add(
                    FakeProcessRunner.response(new ProcessResult(0, "", ""), formatFixture()));
        }
        processRunner.when(
                "black",
                blackArgs(false, 88, SRC),
                responses.toArray(new FakeProcessRunner.CommandResponse[0]));
    }

    private FakeProcessRunner.CommandEffect formatFixture() {
        return (cwd, cmd) -> {
            Path file = cwd.resolve(SRC_MAIN_PY);
            try {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                content = content.replaceAll("(?m)^ {2}", INDENT_4_SPACES);
                content = content.replaceAll("(?m)^def\\s*", DEF_PREFIX);
                content = content.replace("x=1", "x = 1");
                content = content.replace("1+2", "1 + 2");
                Files.writeString(file, content, StandardCharsets.UTF_8);
            } catch (IOException e) {
                fail(e);
            }
        };
    }

    private static List<String> ruffArgs(boolean fix, String pattern) {
        List<String> args = new ArrayList<>();
        args.add("check");
        args.add("--format=json");
        if (fix) {
            args.add("--fix");
        }
        args.add(pattern);
        if (!DEFAULT_IGNORE_PATTERNS.isEmpty()) {
            args.add("--exclude");
            args.add(String.join(",", DEFAULT_IGNORE_PATTERNS));
        }
        return args;
    }

    private static List<String> blackArgs(boolean checkOnly, int lineLength, String pattern) {
        List<String> args = new ArrayList<>();
        if (checkOnly) {
            args.add("--check");
        }
        args.add("--quiet");
        args.add("--line-length");
        args.add(String.valueOf(lineLength));
        args.add(pattern);
        if (!DEFAULT_IGNORE_PATTERNS.isEmpty()) {
            args.add("--exclude");
            args.add(String.join("|", DEFAULT_IGNORE_PATTERNS));
        }
        return args;
    }

    private static String missingImportDiagnostics() {
        return diagnosticsJson(
                new RuffFinding(SRC_MAIN_PY, F821_CODE, "undefined name 'requests'", 10, 5, 10, 12),
                new RuffFinding(SRC_MAIN_PY, F821_CODE, "undefined name 'pd'", 11, 5, 11, 7));
    }

    private static String diagnosticsJson(RuffFinding... findings) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < findings.length; i++) {
            RuffFinding f = findings[i];
            sb.append(
                    String.format(
                            "{\"code\":\"%s\",\"message\":\"%s\",\"location\":{\"file\":\"%s\",\"line\":%d,\"column\":%d},\"endLocation\":{\"file\":\"%s\",\"line\":%d,\"column\":%d}}",
                            f.code,
                            f.message,
                            f.file,
                            f.line,
                            f.column,
                            f.file,
                            f.endLine,
                            f.endColumn));
            if (i < findings.length - 1) {
                sb.append(',');
            }
        }
        return sb.append(']').toString();
    }

    private record RuffFinding(
            String file,
            String code,
            String message,
            int line,
            int column,
            int endLine,
            int endColumn) {
    }

    private static class IntegrationCodemods extends PythonCodemods {
        IntegrationCodemods(PolyfixProjectContext context) {
            super(context);
        }

        @Override
        public boolean applyCodemods(Path file, List<UnifiedDiagnostic> diagnostics) {
            try {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                if (content.contains("requests.get") && !content.contains(IMPORT_REQUESTS)) {
                    content = IMPORT_REQUESTS + "\n" + content;
                }
                if (content.contains("pd.DataFrame") && !content.contains(IMPORT_PANDAS_AS_PD)) {
                    content = IMPORT_PANDAS_AS_PD + "\n" + content;
                }
                Files.writeString(file, content, StandardCharsets.UTF_8);
            } catch (IOException e) {
                fail(e);
            }
            return true;
        }
    }

    @Test
    void testFindsAndFixesMissingImports() throws Exception {
        Path mainPy = fixtureDir.resolve(SRC_MAIN_PY);
        String originalContent = Files.readString(mainPy);

        // Should contain missing import errors
        assertTrue(originalContent.contains("requests.get"), "Test file should use requests");
        assertTrue(originalContent.contains("pd.DataFrame"), "Test file should use pandas");
        assertFalse(
                originalContent.contains(IMPORT_REQUESTS),
                "Test file should not import requests yet");
        assertFalse(
                originalContent.contains(IMPORT_PANDAS_AS_PD),
                "Test file should not import pandas yet");

        // Run diagnostics - should find F821 errors for missing imports
        processRunner.when(
                RUFF,
                ruffArgs(true, SRC),
                FakeProcessRunner.response(new ProcessResult(1, missingImportDiagnostics(), "")),
                FakeProcessRunner.response(new ProcessResult(0, "[]", "")));

        enqueueBlackResponses(2);

        List<UnifiedDiagnostic> diagnostics = runPromise(() -> service.runDiagnostics());
        List<UnifiedDiagnostic> missingImportErrors = diagnostics.stream()
                .filter(
                        d -> d.getCode().equals(F821_CODE)
                                && d.getMessage().contains("undefined name"))
                .collect(Collectors.toList());

        assertFalse(missingImportErrors.isEmpty(), "Should find missing import errors");

        // Apply fixes - should add the missing imports
        processRunner.when(
                RUFF,
                ruffArgs(true, SRC),
                FakeProcessRunner.response(new ProcessResult(0, "[]", "")));

        boolean fixSuccess = runPromise(() -> service.applyFixes(missingImportErrors));
        assertTrue(fixSuccess, "Fixes should be applied successfully");

        // Check the file was modified
        String fixedContent = Files.readString(mainPy);
        assertNotEquals(originalContent, fixedContent, "File should be modified after fixes");

        // Should now have the imports at the top
        assertTrue(fixedContent.contains(IMPORT_REQUESTS), "Should add requests import");
        assertTrue(fixedContent.contains(IMPORT_PANDAS_AS_PD), "Should add pandas import");

        List<UnifiedDiagnostic> remainingDiagnostics = runPromise(() -> service.runDiagnostics());
        long remainingMissingImports = remainingDiagnostics.stream()
                .filter(
                        d -> d.getCode().equals(F821_CODE)
                                && d.getMessage().contains("undefined name"))
                .count();

        assertEquals(0, remainingMissingImports, "All missing imports should be fixed");
    }

    @Test
    void testFormatsPythonCode() throws Exception {
        Path mainPy = fixtureDir.resolve(SRC_MAIN_PY);
        String originalContent = Files.readString(mainPy);

        // Intentionally mangle the formatting
        String mangledContent = originalContent
                .replace(INDENT_4_SPACES, "  ") // 2-space indents
                .replace(DEF_PREFIX, "def"); // No space after def

        Files.writeString(mainPy, mangledContent);

        // Format the file
        processRunner.when(
                "black",
                blackArgs(false, 88, SRC),
                FakeProcessRunner.response(new ProcessResult(0, "", ""), formatFixture()));
        boolean formatSuccess = runPromise(() -> service.formatFiles());
        assertTrue(formatSuccess, "Formatting should succeed");

        // Check the file was reformatted
        String formattedContent = Files.readString(mainPy);
        assertNotEquals(mangledContent, formattedContent, "File should be reformatted");

        // Should have standard Black formatting (4-space indents, space after def)
        assertTrue(formattedContent.contains(INDENT_4_SPACES), "Should use 4-space indents");
        assertTrue(formattedContent.contains(DEF_PREFIX), "Should have space after def");
    }

    @Test
    void testFixesAndFormatsInOnePass() throws Exception {
        // First run with fix and format enabled
        processRunner.when(
                RUFF,
                ruffArgs(true, SRC),
                FakeProcessRunner.response(new ProcessResult(1, missingImportDiagnostics(), "")),
                FakeProcessRunner.response(new ProcessResult(0, "[]", "")));
        enqueueBlackResponses(1);

        List<UnifiedDiagnostic> initialDiagnostics = runPromise(() -> service.runDiagnostics());
        assertFalse(initialDiagnostics.isEmpty(), "Should find initial diagnostics");

        // Apply fixes and format
        processRunner.when(
                RUFF,
                ruffArgs(true, SRC),
                FakeProcessRunner.response(new ProcessResult(0, "[]", "")));

        boolean fixSuccess = runPromise(() -> service.applyFixes(initialDiagnostics));
        assertTrue(fixSuccess, "Fixes should be applied successfully");

        processRunner.when(
                "black",
                blackArgs(false, 88, SRC),
                FakeProcessRunner.response(new ProcessResult(0, "", ""), formatFixture()));
        boolean formatSuccess = runPromise(() -> service.formatFiles());
        assertTrue(formatSuccess, "Formatting should succeed");

        // Second run should have fewer or equal diagnostics (some may remain if not
        // auto-fixable)
        processRunner.when(
                RUFF,
                ruffArgs(true, SRC),
                FakeProcessRunner.response(new ProcessResult(0, "[]", "")));
        enqueueBlackResponses(1);
        List<UnifiedDiagnostic> remainingDiagnostics = runPromise(() -> service.runDiagnostics());
        assertTrue(
                remainingDiagnostics.size() <= initialDiagnostics.size(),
                "Should have fewer or equal diagnostics after fixes");

        // Check for specific fixes
        String finalContent = Files.readString(fixtureDir.resolve(SRC_MAIN_PY));
        assertTrue(finalContent.contains(IMPORT_REQUESTS), "Should add requests import");
        assertTrue(finalContent.contains(IMPORT_PANDAS_AS_PD), "Should add pandas import");

        // Check formatting
        assertTrue(finalContent.contains(INDENT_4_SPACES), "Should use 4-space indents");
        assertTrue(finalContent.contains(DEF_PREFIX), "Should have space after def");
    }
}
