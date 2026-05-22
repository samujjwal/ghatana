package com.ghatana.kernel.plugin;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Dependency graph for plugins with cycle detection and topological ordering.
 *
 * <p>This class builds a dependency graph from plugin dependencies and provides
 * cycle detection and topological sorting for determining startup order. It ensures
 * that plugins are started in the correct order based on their dependencies and
 * detects circular dependencies that would prevent startup.</p>
 *
 * @doc.type class
 * @doc.purpose Plugin dependency graph with cycle detection and topological ordering
 * @doc.layer kernel
 * @doc.pattern Graph
 */
public final class PluginDependencyGraph {

    private final Map<String, Set<String>> adjacencyList;
    private final Map<String, PluginNode> nodes;

    public PluginDependencyGraph() {
        this.adjacencyList = new HashMap<>();
        this.nodes = new HashMap<>();
    }

    /**
     * Adds a plugin node to the graph.
     *
     * @param pluginId the plugin ID
     * @param dependencies the plugin's dependencies
     */
    public void addPlugin(String pluginId, Set<String> dependencies) {
        Objects.requireNonNull(pluginId, "pluginId must not be null");
        Objects.requireNonNull(dependencies, "dependencies must not be null");

        nodes.put(pluginId, new PluginNode(pluginId, dependencies));
        adjacencyList.put(pluginId, new HashSet<>(dependencies));
    }

    /**
     * Detects cycles in the dependency graph.
     *
     * @return a CycleDetectionResult containing whether cycles exist and the cycle path
     */
    public CycleDetectionResult detectCycles() {
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();
        Map<String, String> parentMap = new HashMap<>();

        for (String pluginId : nodes.keySet()) {
            if (!visited.contains(pluginId)) {
                List<String> cycle = detectCycleDFS(pluginId, visited, recursionStack, parentMap);
                if (cycle != null) {
                    return new CycleDetectionResult(true, cycle);
                }
            }
        }

        return new CycleDetectionResult(false, List.of());
    }

    private List<String> detectCycleDFS(
            String pluginId,
            Set<String> visited,
            Set<String> recursionStack,
            Map<String, String> parentMap) {
        visited.add(pluginId);
        recursionStack.add(pluginId);

        Set<String> dependencies = adjacencyList.getOrDefault(pluginId, Set.of());
        for (String dependency : dependencies) {
            if (!visited.contains(dependency)) {
                parentMap.put(dependency, pluginId);
                List<String> cycle = detectCycleDFS(dependency, visited, recursionStack, parentMap);
                if (cycle != null) {
                    return cycle;
                }
            } else if (recursionStack.contains(dependency)) {
                // Found a cycle, reconstruct the path
                return reconstructCycle(dependency, pluginId, parentMap);
            }
        }

        recursionStack.remove(pluginId);
        return null;
    }

    private List<String> reconstructCycle(String start, String end, Map<String, String> parentMap) {
        List<String> cycle = new ArrayList<>();
        cycle.add(start);
        
        String current = end;
        while (current != null && !current.equals(start)) {
            cycle.add(current);
            current = parentMap.get(current);
        }
        cycle.add(start);
        
        Collections.reverse(cycle);
        return cycle;
    }

    /**
     * Computes the topological order for plugin startup.
     *
     * @return a list of plugin IDs in startup order
     * @throws IllegalStateException if a cycle is detected
     */
    public List<String> computeStartupOrder() {
        CycleDetectionResult cycleResult = detectCycles();
        if (cycleResult.hasCycle()) {
            throw new IllegalStateException(
                "Cannot compute startup order: cycle detected in plugin dependencies: " + cycleResult.cyclePath());
        }

        // Kahn's algorithm for topological sorting
        Map<String, Integer> inDegree = new HashMap<>();
        for (String pluginId : nodes.keySet()) {
            inDegree.put(pluginId, 0);
        }

        for (String pluginId : adjacencyList.keySet()) {
            for (String dependency : adjacencyList.get(pluginId)) {
                inDegree.put(dependency, inDegree.getOrDefault(dependency, 0) + 1);
            }
        }

        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<String> startupOrder = new ArrayList<>();
        while (!queue.isEmpty()) {
            String pluginId = queue.poll();
            startupOrder.add(pluginId);

            Set<String> dependencies = adjacencyList.getOrDefault(pluginId, Set.of());
            for (String dependency : dependencies) {
                inDegree.put(dependency, inDegree.get(dependency) - 1);
                if (inDegree.get(dependency) == 0) {
                    queue.add(dependency);
                }
            }
        }

        if (startupOrder.size() != nodes.size()) {
            throw new IllegalStateException("Cycle detected in plugin dependencies");
        }

        return startupOrder;
    }

    /**
     * Gets all plugins in the graph.
     *
     * @return set of plugin IDs
     */
    public Set<String> getPluginIds() {
        return nodes.keySet();
    }

    /**
     * Gets dependencies for a plugin.
     *
     * @param pluginId the plugin ID
     * @return set of dependency plugin IDs
     */
    public Set<String> getDependencies(String pluginId) {
        return adjacencyList.getOrDefault(pluginId, Set.of());
    }

    /**
     * Gets dependents (plugins that depend on) a plugin.
     *
     * @param pluginId the plugin ID
     * @return set of dependent plugin IDs
     */
    public Set<String> getDependents(String pluginId) {
        return adjacencyList.entrySet().stream()
            .filter(entry -> entry.getValue().contains(pluginId))
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());
    }

    /**
     * Result of cycle detection.
     */
    public record CycleDetectionResult(
        boolean hasCycle,
        List<String> cyclePath
    ) {
        public CycleDetectionResult {
            if (cyclePath == null) {
                cyclePath = List.of();
            }
        }
    }

    /**
     * Node in the dependency graph.
     */
    private static final class PluginNode {
        private final String pluginId;
        private final Set<String> dependencies;

        PluginNode(String pluginId, Set<String> dependencies) {
            this.pluginId = pluginId;
            this.dependencies = dependencies;
        }

        String pluginId() { return pluginId; }
        Set<String> dependencies() { return dependencies; }
    }
}
