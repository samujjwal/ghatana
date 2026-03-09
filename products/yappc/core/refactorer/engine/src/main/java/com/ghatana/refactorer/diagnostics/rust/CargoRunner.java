package com.ghatana.refactorer.diagnostics.rust;

import com.fasterxml.jackson.databind.JsonNode;
import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.platform.domain.domain.Severity;
import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import com.ghatana.refactorer.shared.process.ProcessRunner;
import com.ghatana.refactorer.shared.util.JsonSupport;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs `cargo check` to analyze Rust code and parse the JSON output.
 * 
 * @doc.type runner
 * @doc.language rust
 * @doc.tool cargo
 
 * @doc.purpose Handles cargo runner operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public class CargoRunner {
    private static final Logger logger = LoggerFactory.getLogger(CargoRunner.class);
    private static final String CARGO_CMD = "cargo";

    private final PolyfixProjectContext context;
    protected final ProcessRunner processRunner;

    public CargoRunner(PolyfixProjectContext context) {
        this.context = Objects.requireNonNull(context);
        this.processRunner = new ProcessRunner(context);
    }

    /**
     * Runs `cargo check` on the specified directory and returns the parsed
     * diagnostics.
     */
    public List<UnifiedDiagnostic> run(Path directory) {
        try {
            if (!isCargoAvailable()) {
                logger.warn("cargo is not available. Skipping Rust analysis.");
                return List.of();
            }

            List<String> command = List.of(CARGO_CMD, "check", "--message-format=json", "--no-deps");

            String output = processRunner.executeAndGetOutput(command.toArray(new String[0]), directory);
            return parseCargoOutput(output, directory);

        } catch (Exception e) {
            logger.error("Error running cargo check in " + directory, e);
            return List.of();
        }
    }

    private List<UnifiedDiagnostic> parseCargoOutput(String jsonOutput, Path directory) {
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

                if (node.has("message")) {
                    // This is a compiler message
                    UnifiedDiagnostic diagnostic = parseCompilerMessage(node, directory);
                    if (diagnostic != null) {
                        diagnostics.add(diagnostic);
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed to parse cargo output line: " + line, e);
            }
        }

        return diagnostics;
    }

    protected UnifiedDiagnostic parseCompilerMessage(JsonNode messageNode, Path directory) {
        try {
            JsonNode message = messageNode.path("message");
            String messageText = message.path("message").asText();
            String level = message.path("level").asText().toLowerCase();

            // Skip non-error/warning messages
            if (!level.equals("error") && !level.equals("warning")) {
                return null;
            }

            // Get the primary span (first span in the list)
            JsonNode spans = message.path("spans");
            if (spans.isEmpty()) {
                return null;
            }

            JsonNode primarySpan = spans.get(0);
            String filePath = primarySpan.path("file_name").asText();
            int line = primarySpan.path("line_start").asInt();
            int column = primarySpan.path("column_start").asInt();
            int endLine = primarySpan.path("line_end").asInt(line);
            int endColumn = primarySpan.path("column_end").asInt(column);

            // Get the error code if available
            String code = null;
            if (message.has("code") && message.get("code").has("code")) {
                code = message.get("code").get("code").asText();
            }

            // Build the diagnostic
            return UnifiedDiagnostic.builder()
                    .file(directory.resolve(filePath).normalize())
                    .ruleId(code)
                    .message(messageText)
                    .startLine(line)
                    .startColumn(column)
                    .endLine(endLine)
                    .endColumn(endColumn)
                    .severity(mapSeverity(level))
                    .tool("cargo")
                    .build();

        } catch (Exception e) {
            logger.warn("Failed to parse compiler message: " + messageNode, e);
            return null;
        }
    }

    private Severity mapSeverity(String level) {
        if (level == null) {
            return Severity.UNKNOWN;
        }
        return switch (level.toLowerCase()) {
            case "error" -> Severity.ERROR;
            case "warning" -> Severity.WARNING;
            case "note", "help" -> Severity.INFO;
            default -> Severity.UNKNOWN;
        };
    }

    public boolean isCargoAvailable() {
        try {
            Process process = new ProcessBuilder(CARGO_CMD, "--version").start();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
