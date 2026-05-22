package com.ghatana.platform.plugin;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Plugin dependency graph for cycle detection and startup order planning.
 *
 * <p>Builds a directed graph of plugin dependencies and performs topological sort
 * to determine a valid startup order. Detects cycles that would cause deadlocks
 * during plugin initialization.</p>
 *
 * @doc.type class
 * @doc.purpose Detect plugin dependency cycles and compute startup order
 * @doc.layer kernel-plugin
 * @doc.pattern GraphAlgorithm
 */
public final class PluginDependencyGraph {

    private final Map<String, Set<String>> adjacencyList;
    private final Map<String, Set<String>> reverseAdjacencyList;

    public PluginDependencyGraph() {
        this.adjacencyList = new HashMap<>();
        this.reverseAdjacencyList = new HashMap<>();
    }

    /**
     * Adds a dependency from source plugin to target plugin.
     *
     * @param source the plugin that depends on target
     * @param target the plugin that source depends on
     */
    public void addDependency(String source, String target) {
        adjacencyList.computeIfAbsent(source, k -> new HashSet<>()).add(target);
        reverseAdjacencyList.computeIfAbsent(target, k -> new HashSet<>()).add(source);
    }

    /**
     * Adds a plugin with no dependencies.
     *
     * @param plugin the plugin identifier
     */
    public void addPlugin(String plugin) {
        adjacencyList.computeIfAbsent(plugin, k -> new HashSet<>());
        reverseAdjacencyList.computeIfAbsent(plugin, k -> new HashSet<>());
    }

    /**
     * Detects cycles in the plugin dependency graph.
     *
     * @return a cycle detection result with any cycles found
     */
    public CycleDetectionResult detectCycles() {
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();
        List<String> cyclePath = new ArrayList<>();

        for (String plugin : adjacencyList.keySet()) {
            if (!visited.contains(plugin)) {
                if (hasCycleDFS(plugin, visited, recursionStack, cyclePath, new ArrayList<>())) {
                    return CycleDetectionResult.cycleFound(cyclePath);
                }
            }
        }

        return CycleDetectionResult.noCycles();
    }

    private boolean hasCycleDFS(
        String plugin,
        Set<String> visited,
        Set<String> recursionStack,
        List<String> cyclePath,
        List<String> currentPath
    ) {
        visited.add(plugin);
        recursionStack.add(plugin);
        currentPath.add(plugin);

        for (String dependency : adjacencyList.getOrDefault(plugin, Collections.emptySet())) {
            if (!visited.contains(dependency)) {
                if (hasCycleDFS(dependency, visited, recursionStack, cyclePath, currentPath)) {
                    return true;
                }
            } else if (recursionStack.contains(dependency)) {
                // Found a cycle - extract the cycle path
                int cycleStart = currentPath.indexOf(dependency);
                cyclePath.addAll(currentPath.subList(cycleStart, currentPath.size()));
                cyclePath.add(dependency);
                return true;
            }
        }

        recursionStack.remove(plugin);
        currentPath.remove(currentPath.size() - 1);
        return false;
    }

    /**
     * Computes a valid startup order using topological sort.
     *
     * @return a startup order result with the ordered list of plugins
     */
    public StartupOrderResult computeStartupOrder() {
        CycleDetectionResult cycleCheck = detectCycles();
        if (cycleCheck.hasCycles()) {
            return StartupOrderResult.cycleDetected(cycleCheck.cyclePath());
        }

        List<String> order = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Map<String, Integer> inDegree = computeInDegrees();

        // Start with plugins that have no dependencies
        Queue<String> queue = new LinkedList<>();
        for (String plugin : adjacencyList.keySet()) {
            if (inDegree.getOrDefault(plugin, 0) == 0) {
                queue.add(plugin);
            }
        }

        while (!queue.isEmpty()) {
            String current = queue.poll();
            order.add(current);

            for (String dependent : reverseAdjacencyList.getOrDefault(current, Collections.emptySet())) {
                int newDegree = inDegree.get(dependent) - 1;
                inDegree.put(dependent, newDegree);
                if (newDegree == 0) {
                    queue.add(dependent);
                }
            }
        }

        // Check if all plugins were processed (should not happen if no cycles)
        if (order.size() != adjacencyList.keySet().size()) {
            return StartupOrderResult.partialOrder(order, "Some plugins could not be ordered due to unresolved dependencies");
        }

        return StartupOrderResult.validOrder(order);
    }

    private Map<String, Integer> computeInDegrees() {
        Map<String, Integer> inDegree = new HashMap<>();
        for (String plugin : adjacencyList.keySet()) {
            inDegree.put(plugin, 0);
        }
        for (Set<String> dependencies : adjacencyList.values()) {
            for (String dependency : dependencies) {
                inDegree.merge(dependency, 1, Integer::sum);
            }
        }
        return inDegree;
    }

    /**
     * Result of cycle detection operation.
     */
    public record CycleDetectionResult(
        boolean hasCycles,
        List<String> cyclePath
    ) {
        public static CycleDetectionResult cycleFound(List<String> cyclePath) {
            return new CycleDetectionResult(true, Collections.unmodifiableList(cyclePath));
        }

        public static CycleDetectionResult noCycles() {
            return new CycleDetectionResult(false, Collections.emptyList());
        }
    }

    /**
     * Result of startup order computation.
     */
    public record StartupOrderResult(
        boolean valid,
        List<String> startupOrder,
        String error
    ) {
        public static StartupOrderResult validOrder(List<String> order) {
            return new StartupOrderResult(true, Collections.unmodifiableList(order), null);
        }

        public static StartupOrderResult cycleDetected(List<String> cyclePath) {
            return new StartupOrderResult(false, Collections.emptyList(),
                "Cycle detected in plugin dependencies: " + String.join(" -> ", cyclePath));
        }

        public static StartupOrderResult partialOrder(List<String> order, String error) {
            return new StartupOrderResult(false, Collections.unmodifiableList(order), error);
        }
    }
}
