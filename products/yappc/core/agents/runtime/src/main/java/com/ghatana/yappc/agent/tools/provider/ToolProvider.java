/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Core Agents Module - Tool Providers
 */
package com.ghatana.yappc.agent.tools.provider;

import com.ghatana.agent.framework.api.AgentContext;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Tool Provider Interface - Bridges capability definitions to tool implementations.
 *
 * <p><b>Purpose</b><br>
 * Provides a uniform interface for tools that can be invoked by agents based on
 * capability requirements. Each ToolProvider implements one or more capabilities
 * defined in capabilities.yaml.
 *
 * <p><b>Contract</b><br>
 * - Tools are idempotent where possible
 * - Tools return structured results (success/error)
 * - Tools execute asynchronously via Promise
 * - Tools validate inputs before execution
 *
 * <p><b>Example Implementation</b><br>
 * <pre>{@code
 * public class CodeGenerationToolProvider implements ToolProvider {
 *   @Override
 *   public String getCapabilityId() { return "code-generation"; }
 *
 *   @Override
 *   public Promise<ToolResult> execute(AgentContext ctx, Map<String, Object> params) {
 *     String spec = (String) params.get("specification");
 *     // Generate code...
 *     return Promise.of(new ToolResult(generatedCode, Map.of()));
 *   }
 * }
 * }</pre>
 *
 * @see ToolRegistry
 * @see ToolResult
 *
 * @author Ghatana AI Platform
 * @since 2.0.0
 
 * @doc.type interface
 * @doc.purpose Defines the contract for tool provider
 * @doc.layer core
 * @doc.pattern Provider
*/
public interface ToolProvider {

  /**
   * Gets the capability ID this tool provides.
   * Must match an entry in capabilities.yaml.
   *
   * @return the capability ID (e.g., "code-generation", "security-analysis")
   */
  @NotNull
  String getCapabilityId();

  /**
   * Gets the tool name for logging and diagnostics.
   *
   * @return the tool name
   */
  @NotNull
  default String getToolName() {
    return getClass().getSimpleName();
  }

  /**
   * Gets the tool version for compatibility checking.
   *
   * @return semantic version string
   */
  @NotNull
  default String getVersion() {
    return "1.0.0";
  }

  /**
   * Validates input parameters before execution.
   *
   * @param params the input parameters
   * @return null if valid, error message if invalid
   */
  @Nullable
  default String validateParams(@NotNull Map<String, Object> params) {
    return null; // Override for custom validation
  }

  /**
   * Executes the tool with the given parameters.
   *
   * @param ctx the agent execution context
   * @param params the tool parameters (capability-specific)
   * @return Promise of tool result
   */
  @NotNull
  Promise<ToolResult> execute(@NotNull AgentContext ctx, @NotNull Map<String, Object> params);

  /**
   * Checks if this tool is healthy and available.
   *
   * @return true if the tool can be invoked
   */
  default boolean isHealthy() {
    return true;
  }

  /**
   * Gets the estimated cost/complexity of executing this tool.
   * Used for budget gating and resource planning.
   *
   * @param params the proposed parameters
   * @return cost estimate (abstract units, higher = more expensive)
   */
  default int estimateCost(@NotNull Map<String, Object> params) {
    return 1; // Override for LLM-heavy or expensive tools
  }

  /**
   * Gets input parameter schema for validation.
   *
   * @return map of parameter name -> type info
   */
  @NotNull
  default Map<String, ParamSpec> getInputSchema() {
    return Map.of();
  }

  /**
   * Gets output result schema.
   *
   * @return map of output field name -> type info
   */
  @NotNull
  default Map<String, ParamSpec> getOutputSchema() {
    return Map.of();
  }

  /**
   * Parameter specification for schema validation.
   */
  record ParamSpec(
      @NotNull String type,
      boolean required,
      @Nullable String description,
      @Nullable Object defaultValue
  ) {}

  /**
   * Tool execution result.
   */
  record ToolResult(
      boolean success,
      @Nullable Object output,
      @Nullable String errorMessage,
      @NotNull Map<String, Object> metadata
  ) {
    /**
     * Creates a successful result.
     */
    public static ToolResult success(@NotNull Object output, @NotNull Map<String, Object> metadata) {
      return new ToolResult(true, output, null, metadata);
    }

    /**
     * Creates a failed result.
     */
    public static ToolResult failure(@NotNull String errorMessage, @NotNull Map<String, Object> metadata) {
      return new ToolResult(false, null, errorMessage, metadata);
    }

    /**
     * Creates a simple success result.
     */
    public static ToolResult success(@NotNull Object output) {
      return new ToolResult(true, output, null, Map.of());
    }

    /**
     * Creates a simple failure result.
     */
    public static ToolResult failure(@NotNull String errorMessage) {
      return new ToolResult(false, null, errorMessage, Map.of());
    }
  }
}
