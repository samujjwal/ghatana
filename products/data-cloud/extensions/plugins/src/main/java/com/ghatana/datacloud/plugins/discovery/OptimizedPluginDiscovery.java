package com.ghatana.datacloud.plugins.discovery;

import com.ghatana.datacloud.plugins.cache.PluginCacheManager;
import com.ghatana.platform.plugin.PluginMetadata;
import com.ghatana.platform.plugin.PluginProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * Optimized Plugin Discovery Service - Provides fast, cached plugin discovery.
 *
 * <p>This service optimizes plugin discovery by:
 * <ul>
 *   <li>Caching discovered plugins to avoid repeated ServiceLoader calls</li>
 *   <li>Supporting lazy loading of plugin instances</li>
 *   <li>Enabling parallel discovery for faster startup</li>
 *   <li>Providing plugin filtering by type and capability</li>
 *   <li>Integrating with PluginCacheManager for metadata caching</li>
 * </ul>
 *
 * <p><b>Discovery Strategy</b><br>
 * <ol>
 *   <li>Check cache for already discovered plugins</li>
 *   <li>If not cached, use ServiceLoader to discover providers</li>
 *   <li>Cache provider instances and metadata</li>
 *   <li>Support lazy instantiation of actual plugin instances</li>
 * </ol>
 *
 * <p><b>Thread Safety</b><br>
 * All operations are thread-safe using ConcurrentHashMap and synchronized blocks.
 *
 * @doc.type class
 * @doc.purpose Optimized plugin discovery with caching
 * @doc.layer plugin
 * @doc.pattern Service Locator, Cache Aside
 */
public class OptimizedPluginDiscovery {

    private static final Logger log = LoggerFactory.getLogger(OptimizedPluginDiscovery.class);

    private static final AtomicReference<OptimizedPluginDiscovery> INSTANCE = new AtomicReference<>();

    // Cache of discovered providers: provider class -> provider instance
    private final Map<Class<? extends PluginProvider>, PluginProvider> providerCache = new ConcurrentHashMap<>();

    // Cache of plugin metadata: provider class -> metadata
    private final Map<Class<? extends PluginProvider>, PluginMetadata> metadataCache = new ConcurrentHashMap<>();

    // Set of provider classes already discovered
    private final Set<Class<? extends PluginProvider>> discoveredProviders = new ConcurrentSkipListSet<>(
        Comparator.comparing(Class::getName));

    // Integration with PluginCacheManager
    private final PluginCacheManager cacheManager;

    private boolean discoveryPerformed = false;

    private OptimizedPluginDiscovery(PluginCacheManager cacheManager) {
        this.cacheManager = cacheManager != null ? cacheManager : PluginCacheManager.getInstance();
        log.info("OptimizedPluginDiscovery initialized");
    }

    /**
     * Gets the singleton instance.
     */
    public static OptimizedPluginDiscovery getInstance() {
        OptimizedPluginDiscovery instance = INSTANCE.get();
        if (instance == null) {
            instance = new OptimizedPluginDiscovery(PluginCacheManager.getInstance());
            INSTANCE.compareAndSet(null, instance);
        }
        return instance;
    }

    /**
     * Gets the singleton instance, creating it with a custom cache manager if not initialized yet.
     */
    public static OptimizedPluginDiscovery getInstanceWithCacheManager(PluginCacheManager cacheManager) {
        OptimizedPluginDiscovery instance = INSTANCE.get();
        if (instance == null) {
            instance = new OptimizedPluginDiscovery(cacheManager);
            INSTANCE.compareAndSet(null, instance);
        }
        return instance;
    }

    // ========================================================================
    // Discovery Operations
    // ========================================================================

    /**
     * Discovers all available plugin providers.
     *
     * <p>This method uses ServiceLoader to discover all PluginProvider implementations
     * on the classpath. Results are cached for subsequent calls.
     *
     * @return stream of discovered plugin providers
     */
    public synchronized Stream<PluginProvider> discoverProviders() {
        if (discoveryPerformed && !providerCache.isEmpty()) {
            log.debug("Returning cached providers: {} providers", providerCache.size());
            return providerCache.values().stream();
        }

        log.info("Performing plugin discovery via ServiceLoader");
        long startTime = System.currentTimeMillis();

        ServiceLoader<PluginProvider> serviceLoader = ServiceLoader.load(PluginProvider.class);
        
        for (PluginProvider provider : serviceLoader) {
            Class<? extends PluginProvider> providerClass = provider.getClass();
            
            if (!discoveredProviders.contains(providerClass)) {
                providerCache.put(providerClass, provider);
                discoveredProviders.add(providerClass);
                
                // Cache metadata
                try {
                    PluginMetadata metadata = provider.getMetadata();
                    metadataCache.put(providerClass, metadata);
                    cacheManager.cacheMetadata(metadata.id(), metadata);
                    log.debug("Discovered and cached provider: {} with plugin: {}", 
                        providerClass.getSimpleName(), metadata.id());
                } catch (Exception e) {
                    log.warn("Failed to get metadata for provider: {}", providerClass.getSimpleName(), e);
                }
            }
        }

        discoveryPerformed = true;
        long duration = System.currentTimeMillis() - startTime;
        log.info("Plugin discovery completed in {}ms, found {} providers", duration, providerCache.size());

        return providerCache.values().stream();
    }

    /**
     * Discovers all available plugin providers in parallel.
     *
     * <p>This method uses parallel streams for faster discovery when many plugins are present.
     *
     * @return stream of discovered plugin providers
     */
    public synchronized Stream<PluginProvider> discoverProvidersParallel() {
        if (discoveryPerformed && !providerCache.isEmpty()) {
            log.debug("Returning cached providers (parallel mode): {} providers", providerCache.size());
            return providerCache.values().stream().parallel();
        }

        log.info("Performing parallel plugin discovery via ServiceLoader");
        long startTime = System.currentTimeMillis();

        ServiceLoader<PluginProvider> serviceLoader = ServiceLoader.load(PluginProvider.class);
        List<PluginProvider> providers = new ArrayList<>();
        
        // Collect all providers first
        for (PluginProvider provider : serviceLoader) {
            providers.add(provider);
        }
        
        // Process in parallel
        providers.parallelStream().forEach(provider -> {
            Class<? extends PluginProvider> providerClass = provider.getClass();
            
            if (!discoveredProviders.contains(providerClass)) {
                providerCache.put(providerClass, provider);
                discoveredProviders.add(providerClass);
                
                // Cache metadata
                try {
                    PluginMetadata metadata = provider.getMetadata();
                    metadataCache.put(providerClass, metadata);
                    cacheManager.cacheMetadata(metadata.id(), metadata);
                    log.debug("Discovered and cached provider (parallel): {} with plugin: {}", 
                        providerClass.getSimpleName(), metadata.id());
                } catch (Exception e) {
                    log.warn("Failed to get metadata for provider: {}", providerClass.getSimpleName(), e);
                }
            }
        });

        discoveryPerformed = true;
        long duration = System.currentTimeMillis() - startTime;
        log.info("Parallel plugin discovery completed in {}ms, found {} providers", duration, providerCache.size());

        return providerCache.values().stream().parallel();
    }

    /**
     * Gets a specific plugin provider by class.
     *
     * @param providerClass provider class
     * @return provider instance or null if not found
     */
    public PluginProvider getProvider(Class<? extends PluginProvider> providerClass) {
        // Ensure discovery has been performed
        if (!discoveryPerformed) {
            discoverProviders();
        }
        
        return providerCache.get(providerClass);
    }

    /**
     * Gets a plugin provider by plugin ID.
     *
     * @param pluginId plugin identifier
     * @return provider instance or null if not found
     */
    public PluginProvider getProviderByPluginId(String pluginId) {
        // Ensure discovery has been performed
        if (!discoveryPerformed) {
            discoverProviders();
        }
        
        return providerCache.values().stream()
            .filter(provider -> {
                try {
                    return pluginId.equals(provider.getMetadata().id());
                } catch (Exception e) {
                    return false;
                }
            })
            .findFirst()
            .orElse(null);
    }

    /**
     * Filters providers by plugin type.
     *
     * @param pluginType plugin type to filter by
     * @return stream of matching providers
     */
    public Stream<PluginProvider> filterByType(String pluginType) {
        // Ensure discovery has been performed
        if (!discoveryPerformed) {
            discoverProviders();
        }
        
        return providerCache.values().stream()
            .filter(provider -> {
                try {
                    return pluginType.equals(provider.getMetadata().type().name());
                } catch (Exception e) {
                    return false;
                }
            });
    }

    /**
     * Filters providers by capability.
     *
     * @param capability capability to filter by
     * @return stream of matching providers
     */
    public Stream<PluginProvider> filterByCapability(String capability) {
        // Ensure discovery has been performed
        if (!discoveryPerformed) {
            discoverProviders();
        }
        
        return providerCache.values().stream()
            .filter(provider -> {
                try {
                    return provider.getMetadata().capabilities().contains(capability);
                } catch (Exception e) {
                    return false;
                }
            });
    }

    /**
     * Gets all cached plugin metadata.
     *
     * @return collection of plugin metadata
     */
    public Collection<PluginMetadata> getAllMetadata() {
        // Ensure discovery has been performed
        if (!discoveryPerformed) {
            discoverProviders();
        }
        
        return new ArrayList<>(metadataCache.values());
    }

    // ========================================================================
    // Cache Management
    // ========================================================================

    /**
     * Clears the discovery cache.
     *
     * <p>This forces re-discovery on the next call to discoverProviders().
     */
    public synchronized void clearCache() {
        providerCache.clear();
        metadataCache.clear();
        discoveredProviders.clear();
        discoveryPerformed = false;
        log.info("Cleared plugin discovery cache");
    }

    /**
     * Gets discovery statistics.
     *
     * @return discovery statistics
     */
    public DiscoveryStatistics getStatistics() {
        return new DiscoveryStatistics(
            providerCache.size(),
            metadataCache.size(),
            discoveredProviders.size(),
            discoveryPerformed
        );
    }

    /**
     * Checks if discovery has been performed.
     *
     * @return true if discovery has been performed
     */
    public boolean isDiscoveryPerformed() {
        return discoveryPerformed;
    }

    // ========================================================================
    // Inner Classes
    // ========================================================================

    /**
     * Discovery statistics.
     */
    public record DiscoveryStatistics(
        int providerCount,
        int metadataCount,
        int discoveredCount,
        boolean discoveryPerformed
    ) {}
}
