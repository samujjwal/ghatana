package com.ghatana.refactorer.diagnostics.python;

import static org.junit.jupiter.api.Assertions.*;

import com.ghatana.refactorer.shared.PolyfixProjectContext;
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

 * @doc.purpose Handles black runner test operations

 * @doc.layer core

 * @doc.pattern Test

 */

class BlackRunnerTest extends EventloopTestBase {
    @TempDir
    Path tempDir;
    private PolyfixProjectContext context;
    private FakeProcessRunner processRunner;
    private BlackRunner blackRunner;

    private static final List<String> DEFAULT_IGNORE_PATTERNS = List.of("**/.venv/**", "**/venv/**",
            "**/node_modules/**");

    @BeforeEach
    void setUp() {
        context = TestConfig.createTestContext(tempDir);
        processRunner = new FakeProcessRunner(context);
        blackRunner = new BlackRunner(context, processRunner);
    }

    @Test
    void testBlackFormatsPythonFile() throws Exception {
        // Create a Python file with inconsistent formatting
        Path pythonFile = tempDir.resolve("poorly_formatted.py");
        String originalCode = "def foo(  ) :\n    x=1\n    if x<2:print('hello')\n";
        Files.writeString(pythonFile, originalCode);

        processRunner.when(
                "black",
                blackArgs(true, 88, "poorly_formatted.py"),
                FakeProcessRunner.response(
                        new ProcessResult(1, "", "would reformat poorly_formatted.py")),
                FakeProcessRunner.response(new ProcessResult(0, "", "")));

        processRunner.when(
                "black",
                blackArgs(false, 88, "poorly_formatted.py"),
                FakeProcessRunner.response(
                        new ProcessResult(0, "", ""), formatPython("poorly_formatted.py")));

        // Run black in check mode first (should fail)
        BlackRunner checkRunner = new BlackRunner(context, processRunner)
                .withIncludePatterns(List.of("poorly_formatted.py"))
                .withCheckOnly(true);

        boolean needsFormatting = !runPromise(() -> checkRunner.run());
        assertTrue(needsFormatting, "File should need formatting");

        // Now format for real
        boolean formatSuccess = runPromise(() -> blackRunner.withIncludePatterns(List.of("poorly_formatted.py")).run());

        assertTrue(formatSuccess, "Formatting should succeed");

        // Check the file was actually formatted
        String formattedCode = Files.readString(pythonFile);
        assertNotEquals(originalCode, formattedCode, "File should be reformatted");
        assertTrue(formattedCode.contains("x = 1"), "Should have proper spacing");
        assertTrue(formattedCode.contains("if x < 2:"), "Should have proper spacing");

        // Running check again should pass now
        BlackRunner checkRunner2 = new BlackRunner(context, processRunner)
                .withIncludePatterns(List.of("poorly_formatted.py"))
                .withCheckOnly(true);

        boolean isFormatted = runPromise(() -> checkRunner2.run());
        assertTrue(isFormatted, "File should now be properly formatted");
    }

    @Test
    void testBlackWithNonexistentFile() throws Exception {
        processRunner.when(
                "black",
                blackArgs(false, 88, "nonexistent.py"),
                FakeProcessRunner.response(new ProcessResult(0, "", "")));

        // Should not throw, just return success
        boolean success = runPromise(() -> blackRunner.withIncludePatterns(List.of("nonexistent.py")).run());

        assertTrue(success, "Should return true for nonexistent file");
    }

    @Test
    void testBlackRespectsLineLength() throws Exception {
        // Create a long line
        String longLine = "x = 'This is a very long string that should be wrapped by black if the line length"
                + " is set appropriately'\n";
        Path pythonFile = tempDir.resolve("long_line.py");
        Files.writeString(pythonFile, longLine);

        processRunner.when(
                "black",
                blackArgs(false, 20, "long_line.py"),
                FakeProcessRunner.response(
                        new ProcessResult(0, "", ""), wrapLongLine("long_line.py")));

        // Format with very short line length
        boolean success = runPromise(() -> blackRunner.withIncludePatterns(List.of("long_line.py"))
                .withLineLength(20)
                .run());

        assertTrue(success, "Formatting should succeed");

        // Check the line was wrapped
        String formattedCode = Files.readString(pythonFile);
        assertTrue(formattedCode.contains("\n"), "Long line should be wrapped");
    }

    @Test
    void testFindPythonFiles() throws Exception {
        // Create Python files in a directory structure
        Files.createDirectories(tempDir.resolve("src"));
        Path file1 = tempDir.resolve("main.py");
        Path file2 = tempDir.resolve("src/__init__.py");
        Path file3 = tempDir.resolve("src/utils.py");
        Path notPython = tempDir.resolve("notes.txt");

        Files.writeString(file1, "print('main')\n");
        Files.writeString(file2, "# Package\n");
        Files.writeString(file3, "def util(): pass\n");
        Files.writeString(notPython, "Not a Python file\n");

        // Find all Python files
        List<Path> pythonFiles = blackRunner.findPythonFiles();

        assertEquals(3, pythonFiles.size(), "Should find all Python files");
        assertTrue(
                pythonFiles.stream().anyMatch(p -> p.endsWith("main.py")), "Should find main.py");
        assertTrue(
                pythonFiles.stream().anyMatch(p -> p.endsWith("__init__.py")),
                "Should find __init__.py");
        assertTrue(
                pythonFiles.stream().anyMatch(p -> p.endsWith("utils.py")), "Should find utils.py");

        // Test with include pattern
        blackRunner.withIncludePatterns(List.of("src/*.py"));
        pythonFiles = blackRunner.findPythonFiles();
        assertEquals(2, pythonFiles.size(), "Should find Python files in src/");
        assertTrue(
                pythonFiles.stream().noneMatch(p -> p.endsWith("main.py")),
                "Should not find main.py with pattern");
    }

    private static List<String> blackArgs(boolean checkOnly, int lineLength, String... include) {
        List<String> args = new ArrayList<>();
        if (checkOnly) {
            args.add("--check");
        }
        args.add("--quiet");
        args.add("--line-length");
        args.add(String.valueOf(lineLength));
        for (String entry : include) {
            args.add(entry);
        }
        if (!DEFAULT_IGNORE_PATTERNS.isEmpty()) {
            args.add("--exclude");
            args.add(String.join("|", DEFAULT_IGNORE_PATTERNS));
        }
        return args;
    }

    private FakeProcessRunner.CommandEffect formatPython(String relativePath) {
        return (cwd, command) -> {
            Path file = cwd.resolve(relativePath);
            try {
                Files.writeString(
                        file,
                        "def foo():\n"
                                + "    x = 1\n"
                                + "    if x < 2:\n"
                                + "        print('hello')\n");
            } catch (IOException e) {
                fail("Failed to apply fake black formatting: " + e.getMessage());
            }
        };
    }

    private FakeProcessRunner.CommandEffect wrapLongLine(String relativePath) {
        return (cwd, command) -> {
            Path file = cwd.resolve(relativePath);
            try {
                Files.writeString(
                        file,
                        "x = (\n"
                                + "    'This is a very long string that should be wrapped by black if"
                                + " the line length is set appropriately'\n"
                                + ")\n");
            } catch (IOException e) {
                fail("Failed to apply fake black formatting: " + e.getMessage());
            }
        };
    }
}
