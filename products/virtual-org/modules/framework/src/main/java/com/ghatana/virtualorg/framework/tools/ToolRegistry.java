package com.ghatana.virtualorg.framework.tools;

import io.activej.promise.Promise;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Registry for managing and discovering agent tools.
 *
 * <p>
 * <b>Purpose</b><br>
 * Central registry for all available tools in the virtual organization
 * framework. Provides: - Tool registration and discovery - Permission-based
 * filtering - Category-based organization - Schema generation for LLM
 * integration
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * ToolRegistry registry = new ToolRegistry();
 *
 * // Register tools
 * registry.register(new GitHubTool());
 * registry.register(new JiraTool());
 *
 * // Find tools
 * Optional<AgentTool> tool = registry.findByName("github.create_pr");
 *
 * // Get tools by category
 * List<AgentTool> devTools = registry.getByCategory("development");
 *
 * // Filter by permissions
 * Set<String> permissions = Set.of("github.read", "github.write");
 * List<AgentTool> accessible = registry.getAccessibleTools(permissions);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Tool management and discovery
 * @doc.layer product
 * @doc.pattern Registry
 */
public class ToolRegistry {

    private final Map<String, AgentTool> tools;
    private final Map<String, Set<String>> categoryIndex;

    public ToolRegistry() {
        this.tools = new ConcurrentHashMap<>();
        this.categoryIndex = new ConcurrentHashMap<>();
    }

    // ========== Registration ==========
    /**
     * Registers a tool in the registry.
     *
     * @param tool The tool to register
     * @return This registry for chaining
     * @throws IllegalArgumentException if a tool with the same name already
     * exists
     */
    public ToolRegistry register(AgentTool tool) {
        String name = tool.getName();
        if (tools.containsKey(name)) {
            throw new IllegalArgumentException("Tool already registered: " + name);
        }
        tools.put(name, tool);

        // Index by category
        String category = extractCategory(name);
        categoryIndex.computeIfAbsent(category, k -> ConcurrentHashMap.newKeySet())
                .add(name);

        return this;
    }

    /**
     * Registers multiple tools.
     *
     * @param tools The tools to register
     * @return This registry for chaining
     */
    public ToolRegistry registerAll(Collection<AgentTool> tools) {
        tools.forEach(this::register);
        return this;
    }

    /**
     * Unregisters a tool by name.
     *
     * @param name The tool name
     * @return The unregistered tool, or empty if not found
     */
    public Optional<AgentTool> unregister(String name) {
        AgentTool tool = tools.remove(name);
        if (tool != null) {
            String category = extractCategory(name);
            Set<String> categoryTools = categoryIndex.get(category);
            if (categoryTools != null) {
                categoryTools.remove(name);
            }
        }
        return Optional.ofNullable(tool);
    }

    // ========== Discovery ==========
    /**
     * Finds a tool by name.
     *
     * @param name The tool name
     * @return The tool, or empty if not found
     */
    public Optional<AgentTool> findByName(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    /**
     * Gets all registered tools.
     *
     * @return Unmodifiable collection of all tools
     */
    public Collection<AgentTool> getAll() {
        return List.copyOf(tools.values());
    }

    /**
     * Gets all tool names.
     *
     * @return Unmodifiable set of all tool names
     */
    public Set<String> getAllNames() {
        return Set.copyOf(tools.keySet());
    }

    /**
     * Gets tools by category.
     *
     * @param category The category (e.g., "github", "jira", "slack")
     * @return List of tools in that category
     */
    public List<AgentTool> getByCategory(String category) {
        Set<String> toolNames = categoryIndex.get(category);
        if (toolNames == null || toolNames.isEmpty()) {
            return List.of();
        }
        return toolNames.stream()
                .map(tools::get)
                .filter(t -> t != null)
                .collect(Collectors.toList());
    }

    /**
     * Gets all available categories.
     *
     * @return Set of category names
     */
    public Set<String> getCategories() {
        return Set.copyOf(categoryIndex.keySet());
    }

    // ========== Permission Filtering ==========
    /**
     * Gets tools accessible with the given permissions.
     *
     * @param grantedPermissions The permissions the agent has
     * @return List of accessible tools
     */
    public List<AgentTool> getAccessibleTools(Set<String> grantedPermissions) {
        return tools.values().stream()
                .filter(tool -> hasRequiredPermissions(tool, grantedPermissions))
                .collect(Collectors.toList());
    }

    /**
     * Checks if the granted permissions satisfy a tool's requirements.
     *
     * @param tool The tool to check
     * @param grantedPermissions The available permissions
     * @return true if all required permissions are granted
     */
    public boolean hasRequiredPermissions(AgentTool tool, Set<String> grantedPermissions) {
        Set<String> required = tool.getRequiredPermissions();
        if (required == null || required.isEmpty()) {
            return true;
        }
        return grantedPermissions.containsAll(required);
    }

    // ========== Execution ==========
    /**
     * Executes a tool by name.
     *
     * @param name The tool name
     * @param input The tool input
     * @param context The execution context
     * @return Promise of the tool result
     * @throws IllegalArgumentException if tool not found
     */
    public Promise<ToolResult> execute(String name, ToolInput input, ToolContext context) {
        AgentTool tool = tools.get(name);
        if (tool == null) {
            return Promise.of(ToolResult.failure("Tool not found: " + name));
        }
        return executeTool(tool, input, context);
    }

    /**
     * Executes a tool with validation.
     *
     * @param tool The tool to execute
     * @param input The tool input
     * @param context The execution context
     * @return Promise of the tool result
     */
    private Promise<ToolResult> executeTool(AgentTool tool, ToolInput input, ToolContext context) {
        // Validate input
        List<String> errors = tool.validate(input);
        if (!errors.isEmpty()) {
            return Promise.of(ToolResult.failure("Validation failed: " + String.join(", ", errors)));
        }

        // Execute with timeout handling
        return tool.execute(input, context)
                .map(result -> result)
                .mapException(e -> new RuntimeException(
                "Tool execution failed [" + tool.getName() + "]: " + e.getMessage(), e));
    }

    // ========== Schema Generation ==========
    /**
     * Generates tool schemas for LLM integration. Returns schemas in a format
     * suitable for function calling.
     *
     * @return List of tool schemas as maps
     */
    public List<Map<String, Object>> generateSchemas() {
        return tools.values().stream()
                .map(this::toSchema)
                .collect(Collectors.toList());
    }

    /**
     * Generates schemas for accessible tools only.
     *
     * @param grantedPermissions The permissions to filter by
     * @return List of tool schemas
     */
    public List<Map<String, Object>> generateSchemas(Set<String> grantedPermissions) {
        return getAccessibleTools(grantedPermissions).stream()
                .map(this::toSchema)
                .collect(Collectors.toList());
    }

    private Map<String, Object> toSchema(AgentTool tool) {
        return Map.of(
                "name", tool.getName(),
                "description", tool.getDescription(),
                "parameters", tool.getSchema()
        );
    }

    // ========== Utilities ==========
    private String extractCategory(String toolName) {
        int dotIndex = toolName.indexOf('.');
        if (dotIndex > 0) {
            return toolName.substring(0, dotIndex);
        }
        return "general";
    }

    /**
     * Gets the count of registered tools.
     *
     * @return Number of tools
     */
    public int size() {
        return tools.size();
    }

    /**
     * Checks if a tool is registered.
     *
     * @param name The tool name
     * @return true if registered
     */
    public boolean contains(String name) {
        return tools.containsKey(name);
    }

    /**
     * Clears all registered tools.
     */
    public void clear() {
        tools.clear();
        categoryIndex.clear();
    }

    @Override
    public String toString() {
        return "ToolRegistry{"
                + "toolCount=" + tools.size()
                + ", categories=" + categoryIndex.keySet()
                + '}';
    }
}
