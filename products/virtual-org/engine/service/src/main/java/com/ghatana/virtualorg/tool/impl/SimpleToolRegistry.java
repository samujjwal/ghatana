package com.ghatana.virtualorg.tool.impl;

import com.ghatana.virtualorg.tool.Tool;
import com.ghatana.virtualorg.tool.ToolRegistry;
import com.ghatana.virtualorg.v1.ToolProto;
import com.ghatana.virtualorg.v1.ToolTypeProto;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Simple in-memory implementation of ToolRegistry.
 *
 * <p><b>Purpose</b><br>
 * Adapter implementing {@link ToolRegistry} using in-memory ConcurrentHashMap
 * for thread-safe tool storage and discovery.
 *
 * <p><b>Architecture Role</b><br>
 * Registry adapter for tool catalog. Provides:
 * - Thread-safe tool registration/unregistration
 * - Tool lookup by ID
 * - Agent-specific tool filtering
 * - Tool metadata management
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * SimpleToolRegistry registry = new SimpleToolRegistry();
 * 
 * // Register tools
 * registry.register(new GitTool(eventloop, 60));
 * registry.register(new FileOperationsTool(eventloop, "/workspace", 30));
 * 
 * // Get tool by ID
 * Optional<Tool> git = registry.getTool("git");
 * 
 * // Get tools for agent
 * List<ToolProto> tools = registry.getToolsForAgent("agent-123");
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * This implementation uses ConcurrentHashMap for thread-safe concurrent access.
 *
 * @doc.type class
 * @doc.purpose In-memory tool registry adapter with thread-safe storage
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class SimpleToolRegistry implements ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(SimpleToolRegistry.class);

    private final Map<String, Tool> tools = new ConcurrentHashMap<>();
    private final Map<String, List<String>> agentTools = new ConcurrentHashMap<>();

    @Override
    public void register(@NotNull Tool tool) {
        tools.put(tool.getId(), tool);
        log.info("Registered tool: id={}, name={}", tool.getId(), tool.getName());
    }

    @Override
    public void unregister(@NotNull String toolId) {
        Tool removed = tools.remove(toolId);
        if (removed != null) {
            log.info("Unregistered tool: id={}, name={}", toolId, removed.getName());
        }
    }

    @Override
    @NotNull
    public Optional<Tool> getTool(@NotNull String toolId) {
        return Optional.ofNullable(tools.get(toolId));
    }

    @Override
    @NotNull
    public List<ToolProto> getToolsForAgent(@NotNull String agentId) {
        // Get tool IDs for this agent
        List<String> toolIds = agentTools.getOrDefault(agentId, getAllToolIds());

        return toolIds.stream()
                .map(tools::get)
                .filter(tool -> tool != null && tool.isEnabled())
                .map(this::toProto)
                .collect(Collectors.toList());
    }

    @Override
    @NotNull
    public List<ToolProto> getAllTools() {
        return tools.values().stream()
                .map(this::toProto)
                .collect(Collectors.toList());
    }

    @Override
    public boolean isRegistered(@NotNull String toolId) {
        return tools.containsKey(toolId);
    }

    /**
     * Associates tools with an agent.
     *
     * @param agentId the agent ID
     * @param toolIds the tool IDs to associate
     */
    public void setToolsForAgent(@NotNull String agentId, @NotNull List<String> toolIds) {
        agentTools.put(agentId, toolIds);
        log.info("Assigned {} tools to agent: {}", toolIds.size(), agentId);
    }

    private List<String> getAllToolIds() {
        return List.copyOf(tools.keySet());
    }

    private ToolProto toProto(Tool tool) {
        return ToolProto.newBuilder()
                .setToolId(tool.getId())
                .setName(tool.getName())
                .setDescription(tool.getDescription())
                .setType(inferToolType(tool))
                .setParameterSchema(tool.getParameterSchema())
                .setEnabled(tool.isEnabled())
                .setTimeoutSeconds(tool.getTimeoutSeconds())
                .build();
    }

    private ToolTypeProto inferToolType(Tool tool) {
        String name = tool.getName().toLowerCase();
        if (name.contains("git")) return ToolTypeProto.TOOL_TYPE_GIT;
        if (name.contains("file")) return ToolTypeProto.TOOL_TYPE_FILE_OPERATION;
        if (name.contains("http") || name.contains("api")) return ToolTypeProto.TOOL_TYPE_HTTP;
        if (name.contains("shell") || name.contains("bash")) return ToolTypeProto.TOOL_TYPE_SHELL;
        if (name.contains("db") || name.contains("sql")) return ToolTypeProto.TOOL_TYPE_DATABASE;
        if (name.contains("code") || name.contains("execute")) return ToolTypeProto.TOOL_TYPE_CODE_EXECUTION;
        return ToolTypeProto.TOOL_TYPE_CUSTOM;
    }
}
