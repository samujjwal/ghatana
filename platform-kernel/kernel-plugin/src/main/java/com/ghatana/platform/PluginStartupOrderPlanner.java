package com.ghatana.platform.plugin;

import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.kernel.plugin.PluginManifest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Planner for determining plugin startup order based on dependency graph.
 *
 * Uses topological sorting to compute a valid startup sequence that respects
 * plugin dependencies. Throws on circular dependencies to prevent startup deadlocks.
 *
 * @doc.type class
 * @doc.purpose Compute plugin startup order from dependency graph
 * @doc.layer platform
 * @doc.pattern Planner
 */
public class PluginStartupOrderPlanner {

    private static final Logger log = LoggerFactory.getLogger(PluginStartupOrderPlanner.class);
    private final PluginDependencyResolver dependencyResolver;

    public PluginStartupOrderPlanner() {
        this.dependencyResolver = new PluginDependencyResolver();
    }

    public PluginStartupOrderPlanner(PluginDependencyResolver dependencyResolver) {
        this.dependencyResolver = Objects.requireNonNull(dependencyResolver, "dependencyResolver must not be null");
    }

    /**
     * Computes the startup order for plugins based on their dependencies.
     *
     * @param plugins map of plugin ID to plugin manifest
     * @return ordered list of plugin IDs in startup order (dependencies first)
     * @throws PluginDependencyException if circular dependencies are detected
     */
    public List<String> computeStartupOrder(Map<String, PluginManifest> plugins) throws PluginDependencyException {
        log.debug("Computing startup order for {} plugins", plugins.size());

        // First, check for circular dependencies
        dependencyResolver.checkCircularDependencies(plugins);

        // Compute topological order using DFS
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        Deque<String> order = new ArrayDeque<>();

        for (String pluginId : new TreeSet<>(plugins.keySet())) {
            if (!visited.contains(pluginId)) {
                visit(pluginId, plugins, visited, visiting, order);
            }
        }

        // Reverse to get dependencies first
        List<String> startupOrder = new ArrayList<>(order);
        Collections.reverse(startupOrder);

        log.debug("Computed startup order: {}", startupOrder);
        return startupOrder;
    }

    private void visit(
            String pluginId,
            Map<String, PluginManifest> plugins,
            Set<String> visited,
            Set<String> visiting,
            Deque<String> order) throws PluginDependencyException {
        if (visited.contains(pluginId)) {
            return;
        }

        if (visiting.contains(pluginId)) {
            throw new PluginDependencyException("Circular dependency detected during startup order computation: " + pluginId);
        }

        visiting.add(pluginId);

        PluginManifest manifest = plugins.get(pluginId);
        if (manifest != null) {
            for (com.ghatana.kernel.descriptor.KernelDependency dependency : manifest.getDependencies()) {
                if (dependency.getType() != com.ghatana.kernel.descriptor.KernelDependency.DependencyType.PLUGIN) {
                    continue;
                }

                String dependencyId = dependency.getDependencyId();
                PluginManifest dependencyManifest = plugins.get(dependencyId);
                if (dependencyManifest != null) {
                    visit(dependencyId, plugins, visited, visiting, order);
                }
            }
        }

        visiting.remove(pluginId);
        visited.add(pluginId);
        order.addLast(pluginId);
    }

    /**
     * Computes startup order for plugins in a registry.
     *
     * @param registry the plugin registry
     * @return ordered list of plugin IDs in startup order
     * @throws PluginDependencyException if circular dependencies are detected
     */
    public List<String> computeStartupOrder(PluginRegistry registry) throws PluginDependencyException {
        Map<String, PluginManifest> manifests = new HashMap<>();
        for (Plugin plugin : registry.getAllPlugins()) {
            PluginMetadata metadata = plugin.metadata();
            PluginManifest.Builder builder = PluginManifest.builder()
                .pluginId(metadata.id())
                .version(metadata.version())
                .description(metadata.description())
                .author(metadata.author())
                .license(metadata.license());

            for (PluginDependency dependency : metadata.dependencies()) {
                builder.dependency(new KernelDependency(
                    dependency.pluginId(),
                    dependency.versionRange(),
                    KernelDependency.DependencyType.PLUGIN,
                    dependency.optional()
                ));
            }

            manifests.put(metadata.id(), builder.build());
        }
        return computeStartupOrder(manifests);
    }
}
