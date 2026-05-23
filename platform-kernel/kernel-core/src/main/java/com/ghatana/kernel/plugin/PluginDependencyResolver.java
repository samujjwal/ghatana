package com.ghatana.kernel.plugin;

import com.ghatana.kernel.descriptor.KernelDependency;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves plugin startup order based on declared plugin/module dependencies.
 *
 * @doc.type class
 * @doc.purpose Topological sort for plugin dependency ordering
 * @doc.layer core
 * @doc.pattern Resolver
 */
public final class PluginDependencyResolver {

    /**
     * Checks for circular dependencies among plugins.
     *
     * @param plugins plugins to check
     * @throws PluginDependencyException when circular dependency is detected
     */
    public void checkCircularDependencies(Collection<KernelPlugin> plugins) throws PluginDependencyException {
        Map<String, Set<String>> dependencies = new HashMap<>();
        Map<String, Set<String>> visiting = new HashMap<>();
        Map<String, Set<String>> visited = new HashMap<>();

        for (KernelPlugin plugin : plugins) {
            String pluginId = plugin.getModuleId();
            dependencies.put(pluginId, new HashSet<>());
            visiting.put(pluginId, new HashSet<>());
            visited.put(pluginId, new HashSet<>());
        }

        for (KernelPlugin plugin : plugins) {
            String pluginId = plugin.getModuleId();
            for (KernelDependency dependency : plugin.getDependencies()) {
                if (dependency.getType() == KernelDependency.DependencyType.PLUGIN
                    || dependency.getType() == KernelDependency.DependencyType.MODULE) {
                    String depId = dependency.getDependencyId();
                    if (!depId.equals(pluginId)) {
                        dependencies.get(pluginId).add(depId);
                    }
                }
            }
        }

        for (String pluginId : dependencies.keySet()) {
            if (!visited.get(pluginId).contains(pluginId)) {
                if (hasCycle(pluginId, dependencies, visiting, visited, new ArrayList<>())) {
                    throw new PluginDependencyException("Circular dependency detected involving plugin: " + pluginId);
                }
            }
        }
    }

    private boolean hasCycle(
            String pluginId,
            Map<String, Set<String>> dependencies,
            Map<String, Set<String>> visiting,
            Map<String, Set<String>> visited,
            List<String> path) {
        visited.get(pluginId).add(pluginId);
        visiting.get(pluginId).add(pluginId);
        path.add(pluginId);

        for (String depId : dependencies.get(pluginId)) {
            if (!visited.get(depId).contains(depId)) {
                if (hasCycle(depId, dependencies, visiting, visited, path)) {
                    return true;
                }
            } else if (visiting.get(depId).contains(depId)) {
                return true;
            }
        }

        visiting.get(pluginId).remove(pluginId);
        path.remove(path.size() - 1);
        return false;
    }

    /**
     * Resolves plugins into dependency-safe startup order.
     *
     * @param plugins plugins to order
     * @return topologically sorted plugin list
     * @throws IllegalStateException when circular dependency is detected
     */
    public List<KernelPlugin> resolveStartupOrder(Collection<KernelPlugin> plugins) {
        Map<String, KernelPlugin> pluginById = new HashMap<>();
        Map<String, Set<String>> dependencies = new HashMap<>();
        Map<String, Set<String>> dependents = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();

        for (KernelPlugin plugin : plugins) {
            String pluginId = plugin.getModuleId();
            pluginById.put(pluginId, plugin);
            dependencies.put(pluginId, new HashSet<>());
            dependents.put(pluginId, new HashSet<>());
            inDegree.put(pluginId, 0);
        }

        for (KernelPlugin plugin : plugins) {
            String pluginId = plugin.getModuleId();
            for (KernelDependency dependency : plugin.getDependencies()) {
                if (dependency.getType() == KernelDependency.DependencyType.PLUGIN
                    || dependency.getType() == KernelDependency.DependencyType.MODULE) {
                    String depId = dependency.getDependencyId();
                    if (pluginById.containsKey(depId) && !depId.equals(pluginId)) {
                        if (dependencies.get(pluginId).add(depId)) {
                            dependents.get(depId).add(pluginId);
                            inDegree.put(pluginId, inDegree.get(pluginId) + 1);
                        }
                    }
                }
            }
        }

        ArrayDeque<String> queue = new ArrayDeque<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<KernelPlugin> ordered = new ArrayList<>();
        while (!queue.isEmpty()) {
            String pluginId = queue.removeFirst();
            ordered.add(pluginById.get(pluginId));

            for (String dependentId : dependents.get(pluginId)) {
                int nextDegree = inDegree.get(dependentId) - 1;
                inDegree.put(dependentId, nextDegree);
                if (nextDegree == 0) {
                    queue.add(dependentId);
                }
            }
        }

        if (ordered.size() != plugins.size()) {
            throw new IllegalStateException("Circular plugin dependency detected");
        }

        return ordered;
    }
}
