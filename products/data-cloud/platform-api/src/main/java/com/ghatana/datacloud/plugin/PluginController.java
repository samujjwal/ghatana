/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.plugin;

import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.promise.Promise;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * REST controller for plugin management.
 *
 * @doc.type class
 * @doc.purpose Plugin management REST API
 * @doc.layer product
 * @doc.pattern Controller
 */
public class PluginController implements AsyncServlet {

    private final PluginRegistry pluginRegistry;

    public PluginController(PluginRegistry pluginRegistry) {
        this.pluginRegistry = pluginRegistry;
    }

    @Override
    public Promise<HttpResponse> serve(HttpRequest request) {
        String path = request.getRelativePath();
        String method = request.getMethod().toString();

        if ("POST".equals(method) && path.endsWith("/plugins")) {
            return registerPlugin(request);
        } else if ("GET".equals(method) && path.matches(".*/plugins/[^/]+")) {
            return getPlugin(request, extractId(path));
        } else if ("GET".equals(method) && path.endsWith("/plugins")) {
            return listPlugins(request);
        } else if ("POST".equals(method) && path.matches(".*/plugins/[^/]+/activate")) {
            return activatePlugin(request, extractId(path));
        } else if ("POST".equals(method) && path.matches(".*/plugins/[^/]+/deactivate")) {
            return deactivatePlugin(request, extractId(path));
        } else if ("DELETE".equals(method) && path.matches(".*/plugins/[^/]+")) {
            return unregisterPlugin(request, extractId(path));
        } else if ("GET".equals(method) && path.matches(".*/plugins/[^/]+/health")) {
            return getPluginHealth(request, extractId(path));
        } else if ("POST".equals(method) && path.matches(".*/plugins/[^/]+/hooks/[^/]+")) {
            return executeHook(request, extractId(path), extractHookName(path));
        }

        return notFound();
    }

    private Promise<HttpResponse> registerPlugin(HttpRequest request) {
        return request.loadBody()
            .then(body -> {
                try {
                    PluginRegistry.PluginMetadata plugin = parsePlugin(body.asString(StandardCharsets.UTF_8));
                    return pluginRegistry.register(plugin)
                        .then(this::okJson);
                } catch (Exception e) {
                    return badRequest("Invalid request: " + e.getMessage());
                }
            });
    }

    private Promise<HttpResponse> getPlugin(HttpRequest request, String pluginId) {
        return pluginRegistry.getPlugin(pluginId)
            .then(pluginOpt -> {
                if (pluginOpt.isPresent()) {
                    return okJson(pluginOpt.get());
                } else {
                    return notFound("Plugin not found");
                }
            });
    }

    private Promise<HttpResponse> listPlugins(HttpRequest request) {
        String tenantId = extractTenantId(request);
        String statusParam = request.getQueryParameter("status");
        PluginRegistry.PluginStatus status = statusParam != null
            ? PluginRegistry.PluginStatus.valueOf(statusParam)
            : null;

        return pluginRegistry.listPlugins(tenantId, status)
            .then(plugins -> okJson(Map.of("plugins", plugins, "total", plugins.size())));
    }

    private Promise<HttpResponse> activatePlugin(HttpRequest request, String pluginId) {
        return pluginRegistry.activate(pluginId)
            .then(this::okJson);
    }

    private Promise<HttpResponse> deactivatePlugin(HttpRequest request, String pluginId) {
        return pluginRegistry.deactivate(pluginId)
            .then(this::okJson);
    }

    private Promise<HttpResponse> unregisterPlugin(HttpRequest request, String pluginId) {
        return pluginRegistry.unregister(pluginId)
            .then(v -> okJson(Map.of("unregistered", true)));
    }

    private Promise<HttpResponse> getPluginHealth(HttpRequest request, String pluginId) {
        return pluginRegistry.getHealth(pluginId)
            .then(this::okJson);
    }

    private Promise<HttpResponse> executeHook(HttpRequest request, String pluginId, String hookName) {
        return request.loadBody()
            .then(body -> {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> context = parseJson(body.asString(StandardCharsets.UTF_8));
                    return pluginRegistry.executeHook(pluginId, hookName, context)
                        .then(this::okJson);
                } catch (Exception e) {
                    return badRequest("Invalid request");
                }
            });
    }

    private String extractId(String path) {
        String[] parts = path.split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            if ("plugins".equals(parts[i]) && i + 1 < parts.length) {
                return parts[i + 1];
            }
        }
        return "";
    }

    private String extractHookName(String path) {
        String[] parts = path.split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            if ("hooks".equals(parts[i]) && i + 1 < parts.length) {
                return parts[i + 1];
            }
        }
        return "";
    }

    private String extractTenantId(HttpRequest request) {
        String tenantId = request.getHeader(HttpHeaders.of("X-Tenant-ID"));
        return tenantId != null ? tenantId : "default-tenant";
    }

    private PluginRegistry.PluginMetadata parsePlugin(String json) {
        // Simplified parsing - in production use Jackson
        return new PluginRegistry.PluginMetadata(
            "new-plugin", "New Plugin", "", "1.0.0", "tenant-alpha",
            PluginRegistry.PluginType.CUSTOM, PluginRegistry.PluginStatus.REGISTERED,
            java.util.List.of(), java.util.List.of(), java.util.Map.of(),
            java.time.Instant.now(), null, "user"
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        // Simplified - in production use Jackson
        return java.util.Map.of();
    }

    private String toJson(Object obj) {
        // Simplified - in production use Jackson
        return "{}";
    }

    private Promise<HttpResponse> okJson(Object payload) {
        return Promise.of(HttpResponse.ok200().withJson(toJson(payload)).build());
    }

    private Promise<HttpResponse> badRequest(String message) {
        return Promise.of(HttpResponse.ofCode(400).withPlainText(message).build());
    }

    private Promise<HttpResponse> notFound(String message) {
        return Promise.of(HttpResponse.ofCode(404).withPlainText(message).build());
    }

    private Promise<HttpResponse> notFound() {
        return notFound("Not Found");
    }
}
