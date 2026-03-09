package com.ghatana.refactorer.diagnostics.bash;

import com.fasterxml.jackson.databind.JsonNode;
import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.platform.domain.domain.Severity;
import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import com.ghatana.refactorer.shared.process.ProcessRunner;
import com.ghatana.refactorer.shared.util.JsonSupport;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs Shellcheck to analyze Bash scripts and parse the JSON output.
 * 
 * @doc.type runner
 * @doc.language bash
 * @doc.tool shellcheck
 
 * @doc.purpose Handles shellcheck runner operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public class ShellcheckRunner {
    private static final Logger logger = LoggerFactory.getLogger(ShellcheckRunner.class);
    private static final String SHELLCHECK_CMD = "shellcheck";

    private final PolyfixProjectContext context;
    private final ProcessRunner processRunner;

    public ShellcheckRunner(PolyfixProjectContext context) {
        this.context = Objects.requireNonNull(context);
        this.processRunner = new ProcessRunner(context);
    }

    /**
     * Runs shellcheck on the specified file and returns the parsed diagnostics.
     */
    public List<UnifiedDiagnostic> run(Path file) {
        try {
            if (!isShellcheckAvailable()) {
                logger.warn("shellcheck is not available. Skipping Bash file analysis.");
                return List.of();
            }

            List<String> command = List.of(SHELLCHECK_CMD, "--format=json1", "--severity=style", file.toString());

            try {
                // First try with executeAndGetOutput which throws on non-zero exit codes
                String output = processRunner.executeAndGetOutput(
                        command.toArray(new String[0]), file.getParent());
                return parseShellcheckOutput(output, file);
            } catch (Exception e) {
                // If executeAndGetOutput fails, try to parse the error output as it might still
                // contain valid JSON
                logger.debug(
                        "Standard execution failed, trying to parse error output: {}",
                        e.getMessage());

                // Run the command manually to capture both stdout and stderr
                ProcessBuilder pb = new ProcessBuilder(command)
                        .directory(file.getParent().toFile())
                        .redirectErrorStream(false);

                Process process = pb.start();

                // Read both stdout and stderr
                String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                String errorOutput = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);

                int exitCode = process.waitFor();

                // Try to parse stdout first
                if (!output.trim().isEmpty()) {
                    try {
                        return parseShellcheckOutput(output, file);
                    } catch (Exception parseEx) {
                        logger.debug("Failed to parse stdout as JSON: {}", parseEx.getMessage());
                    }
                }

                // If stdout parsing failed, try stderr
                if (!errorOutput.trim().isEmpty()) {
                    try {
                        return parseShellcheckOutput(errorOutput, file);
                    } catch (Exception parseEx) {
                        logger.debug("Failed to parse stderr as JSON: {}", parseEx.getMessage());
                    }
                }

                // If we get here, both parsing attempts failed
                logger.error(
                        "Failed to parse shellcheck output. Exit code: {}\nOutput: {}\nError: {}",
                        exitCode,
                        output,
                        errorOutput);
                return List.of();
            }
        } catch (Exception e) {
            logger.error("Error running shellcheck on " + file, e);
            return List.of();
        }
    }

    private List<UnifiedDiagnostic> parseShellcheckOutput(String jsonOutput, Path file) {
        List<UnifiedDiagnostic> diagnostics = new ArrayList<>();

        if (jsonOutput == null || jsonOutput.trim().isEmpty()) {
            return diagnostics;
        }

        // Clean up the JSON output - shellcheck might output multiple JSON objects
        // Try to extract a valid JSON array from the output
        String cleanedJson = jsonOutput.trim();

        // If the output starts with [ and ends with ], it's already an array
        if (!cleanedJson.startsWith("[")) {
            // If not, try to find the first [ and last ] to extract the array
            int startBracket = cleanedJson.indexOf('[');
            int endBracket = cleanedJson.lastIndexOf(']');

            if (startBracket >= 0 && endBracket > startBracket) {
                cleanedJson = cleanedJson.substring(startBracket, endBracket + 1);
            } else {
                // If we can't find a complete array, try to parse as a single object
                try {
                    JsonNode node = JsonSupport.parseJson(cleanedJson);
                    if (node != null) {
                        // If it's a single object, wrap it in an array
                        cleanedJson = "[" + cleanedJson + "]";
                    }
                } catch (Exception e) {
                    logger.debug("Failed to parse as single JSON object, trying as array", e);
                }
            }
        }

        try {
            JsonNode root = JsonSupport.parseJson(cleanedJson);
            if (root == null) {
                logger.warn("Failed to parse shellcheck output as JSON");
                return diagnostics;
            }

            for (JsonNode node : root) {
                if (node == null)
                    continue;

                // Shellcheck's JSON format has an array of comments
                if (node.has("comments") && node.get("comments").isArray()) {
                    for (JsonNode comment : node.get("comments")) {
                        UnifiedDiagnostic diagnostic = parseDiagnostic(comment, file);
                        if (diagnostic != null) {
                            diagnostics.add(diagnostic);
                        }
                    }
                }
                // Some versions of shellcheck might output the comments directly
                else if (node.has("code") || node.has("message")) {
                    UnifiedDiagnostic diagnostic = parseDiagnostic(node, file);
                    if (diagnostic != null) {
                        diagnostics.add(diagnostic);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error parsing shellcheck output: " + e.getMessage(), e);
            logger.debug("Problematic JSON: {}", jsonOutput);
        }

        return diagnostics;
    }

    private UnifiedDiagnostic parseDiagnostic(JsonNode node, Path file) {
        try {
            // Ensure rule ID has 'SC' prefix
            String ruleId = node.path("code").asText();
            if (!ruleId.startsWith("SC") && !ruleId.isEmpty()) {
                ruleId = "SC" + ruleId;
            }
            String message = node.path("message").asText();
            int line = node.path("line").asInt();
            int column = node.path("column").asInt();

            return UnifiedDiagnostic.builder()
                    .file(file)
                    .ruleId(ruleId)
                    .message(message)
                    .startLine(line)
                    .startColumn(column)
                    .endLine(node.path("endLine").asInt(line))
                    .endColumn(node.path("endColumn").asInt(column))
                    .severity(mapSeverity(node.path("level").asText()))
                    .tool("shellcheck")
                    .build();

        } catch (Exception e) {
            logger.warn("Failed to parse shellcheck diagnostic: " + node, e);
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
            case "info" -> Severity.INFO;
            case "style" -> Severity.INFO; // Map style to INFO as STYLE doesn't exist in the shared
            // Severity enum
            default -> Severity.UNKNOWN;
        };
    }

    public boolean isShellcheckAvailable() {
        try {
            Process process = new ProcessBuilder(SHELLCHECK_CMD, "--version").start();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
