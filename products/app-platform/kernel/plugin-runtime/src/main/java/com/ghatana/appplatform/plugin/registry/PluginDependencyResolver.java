/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.plugin.registry;

import com.ghatana.appplatform.plugin.domain.PluginManifest;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Resolves and validates plugin dependency graphs (STORY-K04-010).
 *
 * <p>Each plugin may declare a list of other plugins it depends on via
 * {@link PluginManifest#dependsOn()}. This resolver:
 * <ol>
 *   <li>Detects circular dependencies (throws {@link CircularDependencyException})</li>
 *   <li>Returns a topologically ordered load sequence (dependencies first)</li>
 *   <li>Reports any missing dependency that has not been registered</li>
 * </ol>
 *
 * @doc.type  class
 * @doc.purpose Topological dependency resolution and cycle detection for plugins (K04-010)
 * @doc.layer kernel
 * @doc.pattern Service
 */
public final class PluginDependencyResolver {

    private static final Logger log = LoggerFactory.getLogger(PluginDependencyResolver.class);

    private final Executor executor;

    public PluginDependencyResolver(Executor executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    /**
     * Resolves the load order for a set of plugins using Kahn's topological sort.
     *
     * @param manifests a collection of plugin manifests — must include all transitively
     *                  required plugins
     * @return promise resolving to the ordered list (dependencies before dependants)
     * @throws CircularDependencyException if a cycle is detected
     * @throws MissingDependencyException  if a declared dependency is absent from {@code manifests}
     */
    public Promise<List<PluginManifest>> resolve(List<PluginManifest> manifests) {
        Objects.requireNonNull(manifests, "manifests");

        return Promise.ofBlocking(executor, () -> {
            Map<String, PluginManifest> byName = new HashMap<>();
            for (PluginManifest m : manifests) {
                byName.put(m.name(), m);
            }

            // Validate all declared dependencies are present
            for (PluginManifest m : manifests) {
                for (String dep : m.dependsOn()) {
                    if (!byName.containsKey(dep)) {
                        throw new MissingDependencyException(
                                "Plugin '" + m.name() + "' depends on '" + dep
                                        + "' which is not in the registered set.");
                    }
                }
            }

            // Kahn's algorithm: compute in-degree for each plugin
            Map<String, Integer> inDegree = new HashMap<>();
            Map<String, List<String>> dependants = new HashMap<>(); // dep → list of plugins that need dep

            for (PluginManifest m : manifests) {
                inDegree.putIfAbsent(m.name(), 0);
                dependants.putIfAbsent(m.name(), new ArrayList<>());
            }
            for (PluginManifest m : manifests) {
                for (String dep : m.dependsOn()) {
                    dependants.get(dep).add(m.name());
                    inDegree.merge(m.name(), 1, Integer::sum);
                }
            }

            Queue<String> queue = new LinkedList<>();
            for (Map.Entry<String, Integer> e : inDegree.entrySet()) {
                if (e.getValue() == 0) queue.add(e.getKey());
            }

            List<PluginManifest> sorted = new ArrayList<>();
            while (!queue.isEmpty()) {
                String name = queue.poll();
                sorted.add(byName.get(name));
                for (String dependant : dependants.getOrDefault(name, List.of())) {
                    int newDegree = inDegree.merge(dependant, -1, Integer::sum);
                    if (newDegree == 0) queue.add(dependant);
                }
            }

            if (sorted.size() != manifests.size()) {
                // Find the cycle participants for a useful error message
                Set<String> inCycle = new HashSet<>(byName.keySet());
                sorted.forEach(m -> inCycle.remove(m.name()));
                throw new CircularDependencyException(
                        "Circular dependency detected among plugins: " + inCycle);
            }

            log.info("Resolved {} plugin(s) in dependency order", sorted.size());
            return sorted;
        });
    }

    // ── Exceptions ────────────────────────────────────────────────────────────

    public static final class CircularDependencyException extends RuntimeException {
        public CircularDependencyException(String message) { super(message); }
    }

    public static final class MissingDependencyException extends RuntimeException {
        public MissingDependencyException(String message) { super(message); }
    }
}
