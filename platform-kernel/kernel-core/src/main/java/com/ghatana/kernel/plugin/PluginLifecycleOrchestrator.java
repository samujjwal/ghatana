package com.ghatana.kernel.plugin;

import io.activej.promise.Promise;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Orchestrates plugin lifecycle operations in dependency-safe order.
 *
 * @doc.type class
 * @doc.purpose Dependency-aware plugin install/start/stop/uninstall orchestration
 * @doc.layer core
 * @doc.pattern Orchestrator
 */
public final class PluginLifecycleOrchestrator {

    private final PluginDependencyResolver dependencyResolver;

    public PluginLifecycleOrchestrator() {
        this(new PluginDependencyResolver());
    }

    public PluginLifecycleOrchestrator(PluginDependencyResolver dependencyResolver) {
        this.dependencyResolver = Objects.requireNonNull(dependencyResolver, "dependencyResolver cannot be null");
    }

    /**
     * Installs and starts plugins in dependency order.
     */
    public Promise<Void> installAndStartAll(Collection<KernelPlugin> plugins) {
        List<KernelPlugin> ordered = dependencyResolver.resolveStartupOrder(plugins);
        Promise<Void> chain = Promise.complete();

        for (KernelPlugin plugin : ordered) {
            chain = chain.then($ -> plugin.install())
                .then($ -> plugin.start());
        }

        return chain;
    }

    /**
     * Stops and uninstalls plugins in reverse dependency order.
     */
    public Promise<Void> stopAndUninstallAll(Collection<KernelPlugin> plugins) {
        List<KernelPlugin> ordered = new ArrayList<>(dependencyResolver.resolveStartupOrder(plugins));
        Collections.reverse(ordered);

        Promise<Void> chain = Promise.complete();
        for (KernelPlugin plugin : ordered) {
            chain = chain.then($ -> plugin.stop())
                .then($ -> plugin.uninstall());
        }

        return chain;
    }
}
