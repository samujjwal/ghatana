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
