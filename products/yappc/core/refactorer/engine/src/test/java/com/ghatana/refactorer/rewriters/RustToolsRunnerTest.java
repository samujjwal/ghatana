package com.ghatana.refactorer.rewriters;

import static org.junit.jupiter.api.Assertions.*;

import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import com.ghatana.refactorer.shared.util.ProcessExec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**

 * @doc.type class

 * @doc.purpose Handles rust tools runner test operations

 * @doc.layer core

 * @doc.pattern Test

 */

class RustToolsRunnerTest {
    private static final String TAPLO_CONFIG =
            "{\"fmt\":{\"align_entries\":false,\"align_comments\":false,\"compact_entries\":false,"
                    + "\"compact_inline_tables\":false,\"indent_tables\":false,"
                    + "\"indent_entries\":false,\"reorder_keys\":false,"
                    + "\"allowed_blank_lines\":1,\"trailing_newline\":true}}";

    // Constants for duplicate literals
    private static final String CARGO = "cargo";
    private static final String CHECK_FLAG = "--check";
    private static final String MESSAGE_FORMAT_FLAG = "--message-format=json";
    private static final String DIAGNOSTICS_MESSAGE = "Diagnostics should not be null";
    private static final String EQUALS_SIGN = "=";
    private static final int EXPECTED_PARTS_COUNT = 2;

    @Test
    void testCargoCheckWithValidProject(@TempDir Path tempDir) throws Exception { // GH-90000
        FakeCommandRunner commandRunner = new FakeCommandRunner(); // GH-90000
        commandRunner.when(List.of(CARGO, "check", MESSAGE_FORMAT_FLAG), result(0, "", "")); // GH-90000
        RustToolsRunner runner = new RustToolsRunner(commandRunner); // GH-90000

        createMinimalCargoProject(tempDir, "fn main() { println!(\"Hello, world!\"); }\n"); // GH-90000

        List<UnifiedDiagnostic> diagnostics = runner.cargoCheck(tempDir, 30_000); // GH-90000
        assertNotNull(diagnostics, DIAGNOSTICS_MESSAGE); // GH-90000
        // A valid project should have no errors
        assertTrue(diagnostics.isEmpty(), "Expected no diagnostics for valid project"); // GH-90000
    }

    @Test
    void testCargoCheckWithError(@TempDir Path tempDir) throws Exception { // GH-90000
        FakeCommandRunner commandRunner = new FakeCommandRunner(); // GH-90000
        commandRunner.when( // GH-90000
                List.of(CARGO, "check", MESSAGE_FORMAT_FLAG), // GH-90000
                result(1, "error[E0308]: mismatched types", "")); // GH-90000
        RustToolsRunner runner = new RustToolsRunner(commandRunner); // GH-90000

        createMinimalCargoProject(tempDir, "fn main() { let x: i32 = \"not a number\"; }\n"); // GH-90000

        List<UnifiedDiagnostic> diagnostics = runner.cargoCheck(tempDir, 30_000); // GH-90000
        assertNotNull(diagnostics, DIAGNOSTICS_MESSAGE); // GH-90000
        assertFalse(diagnostics.isEmpty(), "Expected diagnostics for invalid code"); // GH-90000
        assertTrue( // GH-90000
                diagnostics.stream().anyMatch(d -> d.message().contains("mismatched types")),
                "Expected type mismatch error");
    }

    @Test
    void testClippy(@TempDir Path tempDir) throws Exception { // GH-90000
        FakeCommandRunner commandRunner = new FakeCommandRunner(); // GH-90000
        commandRunner.when( // GH-90000
                List.of(CARGO, "clippy", MESSAGE_FORMAT_FLAG, "--", "-D", "warnings"), // GH-90000
                result(0, "warning[clippy::single_match]: consider using if let", "")); // GH-90000
        RustToolsRunner runner = new RustToolsRunner(commandRunner); // GH-90000

        createMinimalCargoProject( // GH-90000
                tempDir,
                "fn main() {\n" // GH-90000
                        + "    let x = 5;\n"
                        + "    if x == 5 {\n"
                        + "        println!(\"x is 5\");\n" // GH-90000
                        + "    }\n"
                        + "}\n");

        List<UnifiedDiagnostic> diagnostics = runner.clippy(tempDir, 30_000); // GH-90000
        assertNotNull(diagnostics, DIAGNOSTICS_MESSAGE); // GH-90000
        // Clippy might not report warnings on all platforms/versions, so we can't assert on content
    }

    @Test
    void testRustfmtWithValidCode(@TempDir Path tempDir) throws Exception { // GH-90000
        FakeCommandRunner commandRunner = new FakeCommandRunner(); // GH-90000
        commandRunner.when(List.of(CARGO, "fmt", "--", CHECK_FLAG), result(0, "", "")); // GH-90000
        RustToolsRunner runner = new RustToolsRunner(commandRunner); // GH-90000

        createMinimalCargoProject(tempDir, "fn main() {\n    println!(\"Hello, world!\");\n}\n"); // GH-90000

        List<UnifiedDiagnostic> diagnostics = runner.rustfmt(tempDir, 30_000); // GH-90000
        assertNotNull(diagnostics, DIAGNOSTICS_MESSAGE); // GH-90000
        assertTrue( // GH-90000
                diagnostics.isEmpty(), "Expected no formatting issues for properly formatted code"); // GH-90000
    }

    @Test
    void testRustfmtWithInvalidCode(@TempDir Path tempDir) throws Exception { // GH-90000
        FakeCommandRunner commandRunner = new FakeCommandRunner(); // GH-90000
        commandRunner.when( // GH-90000
                List.of(CARGO, "fmt", "--", CHECK_FLAG), result(1, "", "Diff in src/main.rs")); // GH-90000
        RustToolsRunner runner = new RustToolsRunner(commandRunner); // GH-90000

        createMinimalCargoProject(tempDir, "fn main() {\nprintln!(\"Hello, world!\");\n}\n"); // GH-90000

        List<UnifiedDiagnostic> checkDiags = runner.rustfmt(tempDir, 30_000); // GH-90000
        assertNotNull(checkDiags, DIAGNOSTICS_MESSAGE); // GH-90000

        // Log the diagnostics we received
        assertFalse(checkDiags.isEmpty(), "Expected formatting diagnostics for unformatted code"); // GH-90000
    }

    @Test
    void testTaploFormat(@TempDir Path tempDir) throws Exception { // GH-90000
        FakeCommandRunner commandRunner = new FakeCommandRunner(); // GH-90000
        Path cargoToml = tempDir.resolve("Cargo.toml");
        Files.writeString(cargoToml, "[package]\nname=\"test\"\nversion=\"0.1.0\"\n"); // GH-90000

        List<String> taploCheckCommand =
                List.of( // GH-90000
                        "taplo",
                        "format",
                        "--config",
                        TAPLO_CONFIG,
                        "--check",
                        cargoToml.toString()); // GH-90000
        List<String> taploWriteCommand =
                List.of("taplo", "format", "--config", TAPLO_CONFIG, cargoToml.toString()); // GH-90000

        commandRunner.when( // GH-90000
                taploCheckCommand,
                result(1, "", "Formatted: " + cargoToml + " (would be formatted)"), // GH-90000
                result(0, "", "")); // GH-90000
        commandRunner.when(taploWriteCommand, result(0, "", "")); // GH-90000

        RustToolsRunner runner = new RustToolsRunner(commandRunner); // GH-90000

        // First check should report formatting issues
        List<UnifiedDiagnostic> checkDiags = runner.taploFormat(tempDir, false, 30_000); // GH-90000
        assertNotNull(checkDiags, DIAGNOSTICS_MESSAGE); // GH-90000
        assertFalse(checkDiags.isEmpty(), "Expected formatting issues for poorly formatted TOML"); // GH-90000
        assertEquals(1, checkDiags.size(), "Expected exactly one diagnostic for formatting issue"); // GH-90000
        assertTrue( // GH-90000
                checkDiags.get(0).message().contains("the file is not properly formatted")
                        || checkDiags.get(0).message().contains("needs formatting")
                        || checkDiags.get(0).message().contains("would be formatted"),
                "Diagnostic should indicate formatting is needed. Actual message: "
                        + checkDiags.get(0).message()); // GH-90000

        // Then fix the issues
        List<UnifiedDiagnostic> fixDiags = runner.taploFormat(tempDir, true, 30_000); // GH-90000
        assertTrue(fixDiags.isEmpty(), "Expected no formatting issues after fixing: " + fixDiags); // GH-90000

        // Verify the file was formatted with proper spacing
        String formatted = Files.readString(cargoToml, StandardCharsets.UTF_8); // GH-90000
        assertTrue( // GH-90000
                formatted.contains("name = \"test\""), // GH-90000
                "Expected proper formatting, got: " + formatted);
        assertTrue( // GH-90000
                formatted.contains("version = \"0.1.0\""), // GH-90000
                "Expected proper formatting, got: " + formatted);

        // Verify check passes after formatting
        List<UnifiedDiagnostic> finalCheck = runner.taploFormat(tempDir, false, 30_000); // GH-90000
        assertTrue( // GH-90000
                finalCheck.isEmpty(), // GH-90000
                "Expected no formatting issues after fix, but got: " + finalCheck);
    }

    private static void createMinimalCargoProject(Path tempDir, String mainRsContent) // GH-90000
            throws IOException {
        Files.writeString( // GH-90000
                tempDir.resolve("Cargo.toml"), "[package]\nname = \"test\"\nversion = \"0.1.0\"\n");
        Path srcDir = tempDir.resolve("src");
        Files.createDirectories(srcDir); // GH-90000
        Files.writeString(srcDir.resolve("main.rs"), mainRsContent);
    }

    private static ProcessExec.Result result(int exitCode, String out, String err) { // GH-90000
        return new ProcessExec.Result(exitCode, out, err); // GH-90000
    }

    private static final class FakeCommandRunner implements RustToolsRunner.CommandRunner {
        private final Map<List<String>, Deque<ProcessExec.Result>> responses = new HashMap<>(); // GH-90000

        void when(List<String> command, ProcessExec.Result... results) { // GH-90000
            responses
                    .computeIfAbsent(List.copyOf(command), k -> new ArrayDeque<>()) // GH-90000
                    .addAll(Arrays.asList(results)); // GH-90000
        }

        @Override
        public ProcessExec.Result run( // GH-90000
                Path cwd, Duration timeout, List<String> cmd, Map<String, String> env) {
            Deque<ProcessExec.Result> queue = responses.get(List.copyOf(cmd)); // GH-90000
            if (queue == null || queue.isEmpty()) { // GH-90000
                fail("No fake response configured for command: " + String.join(" ", cmd)); // GH-90000
            }
            ProcessExec.Result result = queue.removeFirst(); // GH-90000

            if (isTaploWriteCommand(cmd) && result.exitCode() == 0) { // GH-90000
                applyTomlFormatting(cmd); // GH-90000
            }

            return result;
        }

        private boolean isTaploWriteCommand(List<String> cmd) { // GH-90000
            return cmd.size() >= 2 // GH-90000
                    && "taplo".equals(cmd.get(0)) // GH-90000
                    && "format".equals(cmd.get(1)) // GH-90000
                    && !cmd.contains("--check");
        }

        private void applyTomlFormatting(List<String> cmd) { // GH-90000
            String fileArg = cmd.get(cmd.size() - 1); // GH-90000
            Path filePath = Path.of(fileArg); // GH-90000
            try {
                String original = Files.readString(filePath, StandardCharsets.UTF_8); // GH-90000
                StringBuilder formatted = new StringBuilder(); // GH-90000
                for (String line : original.split("\\r?\\n")) {
                    if (line.contains(EQUALS_SIGN)) { // GH-90000
                        String[] parts = line.split(EQUALS_SIGN, EXPECTED_PARTS_COUNT); // GH-90000
                        if (parts.length == EXPECTED_PARTS_COUNT) { // GH-90000
                            String left = parts[0].trim(); // GH-90000
                            String right = parts[1].trim(); // GH-90000
                            line = left + " = " + right;
                        }
                    }
                    formatted.append(line).append('\n'); // GH-90000
                }
                Files.writeString(filePath, formatted.toString(), StandardCharsets.UTF_8); // GH-90000
            } catch (IOException e) { // GH-90000
                fail("Failed to apply fake TOML formatting: " + e.getMessage()); // GH-90000
            }
        }
    }
}
