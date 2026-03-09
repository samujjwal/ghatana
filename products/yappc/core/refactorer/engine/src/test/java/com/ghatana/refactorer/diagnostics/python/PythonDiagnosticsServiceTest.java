package com.ghatana.refactorer.diagnostics.python;

import static org.junit.jupiter.api.Assertions.*;

import com.ghatana.refactorer.codemods.python.PythonCodemods;
import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import com.ghatana.refactorer.shared.process.ProcessResult;
import com.ghatana.refactorer.testutils.TestConfig;
import io.activej.promise.Promise;
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
    @TempDir
    Path tempDir;
    private PolyfixProjectContext context;
    private FakeProcessRunner processRunner;
    private PythonDiagnosticsService service;

    private static final List<String> DEFAULT_IGNORE_PATTERNS = List.of("**/.venv/**", "**/venv/**",
            "**/node_modules/**");

    @BeforeEach
    void setUp() {
        context = TestConfig.createTestContext(tempDir);
        processRunner = new FakeProcessRunner(context);
        service = new PythonDiagnosticsService(
                context,
                new RuffRunner(context, processRunner),
                new BlackRunner(context, processRunner),
                new TestCodemods(context),
                processRunner);
    }

    @Test
    void testFindsAndFixesPythonIssues() throws Exception {
        Path pythonFile = tempDir.resolve("test.py");
        Files.writeString(pythonFile, sampleCode(), StandardCharsets.UTF_8);

        processRunner.when(
                "ruff",
                ruffArgs(false, "test.py"),
                FakeProcessRunner.response(new ProcessResult(1, sampleDiagnostics("test.py"), "")),
                FakeProcessRunner.response(new ProcessResult(0, "[]", "")));

        List<UnifiedDiagnostic> diagnostics = runPromise(() -> service.withIncludePatterns(List.of("test.py"))
                .withFix(false)
                .runDiagnostics());

        assertTrue(diagnostics.size() >= 2);
        assertTrue(diagnostics.stream().anyMatch(d -> "F401".equals(d.getCode())));
        assertTrue(diagnostics.stream().anyMatch(d -> "E226".equals(d.getCode())));

        boolean fixSuccess = runPromise(() -> service.withFix(true).applyFixes(diagnostics));
        assertTrue(fixSuccess);

        List<UnifiedDiagnostic> remaining = runPromise(() -> service.withFix(false).runDiagnostics());
        assertTrue(remaining.size() < diagnostics.size());
    }

    @Test
    void testFormatsPythonCode() throws Exception {
        Path pythonFile = tempDir.resolve("format_me.py");
        Files.writeString(pythonFile, "def foo() :\n    x=1\n", StandardCharsets.UTF_8);

        processRunner.when(
                "black",
                blackArgs(false, 88, "format_me.py"),
                FakeProcessRunner.response(
                        new ProcessResult(0, "", ""), formatPython("format_me.py")));

        assertTrue(runPromise(() -> service.withIncludePatterns(List.of("format_me.py")).formatFiles()));
        String formatted = Files.readString(pythonFile);
        assertTrue(formatted.contains("x = 1"));
    }

    @Test
    void testIsAvailable() {
        processRunner.when(
                "ruff",
                List.of("--version"),
                FakeProcessRunner.response(new ProcessResult(0, "ruff 0.4.0", "")));
        processRunner.when(
                "black",
                List.of("--version"),
                FakeProcessRunner.response(new ProcessResult(0, "black 24.0", "")));
        assertTrue(service.isAvailable());
    }

    @Test
    void testHandlesMultipleFiles() throws Exception {
        Path file1 = tempDir.resolve("file1.py");
        Path file2 = tempDir.resolve("file2.py");
        Files.writeString(file1, "import os\nprint(1+2)\n", StandardCharsets.UTF_8);
        Files.writeString(file2, "x=1\n", StandardCharsets.UTF_8);

        processRunner.when(
                "ruff",
                ruffArgs(false, "file1.py", "file2.py"),
                FakeProcessRunner.response(new ProcessResult(1, multiDiagnostics(), "")));

        List<UnifiedDiagnostic> diagnostics = runPromise(() -> service.withIncludePatterns(List.of("file1.py", "file2.py"))
                .runDiagnostics());
        assertEquals(3, diagnostics.size());

        assertTrue(runPromise(() -> service.withFix(true).applyFixes(diagnostics)));
    }

    private static List<String> ruffArgs(boolean fix, String... files) {
        List<String> args = new ArrayList<>();
        args.add("check");
        args.add("--format=json");
        if (fix) {
            args.add("--fix");
        }
        for (String file : files) {
            args.add(file);
        }
        if (!DEFAULT_IGNORE_PATTERNS.isEmpty()) {
            args.add("--exclude");
            args.add(String.join(",", DEFAULT_IGNORE_PATTERNS));
        }
        return args;
    }

    private static List<String> blackArgs(boolean checkOnly, int lineLength, String file) {
        List<String> args = new ArrayList<>();
        if (checkOnly) {
            args.add("--check");
        }
        args.add("--quiet");
        args.add("--line-length");
        args.add(String.valueOf(lineLength));
        args.add(file);
        if (!DEFAULT_IGNORE_PATTERNS.isEmpty()) {
            args.add("--exclude");
            args.add(String.join("|", DEFAULT_IGNORE_PATTERNS));
        }
        return args;
    }

    private FakeProcessRunner.CommandEffect formatPython(String file) {
        return (cwd, cmd) -> {
            try {
                Files.writeString(
                        cwd.resolve(file), "def foo():\n    x = 1\n", StandardCharsets.UTF_8);
            } catch (IOException e) {
                fail(e);
            }
        };
    }

    private static String sampleCode() {
        return "import os\n\n"
                + "def greet(name):\n    return'Hello, ' + name\n\n"
                + "print(greet('world'))\n";
    }

    private static String sampleDiagnostics(String file) {
        return diagnosticsJson(
                new RuffFinding(file, "F401", "unused import", 1, 1, 1, 1),
                new RuffFinding(file, "E226", "missing whitespace", 3, 12, 3, 18));
    }

    private static String multiDiagnostics() {
        return diagnosticsJson(
                new RuffFinding("file1.py", "F401", "unused import", 1, 1, 1, 1),
                new RuffFinding("file1.py", "E226", "operator spacing", 2, 6, 2, 7),
                new RuffFinding("file2.py", "E225", "missing whitespace", 1, 1, 1, 5));
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

    private static class TestCodemods extends PythonCodemods {
        TestCodemods(PolyfixProjectContext context) {
            super(context);
        }

        @Override
        public boolean applyCodemods(Path file, List<UnifiedDiagnostic> diagnostics) {
            try {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                content = content.replace("import os", "# removed import");
                content = content.replace("1+2", "1 + 2");
                content = content.replace("x=1", "x = 1");
                Files.writeString(file, content, StandardCharsets.UTF_8);
            } catch (IOException e) {
                fail(e);
            }
            return true;
        }
    }
}
