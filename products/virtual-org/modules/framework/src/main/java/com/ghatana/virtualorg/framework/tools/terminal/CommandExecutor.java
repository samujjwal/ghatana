package com.ghatana.virtualorg.framework.tools.terminal;

import static com.ghatana.virtualorg.framework.util.BlockingExecutors.blockingExecutor;

import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Secure command executor for agent terminal operations.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides safe command execution for agents with: - Command allowlisting -
 * Timeout enforcement - Working directory restrictions - Output capture and
 * limiting
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * CommandExecutor executor = CommandExecutor.builder()
 *     .allowedCommands(List.of("git", "npm", "gradle"))
 *     .workingDirectory("/app/workspace")
 *     .timeout(Duration.ofMinutes(5))
 *     .maxOutputSize(1024 * 100)
 *     .metrics(metricsCollector)
 *     .build();
 *
 * CommandResult result = executor.execute("git", List.of("status")).getResult();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Secure command execution for agents
 * @doc.layer product
 * @doc.pattern Executor
 */
public class CommandExecutor {

    private final List<String> allowedCommands;
    private final File workingDirectory;
    private final Duration timeout;
    private final int maxOutputSize;
    private final Map<String, String> environment;
    private final MetricsCollector metrics;

    private CommandExecutor(Builder builder) {
        this.allowedCommands = builder.allowedCommands != null
                ? List.copyOf(builder.allowedCommands) : List.of();
        this.workingDirectory = builder.workingDirectory;
        this.timeout = builder.timeout != null ? builder.timeout : Duration.ofMinutes(5);
        this.maxOutputSize = builder.maxOutputSize > 0 ? builder.maxOutputSize : 100 * 1024;
        this.environment = builder.environment != null
                ? Map.copyOf(builder.environment) : Map.of();
        this.metrics = Objects.requireNonNull(builder.metrics, "metrics required");
    }

    /**
     * Executes a command with arguments.
     *
     * @param command The command to execute
     * @param args Command arguments
     * @return Promise of the command result
     */
    public Promise<CommandResult> execute(String command, List<String> args) {
        // Validate command is allowed
        if (!isCommandAllowed(command)) {
            return Promise.of(CommandResult.blocked(command,
                    "Command not in allowlist: " + command));
        }

        // Build the full command
        List<String> fullCommand = new java.util.ArrayList<>();
        fullCommand.add(command);
        if (args != null) {
            fullCommand.addAll(args);
        }

        return executeInternal(fullCommand);
    }

    /**
     * Executes a shell command string.
     *
     * @param shellCommand The shell command string
     * @return Promise of the command result
     */
    public Promise<CommandResult> executeShell(String shellCommand) {
        // Extract the base command for validation
        String baseCommand = extractBaseCommand(shellCommand);
        if (!isCommandAllowed(baseCommand)) {
            return Promise.of(CommandResult.blocked(baseCommand,
                    "Command not in allowlist: " + baseCommand));
        }

        // Execute via shell
        List<String> fullCommand = List.of("/bin/sh", "-c", shellCommand);
        return executeInternal(fullCommand);
    }

    private Promise<CommandResult> executeInternal(List<String> command) {
        return Promise.ofBlocking(blockingExecutor(), () -> {
            long startTime = System.currentTimeMillis();
            String commandStr = String.join(" ", command);

            try {
                ProcessBuilder pb = new ProcessBuilder(command);

                if (workingDirectory != null) {
                    pb.directory(workingDirectory);
                }

                // Set environment variables
                Map<String, String> env = pb.environment();
                env.putAll(environment);

                // Redirect stderr to stdout
                pb.redirectErrorStream(true);

                Process process = pb.start();

                // Read output with size limit
                StringBuilder output = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (output.length() + line.length() + 1 > maxOutputSize) {
                            output.append("\n... [output truncated]");
                            break;
                        }
                        if (output.length() > 0) {
                            output.append("\n");
                        }
                        output.append(line);
                    }
                }

                // Wait for completion with timeout
                boolean completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);

                long duration = System.currentTimeMillis() - startTime;

                if (!completed) {
                    process.destroyForcibly();
                    metrics.incrementCounter("terminal.command.timeout",
                            "command", extractBaseCommand(commandStr));
                    return CommandResult.timeout(commandStr, output.toString(), duration);
                }

                int exitCode = process.exitValue();
                metrics.recordTimer("terminal.command.duration", duration,
                        "command", extractBaseCommand(commandStr),
                        "exit_code", String.valueOf(exitCode));

                if (exitCode == 0) {
                    return CommandResult.success(commandStr, output.toString(), duration);
                } else {
                    return CommandResult.failed(commandStr, output.toString(), exitCode, duration);
                }

            } catch (IOException e) {
                metrics.incrementCounter("terminal.command.error",
                        "command", extractBaseCommand(commandStr));
                return CommandResult.error(commandStr, "IO error: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return CommandResult.error(commandStr, "Interrupted");
            }
        });
    }

    private boolean isCommandAllowed(String command) {
        if (allowedCommands.isEmpty()) {
            // If no allowlist specified, allow all (not recommended for production)
            return true;
        }
        return allowedCommands.contains(command);
    }

    private String extractBaseCommand(String command) {
        if (command == null || command.isEmpty()) {
            return "";
        }
        String[] parts = command.trim().split("\\s+");
        String base = parts[0];
        // Handle paths like /usr/bin/git
        int lastSlash = base.lastIndexOf('/');
        if (lastSlash >= 0) {
            base = base.substring(lastSlash + 1);
        }
        return base;
    }

    // ========== Builder ==========
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private List<String> allowedCommands;
        private File workingDirectory;
        private Duration timeout;
        private int maxOutputSize;
        private Map<String, String> environment;
        private MetricsCollector metrics;

        private Builder() {
        }

        public Builder allowedCommands(List<String> commands) {
            this.allowedCommands = commands;
            return this;
        }

        public Builder workingDirectory(String path) {
            this.workingDirectory = path != null ? new File(path) : null;
            return this;
        }

        public Builder workingDirectory(File dir) {
            this.workingDirectory = dir;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder maxOutputSize(int bytes) {
            this.maxOutputSize = bytes;
            return this;
        }

        public Builder environment(Map<String, String> env) {
            this.environment = env;
            return this;
        }

        public Builder metrics(MetricsCollector metrics) {
            this.metrics = metrics;
            return this;
        }

        public CommandExecutor build() {
            return new CommandExecutor(this);
        }
    }

    // ========== Result ==========
    /**
     * Result of command execution.
     */
    public record CommandResult(
            String command,
            String output,
            int exitCode,
            long durationMs,
            Status status,
            String errorMessage
    ) {
        

    

    public enum Status {
        SUCCESS,
        FAILED,
        TIMEOUT,
        BLOCKED,
        ERROR
    }

    public static CommandResult success(String command, String output, long durationMs) {
        return new CommandResult(command, output, 0, durationMs, Status.SUCCESS, null);
    }

    public static CommandResult failed(String command, String output, int exitCode, long durationMs) {
        return new CommandResult(command, output, exitCode, durationMs, Status.FAILED, null);
    }

    public static CommandResult timeout(String command, String output, long durationMs) {
        return new CommandResult(command, output, -1, durationMs, Status.TIMEOUT,
                "Command timed out");
    }

    public static CommandResult blocked(String command, String reason) {
        return new CommandResult(command, "", -1, 0, Status.BLOCKED, reason);
    }

    public static CommandResult error(String command, String errorMessage) {
        return new CommandResult(command, "", -1, 0, Status.ERROR, errorMessage);
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public boolean isBlocked() {
        return status == Status.BLOCKED;
    }
}
}
