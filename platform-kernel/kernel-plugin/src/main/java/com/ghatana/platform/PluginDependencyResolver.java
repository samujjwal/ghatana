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
}
