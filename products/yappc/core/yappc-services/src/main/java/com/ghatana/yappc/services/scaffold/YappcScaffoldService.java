package com.ghatana.yappc.services.scaffold;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ghatana.core.activej.launcher.UnifiedApplicationLauncher;
import com.ghatana.platform.plugin.PluginRegistry;
import com.ghatana.yappc.core.model.ProjectSpec;
import com.ghatana.yappc.core.orchestration.PolyglotBuildOrchestrator;
import com.ghatana.yappc.core.services.ProjectAnalysisService;
import com.ghatana.yappc.plugin.bridge.UnifiedPluginBootstrap;
import com.ghatana.yappc.services.security.YappcApiSecurity;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpServer;
import io.activej.inject.Injector;
import io.activej.inject.module.ModuleBuilder;
import io.activej.promise.Promise;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static io.activej.http.HttpMethod.GET;
import static io.activej.http.HttpMethod.POST;

/**
 * YAPPC Scaffold Service — Code generation, template rendering, and project scaffolding.
 *
 * <p>Components wired via {@link ScaffoldServiceModule}:
 * <ul>
 *   <li>{@link ProjectAnalysisService} — Project structure detection</li>
 *   <li>{@link PolyglotBuildOrchestrator} — Polyglot Makefile generation</li>
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
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Executor for blocking I/O (file-system walks, analysis). Never blocks the event loop. */
    private static final Executor IO_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "scaffold-io");
        t.setDaemon(true);
        return t;
    });

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
        // Makefile-generation orchestrator (orchestration package, not the build-execution one)
        PolyglotBuildOrchestrator makefileOrchestrator = new PolyglotBuildOrchestrator();
        PrometheusMeterRegistry prometheusRegistry = injector.getInstance(PrometheusMeterRegistry.class);

        io.activej.eventloop.Eventloop eventloop = injector.getInstance(io.activej.eventloop.Eventloop.class);

        AsyncServlet apiServlet = io.activej.http.RoutingServlet.builder(eventloop)
            .with(GET, "/api/v1/scaffold/info", request ->
                        io.activej.http.HttpResponse.ok200()
                                .withPlainText("{\"service\":\"yappc-scaffold\",\"version\":\"2.0.0\"}")
                                .toPromise())
                // ── Scaffold generate ─────────────────────────────────────────────────────
                // POST body: { "workspaceName": "my-workspace", "projects": [ { "name": "svc",
                //              "language": "java", "buildSystem": "gradle" } ] }
                // Returns: a polyglot workspace Makefile as plain text
                .with(POST, "/api/v1/scaffold/generate", request ->
                        request.loadBody().then(body -> {
                            try {
                                String json = body.getString(java.nio.charset.StandardCharsets.UTF_8);
                                JsonNode root = MAPPER.readTree(json);
                                String workspaceName = root.path("workspaceName").asText("workspace");
                                List<ProjectSpec> projects = MAPPER.readerForListOf(ProjectSpec.class)
                                        .readValue(root.path("projects"));
                                return Promise.ofBlocking(IO_EXECUTOR, () ->
                                        makefileOrchestrator.generateProjectsMakefile(workspaceName, projects))
                                        .map(makefile -> io.activej.http.HttpResponse.ok200()
                                                .withPlainText(makefile)
                                                .build());
                            } catch (Exception e) {
                                logger.warn("scaffold/generate failed: {}", e.getMessage());
                                return errorResponse(400, e.getMessage());
                            }
                        }))
                // ── Project analyze ───────────────────────────────────────────────────────
                // POST body: { "projectPath": "/absolute/path/to/project" }
                // Returns: ProjectAnalysis JSON
                .with(POST, "/api/v1/scaffold/analyze", request ->
                        request.loadBody().then(body -> {
                            try {
                                String json = body.getString(java.nio.charset.StandardCharsets.UTF_8);
                                JsonNode root = MAPPER.readTree(json);
                                String projectPath = root.path("projectPath").asText();
                                if (projectPath.isBlank()) {
                                    return errorResponse(400, "projectPath is required");
                                }
                                return Promise.ofBlocking(IO_EXECUTOR,
                                        () -> analysisService.analyzeProject(projectPath))
                                        .map(analysis -> {
                                            try {
                                                return io.activej.http.HttpResponse.ok200()
                                                        .withJson(MAPPER.writeValueAsString(analysis))
                                                        .build();
                                            } catch (Exception ex) {
                                                throw new RuntimeException(ex);
                                            }
                                        });
                            } catch (Exception e) {
                                logger.warn("scaffold/analyze failed: {}", e.getMessage());
                                return errorResponse(400, e.getMessage());
                            }
                        }))
                .with(GET, "/api/v1/scaffold/plugins", request ->
                        io.activej.http.HttpResponse.ok200()
                                .withPlainText("{\"plugins\":" + pluginRegistry.size() + "}")
                                .toPromise())
                .build();

            com.ghatana.platform.security.rbac.PolicyRepository policyRepository =
                injector.getInstance(com.ghatana.platform.security.rbac.PolicyRepository.class);
            YappcApiSecurity.SecurityRoutes securedApi =
                YappcApiSecurity.secureApi(apiServlet, "yappc:scaffold-api", policyRepository);
            AsyncServlet securedMetrics = YappcApiSecurity.secureReadEndpoint(
                request -> io.activej.http.HttpResponse.ok200()
                        .withHeader(io.activej.http.HttpHeaders.of("Content-Type"),
                                "text/plain; version=0.0.4; charset=utf-8")
                        .withBody(io.activej.bytebuf.ByteBuf.wrapForReading(
                                prometheusRegistry.scrape().getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                        .toPromise(),
                "yappc:scaffold-metrics",
                policyRepository);

            var router = io.activej.http.RoutingServlet.builder(eventloop)
                .with(GET, "/health", request ->
                    io.activej.http.HttpResponse.ok200().withPlainText("OK").toPromise())
                .with(GET, "/metrics", securedMetrics)
                .with(GET, "/api/*", securedApi.readApi())
                .with(POST, "/api/*", securedApi.writeApi())
                .build();

        logger.info("Creating YAPPC Scaffold HTTP server on port {}", port);

        return HttpServer.builder(eventloop, router)
                .withListenPort(port)
                .build();
    }

    private static Promise<io.activej.http.HttpResponse> errorResponse(int code, String message) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("error", message != null ? message : "Internal error");
        try {
            return io.activej.http.HttpResponse.ofCode(code)
                    .withJson(MAPPER.writeValueAsString(node))
                    .toPromise();
        } catch (Exception e) {
            return io.activej.http.HttpResponse.ofCode(code)
                    .withPlainText("{\"error\":\"Internal error\"}")
                    .toPromise();
        }
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
