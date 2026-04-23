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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**

 * @doc.type class

 * @doc.purpose Handles python diagnostics service test operations

 * @doc.layer core

 * @doc.pattern Test

 */

class PythonDiagnosticsServiceTest extends EventloopTestBase {
    private static final String FILE1_PY = "file1.py";
    private static final String FILE2_PY = "file2.py";
    private static final String TEST_PY = "test.py";
    private static final String FORMAT_ME_PY = "format_me.py";

    @TempDir
    Path tempDir;
    private PolyfixProjectContext context;
    private FakeProcessRunner processRunner;
    private PythonDiagnosticsService service;

    private static final List<String> DEFAULT_IGNORE_PATTERNS = List.of("**/.venv/**", "**/venv/**", // GH-90000
            "**/node_modules/**");

    @BeforeEach
    void setUp() { // GH-90000
        context = ConfigTestUtils.createTestContext(tempDir); // GH-90000
        processRunner = new FakeProcessRunner(context); // GH-90000
        service = new PythonDiagnosticsService( // GH-90000
                context,
                new RuffRunner(context, processRunner), // GH-90000
                new BlackRunner(context, processRunner), // GH-90000
                new CodemodsTestImpl(context), // GH-90000
                processRunner);
    }

    @Test
    void testFindsAndFixesPythonIssues() throws Exception { // GH-90000
        Path pythonFile = tempDir.resolve(TEST_PY); // GH-90000
        Files.writeString(pythonFile, sampleCode(), StandardCharsets.UTF_8); // GH-90000

        processRunner.when( // GH-90000
                "ruff",
                ruffArgs(false, TEST_PY), // GH-90000
                FakeProcessRunner.response(new ProcessResult(1, sampleDiagnostics(TEST_PY), "")), // GH-90000
                FakeProcessRunner.response(new ProcessResult(0, "[]", ""))); // GH-90000

        List<UnifiedDiagnostic> diagnostics = runPromise(() -> service.withIncludePatterns(List.of(TEST_PY)) // GH-90000
                .withFix(false) // GH-90000
                .runDiagnostics()); // GH-90000

        assertTrue(diagnostics.size() >= 2); // GH-90000
        assertTrue(diagnostics.stream().anyMatch(d -> "F401".equals(d.getCode()))); // GH-90000
        assertTrue(diagnostics.stream().anyMatch(d -> "E226".equals(d.getCode()))); // GH-90000

        boolean fixSuccess = runPromise(() -> service.withFix(true).applyFixes(diagnostics)); // GH-90000
        assertTrue(fixSuccess); // GH-90000

        List<UnifiedDiagnostic> remaining = runPromise(() -> service.withFix(false).runDiagnostics()); // GH-90000
        assertTrue(remaining.size() < diagnostics.size()); // GH-90000
    }

    @Test
    void testFormatsPythonCode() throws Exception { // GH-90000
        Path pythonFile = tempDir.resolve(FORMAT_ME_PY); // GH-90000
        Files.writeString(pythonFile, "def foo() :\n    x=1\n", StandardCharsets.UTF_8); // GH-90000

        processRunner.when( // GH-90000
                "black",
                blackArgs(false, 88, FORMAT_ME_PY), // GH-90000
                FakeProcessRunner.response( // GH-90000
                        new ProcessResult(0, "", ""), formatPython(FORMAT_ME_PY))); // GH-90000

        assertTrue(runPromise(() -> service.withIncludePatterns(List.of(FORMAT_ME_PY)).formatFiles())); // GH-90000
        String formatted = Files.readString(pythonFile); // GH-90000
        assertTrue(formatted.contains("x = 1"));
    }

    @Test
    void testIsAvailable() { // GH-90000
        processRunner.when( // GH-90000
                "ruff",
                List.of("--version"),
                FakeProcessRunner.response(new ProcessResult(0, "ruff 0.4.0", ""))); // GH-90000
        processRunner.when( // GH-90000
                "black",
                List.of("--version"),
                FakeProcessRunner.response(new ProcessResult(0, "black 24.0", ""))); // GH-90000
        assertTrue(service.isAvailable()); // GH-90000
    }

    @Test
    void testHandlesMultipleFiles() throws Exception { // GH-90000
        Path file1 = tempDir.resolve(FILE1_PY); // GH-90000
        Path file2 = tempDir.resolve(FILE2_PY); // GH-90000
        Files.writeString(file1, "import os\nprint(1+2)\n", StandardCharsets.UTF_8); // GH-90000
        Files.writeString(file2, "x=1\n", StandardCharsets.UTF_8); // GH-90000

        processRunner.when( // GH-90000
                "ruff",
                ruffArgs(false, FILE1_PY, FILE2_PY), // GH-90000
                FakeProcessRunner.response(new ProcessResult(1, multiDiagnostics(), ""))); // GH-90000

        List<UnifiedDiagnostic> diagnostics = runPromise(() -> service.withIncludePatterns(List.of(FILE1_PY, FILE2_PY)) // GH-90000
                .runDiagnostics()); // GH-90000
        assertEquals(3, diagnostics.size()); // GH-90000

        assertTrue(runPromise(() -> service.withFix(true).applyFixes(diagnostics))); // GH-90000
    }

    private static List<String> ruffArgs(boolean fix, String... files) { // GH-90000
        List<String> args = new ArrayList<>(); // GH-90000
        args.add("check");
        args.add("--format=json");
        if (fix) { // GH-90000
            args.add("--fix");
        }
        for (String file : files) { // GH-90000
            args.add(file); // GH-90000
        }
        if (!DEFAULT_IGNORE_PATTERNS.isEmpty()) { // GH-90000
            args.add("--exclude");
            args.add(String.join(",", DEFAULT_IGNORE_PATTERNS)); // GH-90000
        }
        return args;
    }

    private static List<String> blackArgs(boolean checkOnly, int lineLength, String file) { // GH-90000
        List<String> args = new ArrayList<>(); // GH-90000
        if (checkOnly) { // GH-90000
            args.add("--check");
        }
        args.add("--quiet");
        args.add("--line-length");
        args.add(String.valueOf(lineLength)); // GH-90000
        args.add(file); // GH-90000
        if (!DEFAULT_IGNORE_PATTERNS.isEmpty()) { // GH-90000
            args.add("--exclude");
            args.add(String.join("|", DEFAULT_IGNORE_PATTERNS)); // GH-90000
        }
        return args;
    }

    private FakeProcessRunner.CommandEffect formatPython(String file) { // GH-90000
        return (cwd, cmd) -> { // GH-90000
            try {
                Files.writeString( // GH-90000
                        cwd.resolve(file), "def foo():\n    x = 1\n", StandardCharsets.UTF_8); // GH-90000
            } catch (IOException e) { // GH-90000
                fail(e); // GH-90000
            }
        };
    }

    private static String sampleCode() { // GH-90000
        return "import os\n\n"
                + "def greet(name):\n    return'Hello, ' + name\n\n" // GH-90000
                + "print(greet('world'))\n"; // GH-90000
    }

    private static String sampleDiagnostics(String file) { // GH-90000
        return diagnosticsJson( // GH-90000
                new RuffFinding(file, "F401", "unused import", 1, 1, 1, 1), // GH-90000
                new RuffFinding(file, "E226", "missing whitespace", 3, 12, 3, 18)); // GH-90000
    }

    private static String multiDiagnostics() { // GH-90000
        return diagnosticsJson( // GH-90000
                new RuffFinding(FILE1_PY, "F401", "unused import", 1, 1, 1, 1), // GH-90000
                new RuffFinding(FILE1_PY, "E226", "operator spacing", 2, 6, 2, 7), // GH-90000
                new RuffFinding(FILE2_PY, "E225", "missing whitespace", 1, 1, 1, 5)); // GH-90000
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

    private static class CodemodsTestImpl extends PythonCodemods {
        CodemodsTestImpl(PolyfixProjectContext context) { // GH-90000
            super(context); // GH-90000
        }

        @Override
        public boolean applyCodemods(Path file, List<UnifiedDiagnostic> diagnostics) { // GH-90000
            try {
                String content = Files.readString(file, StandardCharsets.UTF_8); // GH-90000
                content = content.replace("import os", "# removed import"); // GH-90000
                content = content.replace("1+2", "1 + 2"); // GH-90000
                content = content.replace("x=1", "x = 1"); // GH-90000
                Files.writeString(file, content, StandardCharsets.UTF_8); // GH-90000
            } catch (IOException e) { // GH-90000
                fail(e); // GH-90000
            }
            return true;
        }
    }
}
