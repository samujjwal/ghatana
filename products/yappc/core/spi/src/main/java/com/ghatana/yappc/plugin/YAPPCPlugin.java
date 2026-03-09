package com.ghatana.yappc.plugin;

import io.activej.promise.Promise;

/**
 * Unified Plugin SPI for all YAPPC extensions.
 * 
 * <p>This is the base interface for all YAPPC plugins. All plugin types
 * (validators, generators, agents, etc.) extend this interface.
 * 
 * <p>Plugin Lifecycle:
 * <ol>
 *   <li>Plugin is discovered via ServiceLoader</li>
 *   <li>{@link #initialize(PluginContext)} is called</li>
 *   <li>{@link #start()} is called to activate the plugin</li>
 *   <li>Plugin performs its work</li>
 *   <li>{@link #stop()} is called to deactivate</li>
 *   <li>{@link #shutdown()} is called to release resources</li>
 * </ol>
 * 
 * <p>Example implementation:
 * <pre>{@code
 * public class MyValidatorPlugin implements ValidatorPlugin {
 *     private PluginContext context;
 *     private boolean running;
 *     
 *     @Override
 *     public Promise<Void> initialize(PluginContext context) {
 *         this.context = context;
 *         return Promise.complete();
 *     }
 *     
 *     @Override
 *     public Promise<Void> start() {
 *         this.running = true;
 *         return Promise.complete();
 *     }
 *     
 *     @Override
 *     public Promise<Void> stop() {
 *         this.running = false;
 *         return Promise.complete();
 *     }
 *     
 *     @Override
 *     public Promise<Void> shutdown() {
 *         return Promise.complete();
 *     }
 *     
 *     @Override
 *     public PluginMetadata getMetadata() {
 *         return PluginMetadata.builder()
 *             .id("my-validator")
 *             .name("My Validator")
 *             .version("1.0.0")
 *             .build();
 *     }
 * }
 * }</pre>
 * 
 * @author YAPPC Team
 * @version 1.0.0
 * @since 1.0.0
 
 * @doc.type interface
 * @doc.purpose Defines the contract for yappc plugin
 * @doc.layer core
 * @doc.pattern Plugin
*/
public interface YAPPCPlugin {
    
    /**
     * Initializes the plugin with the given context.
     * 
     * <p>This method is called once when the plugin is first loaded.
     * Plugins should perform one-time initialization here.
     * 
     * @param context the plugin context providing access to YAPPC services
     * @return a Promise that completes when initialization is done
     */
    Promise<Void> initialize(PluginContext context);
    
    /**
     * Starts the plugin, making it active and ready to process requests.
     * 
     * <p>This method is called after initialization and may be called
     * multiple times if the plugin is stopped and restarted.
     * 
     * @return a Promise that completes when the plugin has started
     */
    Promise<Void> start();
    
    /**
     * Stops the plugin, making it inactive.
     * 
     * <p>The plugin should stop processing requests but maintain its
     * state so it can be restarted if needed.
     * 
     * @return a Promise that completes when the plugin has stopped
     */
    Promise<Void> stop();
    
    /**
     * Shuts down the plugin and releases all resources.
     * 
     * <p>This method is called when the plugin is being unloaded.
     * After this method returns, the plugin will not be used again.
     * 
     * @return a Promise that completes when shutdown is done
     */
    Promise<Void> shutdown();
    
    /**
     * Returns the plugin metadata.
     * 
     * @return the plugin metadata
     */
    PluginMetadata getMetadata();
    
    /**
     * Returns the plugin capabilities.
     * 
     * @return the plugin capabilities
     */
    PluginCapabilities getCapabilities();
    
    /**
     * Performs a health check on the plugin.
     * 
     * @return a Promise containing the health status
     */
    Promise<HealthStatus> checkHealth();
}
