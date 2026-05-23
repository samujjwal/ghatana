/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.plugin;

import com.ghatana.kernel.plugin.PluginManifest;
import com.ghatana.kernel.descriptor.KernelDependency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Resolves plugin dependencies and checks for circular dependencies.
 *
 * @doc.type class
 * @doc.purpose Plugin dependency resolution and circular dependency detection
 * @doc.layer platform
 * @doc.pattern Resolver
 * @author Ghatana Platform Team
 * @since 1.0.0
 */
public class PluginDependencyResolver {

    private static final Logger log = LoggerFactory.getLogger(PluginDependencyResolver.class);

    /**
     * Resolves dependencies for a plugin manifest.
     *
     * @param manifest the plugin manifest
     * @throws PluginDependencyException if resolution fails
     */
    public void resolveDependencies(PluginManifest manifest) throws PluginDependencyException {
        log.debug("Resolving dependencies for plugin: {}", manifest.getPluginId());
        for (KernelDependency dependency : manifest.getDependencies()) {
            if (dependency.getType() == KernelDependency.DependencyType.PLUGIN
                    && dependency.getDependencyId().equals(manifest.getPluginId())) {
                throw new PluginDependencyException(
                        "Plugin '" + manifest.getPluginId() + "' cannot depend on itself");
            }
        }

        log.debug("Dependencies resolved for plugin: {}", manifest.getPluginId());
    }

    /**
     * Checks for circular dependencies in a set of plugins.
     *
     * @param plugins the plugins to check
     * @throws PluginDependencyException if circular dependencies are found
     */
    public void checkCircularDependencies(Map<String, PluginManifest> plugins)
            throws PluginDependencyException {

        log.debug("Checking for circular dependencies in {} plugins", plugins.size());

        Set<String> visited = new HashSet<>();
        Deque<String> path = new ArrayDeque<>();
        Set<String> active = new HashSet<>();

        for (String pluginId : new TreeSet<>(plugins.keySet())) {
            if (!visited.contains(pluginId)) {
                checkPluginGraph(pluginId, plugins, visited, active, path);
            }
        }

        log.debug("No circular dependencies found");
    }

    private void checkPluginGraph(
            String pluginId,
            Map<String, PluginManifest> plugins,
            Set<String> visited,
            Set<String> active,
            Deque<String> path) {
        visited.add(pluginId);
        active.add(pluginId);
        path.addLast(pluginId);

        PluginManifest manifest = plugins.get(pluginId);
        if (manifest != null) {
            resolveDependencies(manifest);
            for (KernelDependency dependency : manifest.getDependencies()) {
                if (dependency.getType() != KernelDependency.DependencyType.PLUGIN) {
                    continue;
                }

                String dependencyId = dependency.getDependencyId();
                PluginManifest dependencyManifest = plugins.get(dependencyId);
                if (dependencyManifest == null) {
                    if (!dependency.isOptional()) {
                        throw new PluginDependencyException(
                                "Plugin '" + pluginId + "' requires missing plugin dependency '" + dependencyId + "'");
                    }
                    continue;
                }
                if (!dependency.isVersionSatisfied(dependencyManifest.getVersion())) {
                    throw new PluginDependencyException(
                            "Plugin '" + pluginId + "' requires plugin dependency '" + dependencyId
                                    + "' version '" + dependency.getVersionConstraint()
                                    + "' but found '" + dependencyManifest.getVersion() + "'");
                }
                if (active.contains(dependencyId)) {
                    throw new PluginDependencyException("Circular dependency detected: "
                            + renderCycle(path, dependencyId));
                }
                if (!visited.contains(dependencyId)) {
                    checkPluginGraph(dependencyId, plugins, visited, active, path);
                }
            }
        }

        active.remove(pluginId);
        path.removeLast();
    }

    private String renderCycle(Deque<String> path, String repeatedPluginId) {
        List<String> cycle = new ArrayList<>();
        boolean inCycle = false;
        for (String pluginId : path) {
            if (pluginId.equals(repeatedPluginId)) {
                inCycle = true;
            }
            if (inCycle) {
                cycle.add(pluginId);
            }
        }
        cycle.add(repeatedPluginId);
        return String.join(" -> ", cycle);
    }

    /**
     * Builds a dependency graph for visualization and analysis.
     *
     * @param plugins the plugins to build the graph from
     * @return the dependency graph
     */
    public PluginDependencyGraph buildDependencyGraph(Map<String, PluginManifest> plugins) {
        Map<String, Set<String>> adjacencyList = new HashMap<>();
        Map<String, Integer> dependencyDepth = new HashMap<>();

        for (Map.Entry<String, PluginManifest> entry : plugins.entrySet()) {
            String pluginId = entry.getKey();
            PluginManifest manifest = entry.getValue();

            Set<String> dependencies = new HashSet<>();
            for (KernelDependency dependency : manifest.getDependencies()) {
                if (dependency.getType() == KernelDependency.DependencyType.PLUGIN) {
                    dependencies.add(dependency.getDependencyId());
                }
            }
            adjacencyList.put(pluginId, dependencies);
            dependencyDepth.put(pluginId, calculateDepth(pluginId, adjacencyList, new HashSet<>()));
        }

        return new PluginDependencyGraph(adjacencyList, dependencyDepth);
    }

    /**
     * Returns a topological sort of plugins based on dependencies.
     *
     * @param plugins the plugins to sort
     * @return list of plugin IDs in dependency order (dependencies before dependents)
     * @throws PluginDependencyException if circular dependencies exist
     */
    public List<String> topologicalSort(Map<String, PluginManifest> plugins) throws PluginDependencyException {
        checkCircularDependencies(plugins);

        Map<String, Set<String>> graph = buildDependencyGraph(plugins).adjacencyList();
        List<String> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();

        for (String pluginId : new TreeSet<>(plugins.keySet())) {
            if (!visited.contains(pluginId)) {
                topologicalVisit(pluginId, graph, visited, visiting, result);
            }
        }

        Collections.reverse(result);
        return result;
    }

    private int calculateDepth(String pluginId, Map<String, Set<String>> adjacencyList, Set<String> visiting) {
        if (visiting.contains(pluginId)) {
            return 0;
        }
        visiting.add(pluginId);

        Set<String> dependencies = adjacencyList.getOrDefault(pluginId, Set.of());
        int maxDepth = 0;
        for (String dep : dependencies) {
            int depDepth = calculateDepth(dep, adjacencyList, new HashSet<>(visiting));
            maxDepth = Math.max(maxDepth, depDepth + 1);
        }

        return maxDepth;
    }

    private void topologicalVisit(
            String pluginId,
            Map<String, Set<String>> graph,
            Set<String> visited,
            Set<String> visiting,
            List<String> result) {
        if (visited.contains(pluginId)) {
            return;
        }
        if (visiting.contains(pluginId)) {
            throw new PluginDependencyException("Circular dependency detected during topological sort");
        }

        visiting.add(pluginId);

        for (String dependency : graph.getOrDefault(pluginId, Set.of())) {
            topologicalVisit(dependency, graph, visited, visiting, result);
        }

        visiting.remove(pluginId);
        visited.add(pluginId);
        result.add(pluginId);
    }

    /**
     * Dependency graph representation for visualization and analysis.
     */
    public static final class PluginDependencyGraph {
        private final Map<String, Set<String>> adjacencyList;
        private final Map<String, Integer> dependencyDepth;

        PluginDependencyGraph(Map<String, Set<String>> adjacencyList, Map<String, Integer> dependencyDepth) {
            this.adjacencyList = Map.copyOf(adjacencyList);
            this.dependencyDepth = Map.copyOf(dependencyDepth);
        }

        public Map<String, Set<String>> adjacencyList() {
            return adjacencyList;
        }

        public Map<String, Integer> dependencyDepth() {
            return dependencyDepth;
        }

        public String toDotFormat() {
            StringBuilder sb = new StringBuilder();
            sb.append("digraph PluginDependencies {\n");
            sb.append("  rankdir=LR;\n");
            sb.append("  node [shape=box];\n\n");

            for (Map.Entry<String, Set<String>> entry : adjacencyList.entrySet()) {
                String pluginId = entry.getKey();
                int depth = dependencyDepth.getOrDefault(pluginId, 0);
                sb.append("  \"").append(pluginId).append("\" [label=\"").append(pluginId)
                  .append(" (depth: ").append(depth).append(")\"];\n");

                for (String dependency : entry.getValue()) {
                    sb.append("  \"").append(dependency).append("\" -> \"").append(pluginId).append("\";\n");
                }
            }

            sb.append("}\n");
            return sb.toString();
        }
    }
}
