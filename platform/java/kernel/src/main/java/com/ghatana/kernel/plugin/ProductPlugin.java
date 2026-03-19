package com.ghatana.kernel.plugin;

import com.ghatana.kernel.capability.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;

import java.util.Set;

/**
 * Product plugin interface - decoupled from kernel.
 * 
 * Products implement this interface to register their capabilities
 * dynamically with the kernel without creating tight coupling.
 */
public interface ProductPlugin {
    
    /**
     * Get the unique product identifier.
     */
    String getProductId();

    /**
     * Get the product version.
     */
    String getProductVersion();

    /**
     * Get the product description.
     */
    String getProductDescription();
    
    /**
     * Product declares its capabilities.
     * These capabilities will be registered with the kernel.
     */
    Set<KernelCapability> getDeclaredCapabilities();
    
    /**
     * Product declares its requirements.
     * These dependencies must be satisfied for the plugin to start.
     */
    Set<KernelDependency> getRequiredDependencies();
    
    /**
     * Initialize the plugin with kernel context.
     * Called after plugin registration but before start().
     */
    void initialize(PluginContext context);
    
    /**
     * Start the plugin.
     * Called after all dependencies are satisfied.
     */
    void start();
    
    /**
     * Stop the plugin.
     * Graceful shutdown of plugin services.
     */
    void stop();
    
    /**
     * Shutdown the plugin.
     * Complete cleanup of plugin resources.
     */
    void shutdown();
    
    // Default methods for extensibility
    default Set<KernelExtension> getExtensions() { 
        return Set.of(); 
    }
    
    default Set<KernelOperator> getOperators() { 
        return Set.of(); 
    }
    
    default Set<KernelWorkflow> getWorkflows() { 
        return Set.of(); 
    }
}
