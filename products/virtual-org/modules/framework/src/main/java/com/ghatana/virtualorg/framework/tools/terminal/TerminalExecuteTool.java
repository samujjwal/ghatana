package com.ghatana.virtualorg.framework.tools.terminal;

import com.ghatana.virtualorg.framework.tools.AgentTool;
import com.ghatana.virtualorg.framework.tools.ToolContext;
import com.ghatana.virtualorg.framework.tools.ToolInput;
import com.ghatana.virtualorg.framework.tools.ToolResult;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Tool for executing terminal commands.
 *
 * <p>
 * <b>Purpose</b><br>
 * Allows agents to execute shell commands for automation tasks like: - Running
 * build commands (gradle, npm, make) - Git operations (clone, pull, push) -
 * File operations (with restrictions) - Running tests
 *
 * <p>
 * <b>Security</b><br>
 * Commands are restricted to an allowlist and executed in a sandboxed
 * environment with timeout enforcement.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * AgentTool tool = new TerminalExecuteTool(commandExecutor);
 *
 * ToolInput input = ToolInput.builder()
 *     .put("command", "git")
 *     .put("args", List.of("status", "--short"))
 *     .build();
 *
 * ToolResult result = tool.execute(input, context).getResult();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Execute terminal commands
 * @doc.layer product
 * @doc.pattern Command
 */
public class TerminalExecuteTool implements AgentTool {

    private static final String TOOL_NAME = "terminal.execute";

    private final CommandExecutor executor;

    public TerminalExecuteTool(CommandExecutor executor) {
        this.executor = Objects.requireNonNull(executor, "executor required");
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "Execute a terminal command. "
                + "Supports common development commands like git, npm, gradle. "
                + "Commands are restricted to an allowlist for security.";
    }

    @Override
    public Map<String, Object> getSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "command", Map.of(
                                "type", "string",
                                "description", "The command to execute (e.g., 'git', 'npm', 'gradle')"
                        ),
                        "args", Map.of(
                                "type", "array",
                                "items", Map.of("type", "string"),
                                "description", "Command arguments as an array"
                        ),
                        "shell_command", Map.of(
                                "type", "string",
                                "description", "Alternative: full shell command string (e.g., 'git status --short')"
                        ),
                        "working_directory", Map.of(
                                "type", "string",
                                "description", "Working directory for the command (optional)"
                        )
                ),
                "required", List.of()
        );
    }

    @Override
    public Set<String> getRequiredPermissions() {
        return Set.of("terminal.execute");
    }

    @Override
    public Promise<ToolResult> execute(ToolInput input, ToolContext context) {
        try {
            Promise<CommandExecutor.CommandResult> resultPromise;

            if (input.has("shell_command")) {
                // Execute as shell command
                String shellCommand = input.getString("shell_command");
                resultPromise = executor.executeShell(shellCommand);
            } else if (input.has("command")) {
                // Execute command with args
                String command = input.getString("command");
                List<Object> rawArgs = input.getList("args");
                List<String> args = rawArgs.stream()
                        .map(Object::toString)
                        .toList();
                resultPromise = executor.execute(command, args);
            } else {
                return Promise.of(ToolResult.failure(
                        "Either 'command' or 'shell_command' is required"));
            }

            return resultPromise.map(result -> {
                if (result.isBlocked()) {
                    return ToolResult.failure("Command blocked: " + result.errorMessage());
                }

                Map<String, Object> data = new java.util.HashMap<>();
                data.put("command", result.command());
                data.put("exit_code", result.exitCode());
                data.put("duration_ms", result.durationMs());
                data.put("status", result.status().name());

                if (result.output() != null && !result.output().isEmpty()) {
                    data.put("output", result.output());
                }

                if (result.errorMessage() != null) {
                    data.put("error", result.errorMessage());
                }

                if (result.isSuccess()) {
                    return ToolResult.success(data);
                } else {
                    return ToolResult.partialSuccess(data,
                            "Command completed with exit code " + result.exitCode());
                }
            });
        } catch (IllegalArgumentException e) {
            return Promise.of(ToolResult.failure("Invalid input: " + e.getMessage()));
        }
    }

    @Override
    public List<String> validate(ToolInput input) {
        List<String> errors = new java.util.ArrayList<>();

        boolean hasCommand = input.has("command") || input.has("shell_command");
        if (!hasCommand) {
            errors.add("Either 'command' or 'shell_command' is required");
        }

        if (input.has("command") && input.has("shell_command")) {
            errors.add("Provide either 'command' or 'shell_command', not both");
        }

        return errors;
    }
}
