package com.ghatana.yappc.plugin;

import io.activej.promise.Promise;

/**
 * Unified Plugin SPI for all YAPPC extensions.
 * 
 * <p>This is the base interface for all YAPPC plugins. All plugin types
 * (validators, generators, agents, etc.) extend this interface.
 * 
 * <p>To bridge with the platform {@link com.ghatana.platform.plugin.Plugin} interface,
 * use {@link YappcPluginToPlatformAdapter} which wraps any YAPPCPlugin as a platform Plugin.
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
 * @version 2.0.0
 * @since 1.0.0
 
 * @doc.type interface
 * @doc.purpose YAPPC plugin SPI with platform Plugin bridge via adapter
 * @doc.layer core
 * @doc.pattern Plugin
*/
public interface YAPPCPlugin {
    
    /**
     * Initializes the plugin with the YAPPC-specific context.
     * 
     * <p>This method is called once when the plugin is first loaded.
     * Plugins should perform one-time initialization here.
     * 
     * @param context the YAPPC plugin context providing access to YAPPC services
     * @return a Promise that completes when initialization is done
     */
    Promise<Void> initialize(PluginContext context);

    /**
     * Starts the plugin after initialization.
     *
     * @return a Promise that completes when the plugin is started
     */
    Promise<Void> start();

    /**
     * Stops the plugin (may be restarted later).
     *
     * @return a Promise that completes when the plugin is stopped
     */
    Promise<Void> stop();

    /**
     * Shuts down the plugin and releases all resources.
     *
     * @return a Promise that completes when shutdown is finished
     */
    Promise<Void> shutdown();

    /**
     * Returns the YAPPC-specific plugin metadata.
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
