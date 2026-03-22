/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.framework.tools;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry for agent tools, enabling catalog-level discovery, registration,
 * and lookup of {@link FunctionTool} instances by name, category, or tag.
 *
 * <p>The {@code ToolRegistry} is the single source of truth for all tools available
 * to agents within a given execution context. Agents query the registry during
 * initialization to load their declared tools.
 *
 * <h2>Key capabilities</h2>
 * <ul>
 *   <li><b>Named registration</b>: Register tools by unique name</li>
 *   <li><b>Category grouping</b>: Organize tools by domain category</li>
 *   <li><b>Tag-based discovery</b>: Find tools by metadata tags</li>
 *   <li><b>JSON Schema export</b>: Export all tool schemas for LLM function calling</li>
 *   <li><b>Thread-safe</b>: ConcurrentHashMap-backed for safe concurrent access</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * ToolRegistry registry = ToolRegistry.create();
 * registry.register("sendAlert",
 *     FunctionTool.create(AlertService.class, "sendAlert")
 *         .withDescription("Send an alert to a channel"));
 *
 * Optional<FunctionTool> tool = registry.get("sendAlert");
 * Map<String, Object> allSchemas = registry.exportJsonSchemas();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Central registry for agent tool discovery and catalog-level management
 * @doc.layer framework
 * @doc.pattern Registry
 */
public final class ToolRegistry {

    private final Map<String, ToolEntry> tools = new ConcurrentHashMap<>();

    private ToolRegistry() {}

    /**
     * Creates a new empty tool registry.
     *
     * @return a new {@code ToolRegistry} instance
     */
    @NotNull
    public static ToolRegistry create() {
        return new ToolRegistry();
    }

    /**
     * Registers a tool under the given name.
     *
     * @param name the unique tool name (must match agent YAML tool declarations)
     * @param tool the tool to register
     * @return this registry (fluent)
     * @throws IllegalArgumentException if a tool with the same name is already registered
     */
    @NotNull
    public ToolRegistry register(@NotNull String name, @NotNull FunctionTool tool) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(tool, "tool must not be null");
        if (tools.containsKey(name)) {
            throw new IllegalArgumentException("Tool already registered: " + name);
        }
        tools.put(name, new ToolEntry(name, tool, Collections.emptyList(), Collections.emptyList()));
        return this;
    }

    /**
     * Registers a tool with category and tags for discovery.
     *
     * @param name     unique tool name
     * @param tool     the tool
     * @param category domain category (e.g. "storage", "alerting", "routing")
     * @param tags     discovery tags
     * @return this registry (fluent)
     */
    @NotNull
    public ToolRegistry register(
            @NotNull String name,
            @NotNull FunctionTool tool,
            @NotNull String category,
            @NotNull List<String> tags) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(tool, "tool must not be null");
        if (tools.containsKey(name)) {
            throw new IllegalArgumentException("Tool already registered: " + name);
        }
        tools.put(name, new ToolEntry(name, tool, List.of(category), new ArrayList<>(tags)));
        return this;
    }

    /**
     * Retrieves a tool by its registered name.
     *
     * @param name tool name
     * @return an {@code Optional} containing the tool, or empty if not registered
     */
    @NotNull
    public Optional<FunctionTool> get(@NotNull String name) {
        ToolEntry entry = tools.get(name);
        return entry == null ? Optional.empty() : Optional.of(entry.tool());
    }

    /**
     * Returns all tools registered under a given category.
     *
     * @param category category name
     * @return immutable list of matching tools (may be empty)
     */
    @NotNull
    public List<FunctionTool> findByCategory(@NotNull String category) {
        return tools.values().stream()
                .filter(e -> e.categories().contains(category))
                .map(ToolEntry::tool)
                .toList();
    }

    /**
     * Returns all tools tagged with the given tag.
     *
     * @param tag tag to search for
     * @return immutable list of matching tools (may be empty)
     */
    @NotNull
    public List<FunctionTool> findByTag(@NotNull String tag) {
        return tools.values().stream()
                .filter(e -> e.tags().contains(tag))
                .map(ToolEntry::tool)
                .toList();
    }

    /**
     * Exports JSON Schema definitions for all registered tools.
     * Suitable for LLM function-calling API payload construction.
     *
     * @return map from tool name to JSON Schema map
     */
    @NotNull
    public Map<String, Map<String, Object>> exportJsonSchemas() {
        Map<String, Map<String, Object>> schemas = new LinkedHashMap<>();
        tools.forEach((name, entry) -> schemas.put(name, entry.tool().toJsonSchema()));
        return Collections.unmodifiableMap(schemas);
    }

    /**
     * Returns the number of registered tools.
     *
     * @return tool count
     */
    public int size() {
        return tools.size();
    }

    /**
     * Returns all registered tool names.
     *
     * @return immutable set of tool names
     */
    @NotNull
    public Set<String> names() {
        return Collections.unmodifiableSet(tools.keySet());
    }

    /**
     * Checks whether a tool with the given name is registered.
     *
     * @param name tool name
     * @return {@code true} if registered
     */
    public boolean contains(@NotNull String name) {
        return tools.containsKey(name);
    }

    // -------------------------------------------------------------------------
    // Internal record
    // -------------------------------------------------------------------------

    private record ToolEntry(
            @NotNull String name,
            @NotNull FunctionTool tool,
            @NotNull List<String> categories,
            @NotNull List<String> tags) {}
}
