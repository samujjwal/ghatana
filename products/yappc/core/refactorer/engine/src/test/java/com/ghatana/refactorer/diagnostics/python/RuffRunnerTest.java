package com.ghatana.refactorer.diagnostics.python;

import static org.junit.jupiter.api.Assertions.*;

import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import com.ghatana.refactorer.shared.process.ProcessResult;
import com.ghatana.refactorer.testutils.TestConfig;
import io.activej.promise.Promise;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**

 * @doc.type class

 * @doc.purpose Handles ruff runner test operations

 * @doc.layer core

 * @doc.pattern Test

 */

class RuffRunnerTest extends EventloopTestBase {
        @TempDir
        Path tempDir;
        private PolyfixProjectContext context;
        private FakeProcessRunner processRunner;
        private RuffRunner ruffRunner;

        private static final List<String> DEFAULT_IGNORE_PATTERNS = List.of("**/.venv/**", "**/venv/**",
                        "**/node_modules/**");

        @BeforeEach
        void setUp() {
                context = TestConfig.createTestContext(tempDir);
                processRunner = new FakeProcessRunner(context);
                ruffRunner = new RuffRunner(context, processRunner);
        }

        @Test
        void testRuffFindsSyntaxError() throws Exception {
                // Create a Python file with a syntax error
                Path pythonFile = tempDir.resolve("syntax_error.py");
                Files.writeString(pythonFile, "print('Hello world'  # Missing closing parenthesis\n");

                processRunner.when(
                                "ruff",
                                ruffArgs(false, "syntax_error.py"),
                                FakeProcessRunner.response(
                                                new ProcessResult(
                                                                1,
                                                                diagnosticsJson(
                                                                                "syntax_error.py",
                                                                                "E999",
                                                                                "SyntaxError: '(' was never closed",
                                                                                1,
                                                                                1,
                                                                                1,
                                                                                2),
                                                                "")));

                List<UnifiedDiagnostic> diagnostics = runPromise(() ->
                                ruffRunner.withIncludePatterns(List.of("syntax_error.py")).run());

                assertFalse(diagnostics.isEmpty(), "Should find at least one diagnostic");
                UnifiedDiagnostic first = diagnostics.get(0);
                assertEquals("syntax_error.py", first.getFile());
                assertTrue(first.getMessage().contains("SyntaxError"), "Should be a syntax error");
        }

        @Test
        void testRuffFindsStyleIssue() throws Exception {
                // Create a Python file with a style issue (unused import)
                Path pythonFile = tempDir.resolve("unused_import.py");
                Files.writeString(pythonFile, "import os\nprint('Hello world')\n");

                processRunner.when(
                                "ruff",
                                ruffArgs(false, "unused_import.py"),
                                FakeProcessRunner.response(
                                                new ProcessResult(
                                                                1,
                                                                diagnosticsJson(
                                                                                "unused_import.py",
                                                                                "F401",
                                                                                "unused import 'os'",
                                                                                1,
                                                                                1,
                                                                                1,
                                                                                1),
                                                                "")));

                List<UnifiedDiagnostic> diagnostics = runPromise(() ->
                                ruffRunner.withIncludePatterns(List.of("unused_import.py")).run());

                assertFalse(diagnostics.isEmpty(), "Should find at least one diagnostic");
                UnifiedDiagnostic first = diagnostics.get(0);
                assertEquals("unused_import.py", first.getFile());
                assertEquals("F401", first.getCode()); // F401 is unused import
        }

        @Test
        void testRuffFixesSimpleIssue() throws Exception {
                // Create a Python file with a fixable issue (unused import)
                Path pythonFile = tempDir.resolve("fixable.py");
                String originalCode = "import os\nprint('Hello world')\n";
                Files.writeString(pythonFile, originalCode);

                processRunner.when(
                                "ruff",
                                ruffArgs(false, "fixable.py"),
                                FakeProcessRunner.response(
                                                new ProcessResult(
                                                                1,
                                                                diagnosticsJson(
                                                                                "fixable.py", "F401",
                                                                                "unused import 'os'", 1, 1, 1, 1),
                                                                "")));

                // First run to get diagnostics
                List<UnifiedDiagnostic> diagnostics = runPromise(() ->
                                ruffRunner.withIncludePatterns(List.of("fixable.py")).run());

                assertFalse(diagnostics.isEmpty(), "Should find at least one diagnostic");

                // Run with fix enabled
                RuffRunner fixRunner = new RuffRunner(context, processRunner)
                                .withIncludePatterns(List.of("fixable.py"))
                                .withFix(true);

                processRunner.when(
                                "ruff",
                                ruffArgs(true, "fixable.py"),
                                FakeProcessRunner.response(
                                                new ProcessResult(0, "", ""), removeUnusedImport("fixable.py")));

                // Run the fix operation - we don't need the diagnostics, just want the side
                // effect
                runPromise(() -> fixRunner.run());

                // After fixing, the file should be modified
                String fixedCode = Files.readString(pythonFile);
                assertNotEquals(originalCode, fixedCode, "File should be modified after fix");
                assertFalse(fixedCode.contains("import os"), "Unused import should be removed");
        }

        @Test
        void testRuffWithNonexistentFile() throws Exception {
                // Should not throw, just return empty diagnostics
                processRunner.when(
                                "ruff",
                                ruffArgs(false, "nonexistent.py"),
                                FakeProcessRunner.response(new ProcessResult(0, "[]", "")));

                List<UnifiedDiagnostic> diagnostics = runPromise(() ->
                                ruffRunner.withIncludePatterns(List.of("nonexistent.py")).run());

                assertTrue(diagnostics.isEmpty(), "Should return empty diagnostics for nonexistent file");
        }

        @Test
        void testRuffWithInvalidPython() throws Exception {
                // Create invalid Python file
                Path pythonFile = tempDir.resolve("invalid.py");
                Files.writeString(pythonFile, "def missing_colon()\n    pass\n");

                processRunner.when(
                                "ruff",
                                ruffArgs(false, "invalid.py"),
                                FakeProcessRunner.response(
                                                new ProcessResult(
                                                                1,
                                                                diagnosticsJson(
                                                                                "invalid.py",
                                                                                "E999",
                                                                                "SyntaxError: expected ':'",
                                                                                1,
                                                                                1,
                                                                                1,
                                                                                2),
                                                                "")));

                List<UnifiedDiagnostic> diagnostics = runPromise(() ->
                                ruffRunner.withIncludePatterns(List.of("invalid.py")).run());

                assertFalse(diagnostics.isEmpty(), "Should find syntax error in invalid Python");
                assertTrue(
                                diagnostics.stream().anyMatch(d -> d.getCode().startsWith("E9")),
                                "Should have syntax error code (E9xx)");
        }

        private static List<String> ruffArgs(boolean fix, String... include) {
                List<String> args = new ArrayList<>();
                args.add("check");
                args.add("--format=json");
                if (fix) {
                        args.add("--fix");
                }
                for (String entry : include) {
                        args.add(entry);
                }
                if (!DEFAULT_IGNORE_PATTERNS.isEmpty()) {
                        args.add("--exclude");
                        args.add(String.join(",", DEFAULT_IGNORE_PATTERNS));
                }
                return args;
        }

        private static String diagnosticsJson(
                        String file,
                        String code,
                        String message,
                        int line,
                        int column,
                        int endLine,
                        int endColumn) {
                return "["
                                + String.format(
                                                "{\"code\":\"%s\",\"message\":\"%s\",\"location\":{\"file\":\"%s\",\"line\":%d,\"column\":%d},\"endLocation\":{\"file\":\"%s\",\"line\":%d,\"column\":%d}}",
                                                code, message, file, line, column, file, endLine, endColumn)
                                + "]";
        }

        private FakeProcessRunner.CommandEffect removeUnusedImport(String relativePath) {
                return (cwd, command) -> {
                        Path file = cwd.resolve(relativePath);
                        try {
                                List<String> lines = Files.readAllLines(file);
                                List<String> filtered = new ArrayList<>();
                                for (String line : lines) {
                                        if (!line.startsWith("import ")) {
                                                filtered.add(line);
                                        }
                                }
                                Files.write(file, filtered);
                        } catch (IOException e) {
                                fail("Failed to apply fake ruff fix: " + e.getMessage());
                        }
                };
        }
}
