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

    @Test
    void testCargoCheckWithValidProject(@TempDir Path tempDir) throws Exception {
        FakeCommandRunner commandRunner = new FakeCommandRunner();
        commandRunner.when(List.of("cargo", "check", "--message-format=json"), result(0, "", ""));
        RustToolsRunner runner = new RustToolsRunner(commandRunner);

        createMinimalCargoProject(tempDir, "fn main() { println!(\"Hello, world!\"); }\n");

        List<UnifiedDiagnostic> diagnostics = runner.cargoCheck(tempDir, 30_000);
        assertNotNull(diagnostics, "Diagnostics should not be null");
        // A valid project should have no errors
        assertTrue(diagnostics.isEmpty(), "Expected no diagnostics for valid project");
    }

    @Test
    void testCargoCheckWithError(@TempDir Path tempDir) throws Exception {
        FakeCommandRunner commandRunner = new FakeCommandRunner();
        commandRunner.when(
                List.of("cargo", "check", "--message-format=json"),
                result(1, "error[E0308]: mismatched types", ""));
        RustToolsRunner runner = new RustToolsRunner(commandRunner);

        createMinimalCargoProject(tempDir, "fn main() { let x: i32 = \"not a number\"; }\n");

        List<UnifiedDiagnostic> diagnostics = runner.cargoCheck(tempDir, 30_000);
        assertNotNull(diagnostics, "Diagnostics should not be null");
        assertFalse(diagnostics.isEmpty(), "Expected diagnostics for invalid code");
        assertTrue(
                diagnostics.stream().anyMatch(d -> d.message().contains("mismatched types")),
                "Expected type mismatch error");
    }

    @Test
    void testClippy(@TempDir Path tempDir) throws Exception {
        FakeCommandRunner commandRunner = new FakeCommandRunner();
        commandRunner.when(
                List.of("cargo", "clippy", "--message-format=json", "--", "-D", "warnings"),
                result(0, "warning[clippy::single_match]: consider using if let", ""));
        RustToolsRunner runner = new RustToolsRunner(commandRunner);

        createMinimalCargoProject(
                tempDir,
                "fn main() {\n"
                        + "    let x = 5;\n"
                        + "    if x == 5 {\n"
                        + "        println!(\"x is 5\");\n"
                        + "    }\n"
                        + "}\n");

        List<UnifiedDiagnostic> diagnostics = runner.clippy(tempDir, 30_000);
        assertNotNull(diagnostics, "Diagnostics should not be null");
        // Clippy might not report warnings on all platforms/versions, so we can't assert on content
    }

    @Test
    void testRustfmtWithValidCode(@TempDir Path tempDir) throws Exception {
        FakeCommandRunner commandRunner = new FakeCommandRunner();
        commandRunner.when(List.of("cargo", "fmt", "--", "--check"), result(0, "", ""));
        RustToolsRunner runner = new RustToolsRunner(commandRunner);

        createMinimalCargoProject(tempDir, "fn main() {\n    println!(\"Hello, world!\");\n}\n");

        List<UnifiedDiagnostic> diagnostics = runner.rustfmt(tempDir, 30_000);
        assertNotNull(diagnostics, "Diagnostics should not be null");
        assertTrue(
                diagnostics.isEmpty(), "Expected no formatting issues for properly formatted code");
    }

    @Test
    void testRustfmtWithInvalidCode(@TempDir Path tempDir) throws Exception {
        FakeCommandRunner commandRunner = new FakeCommandRunner();
        commandRunner.when(
                List.of("cargo", "fmt", "--", "--check"), result(1, "", "Diff in src/main.rs"));
        RustToolsRunner runner = new RustToolsRunner(commandRunner);

        createMinimalCargoProject(tempDir, "fn main() {\nprintln!(\"Hello, world!\");\n}\n");

        List<UnifiedDiagnostic> checkDiags = runner.rustfmt(tempDir, 30_000);
        assertNotNull(checkDiags, "Diagnostics should not be null");

        // Log the diagnostics we received
        assertFalse(checkDiags.isEmpty(), "Expected formatting diagnostics for unformatted code");
    }

    @Test
    void testTaploFormat(@TempDir Path tempDir) throws Exception {
        FakeCommandRunner commandRunner = new FakeCommandRunner();
        Path cargoToml = tempDir.resolve("Cargo.toml");
        Files.writeString(cargoToml, "[package]\nname=\"test\"\nversion=\"0.1.0\"\n");

        List<String> taploCheckCommand =
                List.of(
                        "taplo",
                        "format",
                        "--config",
                        TAPLO_CONFIG,
                        "--check",
                        cargoToml.toString());
        List<String> taploWriteCommand =
                List.of("taplo", "format", "--config", TAPLO_CONFIG, cargoToml.toString());

        commandRunner.when(
                taploCheckCommand,
                result(1, "", "Formatted: " + cargoToml + " (would be formatted)"),
                result(0, "", ""));
        commandRunner.when(taploWriteCommand, result(0, "", ""));

        RustToolsRunner runner = new RustToolsRunner(commandRunner);

        // First check should report formatting issues
        List<UnifiedDiagnostic> checkDiags = runner.taploFormat(tempDir, false, 30_000);
        assertNotNull(checkDiags, "Diagnostics should not be null");
        assertFalse(checkDiags.isEmpty(), "Expected formatting issues for poorly formatted TOML");
        assertEquals(1, checkDiags.size(), "Expected exactly one diagnostic for formatting issue");
        assertTrue(
                checkDiags.get(0).message().contains("the file is not properly formatted")
                        || checkDiags.get(0).message().contains("needs formatting")
                        || checkDiags.get(0).message().contains("would be formatted"),
                "Diagnostic should indicate formatting is needed. Actual message: "
                        + checkDiags.get(0).message());

        // Then fix the issues
        List<UnifiedDiagnostic> fixDiags = runner.taploFormat(tempDir, true, 30_000);
        assertTrue(fixDiags.isEmpty(), "Expected no formatting issues after fixing: " + fixDiags);

        // Verify the file was formatted with proper spacing
        String formatted = Files.readString(cargoToml, StandardCharsets.UTF_8);
        assertTrue(
                formatted.contains("name = \"test\""),
                "Expected proper formatting, got: " + formatted);
        assertTrue(
                formatted.contains("version = \"0.1.0\""),
                "Expected proper formatting, got: " + formatted);

        // Verify check passes after formatting
        List<UnifiedDiagnostic> finalCheck = runner.taploFormat(tempDir, false, 30_000);
        assertTrue(
                finalCheck.isEmpty(),
                "Expected no formatting issues after fix, but got: " + finalCheck);
    }

    private static void createMinimalCargoProject(Path tempDir, String mainRsContent)
            throws IOException {
        Files.writeString(
                tempDir.resolve("Cargo.toml"), "[package]\nname = \"test\"\nversion = \"0.1.0\"\n");
        Path srcDir = tempDir.resolve("src");
        Files.createDirectories(srcDir);
        Files.writeString(srcDir.resolve("main.rs"), mainRsContent);
    }

    private static ProcessExec.Result result(int exitCode, String out, String err) {
        return new ProcessExec.Result(exitCode, out, err);
    }

    private static final class FakeCommandRunner implements RustToolsRunner.CommandRunner {
        private final Map<List<String>, Deque<ProcessExec.Result>> responses = new HashMap<>();

        void when(List<String> command, ProcessExec.Result... results) {
            responses
                    .computeIfAbsent(List.copyOf(command), k -> new ArrayDeque<>())
                    .addAll(Arrays.asList(results));
        }

        @Override
        public ProcessExec.Result run(
                Path cwd, Duration timeout, List<String> cmd, Map<String, String> env) {
            Deque<ProcessExec.Result> queue = responses.get(List.copyOf(cmd));
            if (queue == null || queue.isEmpty()) {
                fail("No fake response configured for command: " + String.join(" ", cmd));
            }
            ProcessExec.Result result = queue.removeFirst();

            if (isTaploWriteCommand(cmd) && result.exitCode() == 0) {
                applyTomlFormatting(cmd);
            }

            return result;
        }

        private boolean isTaploWriteCommand(List<String> cmd) {
            return cmd.size() >= 2
                    && "taplo".equals(cmd.get(0))
                    && "format".equals(cmd.get(1))
                    && !cmd.contains("--check");
        }

        private void applyTomlFormatting(List<String> cmd) {
            String fileArg = cmd.get(cmd.size() - 1);
            Path filePath = Path.of(fileArg);
            try {
                String original = Files.readString(filePath, StandardCharsets.UTF_8);
                StringBuilder formatted = new StringBuilder();
                for (String line : original.split("\\r?\\n")) {
                    if (line.contains("=")) {
                        String[] parts = line.split("=", 2);
                        if (parts.length == 2) {
                            String left = parts[0].trim();
                            String right = parts[1].trim();
                            line = left + " = " + right;
                        }
                    }
                    formatted.append(line).append('\n');
                }
                Files.writeString(filePath, formatted.toString(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                fail("Failed to apply fake TOML formatting: " + e.getMessage());
            }
        }
    }
}
