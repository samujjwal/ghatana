package com.ghatana.kernel.registry;

import com.ghatana.kernel.capability.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.kernel.plugin.ProductPlugin;
import com.ghatana.kernel.plugin.PluginContext;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Plugin registry for dynamic plugin management.
 * 
 * This registry manages product plugins without creating coupling
 * between the kernel and specific products.
 */
public class PluginRegistry {
    private final Map<String, ProductPlugin> plugins = new ConcurrentHashMap<>();
    private final Map<String, Set<KernelCapability>> capabilitiesByPlugin = new ConcurrentHashMap<>();
    private final CapabilityRegistry capabilityRegistry;
    private final ServiceRegistry serviceRegistry;

    public PluginRegistry(CapabilityRegistry capabilityRegistry, ServiceRegistry serviceRegistry) {
        this.capabilityRegistry = Objects.requireNonNull(capabilityRegistry);
        this.serviceRegistry = Objects.requireNonNull(serviceRegistry);
    }

    /**
     * Register a product plugin dynamically.
     * 
     * @param plugin the plugin to register
     * @throws IllegalStateException if plugin is already registered
     */
    public void registerPlugin(ProductPlugin plugin) {
        String productId = plugin.getProductId();
        
        // Check for conflicts
        if (plugins.containsKey(productId)) {
            throw new IllegalStateException("Plugin already registered: " + productId);
        }

        // Validate plugin dependencies
        validatePluginDependencies(plugin);

        // Register plugin
        plugins.put(productId, plugin);
        
        // Register plugin capabilities
        Set<KernelCapability> capabilities = plugin.getDeclaredCapabilities();
        capabilitiesByPlugin.put(productId, capabilities);
        
        // Register capabilities with kernel registry
        for (KernelCapability capability : capabilities) {
            capabilityRegistry.registerCapability(capability);
        }

        // Initialize plugin
        PluginContext context = new PluginContextImpl(capabilityRegistry, serviceRegistry);
        plugin.initialize(context);
    }

    /**
     * Get plugin by ID.
     * 
     * @param productId the product identifier
     * @return optional plugin
     */
    public Optional<ProductPlugin> getPlugin(String productId) {
        return Optional.ofNullable(plugins.get(productId));
    }

    /**
     * Get all registered plugins.
     * 
     * @return set of all plugins
     */
    public Set<ProductPlugin> getAllPlugins() {
        return new HashSet<>(plugins.values());
    }

    /**
     * Get capabilities for a plugin.
     * 
     * @param productId the product identifier
     * @return set of capabilities
     */
    public Set<KernelCapability> getPluginCapabilities(String productId) {
        return capabilitiesByPlugin.getOrDefault(productId, Set.of());
    }

    /**
     * Find plugins by capability.
     * 
     * @param capability the capability to search for
     * @return set of plugins that provide the capability
     */
    public Set<ProductPlugin> getPluginsByCapability(KernelCapability capability) {
        return plugins.values().stream()
            .filter(plugin -> plugin.getDeclaredCapabilities().contains(capability))
            .collect(Collectors.toSet());
    }

    /**
     * Start all plugins.
     */
    public void startAllPlugins() {
        plugins.values().forEach(plugin -> {
            try {
                plugin.start();
            } catch (Exception e) {
                throw new RuntimeException("Failed to start plugin: " + plugin.getProductId(), e);
            }
        });
    }

    /**
     * Stop all plugins.
     */
    public void stopAllPlugins() {
        plugins.values().forEach(plugin -> {
            try {
                plugin.stop();
            } catch (Exception e) {
                // Log error but continue stopping other plugins
                System.err.println("Error stopping plugin " + plugin.getProductId() + ": " + e.getMessage());
            }
        });
    }

    /**
     * Shutdown all plugins.
     */
    public void shutdownAllPlugins() {
        plugins.values().forEach(plugin -> {
            try {
                plugin.shutdown();
            } catch (Exception e) {
                // Log error but continue shutting down other plugins
                System.err.println("Error shutting down plugin " + plugin.getProductId() + ": " + e.getMessage());
            }
        });
    }

    /**
     * Unregister a plugin.
     * 
     * @param productId the product identifier
     */
    public void unregisterPlugin(String productId) {
        ProductPlugin plugin = plugins.get(productId);
        if (plugin != null) {
            // Stop and shutdown plugin
            plugin.stop();
            plugin.shutdown();
            
            // Remove from registry
            plugins.remove(productId);
            capabilitiesByPlugin.remove(productId);
            
            // Note: We don't remove capabilities from the capability registry
            // as other plugins might be using them
        }
    }

    /**
     * Validate plugin dependencies.
     * 
     * @param plugin the plugin to validate
     * @throws IllegalStateException if dependencies are not satisfied
     */
    private void validatePluginDependencies(ProductPlugin plugin) {
        Set<KernelDependency> dependencies = plugin.getRequiredDependencies();
        
        for (KernelDependency dependency : dependencies) {
            if (dependency.getType() == KernelDependency.DependencyType.CAPABILITY) {
                if (!capabilityRegistry.getCapability(dependency.getDependencyId()).isPresent()) {
                    throw new IllegalStateException(
                        "Plugin " + plugin.getProductId() + " requires capability: " + dependency.getDependencyId());
                }
            }
        }
    }

    /**
     * Get plugin registry statistics.
     * 
     * @return statistics map
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total_plugins", plugins.size());
        stats.put("total_capabilities", capabilitiesByPlugin.values().stream()
            .mapToInt(Set::size).sum());
        stats.put("plugins_by_id", new HashMap<>(plugins.keySet().stream()
            .collect(Collectors.toMap(id -> id, id -> plugins.get(id).getProductVersion()))));
        return stats;
    }

    /**
     * Plugin context implementation.
     */
    private static class PluginContextImpl implements PluginContext {
        private final CapabilityRegistry capabilityRegistry;
        private final ServiceRegistry serviceRegistry;

        public PluginContextImpl(CapabilityRegistry capabilityRegistry, ServiceRegistry serviceRegistry) {
            this.capabilityRegistry = capabilityRegistry;
            this.serviceRegistry = serviceRegistry;
        }

        @Override
        public com.ghatana.kernel.context.KernelContext getKernelContext() {
            // Return a wrapper around the kernel context
            return serviceRegistry.getKernelContext();
        }

        @Override
        public CapabilityRegistry getCapabilityRegistry() {
            return capabilityRegistry;
        }

        @Override
        public ServiceRegistry getServiceRegistry() {
            return serviceRegistry;
        }

        @Override
        public <T> T getCapability(KernelCapability capability, Class<T> type) {
            return capabilityRegistry.getCapabilityInstance(capability, type);
        }

        @Override
        public <T> Optional<T> getOptionalCapability(KernelCapability capability, Class<T> type) {
            try {
                return Optional.ofNullable(getCapability(capability, type));
            } catch (Exception e) {
                return Optional.empty();
            }
        }

        @Override
        public void registerService(String serviceId, Object service) {
            serviceRegistry.registerService(serviceId, service);
        }

        @Override
        public void registerExtension(KernelExtension extension) {
            serviceRegistry.registerExtension(extension);
        }

        @Override
        public void registerOperator(KernelOperator operator) {
            serviceRegistry.registerOperator(operator);
        }
    }
}
