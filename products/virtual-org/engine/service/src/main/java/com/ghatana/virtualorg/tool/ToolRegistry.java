package com.ghatana.virtualorg.tool;

import com.ghatana.virtualorg.v1.ToolProto;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

/**
 * Registry for tools available to agents.
 *
 * <p><b>Purpose</b><br>
 * Port interface for tool discovery and management. Tools are registered
 * and made available to agents based on roles and permissions.
 *
 * <p><b>Architecture Role</b><br>
 * Registry port interface for tool catalog. Implementations provide:
 * - Tool registration and discovery
 * - Role-based tool filtering
 * - Tool metadata management
 * - Dynamic tool loading
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * ToolRegistry registry = new SimpleToolRegistry();
 * 
 * // Register tools
 * registry.register(new GitTool());
 * registry.register(new FileOperationsTool());
 * 
 * // Get tool by ID
 * Optional<Tool> tool = registry.getTool("git");
 * 
 * // Get all tools for role
 * List<Tool> tools = registry.getToolsForRole(AgentRoleProto.SENIOR_ENGINEER);
 * }</pre>
 *
 * @doc.type interface
 * @doc.purpose Tool registry port for discovery and management
 * @doc.layer product
 * @doc.pattern Port
 */
public interface ToolRegistry {

    /**
     * Registers a tool.
     *
     * @param tool the tool to register
     */
    void register(@NotNull Tool tool);

    /**
     * Unregisters a tool.
     *
     * @param toolId the tool ID
     */
    void unregister(@NotNull String toolId);

    /**
     * Gets a tool by ID.
     *
     * @param toolId the tool ID
     * @return the tool, or empty if not found
     */
    @NotNull
    Optional<Tool> getTool(@NotNull String toolId);

    /**
     * Gets all tools available to an agent.
     *
     * @param agentId the agent ID
     * @return the list of available tools
     */
    @NotNull
    List<ToolProto> getToolsForAgent(@NotNull String agentId);

    /**
     * Gets all registered tools.
     *
     * @return the list of all tools
     */
    @NotNull
    List<ToolProto> getAllTools();

    /**
     * Checks if a tool is registered.
     *
     * @param toolId the tool ID
     * @return true if registered, false otherwise
     */
    boolean isRegistered(@NotNull String toolId);
}
