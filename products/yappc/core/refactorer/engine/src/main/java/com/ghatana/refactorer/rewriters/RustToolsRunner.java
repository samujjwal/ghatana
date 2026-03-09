package com.ghatana.refactorer.rewriters;

import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import com.ghatana.refactorer.shared.util.ProcessExec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration with Rust tooling including cargo check, clippy, and rustfmt. 
 * @doc.type class
 * @doc.purpose Handles rust tools runner operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public final class RustToolsRunner {

    private static final Logger log = LoggerFactory.getLogger(RustToolsRunner.class);

    private final CommandRunner commandRunner;

    public RustToolsRunner() {
        this(ProcessExec::run);
    }

    RustToolsRunner(CommandRunner commandRunner) {
        this.commandRunner = Objects.requireNonNull(commandRunner, "commandRunner");
    }

    /**
 * Run cargo check and return any diagnostics. */
    public List<UnifiedDiagnostic> cargoCheck(Path cwd, long timeoutMillis) {
        Objects.requireNonNull(cwd, "cwd");
        ProcessExec.Result result =
                commandRunner.run(
                        cwd,
                        Duration.ofMillis(timeoutMillis),
                        List.of("cargo", "check", "--message-format=json"),
                        Map.of());
        return parseCargoOutput(result);
    }

    /**
 * Run clippy and return any diagnostics. */
    public List<UnifiedDiagnostic> clippy(Path cwd, long timeoutMillis) {
        Objects.requireNonNull(cwd, "cwd");
        ProcessExec.Result result =
                commandRunner.run(
                        cwd,
                        Duration.ofMillis(timeoutMillis),
                        List.of("cargo", "clippy", "--message-format=json", "--", "-D", "warnings"),
                        Map.of());
        return parseCargoOutput(result);
    }

    /**
 * Run rustfmt and return any formatting issues. */
    public List<UnifiedDiagnostic> rustfmt(Path cwd, long timeoutMillis) {
        Objects.requireNonNull(cwd, "cwd");
        ProcessExec.Result result =
                commandRunner.run(
                        cwd,
                        Duration.ofMillis(timeoutMillis),
                        List.of("cargo", "fmt", "--", "--check"),
                        Map.of());

        List<UnifiedDiagnostic> diagnostics = new ArrayList<>();
        if (result.exitCode() != 0) {
            // Parse rustfmt output for specific file and line information
            String[] lines = result.err().split("\n");
            for (String line : lines) {
                if (line.startsWith("Diff in ")) {
                    String filePath = line.substring(8).trim();
                    diagnostics.add(
                            UnifiedDiagnostic.warning(
                                    "rustfmt",
                                    "Code formatting issues found. Run 'cargo fmt' to fix.",
                                    Path.of(filePath),
                                    -1,
                                    -1, // Line and column not available from --check
                                    null));
                }
            }
        }
        return diagnostics;
    }

    /**
     * Format TOML files using taplo.
     *
     * @param cwd The working directory containing the TOML files
     * @param write Whether to write changes to files (false for dry-run)
     * @param timeoutMillis Maximum time to wait for taplo to complete
     * @return List of diagnostics for any formatting issues found
     */
    public List<UnifiedDiagnostic> taploFormat(Path cwd, boolean write, long timeoutMillis) {
        Objects.requireNonNull(cwd, "cwd");
        List<UnifiedDiagnostic> diagnostics = new ArrayList<>();

        try {
            // Add the Cargo.toml file explicitly to avoid globbing issues
            Path cargoToml = cwd.resolve("Cargo.toml");
            if (!Files.exists(cargoToml)) {
                String errorMsg = "No Cargo.toml file found in " + cwd;
                log.info("taploFormat error: {}", errorMsg);
                diagnostics.add(UnifiedDiagnostic.error("taplo", errorMsg, cwd, -1, -1, null));
                return diagnostics;
            }

            // Build the command
            List<String> command = new ArrayList<>();
            command.add("taplo");
            command.add("format");

            // Use minimal configuration to ensure consistent behavior
            command.add("--config");
            command.add(
                    "{\"fmt\":{\"align_entries\":false,\"align_comments\":false,\"compact_entries\":false,\"compact_inline_tables\":false,\"indent_tables\":false,\"indent_entries\":false,\"reorder_keys\":false,\"allowed_blank_lines\":1,\"trailing_newline\":true}}");

            if (!write) {
                command.add("--check");
            }
            // For write mode, we don't need any additional flags - taplo formats in-place by
            // default

            command.add(cargoToml.toString());

            log.info("Running taplo command: {}", String.join(" ", command));
            ProcessExec.Result result =
                    commandRunner.run(
                            cwd,
                            Duration.ofMillis(timeoutMillis),
                            command,
                            Map.of(
                                    "NO_COLOR", "1", // Disable color output for easier parsing
                                    "TAPLO_FORCE_COLOR", "0"));

            String output = result.out();
            String errorOutput = result.err();

            // In check mode, we expect exit code 1 when formatting is needed
            if (!write) {
                if (result.exitCode() == 1 || result.exitCode() == 2) {
                    // Check if the output indicates formatting is needed
                    String fullOutput = output + "\n" + errorOutput;
                    if (fullOutput.contains("would be formatted")
                            || fullOutput.contains("needs formatting")
                            || fullOutput.contains("Formatted:")) {

                        diagnostics.add(
                                UnifiedDiagnostic.warning(
                                        "taplo",
                                        "TOML file needs formatting. Run 'taplo format' to fix.",
                                        cargoToml,
                                        -1,
                                        -1,
                                        null));
                    } else if (!fullOutput.trim().isEmpty()) {
                        // Some other error occurred
                        diagnostics.add(
                                UnifiedDiagnostic.error(
                                        "taplo",
                                        "Error checking TOML formatting: " + fullOutput.trim(),
                                        cargoToml,
                                        -1,
                                        -1,
                                        null));
                    }
                }
            }
            // In write mode, any non-zero exit code is an error
            else if (result.exitCode() != 0) {
                String errorMsg =
                        "Error "
                                + result.exitCode()
                                + " formatting TOML: "
                                + output.trim()
                                + " "
                                + errorOutput.trim();
                diagnostics.add(
                        UnifiedDiagnostic.error("taplo", errorMsg, cargoToml, -1, -1, null));
            }
        } catch (Exception e) {
            String errorMsg = "Failed to run taplo: " + e.getMessage();
            diagnostics.add(UnifiedDiagnostic.error("taplo", errorMsg, cwd, -1, -1, e));
        }

        return diagnostics;
    }

    /**
 * Parse cargo's JSON output into UnifiedDiagnostic objects. */
    private List<UnifiedDiagnostic> parseCargoOutput(ProcessExec.Result result) {
        List<UnifiedDiagnostic> diagnostics = new ArrayList<>();

        // Skip if no output
        if (result.out() == null || result.out().isBlank()) {
            return diagnostics;
        }

        // Split by newlines and process each JSON message
        String[] lines = result.out().split("\\r?\\n");
        for (String line : lines) {
            try {
                // Skip empty lines
                if (line.trim().isEmpty()) continue;

                // NOTE: Parse the JSON output from cargo/rustc
                // This is a simplified version - in a real implementation, you would parse the JSON
                // and extract detailed diagnostic information
                if (line.contains("error[") || line.contains("warning[")) {
                    diagnostics.add(
                            UnifiedDiagnostic.warning(
                                    "rust", line, null, -1,
                                    -1, // Line and column would be parsed from JSON
                                    null));
                }
            } catch (Exception e) {
                // Log and continue with other lines
                log.error("Error parsing cargo output: {}", e.getMessage());
            }
        }

        return diagnostics;
    }

    @FunctionalInterface
    interface CommandRunner {
        ProcessExec.Result run(
                Path cwd, Duration timeout, List<String> cmd, Map<String, String> env);
    }
}
