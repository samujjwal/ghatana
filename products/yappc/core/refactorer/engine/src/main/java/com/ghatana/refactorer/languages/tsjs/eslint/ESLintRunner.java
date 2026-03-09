/* Licensed under Apache-2.0 */
package com.ghatana.refactorer.languages.tsjs.eslint;

import com.ghatana.platform.domain.domain.Severity;
import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import com.ghatana.refactorer.shared.util.JsonSupport;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Handles the execution of ESLint and parsing of its output. 
 * @doc.type class
 * @doc.purpose Handles es lint runner operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
class ESLintRunner {
    private static final Logger logger = LogManager.getLogger(ESLintRunner.class);

    private final Path projectRoot;
    private final Path nodeModulesPath;
    private final Path eslintConfigPath;

    public ESLintRunner(Path projectRoot, Path nodeModulesPath, Path eslintConfigPath) {
        this.projectRoot = projectRoot;
        this.nodeModulesPath = nodeModulesPath;
        this.eslintConfigPath = eslintConfigPath;
    }

    /**
 * Run ESLint on the specified files and return the results as UnifiedDiagnostic objects. */
    public List<UnifiedDiagnostic> analyze(List<Path> files)
            throws IOException, InterruptedException {
        logger.info("Starting ESLint analysis for {} files", files.size());

        // Find the ESLint executable
        Path eslintExecutable = findESLintExecutable();
        if (eslintExecutable == null) {
            String error =
                    "ESLint executable not found in node_modules. Expected at: "
                            + nodeModulesPath.resolve(".bin/eslint")
                            + " or "
                            + nodeModulesPath.resolve("eslint/bin/eslint.js");
            logger.error(error);
            return Collections.emptyList();
        }

        logger.info("Using ESLint executable: {}", eslintExecutable);

        // Prepare the ESLint command
        List<String> command = new ArrayList<>();
        command.add(eslintExecutable.toString());
        command.add("--format=json");

        // Use the configuration from package.json if available
        Path packageJsonPath = projectRoot.resolve("package.json");
        if (Files.exists(packageJsonPath) && packageJsonPath.toFile().length() > 0) {
            String configOption = "--config=" + packageJsonPath.toAbsolutePath();
            command.add(configOption);
            logger.info("Using ESLint config from: {}", packageJsonPath.toAbsolutePath());
        } else if (Files.exists(eslintConfigPath)) {
            String configOption = "--config=" + eslintConfigPath.toAbsolutePath();
            command.add(configOption);
            logger.info("Using ESLint config from: {}", eslintConfigPath.toAbsolutePath());
        } else {
            logger.warn("No ESLint configuration found. Using default rules.");
        }

        // Add the files to analyze
        files.stream().map(Path::toString).forEach(command::add);

        logger.debug("Executing ESLint command: {}", String.join(" ", command));

        // Execute the command
        ProcessBuilder processBuilder =
                new ProcessBuilder(command)
                        .directory(projectRoot.toFile())
                        .redirectErrorStream(true);

        Process process = processBuilder.start();

        // Read the output
        StringBuilder outputBuilder = new StringBuilder();
        StringBuilder errorOutput = new StringBuilder();

        try (BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                outputBuilder.append(line).append("\n");
                // Check for error patterns in the output
                if (line.contains("Error")
                        || line.contains("error")
                        || line.contains("Error:")
                        || line.contains("error:")) {
                    errorOutput.append(line).append("\n");
                }
            }
        }

        String output = outputBuilder.toString().trim();

        // Log any errors found in the output
        if (errorOutput.length() > 0) {
            logger.error("ESLint reported errors:\n{}", errorOutput.toString().trim());
        }

        // Wait for the process to complete
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        int exitCode = process.exitValue();

        logger.debug("ESLint process exited with code: {}", exitCode);

        if (!finished) {
            String error = "ESLint analysis timed out after 30 seconds";
            logger.error(error);
            process.destroy();
            throw new IOException(error);
        }

        if (output.isEmpty()) {
            logger.warn("ESLint produced no output. Command: {}", String.join(" ", command));
            return Collections.emptyList();
        }

        logger.debug("ESLint raw output: {}", output);

        // Parse the ESLint output
        return parseESLintOutput(output);
    }

    /**
 * Find the ESLint executable in the node_modules directory. */
    private Path findESLintExecutable() {
        // Check for the binary in .bin directory
        Path binPath = nodeModulesPath.resolve(".bin/eslint");
        if (Files.exists(binPath)) {
            return binPath;
        }

        // Check for the main ESLint script
        Path eslintPath = nodeModulesPath.resolve("eslint/bin/eslint.js");
        if (Files.exists(eslintPath)) {
            return eslintPath;
        }

        return null;
    }

    /**
 * Parse the JSON output from ESLint into UnifiedDiagnostic objects. */
    private List<UnifiedDiagnostic> parseESLintOutput(String jsonOutput) {
        List<UnifiedDiagnostic> diagnostics = new ArrayList<>();

        if (jsonOutput == null || jsonOutput.trim().isEmpty()) {
            logger.warn("Empty or null JSON output from ESLint");
            return diagnostics;
        }

        try {
            // Parse the JSON output
            ESLintResult[] results = JsonSupport.defaultMapper().readValue(jsonOutput, ESLintResult[].class);

            if (results == null || results.length == 0) {
                logger.debug("No ESLint results to process");
                return diagnostics;
            }

            for (ESLintResult result : results) {
                if (result.messages != null) {
                    for (ESLintMessage message : result.messages) {
                        // Convert ESLint severity to our Severity enum
                        Severity severity =
                                message.severity == 2 ? Severity.ERROR : Severity.WARNING;

                        // Create metadata map
                        Map<String, String> meta = new HashMap<>();
                        meta.put("ruleId", message.ruleId != null ? message.ruleId : "");
                        meta.put("source", message.source != null ? message.source : "");

                        // Create the diagnostic
                        UnifiedDiagnostic diagnostic =
                                new UnifiedDiagnostic(
                                        "eslint",
                                        message.ruleId != null ? message.ruleId : "unknown",
                                        message.message,
                                        result.filePath,
                                        message.line,
                                        message.column,
                                        severity,
                                        meta);

                        diagnostics.add(diagnostic);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error parsing ESLint output", e);
        }

        return diagnostics;
    }

    // Helper classes for JSON parsing
    private static class ESLintResult {
        public String filePath;
        public List<ESLintMessage> messages;
    }

    private static class ESLintMessage {
        public int line;
        public int column;
        public int severity; // 1 = warning, 2 = error
        public String message;
        public String ruleId;
        public String source;
    }
}
