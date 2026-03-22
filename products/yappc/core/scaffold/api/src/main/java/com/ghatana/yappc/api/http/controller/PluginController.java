/*
 * Copyright (c) 2025 Ghatana Platform Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.yappc.api.http.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.platform.http.server.response.ResponseBuilder;
import com.ghatana.platform.http.server.servlet.RoutingServlet;
import com.ghatana.yappc.core.plugin.*;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST controller for plugin management using ActiveJ HTTP.
 *
 * @doc.type class
 * @doc.purpose HTTP API for plugin operations
 * @doc.layer presentation
 * @doc.pattern Controller
 */
public final class PluginController {

    private static final Logger LOG = LoggerFactory.getLogger(PluginController.class);
    private static final ObjectMapper MAPPER = JsonUtils.getDefaultMapper();

    private final PluginManager pluginManager;

    public PluginController(PluginManager pluginManager) {
        this.pluginManager = pluginManager;
    }

    /**
     * Register plugin routes with an ActiveJ RoutingServlet.
     */
    public void registerRoutes(RoutingServlet router) {
        router.addAsyncRoute(HttpMethod.GET, "/api/v1/plugins/health", this::healthCheckAll);
        router.addAsyncRoute(HttpMethod.POST, "/api/v1/plugins/load", this::loadPlugin);
        router.addAsyncRoute(HttpMethod.GET, "/api/v1/plugins", this::listPlugins);
        router.addAsyncRoute(HttpMethod.GET, "/api/v1/plugins/:id", this::getPlugin);
        router.addAsyncRoute(HttpMethod.DELETE, "/api/v1/plugins/:id", this::unloadPlugin);
        router.addAsyncRoute(HttpMethod.GET, "/api/v1/plugins/:id/health", this::healthCheck);
        router.addAsyncRoute(HttpMethod.POST, "/api/v1/plugins/:id/health", this::healthCheck);
    }

    private static String errorMessage(Throwable t) {
        String msg = t.getMessage();
        return msg != null ? msg : t.getClass().getSimpleName();
    }

    /**
     * GET /api/v1/plugins
     */
    public Promise<HttpResponse> listPlugins(HttpRequest request) {
        try {
            String capabilityStr = request.getQueryParameter("capability");
            String language = request.getQueryParameter("language");
            String buildSystem = request.getQueryParameter("buildSystem");

            List<YappcPlugin> plugins;
            if (capabilityStr != null) {
                PluginCapability capability = PluginCapability.valueOf(capabilityStr.toUpperCase());
                plugins = pluginManager.getRegistry().getPluginsByCapability(capability);
            } else if (language != null) {
                plugins = pluginManager.getRegistry().getPluginsByLanguage(language);
            } else if (buildSystem != null) {
                plugins = pluginManager.getRegistry().getPluginsByBuildSystem(buildSystem);
            } else {
                plugins = pluginManager.getRegistry().getAllPlugins();
            }

            List<PluginResponse> response = plugins.stream().map(this::toResponse).collect(Collectors.toList());
            return Promise.of(ResponseBuilder.ok().json(Map.of("plugins", response, "count", response.size())).build());
        } catch (IllegalArgumentException e) {
            return Promise.of(ResponseBuilder.badRequest()
                    .json(Map.of("error", "Invalid capability: " + e.getMessage())).build());
        } catch (Exception e) {
            return Promise.of(ResponseBuilder.internalServerError()
                    .json(Map.of("error", errorMessage(e))).build());
        }
    }

    /**
     * GET /api/v1/plugins/:id
     */
    public Promise<HttpResponse> getPlugin(HttpRequest request) {
        try {
            String pluginId = request.getPathParameter("id");
            YappcPlugin plugin = pluginManager.getRegistry().getPlugin(pluginId).orElse(null);
            if (plugin == null) {
                return Promise.of(ResponseBuilder.notFound()
                        .json(Map.of("error", "Plugin not found: " + pluginId)).build());
            }
            return Promise.of(ResponseBuilder.ok().json(toDetailedResponse(plugin)).build());
        } catch (Exception e) {
            return Promise.of(ResponseBuilder.internalServerError()
                    .json(Map.of("error", errorMessage(e))).build());
        }
    }

    /**
     * POST /api/v1/plugins/load
     */
    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> loadPlugin(HttpRequest request) {
        return request.loadBody().map(body -> {
            try {
                Map<String, Object> reqBody = MAPPER.readValue(
                        body.getString(StandardCharsets.UTF_8), Map.class);
                String jarPath = (String) reqBody.get("jarPath");
                Map<String, String> config = (Map<String, String>) reqBody.getOrDefault("config", Map.of());

                if (jarPath == null || jarPath.isBlank()) {
                    return ResponseBuilder.badRequest().json(Map.of("error", "jarPath is required")).build();
                }

                Path jar = Paths.get(jarPath);
                Path workspace = Paths.get(System.getProperty("user.dir"));
                Path packs = workspace.resolve("packs");

                PluginContext context = new PluginContext(workspace, packs, config,
                        pluginManager.getEventBus(), PluginSandbox.permissive(workspace));
                YappcPlugin plugin = pluginManager.loadAndInitialize(jar, context);

                return ResponseBuilder.created()
                        .json(Map.of("message", "Plugin loaded successfully", "plugin", toResponse(plugin))).build();
            } catch (PluginException e) {
                return ResponseBuilder.badRequest()
                        .json(Map.of("error", "Failed to load plugin: " + e.getMessage())).build();
            } catch (Exception e) {
                return ResponseBuilder.internalServerError().json(Map.of("error", errorMessage(e))).build();
            }
        });
    }

    /**
     * DELETE /api/v1/plugins/:id
     */
    public Promise<HttpResponse> unloadPlugin(HttpRequest request) {
        try {
            String pluginId = request.getPathParameter("id");
            if (!pluginManager.getRegistry().isRegistered(pluginId)) {
                return Promise.of(ResponseBuilder.notFound()
                        .json(Map.of("error", "Plugin not found: " + pluginId)).build());
            }
            pluginManager.shutdown(pluginId);
            return Promise.of(ResponseBuilder.ok().json(Map.of("message", "Plugin unloaded successfully")).build());
        } catch (PluginException e) {
            return Promise.of(ResponseBuilder.badRequest()
                    .json(Map.of("error", "Failed to unload plugin: " + e.getMessage())).build());
        } catch (Exception e) {
            return Promise.of(ResponseBuilder.internalServerError()
                    .json(Map.of("error", errorMessage(e))).build());
        }
    }

    /**
     * GET/POST /api/v1/plugins/:id/health
     */
    public Promise<HttpResponse> healthCheck(HttpRequest request) {
        try {
            String pluginId = request.getPathParameter("id");
            PluginHealthResult result = pluginManager.healthCheck(pluginId);
            return Promise.of(ResponseBuilder.ok().json(Map.of(
                    "pluginId", pluginId,
                    "healthy", result.healthy(),
                    "message", result.message(),
                    "details", result.details())).build());
        } catch (PluginException e) {
            return Promise.of(ResponseBuilder.notFound().json(Map.of("error", errorMessage(e))).build());
        } catch (Exception e) {
            return Promise.of(ResponseBuilder.internalServerError()
                    .json(Map.of("error", errorMessage(e))).build());
        }
    }

    /**
     * GET /api/v1/plugins/health
     */
    public Promise<HttpResponse> healthCheckAll(HttpRequest request) {
        try {
            Map<String, PluginHealthResult> results = pluginManager.healthCheckAll();
            List<Map<String, Object>> healthResults = results.entrySet().stream()
                    .map(entry -> Map.<String, Object>of(
                            "pluginId", entry.getKey(),
                            "healthy", entry.getValue().healthy(),
                            "message", entry.getValue().message(),
                            "details", entry.getValue().details()))
                    .collect(Collectors.toList());

            long unhealthy = healthResults.stream().filter(r -> !(Boolean) r.get("healthy")).count();
            return Promise.of(ResponseBuilder.ok().json(Map.of(
                    "results", healthResults,
                    "total", healthResults.size(),
                    "healthy", healthResults.size() - unhealthy,
                    "unhealthy", unhealthy)).build());
        } catch (Exception e) {
            return Promise.of(ResponseBuilder.internalServerError()
                    .json(Map.of("error", errorMessage(e))).build());
        }
    }

    private PluginResponse toResponse(YappcPlugin plugin) {
        PluginMetadata metadata = plugin.getMetadata();
        PluginState state = pluginManager.getPluginState(metadata.id());
        return new PluginResponse(metadata.id(), metadata.name(), metadata.version(),
                state.toString(), metadata.stability().toString(),
                metadata.capabilities().stream().map(Enum::toString).collect(Collectors.toList()));
    }

    private PluginDetailedResponse toDetailedResponse(YappcPlugin plugin) {
        PluginMetadata metadata = plugin.getMetadata();
        PluginState state = pluginManager.getPluginState(metadata.id());
        return new PluginDetailedResponse(metadata.id(), metadata.name(), metadata.version(),
                metadata.description(), metadata.author(), state.toString(), metadata.stability().toString(),
                metadata.capabilities().stream().map(Enum::toString).collect(Collectors.toList()),
                metadata.supportedLanguages(), metadata.supportedBuildSystems(),
                metadata.requiredConfig(), metadata.optionalConfig(), metadata.dependencies());
    }

    record PluginResponse(String id, String name, String version, String state,
                           String stability, List<String> capabilities) {}
    record PluginDetailedResponse(String id, String name, String version, String description,
                                   String author, String state, String stability, List<String> capabilities,
                                   List<String> supportedLanguages, List<String> supportedBuildSystems,
                                   Map<String, String> requiredConfig, Map<String, String> optionalConfig,
                                   List<String> dependencies) {}
}
