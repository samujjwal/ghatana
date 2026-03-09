package com.ghatana.virtualorg.framework.spi;

import com.ghatana.virtualorg.framework.VirtualOrgContext;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages loading and lifecycle of VirtualOrgExtensions.
 *
 * <p><b>Purpose</b><br>
 * The ExtensionLoader discovers, validates, and initializes extensions
 * in the correct order based on dependencies and priorities.
 *
 * <p><b>Key Features</b><br>
 * - SPI-based discovery via ServiceLoader
 * - Dependency resolution and ordering
 * - Lifecycle management (init, shutdown)
 * - Health monitoring
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * ExtensionLoader loader = new ExtensionLoader();
 * int count = loader.discover();
 * loader.initializeAll(context).getResult();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Extension discovery and lifecycle management
 * @doc.layer platform
 * @doc.pattern Facade
 */
public class ExtensionLoader {

    private static final Logger LOG = LoggerFactory.getLogger(ExtensionLoader.class);

    private final Map<String, VirtualOrgExtension> extensions = new ConcurrentHashMap<>();
    private final List<VirtualOrgExtension> initializationOrder = new ArrayList<>();
    private boolean initialized = false;

    /**
     * Discovers extensions via ServiceLoader.
     *
     * @return number of extensions discovered
     */
    public int discover() {
        ServiceLoader<VirtualOrgExtension> loader = ServiceLoader.load(VirtualOrgExtension.class);
        int count = 0;

        for (VirtualOrgExtension extension : loader) {
            register(extension);
            count++;
        }

        if (count > 0) {
            resolveOrder();
            LOG.info("Discovered {} extensions: {}", count, extensions.keySet());
        }

        return count;
    }

    /**
     * Registers an extension manually.
     *
     * @param extension the extension to register
     */
    public void register(VirtualOrgExtension extension) {
        extensions.put(extension.getName(), extension);
        LOG.debug("Registered extension: {} v{} (priority: {})",
                extension.getName(), extension.getVersion(), extension.getPriority());
    }

    /**
     * Gets an extension by name.
     *
     * @param name the extension name
     * @return optional extension
     */
    public Optional<VirtualOrgExtension> get(String name) {
        return Optional.ofNullable(extensions.get(name));
    }

    /**
     * Initializes all extensions in dependency order.
     *
     * @param context the framework context
     * @return promise completing when all extensions are initialized
     */
    public Promise<Void> initializeAll(VirtualOrgContext context) {
        if (initialized) {
            LOG.warn("Extensions already initialized");
            return Promise.complete();
        }

        if (initializationOrder.isEmpty()) {
            resolveOrder();
        }

        return initializeSequentially(context, 0);
    }

    private Promise<Void> initializeSequentially(VirtualOrgContext context, int index) {
        if (index >= initializationOrder.size()) {
            initialized = true;
            LOG.info("All {} extensions initialized", initializationOrder.size());
            return Promise.complete();
        }

        VirtualOrgExtension extension = initializationOrder.get(index);
        LOG.info("Initializing extension: {} v{}", extension.getName(), extension.getVersion());

        return extension.initialize(context)
                .then(() -> initializeSequentially(context, index + 1))
                .whenException(e -> LOG.error("Failed to initialize extension: {}",
                        extension.getName(), e));
    }

    /**
     * Shuts down all extensions in reverse order.
     *
     * @return promise completing when all extensions are shut down
     */
    public Promise<Void> shutdownAll() {
        if (!initialized) {
            return Promise.complete();
        }

        // Shutdown in reverse order
        List<VirtualOrgExtension> reversed = new ArrayList<>(initializationOrder);
        Collections.reverse(reversed);

        return shutdownSequentially(reversed, 0);
    }

    private Promise<Void> shutdownSequentially(List<VirtualOrgExtension> list, int index) {
        if (index >= list.size()) {
            initialized = false;
            LOG.info("All extensions shut down");
            return Promise.complete();
        }

        VirtualOrgExtension extension = list.get(index);
        LOG.info("Shutting down extension: {}", extension.getName());

        return extension.shutdown()
                .then(() -> shutdownSequentially(list, index + 1))
                .whenException(e -> LOG.error("Error shutting down extension: {}",
                        extension.getName(), e));
    }

    /**
     * Checks health of all extensions.
     *
     * @return promise with map of extension name to health status
     */
    public Promise<Map<String, Boolean>> healthCheck() {
        Map<String, Boolean> results = new HashMap<>();

        for (VirtualOrgExtension extension : extensions.values()) {
            try {
                Boolean healthy = extension.healthCheck().getResult();
                results.put(extension.getName(), healthy);
            } catch (Exception e) {
                results.put(extension.getName(), false);
            }
        }

        return Promise.of(results);
    }

    /**
     * Gets all registered extensions.
     */
    public List<VirtualOrgExtension> getAll() {
        return new ArrayList<>(extensions.values());
    }

    /**
     * Resolves initialization order based on dependencies and priorities.
     */
    private void resolveOrder() {
        initializationOrder.clear();

        // Simple topological sort with priority consideration
        Set<String> resolved = new HashSet<>();
        Set<String> processing = new HashSet<>();

        List<VirtualOrgExtension> sorted = new ArrayList<>(extensions.values());
        sorted.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));

        for (VirtualOrgExtension ext : sorted) {
            resolve(ext, resolved, processing);
        }
    }

    private void resolve(VirtualOrgExtension extension, Set<String> resolved, Set<String> processing) {
        if (resolved.contains(extension.getName())) {
            return;
        }

        if (processing.contains(extension.getName())) {
            throw new IllegalStateException("Circular dependency detected for: " + extension.getName());
        }

        processing.add(extension.getName());

        for (String dep : extension.getDependencies()) {
            VirtualOrgExtension depExt = extensions.get(dep);
            if (depExt == null) {
                throw new IllegalStateException("Missing dependency: " + dep + " for extension: " + extension.getName());
            }
            resolve(depExt, resolved, processing);
        }

        processing.remove(extension.getName());
        resolved.add(extension.getName());
        initializationOrder.add(extension);
    }
}
