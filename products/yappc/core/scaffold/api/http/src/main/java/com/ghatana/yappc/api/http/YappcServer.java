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

package com.ghatana.yappc.api.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.platform.http.server.response.ResponseBuilder;
import com.ghatana.platform.http.server.servlet.RoutingServlet;
import com.ghatana.yappc.api.YappcApi;
import com.ghatana.yappc.api.YappcConfig;
import com.ghatana.yappc.api.http.controller.*;
import com.ghatana.yappc.api.http.websocket.ProgressWebSocket;
import com.ghatana.yappc.api.http.websocket.WebSocketManager;
import com.ghatana.yappc.core.plugin.PluginManager;
import io.activej.eventloop.Eventloop;
import io.activej.http.HttpMethod;
import io.activej.http.HttpResponse;
import io.activej.http.HttpServer;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * YAPPC HTTP Server backed by ActiveJ — RESTful API for scaffold operations.
 *
 * @doc.type class
 * @doc.purpose HTTP server for YAPPC API
 * @doc.layer platform
 * @doc.pattern Facade
 */
public final class YappcServer {

    private static final Logger LOG = LoggerFactory.getLogger(YappcServer.class);

    private final YappcServerConfig config;
    private final YappcApi api;
    private final RoutingServlet router;
    private final ProgressWebSocket progressWebSocket;
    private final WebSocketManager webSocketManager;
    private final PluginManager pluginManager;
    private final Eventloop eventloop;
    private HttpServer server;

    private YappcServer(YappcServerConfig config) {
        this.config = config;
        this.eventloop = Eventloop.builder().build();

        // Initialize YAPPC API
        YappcConfig apiConfig = YappcConfig.builder()
                .packsPath(config.getPacksPath())
                .workspacePath(config.getWorkspacePath())
                .enableCache(true)
                .build();
        this.api = YappcApi.create(apiConfig);
        this.pluginManager = new PluginManager();
        this.pluginManager.getEventBus().subscribe(event ->
                LOG.info("Plugin event: {} pluginId={} data={}", event.type(), event.pluginId(), event.data()));

        // Initialize WebSocket handlers
        this.progressWebSocket = new ProgressWebSocket();
        this.webSocketManager = new WebSocketManager();

        // Build ActiveJ routing servlet
        this.router = createRouter();
    }

    public static YappcServer create(YappcServerConfig config) {
        return new YappcServer(config);
    }

    public static YappcServer create() {
        return create(YappcServerConfig.defaults());
    }

    /**
     * Start the server on the configured host/port.
     */
    public YappcServer start() {
        server = HttpServer.builder(eventloop, router)
                .withListenPort(config.getPort())
                .build();

        new Thread(() -> {
            try { server.listen(); }
            catch (Exception e) { LOG.error("Failed to listen", e); }
            eventloop.run();
        }, "yappc-http").start();

        LOG.info("YAPPC Server started on http://{}:{}", config.getHost(), config.getPort());
        return this;
    }

    /**
     * Stop the server gracefully.
     */
    public void stop() {
        LOG.info("Stopping YAPPC Server...");
        if (server != null) server.close();
        webSocketManager.shutdown();
        api.shutdown();
        eventloop.breakEventloop();
        LOG.info("YAPPC Server stopped");
    }

    public YappcApi getApi() { return api; }
    public ProgressWebSocket getProgressWebSocket() { return progressWebSocket; }
    public WebSocketManager getWebSocketManager() { return webSocketManager; }
    public Eventloop getEventloop() { return eventloop; }

    // ── Routing ──────────────────────────────────────────────────────────────

    private RoutingServlet createRouter() {
        RoutingServlet servlet = new RoutingServlet();

        // Pack endpoints (specific routes before parameterised ones)
        PackController packCtrl = new PackController(api);
        servlet.addAsyncRoute(HttpMethod.GET, "/api/v1/packs/languages", packCtrl::getLanguages);
        servlet.addAsyncRoute(HttpMethod.GET, "/api/v1/packs/categories", packCtrl::getCategories);
        servlet.addAsyncRoute(HttpMethod.GET, "/api/v1/packs/platforms", packCtrl::getPlatforms);
        servlet.addAsyncRoute(HttpMethod.POST, "/api/v1/packs/refresh", packCtrl::refresh);
        servlet.addAsyncRoute(HttpMethod.GET, "/api/v1/packs", packCtrl::list);
        servlet.addAsyncRoute(HttpMethod.GET, "/api/v1/packs/:name", packCtrl::get);
        servlet.addAsyncRoute(HttpMethod.GET, "/api/v1/packs/:name/validate", packCtrl::validate);
        servlet.addAsyncRoute(HttpMethod.GET, "/api/v1/packs/:name/templates", packCtrl::listTemplates);
        servlet.addAsyncRoute(HttpMethod.GET, "/api/v1/packs/:name/variables", packCtrl::getVariables);

        // Project endpoints
        ProjectController projectCtrl = new ProjectController(api, progressWebSocket);
        servlet.addAsyncRoute(HttpMethod.POST, "/api/v1/projects", projectCtrl::create);
        servlet.addAsyncRoute(HttpMethod.POST, "/api/v1/projects/add-feature", projectCtrl::addFeature);
        servlet.addAsyncRoute(HttpMethod.POST, "/api/v1/projects/update", projectCtrl::update);
        servlet.addAsyncRoute(HttpMethod.GET, "/api/v1/projects/info", projectCtrl::getInfo);
        servlet.addAsyncRoute(HttpMethod.GET, "/api/v1/projects/state", projectCtrl::getState);
        servlet.addAsyncRoute(HttpMethod.GET, "/api/v1/projects/validate", projectCtrl::validate);
        servlet.addAsyncRoute(HttpMethod.GET, "/api/v1/projects/check-updates", projectCtrl::checkUpdates);
        servlet.addAsyncRoute(HttpMethod.POST, "/api/v1/projects/preview-update", projectCtrl::previewUpdate);
        servlet.addAsyncRoute(HttpMethod.GET, "/api/v1/projects/features", projectCtrl::getFeatures);
        servlet.addAsyncRoute(HttpMethod.POST, "/api/v1/projects/export", projectCtrl::exportState);
        servlet.addAsyncRoute(HttpMethod.POST, "/api/v1/projects/import", projectCtrl::importState);

        // Template endpoints
        TemplateController templateCtrl = new TemplateController(api);
        servlet.addAsyncRoute(HttpMethod.POST, "/api/v1/templates/render", templateCtrl::render);
        servlet.addAsyncRoute(HttpMethod.GET, "/api/v1/templates/helpers", templateCtrl::getHelpers);
        servlet.addAsyncRoute(HttpMethod.POST, "/api/v1/templates/validate", templateCtrl::validate);

        // Dependency endpoints
        DependencyController depCtrl = new DependencyController(api);
        servlet.addAsyncRoute(HttpMethod.GET, "/api/v1/dependencies/analyze/pack/:name", depCtrl::analyzePack);
        servlet.addAsyncRoute(HttpMethod.POST, "/api/v1/dependencies/analyze/project", depCtrl::analyzeProject);
        servlet.addAsyncRoute(HttpMethod.POST, "/api/v1/dependencies/conflicts", depCtrl::checkConflicts);
        servlet.addAsyncRoute(HttpMethod.POST, "/api/v1/dependencies/add-conflicts", depCtrl::checkAddConflicts);

        // Plugin endpoints
        PluginController pluginCtrl = new PluginController(pluginManager);
        pluginCtrl.registerRoutes(servlet);

        // Health check
        servlet.addAsyncRoute(HttpMethod.GET, "/health", req ->
                Promise.of(ResponseBuilder.ok().json(new HealthResponse("ok", api.getVersion())).build()));

        return servlet;
    }

    // Response types
    public record HealthResponse(String status, String version) {}
    public record ErrorResponse(String code, String message) {}

    /**
     * Main entry point for standalone server.
     */
    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("YAPPC_PORT", "8080"));
        YappcServerConfig config = YappcServerConfig.builder()
                .port(port)
                .enableSwagger(true)
                .enableWebSocket(true)
                .build();
        YappcServer server = YappcServer.create(config).start();
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
    }
}
