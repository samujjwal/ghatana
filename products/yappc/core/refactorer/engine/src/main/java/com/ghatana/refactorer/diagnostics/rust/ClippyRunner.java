package com.ghatana.refactorer.diagnostics.rust;

import com.fasterxml.jackson.databind.JsonNode;
import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import com.ghatana.refactorer.shared.util.JsonSupport;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs `cargo clippy` to perform additional Rust linting and parse the JSON
 * output.
 * 
 * @doc.type runner
 * @doc.language rust
 * @doc.tool clippy
 
 * @doc.purpose Handles clippy runner operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public class ClippyRunner extends CargoRunner {
    private static final Logger logger = LoggerFactory.getLogger(ClippyRunner.class);

    public ClippyRunner(PolyfixProjectContext context) {
        super(context);
    }

    @Override
    public List<UnifiedDiagnostic> run(Path directory) {
        try {
            if (!isClippyAvailable()) {
                logger.warn("cargo clippy is not available. Skipping clippy analysis.");
                return List.of();
            }

            List<String> command = List.of(
                    "cargo",
                    "clippy",
                    "--message-format=json",
                    "--no-deps",
                    "--",
                    "-D",
                    "warnings");

            String output = processRunner.executeAndGetOutput(command.toArray(new String[0]), directory);

            return parseClippyOutput(output, directory);

        } catch (Exception e) {
            logger.error("Error running cargo clippy in " + directory, e);
            return List.of();
        }
    }

    private List<UnifiedDiagnostic> parseClippyOutput(String jsonOutput, Path directory) {
        List<UnifiedDiagnostic> diagnostics = new ArrayList<>();

        if (jsonOutput == null || jsonOutput.trim().isEmpty()) {
            return diagnostics;
        }

        // Process each line of JSON output
        for (String line : jsonOutput.split("\n")) {
            line = line.trim();
            if (line.isEmpty())
                continue;

            try {
                JsonNode node = JsonSupport.parseJson(line);
                if (node == null)
                    continue;

                // Skip non-message entries
                if (!node.has("message"))
                    continue;

                // Skip if this is a compiler message (handled by CargoRunner)
                if (node.path("reason").asText().equals("compiler-message")) {
                    UnifiedDiagnostic diagnostic = parseCompilerMessage(node, directory);
                    if (diagnostic != null) {
                        diagnostics.add(diagnostic);
                    }
                    continue;
                }

                // Handle clippy-specific messages
                if (node.path("reason").asText().equals("compiler-artifact")) {
                    // Handle build artifacts if needed
                    continue;
                }

            } catch (Exception e) {
                logger.warn("Failed to parse clippy output line: " + line, e);
            }
        }

        return diagnostics;
    }

    public boolean isClippyAvailable() {
        try {
            Process process = new ProcessBuilder("cargo", "clippy", "--version").start();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
