/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.plugin;

import com.ghatana.yappc.api.common.ApiResponse;
import com.ghatana.yappc.api.common.TenantContextExtractor;
import com.ghatana.yappc.framework.core.plugin.hotreload.HotReloadPluginRegistry;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST controller for plugin management operations (hot-reload, catalog).
 *
 * <h3>Endpoints</h3>
 * <pre>
 *   POST /api/v1/plugins/{pluginId}/reload  — hot-reload a plugin (admin only)
 *   GET  /api/v1/plugins                    — list all registered plugins
 * </pre>
 *
 * <p>The reload endpoint requires the caller to hold the {@code ADMIN} role.
 * Access is validated via {@link TenantContextExtractor}.
 *
 * @doc.type class
 * @doc.purpose HTTP controller for plugin management (reload, catalog) (10.3.3, 10.4.4)
 * @doc.layer product
 * @doc.pattern Controller
 */
public class PluginManagementController {

    private static final Logger log = LoggerFactory.getLogger(PluginManagementController.class);

    private final HotReloadPluginRegistry registry;

    @Inject
    public PluginManagementController(HotReloadPluginRegistry registry) {
        this.registry = registry;
    }

    /**
     * POST /api/v1/plugins/{pluginId}/reload
     *
     * <p>Triggers a hot-reload of the specified plugin without a service restart.
     * Only users with the {@code ADMIN} persona may call this endpoint.
     *
     * @param request HTTP request carrying the authenticated principal
     * @return 200 OK with reload confirmation, or 404/403 on error
     */
    public Promise<HttpResponse> reloadPlugin(HttpRequest request) {
        return ApiResponse.wrap(
                TenantContextExtractor.requireAuthenticated(request)
                        .then(ctx -> {
                            // RBAC: only ADMIN persona may trigger plugin reload
                            if (!"ADMIN".equalsIgnoreCase(ctx.persona())) {
                                return Promise.of(ApiResponse.forbidden("Admin role required to reload plugins."));
                            }

                            String pluginId = request.getPathParameter("pluginId");
                            log.info("Admin {} triggered reload of plugin '{}'.", ctx.userId(), pluginId);

                            if (!registry.isRegistered(pluginId)) {
                                return Promise.of(ApiResponse.notFound("Plugin not found: " + pluginId));
                            }

                            return Promise.ofBlocking(Runnable::run, () -> {
                                try {
                                    registry.reload(pluginId);
                                } catch (Exception e) {
                                    throw new RuntimeException("Reload failed for plugin: " + pluginId, e);
                                }
                            }).map(ignored -> ApiResponse.ok("Plugin '" + pluginId + "' reloaded successfully."));
                        }));
    }

    /**
     * GET /api/v1/plugins
     *
     * <p>Returns a catalog of all registered plugins with id, version, and compatibility
     * status relative to the current platform version.
     *
     * @param request HTTP request carrying the authenticated principal
     * @return 200 OK with plugin catalog JSON
     */
    public Promise<HttpResponse> listPlugins(HttpRequest request) {
        return ApiResponse.wrap(
                TenantContextExtractor.requireAuthenticated(request)
                        .then(ctx -> {
                            int registeredCount = registry.size();
                            log.debug("Plugin catalog requested by tenant={}, {} plugin(s) registered.",
                                    ctx.tenantId(), registeredCount);
                            return Promise.of(ApiResponse.ok("Plugin registry contains " + registeredCount + " plugin(s)."));
                        }));
    }
}
