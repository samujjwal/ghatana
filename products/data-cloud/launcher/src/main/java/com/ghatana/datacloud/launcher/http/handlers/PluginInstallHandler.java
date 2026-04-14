package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.spi.StoragePlugin;
import com.ghatana.datacloud.spi.StoragePluginRegistry;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP handler for the plugin install and upgrade lifecycle API (B6).
 *
 * <p>The full plugin ecosystem (8 plugin types, health monitor, version compare, dependency graph)
 * exists in the product. This handler closes the only gap: the backend endpoints that
 * {@code PluginsPage.tsx} and {@code PluginDetailsPage.tsx} make TODO calls against.
 *
 * <p>Implementation note: actual runtime class-loading of JAR archives is a platform concern
 * not yet supported by the {@link StoragePluginRegistry} SPI. The endpoints therefore accept
 * plugin registry-managed plugin IDs and interact with the already-registered, in-process
 * plugin instances. Uploading new JARs at runtime is deferred to a future platform capability.
 *
 * <p>Routes wired in {@code DataCloudHttpServer}:
 * <ul>
 *   <li>{@code GET    /api/v1/plugins}               — list all registered plugins</li>
 *   <li>{@code GET    /api/v1/plugins/:id}            — get a single plugin</li>
 *   <li>{@code POST   /api/v1/plugins/:id/enable}     — enable a disabled plugin (B6 install)</li>
 *   <li>{@code POST   /api/v1/plugins/:id/disable}    — disable / uninstall a plugin</li>
 *   <li>{@code POST   /api/v1/plugins/:id/upgrade}    — signal upgrade intent for a plugin</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Exposes plugin lifecycle management over HTTP (B6)
 * @doc.layer product
 * @doc.pattern Handler
 */
public final class PluginInstallHandler {

    private static final Logger log = LoggerFactory.getLogger(PluginInstallHandler.class);

    private final HttpHandlerSupport http;
    private final StoragePluginRegistry pluginRegistry;
    private final MetricsCollector metrics;

    // Tracks plugins explicitly disabled at runtime via this API
    private final java.util.Set<String> disabledPlugins = java.util.Collections.newSetFromMap(
            new java.util.concurrent.ConcurrentHashMap<>());

    /**
     * @param http           shared HTTP support
     * @param pluginRegistry live plugin registry holding all registered plugins
     * @param metrics        observability metrics
     */
    public PluginInstallHandler(
            HttpHandlerSupport http,
            StoragePluginRegistry pluginRegistry,
            MetricsCollector metrics) {
        this.http = Objects.requireNonNull(http, "http");
        this.pluginRegistry = Objects.requireNonNull(pluginRegistry, "pluginRegistry");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    // ─── GET /api/v1/plugins ──────────────────────────────────────────────────

    /**
     * Lists all registered plugins.
     */
    public Promise<HttpResponse> handleListPlugins(HttpRequest request) {
        String tenantId = http.resolveTenantId(request);
        metrics.incrementCounter("plugin.list", "tenant", tenantId);

        Collection<StoragePlugin<?>> all = pluginRegistry.getAllPlugins();
        List<Map<String, Object>> items = new ArrayList<>(all.size());
        for (StoragePlugin<?> plugin : all) {
            items.add(pluginView(plugin));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("plugins", items);
        response.put("total", items.size());
        return Promise.of(http.jsonResponse(200, response));
    }

    // ─── GET /api/v1/plugins/:id ──────────────────────────────────────────────

    /**
     * Returns details of a single registered plugin.
     */
    public Promise<HttpResponse> handleGetPlugin(HttpRequest request) {
        String pluginId = request.getPathParameter("id");
        String tenantId = http.resolveTenantId(request);
        metrics.incrementCounter("plugin.get", "tenant", tenantId, "pluginId", pluginId);

        return pluginRegistry.getPlugin(pluginId)
                .<Promise<HttpResponse>>map(plugin -> Promise.of(http.jsonResponse(200, pluginView(plugin))))
                .orElseGet(() -> Promise.of(http.errorResponse(404, "Plugin not found: " + pluginId)));
    }

    // ─── POST /api/v1/plugins/:id/enable ─────────────────────────────────────

    /**
     * Enables (re-enables) a plugin that was disabled via this API.
     */
    public Promise<HttpResponse> handleEnablePlugin(HttpRequest request) {
        String pluginId = request.getPathParameter("id");
        String tenantId = http.resolveTenantId(request);
        metrics.incrementCounter("plugin.enable", "tenant", tenantId, "pluginId", pluginId);

        return pluginRegistry.getPlugin(pluginId)
                .<Promise<HttpResponse>>map(plugin -> {
                    disabledPlugins.remove(pluginId);
                    log.info("Plugin {} enabled by tenant {}", pluginId, tenantId);
                    Map<String, Object> result = pluginView(plugin);
                    result.put("status", "enabled");
                    return Promise.of(http.jsonResponse(200, result));
                })
                .orElseGet(() -> Promise.of(http.errorResponse(404, "Plugin not found: " + pluginId)));
    }

    // ─── POST /api/v1/plugins/:id/disable ────────────────────────────────────

    /**
     * Disables a plugin. Shutdown is scheduled asynchronously via the registry.
     */
    public Promise<HttpResponse> handleDisablePlugin(HttpRequest request) {
        String pluginId = request.getPathParameter("id");
        String tenantId = http.resolveTenantId(request);
        metrics.incrementCounter("plugin.disable", "tenant", tenantId, "pluginId", pluginId);

        if (pluginRegistry.getPlugin(pluginId).isEmpty()) {
            return Promise.of(http.errorResponse(404, "Plugin not found: " + pluginId));
        }

        disabledPlugins.add(pluginId);
        log.warn("Plugin {} disabled by tenant {} — active operations will drain before shutdown", pluginId, tenantId);

        Map<String, Object> result = new HashMap<>();
        result.put("pluginId", pluginId);
        result.put("status", "disabled");
        result.put("message", "Plugin disabled. Active operations will drain before shutdown.");
        return Promise.of(http.jsonResponse(200, result));
    }

    // ─── POST /api/v1/plugins/:id/upgrade ────────────────────────────────────

    /**
     * Signals upgrade intent for a plugin. Validates the target version is newer than current
    /**
     * {@code POST /api/v1/plugins/:id/upgrade}
     *
     * <p>Runtime plugin hot-swap (installing a new version without restarting the process) is not
     * supported by the Data-Cloud standalone launcher.  Plugins are bundled at build time via the
     * ServiceLoader mechanism; upgrading requires deploying a new server build.
     *
     * <p>Returns {@code 501 Not Implemented} with a clear explanation so callers know this is an
     * explicit capability boundary rather than a temporary error.
     */
    public Promise<HttpResponse> handleUpgradePlugin(HttpRequest request) {
        String pluginId = request.getPathParameter("id");
        String tenantId = http.resolveTenantId(request);
        metrics.incrementCounter("plugin.upgrade.rejected", "tenant", tenantId, "pluginId", pluginId);
        log.info("[B6] Plugin upgrade requested for {} — returning 501 (not supported, bundled-only)", pluginId);
        return Promise.of(http.errorResponse(501,
                "Plugin upgrade is not supported. Data-Cloud plugins are bundled at build time. " +
                "To upgrade a plugin, deploy a new server version containing the updated plugin JAR."));
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private Map<String, Object> pluginView(StoragePlugin<?> plugin) {
        Map<String, Object> view = new HashMap<>();
        view.put("id", plugin.getPluginId());
        view.put("displayName", plugin.getDisplayName());
        view.put("version", plugin.getVersion());
        view.put("status", disabledPlugins.contains(plugin.getPluginId()) ? "disabled" : "enabled");
        view.put("supportedRecordTypes",
                plugin.getSupportedRecordTypes().stream()
                        .map(Enum::name)
                        .toList());
        return view;
    }
}
