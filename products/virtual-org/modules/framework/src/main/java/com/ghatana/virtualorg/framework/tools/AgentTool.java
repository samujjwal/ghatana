package com.ghatana.virtualorg.framework.tools;

import io.activej.promise.Promise;

import java.time.Duration;
import java.util.Set;

/**
 * Interface for executable agent tools.
 *
 * <p>
 * <b>Purpose</b><br>
 * Defines the contract for tools that agents can use to interact with external
 * systems. Each tool has a name, description, input/output schemas, required
 * permissions, and execution logic.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * public class GitHubCreatePRTool implements AgentTool {
 *     @Override
 *     public String getName() { return "github_create_pr"; }
 *
 *     @Override
 *     public Promise<ToolResult> execute(ToolInput input, ToolContext context) {
 *         String repo = input.getString("repository");
 *         String title = input.getString("title");
 *         // ... create PR
 *         return Promise.of(ToolResult.success(Map.of("pr_number", prNumber)));
 *     }
 * }
 * }</pre>
 *
 * @doc.type interface
 * @doc.purpose Standardized agent tool interface
 * @doc.layer product
 * @doc.pattern Command
 */
public interface AgentTool {

    /**
     * Gets the unique name of this tool. Must be a valid identifier (snake_case
     * recommended).
     *
     * @return The tool name
     */
    String getName();

    /**
     * Gets a human-readable description of what this tool does. This is
     * provided to the LLM to help it decide when to use the tool.
     *
     * @return The tool description
     */
    String getDescription();

    /**
     * Gets the JSON schema for the tool's input parameters. Default
     * implementation returns an empty schema. Override for proper schema.
     *
     * @return The input schema
     */
    default ToolSchema getInputSchema() {
        return ToolSchema.empty();
    }

    /**
     * Gets the JSON schema for the tool's output. Default implementation
     * returns an empty schema. Override for proper schema.
     *
     * @return The output schema
     */
    default ToolSchema getOutputSchema() {
        return ToolSchema.empty();
    }

    /**
     * Gets the permissions required to use this tool.
     *
     * @return Set of required permission strings
     */
    Set<String> getRequiredPermissions();

    /**
     * Gets the maximum execution time for this tool.
     *
     * @return The timeout duration
     */
    default Duration getTimeout() {
        return Duration.ofSeconds(30);
    }

    /**
     * Checks if this tool is idempotent (safe to retry).
     *
     * @return true if the tool is idempotent
     */
    default boolean isIdempotent() {
        return false;
    }

    /**
     * Gets the category of this tool (for organization/filtering).
     *
     * @return The tool category
     */
    default String getCategory() {
        return "general";
    }

    /**
     * Executes the tool with the given input and context.
     *
     * @param input The tool input parameters
     * @param context The execution context
     * @return Promise completing with the result
     */
    Promise<ToolResult> execute(ToolInput input, ToolContext context);

    /**
     * Validates the input before execution. Default implementation uses the
     * input schema.
     *
     * @param input The input to validate
     * @return Optional validation error message, empty if valid
     */
    default java.util.Optional<String> validateInput(ToolInput input) {
        java.util.List<String> errors = getInputSchema().validate(input);
        return errors.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(String.join("; ", errors));
    }

    /**
     * Validates input and returns list of errors.
     *
     * @param input The input to validate
     * @return List of validation errors, empty if valid
     */
    default java.util.List<String> validate(ToolInput input) {
        return getInputSchema().validate(input);
    }

    /**
     * Gets the schema as a JSON-like map (for LLM tool definitions).
     *
     * @return Map representation of the input schema
     */
    default java.util.Map<String, Object> getSchema() {
        return getInputSchema().toMap();
    }
}
