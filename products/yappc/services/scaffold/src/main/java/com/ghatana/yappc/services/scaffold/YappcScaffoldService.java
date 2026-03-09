package com.ghatana.yappc.services.scaffold;

import com.ghatana.core.activej.launcher.UnifiedApplicationLauncher;
import com.ghatana.platform.plugin.PluginRegistry;
import com.ghatana.yappc.core.orchestrator.PolyglotBuildOrchestrator;
import com.ghatana.yappc.core.services.ProjectAnalysisService;
import com.ghatana.yappc.plugin.bridge.UnifiedPluginBootstrap;
import io.activej.http.HttpServer;
import io.activej.inject.Injector;
import io.activej.inject.module.ModuleBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.activej.http.HttpMethod.GET;
import static io.activej.http.HttpMethod.POST;

/**
 * YAPPC Scaffold Service — Code generation, template rendering, and project scaffolding.
 *
 * <p>Components wired via {@link ScaffoldServiceModule}:
 * <ul>
 *   <li>{@link ProjectAnalysisService} — Project structure detection</li>
 *   <li>{@link PolyglotBuildOrchestrator} — Multi-language build generation</li>
 * </ul>
 *
 * <p>Also manages scaffold plugins via {@link PluginRegistry}
 * and {@link UnifiedPluginBootstrap}.</p>
 *
 * @doc.type class
 * @doc.purpose Code generation and scaffolding service entry point
 * @doc.layer product
 * @doc.pattern Launcher
 */
public class YappcScaffoldService extends UnifiedApplicationLauncher {

    private static final Logger logger = LoggerFactory.getLogger(YappcScaffoldService.class);
    private static final int DEFAULT_PORT = 8083;

    private final PluginRegistry pluginRegistry = new PluginRegistry();

    @Override
    protected String getServiceName() {
        return "yappc-scaffold";
    }

    @Override
    protected String getServiceVersion() {
        return "2.0.0";
    }

    @Override
    protected void setupService(ModuleBuilder builder) {
        // Eventloop (NioReactor) for HTTP server and routing
        builder.bind(io.activej.eventloop.Eventloop.class)
                .toInstance(io.activej.eventloop.Eventloop.create());

        // Plugin registry
        builder.bind(PluginRegistry.class).toInstance(pluginRegistry);

        // Discover scaffold plugins (generators, validators, analyzers)
        UnifiedPluginBootstrap bootstrap = new UnifiedPluginBootstrap(pluginRegistry);
        bootstrap.discoverAndRegister();

        // Install the scaffold DI module
        builder.install(new ScaffoldServiceModule());

        logger.info("YAPPC Scaffold service bindings configured with {} plugins",
                pluginRegistry.size());
    }

    @Override
    protected HttpServer createHttpServer(Injector injector) {
        int port = Integer.parseInt(System.getProperty("yappc.scaffold.port",
                String.valueOf(DEFAULT_PORT)));

        ProjectAnalysisService analysisService = injector.getInstance(ProjectAnalysisService.class);

        io.activej.eventloop.Eventloop eventloop = injector.getInstance(io.activej.eventloop.Eventloop.class);

        var router = io.activej.http.RoutingServlet.builder(eventloop)
                .with(GET, "/health", request ->
                        io.activej.http.HttpResponse.ok200().withPlainText("OK").toPromise())
                .with(GET, "/api/v1/scaffold/info", request ->
                        io.activej.http.HttpResponse.ok200()
                                .withPlainText("{\"service\":\"yappc-scaffold\",\"version\":\"2.0.0\"}")
                                .toPromise())
                .with(POST, "/api/v1/scaffold/generate", request ->
                        io.activej.http.HttpResponse.ok200()
                                .withPlainText("{\"status\":\"not_implemented\"}")
                                .toPromise())
                .with(POST, "/api/v1/scaffold/analyze", request ->
                        io.activej.http.HttpResponse.ok200()
                                .withPlainText("{\"status\":\"not_implemented\"}")
                                .toPromise())
                .with(GET, "/api/v1/scaffold/plugins", request ->
                        io.activej.http.HttpResponse.ok200()
                                .withPlainText("{\"plugins\":" + pluginRegistry.size() + "}")
                                .toPromise())
                .build();

        logger.info("Creating YAPPC Scaffold HTTP server on port {}", port);

        return HttpServer.builder(eventloop, router)
                .withListenPort(port)
                .build();
    }

    @Override
    protected void onApplicationStarted() {
        logger.info("=== YAPPC Scaffold Service v{} started on port {} ===",
                getServiceVersion(),
                System.getProperty("yappc.scaffold.port", String.valueOf(DEFAULT_PORT)));
        logger.info("  Registered plugins: {}", pluginRegistry.size());
    }

    @Override
    protected void onApplicationStopping() {
        logger.info("Shutting down YAPPC Scaffold Service...");
        pluginRegistry.shutdownAll();
    }

    public static void main(String[] args) throws Exception {
        new YappcScaffoldService().launch(args);
    }
}
