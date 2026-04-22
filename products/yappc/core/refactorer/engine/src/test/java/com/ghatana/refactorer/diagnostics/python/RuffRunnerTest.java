package com.ghatana.refactorer.diagnostics.python;

import static org.junit.jupiter.api.Assertions.*;

import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import com.ghatana.refactorer.shared.process.ProcessResult;
import com.ghatana.refactorer.testutils.ConfigTestUtils;
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

        private static final List<String> DEFAULT_IGNORE_PATTERNS = List.of("**/.venv/**", "**/venv/**", // GH-90000
                        "**/node_modules/**");

        // Constants for duplicate literals
        private static final String SYNTAX_ERROR_PY = "syntax_error.py";
        private static final String UNUSED_IMPORT_PY = "unused_import.py";
        private static final String FIXABLE_PY = "fixable.py";
        private static final String INVALID_PY = "invalid.py";
        private static final String RUFF = "ruff";

        @BeforeEach
        void setUp() { // GH-90000
                context = ConfigTestUtils.createTestContext(tempDir); // GH-90000
                processRunner = new FakeProcessRunner(context); // GH-90000
                ruffRunner = new RuffRunner(context, processRunner); // GH-90000
        }

        @Test
        void testRuffFindsSyntaxError() throws Exception { // GH-90000
                // Create a Python file with a syntax error
                Path pythonFile = tempDir.resolve(SYNTAX_ERROR_PY); // GH-90000
                Files.writeString(pythonFile, "print('Hello world'  # Missing closing parenthesis\n"); // GH-90000

                processRunner.when( // GH-90000
                                RUFF,
                                ruffArgs(false, SYNTAX_ERROR_PY), // GH-90000
                                FakeProcessRunner.response( // GH-90000
                                                new ProcessResult( // GH-90000
                                                                1,
                                                                diagnosticsJson( // GH-90000
                                                                                SYNTAX_ERROR_PY,
                                                                                "E999",
                                                                                "SyntaxError: '(' was never closed", // GH-90000
                                                                                1,
                                                                                1,
                                                                                1,
                                                                                2),
                                                                "")));

                List<UnifiedDiagnostic> diagnostics = runPromise(() -> // GH-90000
                                ruffRunner.withIncludePatterns(List.of(SYNTAX_ERROR_PY)).run()); // GH-90000

                assertFalse(diagnostics.isEmpty(), "Should find at least one diagnostic"); // GH-90000
                UnifiedDiagnostic first = diagnostics.get(0); // GH-90000
                assertEquals(SYNTAX_ERROR_PY, first.getFile()); // GH-90000
                assertTrue(first.getMessage().contains("SyntaxError [GH-90000]"), "Should be a syntax error");
        }

        @Test
        void testRuffFindsStyleIssue() throws Exception { // GH-90000
                // Create a Python file with a style issue (unused import) // GH-90000
                Path pythonFile = tempDir.resolve(UNUSED_IMPORT_PY); // GH-90000
                Files.writeString(pythonFile, "import os\nprint('Hello world')\n"); // GH-90000

                processRunner.when( // GH-90000
                                RUFF,
                                ruffArgs(false, UNUSED_IMPORT_PY), // GH-90000
                                FakeProcessRunner.response( // GH-90000
                                                new ProcessResult( // GH-90000
                                                                1,
                                                                diagnosticsJson( // GH-90000
                                                                                UNUSED_IMPORT_PY,
                                                                                "F401",
                                                                                "unused import 'os'",
                                                                                1,
                                                                                1,
                                                                                1,
                                                                                1),
                                                                "")));

                List<UnifiedDiagnostic> diagnostics = runPromise(() -> // GH-90000
                                ruffRunner.withIncludePatterns(List.of(UNUSED_IMPORT_PY)).run()); // GH-90000

                assertFalse(diagnostics.isEmpty(), "Should find at least one diagnostic"); // GH-90000
                UnifiedDiagnostic first = diagnostics.get(0); // GH-90000
                assertEquals(UNUSED_IMPORT_PY, first.getFile()); // GH-90000
                assertEquals("F401", first.getCode()); // F401 is unused import // GH-90000
        }

        @Test
        void testRuffFixesSimpleIssue() throws Exception { // GH-90000
                // Create a Python file with a fixable issue (unused import) // GH-90000
                Path pythonFile = tempDir.resolve(FIXABLE_PY); // GH-90000
                String originalCode = "import os\nprint('Hello world')\n"; // GH-90000
                Files.writeString(pythonFile, originalCode); // GH-90000

                processRunner.when( // GH-90000
                                RUFF,
                                ruffArgs(false, FIXABLE_PY), // GH-90000
                                FakeProcessRunner.response( // GH-90000
                                                new ProcessResult( // GH-90000
                                                                1,
                                                                diagnosticsJson( // GH-90000
                                                                                FIXABLE_PY, "F401",
                                                                                "unused import 'os'", 1, 1, 1, 1),
                                                                "")));

                // First run to get diagnostics
                List<UnifiedDiagnostic> diagnostics = runPromise(() -> // GH-90000
                                ruffRunner.withIncludePatterns(List.of(FIXABLE_PY)).run()); // GH-90000

                assertFalse(diagnostics.isEmpty(), "Should find at least one diagnostic"); // GH-90000

                // Run with fix enabled
                RuffRunner fixRunner = new RuffRunner(context, processRunner) // GH-90000
                                .withIncludePatterns(List.of(FIXABLE_PY)) // GH-90000
                                .withFix(true); // GH-90000

                processRunner.when( // GH-90000
                                RUFF,
                                ruffArgs(true, FIXABLE_PY), // GH-90000
                                FakeProcessRunner.response( // GH-90000
                                                new ProcessResult(0, "", ""), removeUnusedImport(FIXABLE_PY))); // GH-90000

                // Run the fix operation - we don't need the diagnostics, just want the side
                // effect
                runPromise(() -> fixRunner.run()); // GH-90000

                // After fixing, the file should be modified
                String fixedCode = Files.readString(pythonFile); // GH-90000
                assertNotEquals(originalCode, fixedCode, "File should be modified after fix"); // GH-90000
                assertFalse(fixedCode.contains("import os [GH-90000]"), "Unused import should be removed");
        }

        @Test
        void testRuffWithNonexistentFile() throws Exception { // GH-90000
                // Should not throw, just return empty diagnostics
                processRunner.when( // GH-90000
                                RUFF,
                                ruffArgs(false, "nonexistent.py"), // GH-90000
                                FakeProcessRunner.response(new ProcessResult(0, "[]", ""))); // GH-90000

                List<UnifiedDiagnostic> diagnostics = runPromise(() -> // GH-90000
                                ruffRunner.withIncludePatterns(List.of("nonexistent.py [GH-90000]")).run());

                assertTrue(diagnostics.isEmpty(), "Should return empty diagnostics for nonexistent file"); // GH-90000
        }

        @Test
        void testRuffWithInvalidPython() throws Exception { // GH-90000
                // Create invalid Python file
                Path pythonFile = tempDir.resolve(INVALID_PY); // GH-90000
                Files.writeString(pythonFile, "def missing_colon()\n    pass\n"); // GH-90000

                processRunner.when( // GH-90000
                                RUFF,
                                ruffArgs(false, INVALID_PY), // GH-90000
                                FakeProcessRunner.response( // GH-90000
                                                new ProcessResult( // GH-90000
                                                                1,
                                                                diagnosticsJson( // GH-90000
                                                                                INVALID_PY,
                                                                                "E999",
                                                                                "SyntaxError: expected ':'",
                                                                                1,
                                                                                1,
                                                                                1,
                                                                                2),
                                                                "")));

                List<UnifiedDiagnostic> diagnostics = runPromise(() -> // GH-90000
                                ruffRunner.withIncludePatterns(List.of(INVALID_PY)).run()); // GH-90000

                assertFalse(diagnostics.isEmpty(), "Should find syntax error in invalid Python"); // GH-90000
                assertTrue( // GH-90000
                                diagnostics.stream().anyMatch(d -> d.getCode().startsWith("E9 [GH-90000]")),
                                "Should have syntax error code (E9xx)"); // GH-90000
        }

        private static List<String> ruffArgs(boolean fix, String... include) { // GH-90000
                List<String> args = new ArrayList<>(); // GH-90000
                args.add("check [GH-90000]");
                args.add("--format=json [GH-90000]");
                if (fix) { // GH-90000
                        args.add("--fix [GH-90000]");
                }
                for (String entry : include) { // GH-90000
                        args.add(entry); // GH-90000
                }
                if (!DEFAULT_IGNORE_PATTERNS.isEmpty()) { // GH-90000
                        args.add("--exclude [GH-90000]");
                        args.add(String.join(",", DEFAULT_IGNORE_PATTERNS)); // GH-90000
                }
                return args;
        }

        private static String diagnosticsJson( // GH-90000
                        String file,
                        String code,
                        String message,
                        int line,
                        int column,
                        int endLine,
                        int endColumn) {
                return "["
                                + String.format( // GH-90000
                                                "{\"code\":\"%s\",\"message\":\"%s\",\"location\":{\"file\":\"%s\",\"line\":%d,\"column\":%d},\"endLocation\":{\"file\":\"%s\",\"line\":%d,\"column\":%d}}",
                                                code, message, file, line, column, file, endLine, endColumn)
                                + "]";
        }

        private FakeProcessRunner.CommandEffect removeUnusedImport(String relativePath) { // GH-90000
                return (cwd, command) -> { // GH-90000
                        Path file = cwd.resolve(relativePath); // GH-90000
                        try {
                                List<String> lines = Files.readAllLines(file); // GH-90000
                                List<String> filtered = new ArrayList<>(); // GH-90000
                                for (String line : lines) { // GH-90000
                                        if (!line.startsWith("import  [GH-90000]")) {
                                                filtered.add(line); // GH-90000
                                        }
                                }
                                Files.write(file, filtered); // GH-90000
                        } catch (IOException e) { // GH-90000
                                fail("Failed to apply fake ruff fix: " + e.getMessage()); // GH-90000
                        }
                };
        }
}
