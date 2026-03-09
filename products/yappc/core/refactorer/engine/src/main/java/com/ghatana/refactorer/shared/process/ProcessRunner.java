package com.ghatana.refactorer.shared.process;

import com.ghatana.refactorer.shared.PolyfixProjectContext;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.Logger;

/**
 * Helper class to run external processes. 
 * @doc.type class
 * @doc.purpose Handles process runner operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public class ProcessRunner {
    protected static final long DEFAULT_TIMEOUT_MINUTES = 5L;

    protected final PolyfixProjectContext context;
    protected final Logger logger;

    public ProcessRunner(PolyfixProjectContext context) {
        this.context = context;
        this.logger = context.log();
    }

    /**
     * Executes a command and returns the result.
     *
     * @param command The command to execute
     * @param args Command arguments
     * @param workingDir Working directory for the process
     * @param captureOutput Whether to capture the output
     * @return Process execution result
     */
    /**
     * Executes a command and returns its output as a string.
     *
     * @param command The command and its arguments
     * @param workingDir The working directory for the command
     * @return The command's output as a string
     * @throws IOException if an I/O error occurs
     * @throws InterruptedException if the process is interrupted
     */
    public String executeAndGetOutput(String[] command, Path workingDir)
            throws IOException, InterruptedException {
        ProcessResult result =
                execute(
                        null, // No environment variables
                        command[0],
                        Arrays.asList(Arrays.copyOfRange(command, 1, command.length)),
                        workingDir,
                        true);
        if (result.exitCode() != 0) {
            throw new IOException(
                    "Command failed with exit code " + result.exitCode() + ": " + result.error());
        }
        return result.output();
    }

    /**
     * Executes a command and returns the result.
     *
     * @param command The command to execute
     * @param args Command arguments
     * @param workingDir Working directory for the process
     * @param captureOutput Whether to capture the output
     * @return Process execution result
     */
    public ProcessResult execute(
            String command, List<String> args, Path workingDir, boolean captureOutput) {
        return execute(null, command, args, workingDir, captureOutput);
    }

    /**
     * Executes a command with custom environment variables and returns the result.
     *
     * @param envVars Environment variables to set (can be null)
     * @param command The command to execute
     * @param args Command arguments
     * @param workingDir Working directory for the process
     * @param captureOutput Whether to capture the output
     * @return Process execution result
     */
    public ProcessResult execute(
            Map<String, String> envVars,
            String command,
            List<String> args,
            Path workingDir,
            boolean captureOutput) {
        try {
            List<String> commandLine = new ArrayList<>();
            commandLine.add(command);
            commandLine.addAll(args);

            ProcessBuilder processBuilder =
                    new ProcessBuilder(commandLine).directory(workingDir.toFile());

            // Set custom environment variables if provided
            if (envVars != null && !envVars.isEmpty()) {
                Map<String, String> processEnv = processBuilder.environment();
                processEnv.putAll(envVars);
            }

            logger.debug("Executing in {}: {}", workingDir, String.join(" ", commandLine));

            Process process = processBuilder.start();

            StringBuilder output = new StringBuilder();
            StringBuilder error = new StringBuilder();

            // Read output in a separate thread to avoid deadlocks
            Thread outputThread =
                    new Thread(
                            () -> {
                                try (BufferedReader reader =
                                        new BufferedReader(
                                                new InputStreamReader(
                                                        process.getInputStream(),
                                                        StandardCharsets.UTF_8))) {
                                    String line;
                                    while ((line = reader.readLine()) != null) {
                                        if (captureOutput) {
                                            output.append(line).append("\n");
                                        } else {
                                            logger.info("[{}] {}", command, line);
                                        }
                                    }
                                } catch (IOException e) {
                                    logger.error(
                                            "Error reading process output for command '{}': {}",
                                            command,
                                            e.getMessage(),
                                            e);
                                }
                            },
                            "process-output-" + command);

            outputThread.start();

            // Read error in a separate thread
            Thread errorThread =
                    new Thread(
                            () -> {
                                try (BufferedReader reader =
                                        new BufferedReader(
                                                new InputStreamReader(
                                                        process.getErrorStream(),
                                                        StandardCharsets.UTF_8))) {
                                    String line;
                                    while ((line = reader.readLine()) != null) {
                                        if (captureOutput) {
                                            error.append(line).append("\n");
                                        } else {
                                            logger.warn("[{}][stderr] {}", command, line);
                                        }
                                    }
                                } catch (IOException e) {
                                    logger.error(
                                            "Error reading process error for command '{}': {}",
                                            command,
                                            e.getMessage(),
                                            e);
                                }
                            },
                            "process-error-" + command);

            errorThread.start();

            // Wait for the process to complete with default timeout
            boolean finished = process.waitFor(DEFAULT_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            if (!finished) {
                String errorMsg =
                        String.format(
                                "Process timed out after %d minutes: %s",
                                DEFAULT_TIMEOUT_MINUTES, String.join(" ", commandLine));
                logger.error(errorMsg);
                process.destroyForcibly();
                throw new RuntimeException(errorMsg);
            }

            // Wait for output to be fully read
            outputThread.join(5000);
            errorThread.join(5000);

            int exitCode = process.exitValue();

            return new ProcessResult(exitCode, output.toString().trim(), error.toString().trim());

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to execute process", e);
        }
    }
}
