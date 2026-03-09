package com.ghatana.virtualorg.tool;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Interface for tools that agents can execute.
 *
 * <p><b>Purpose</b><br>
 * Port interface defining tool capabilities for agent task execution.
 * Tools provide external operations like Git, file I/O, code execution, and HTTP requests.
 *
 * <p><b>Architecture Role</b><br>
 * Port interface for tool abstractions. Implementations provide:
 * - Git operations (clone, commit, push, pull)
 * - File operations (read, write, delete, search)
 * - Code execution (compile, test, analyze)
 * - Database queries
 * - HTTP requests
 * - Shell commands
 *
 * <p><b>Tool Types</b><br>
 * - **GitTool**: Version control operations
 * - **FileOperationsTool**: File system operations
 * - **HttpTool**: HTTP API calls
 * - **CodeExecutionTool**: Code compilation and execution
 * - **DatabaseTool**: SQL queries and data operations
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * Tool gitTool = new GitTool();
 * ToolResult result = gitTool.execute(Map.of(
 *     "operation", "clone",
 *     "url", "https://github.com/user/repo.git"
 * )).getResult();
 * }</pre>
 *
 * @doc.type interface
 * @doc.purpose Tool execution port for agent capabilities
 * @doc.layer product
 * @doc.pattern Port
 */
public interface Tool {

    /**
     * Gets the unique identifier of this tool.
     *
     * @return the tool ID
     */
    @NotNull
    String getId();

    /**
     * Gets the name of this tool.
     *
     * @return the tool name
     */
    @NotNull
    String getName();

    /**
     * Gets the description of what this tool does.
     *
     * @return the description
     */
    @NotNull
    String getDescription();

    /**
     * Gets the parameter schema as a JSON string.
     *
     * <p>This describes the expected parameters and their types.</p>
     *
     * @return the parameter schema
     */
    @NotNull
    String getParameterSchema();

    /**
     * Executes the tool with the given arguments.
     *
     * @param arguments the tool arguments
     * @return a promise of the result
     */
    @NotNull
    Promise<ToolResult> execute(@NotNull Map<String, String> arguments);

    /**
     * Gets the timeout for this tool in seconds.
     *
     * @return the timeout in seconds
     */
    int getTimeoutSeconds();

    /**
     * Checks if this tool is enabled.
     *
     * @return true if enabled, false otherwise
     */
    boolean isEnabled();
}
