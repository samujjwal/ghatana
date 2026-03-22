package com.ghatana.yappc.services.ai;

import com.ghatana.core.activej.launcher.UnifiedApplicationLauncher;
import com.ghatana.yappc.ai.router.AIModelRouter;
import com.ghatana.yappc.ai.service.YAPPCAIService;
import com.ghatana.yappc.ai.canvas.CanvasService;
import com.ghatana.yappc.ai.canvas.CanvasGenerationService;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpServer;
import io.activej.inject.Injector;
import io.activej.inject.module.ModuleBuilder;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.activej.http.HttpMethod.GET;
import static io.activej.http.HttpMethod.POST;

/**
 * YAPPC AI Service — Agent orchestration, workflows, and model integrations.
 *
 * <p>Manages AI agent lifecycles following the GAA framework:
 * PERCEIVE → REASON → ACT → CAPTURE → REFLECT.</p>
 *
 * <p>Components wired via {@link AiServiceModule}:
 * <ul>
 *   <li>{@link YAPPCAIService} — High-level AI facade</li>
 *   <li>{@link AIModelRouter} — Multi-model routing with fallback</li>
 *   <li>{@link CanvasService} — Canvas generation and management</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose AI agent service entry point
 * @doc.layer product
 * @doc.pattern Launcher
 * @doc.gaa.lifecycle perceive
 */
public class YappcAiService extends UnifiedApplicationLauncher {

    private static final Logger logger = LoggerFactory.getLogger(YappcAiService.class);
    private static final int DEFAULT_PORT = 8081;

    @Override
    protected String getServiceName() {
        return "yappc-ai";
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

        // Install the AI service DI module
        builder.install(new AiServiceModule());

        logger.info("YAPPC AI service bindings configured via AiServiceModule");
    }

    @Override
    protected HttpServer createHttpServer(Injector injector) {
        int port = Integer.parseInt(System.getProperty("yappc.ai.port",
                String.valueOf(DEFAULT_PORT)));

        YAPPCAIService aiService = injector.getInstance(YAPPCAIService.class);
        CanvasGenerationService canvasGenService = injector.getInstance(CanvasGenerationService.class);
        PrometheusMeterRegistry prometheusRegistry = injector.getInstance(PrometheusMeterRegistry.class);

        io.activej.eventloop.Eventloop eventloop = injector.getInstance(io.activej.eventloop.Eventloop.class);

        var router = io.activej.http.RoutingServlet.builder(eventloop)
                .with(GET, "/health", request ->
                        io.activej.http.HttpResponse.ok200().withPlainText("OK").toPromise())
                .with(GET, "/metrics", request -> {
                    String metricsOutput = prometheusRegistry.scrape();
                    return io.activej.http.HttpResponse.ok200()
                            .withBody(metricsOutput.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                            .withHeader(HttpHeaders.CONTENT_TYPE, "text/plain; version=0.0.4; charset=utf-8")
                            .toPromise();
                })
                .with(GET, "/api/v1/ai/info", request ->
                        io.activej.http.HttpResponse.ok200()
                                .withPlainText("{\"service\":\"yappc-ai\",\"version\":\"2.0.0\"}")
                                .toPromise())
                .with(GET, "/api/v1/ai/agents", request ->
                        io.activej.http.HttpResponse.ok200()
                                .withPlainText("{\"agents\":[]}")
                                .toPromise())
                .with(POST, "/api/v1/ai/analyze", request ->
                        request.loadBody().map($ -> request.getBody().asString(java.nio.charset.StandardCharsets.UTF_8))
                                .then(body -> aiService.analyzeCode(body)
                                        .map(result -> (io.activej.http.HttpResponse) io.activej.http.HttpResponse.ok200()
                                                .withBody(result.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8))
                                                .withHeader(io.activej.http.HttpHeaders.CONTENT_TYPE, "application/json")
                                                .build()))
                                .then(resp -> io.activej.promise.Promise.of(resp), e -> {
                                    logger.error("AI analyze request failed", e);
                                    return io.activej.promise.Promise.of(io.activej.http.HttpResponse.ofCode(500).withPlainText("Internal server error").build());
                                }))
                .with(POST, "/api/v1/ai/canvas/generate", request ->
                        request.loadBody().map($ -> request.getBody().asString(java.nio.charset.StandardCharsets.UTF_8))
                                .then(body -> {
                                    try {
                                        var requestBuilder = com.ghatana.contracts.canvas.v1.GenerateCodeRequest.newBuilder();
                                        com.google.protobuf.util.JsonFormat.parser()
                                                .ignoringUnknownFields()
                                                .merge(body, requestBuilder);
                                        com.ghatana.contracts.canvas.v1.GenerateCodeRequest genRequest = requestBuilder.build();
                                        return canvasGenService.generate(genRequest)
                                                .map(result -> {
                                                    try {
                                                        String json = com.google.protobuf.util.JsonFormat.printer()
                                                                .omittingInsignificantWhitespace()
                                                                .print(result);
                                                        return io.activej.http.HttpResponse.ok200()
                                                                .withBody(json.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                                                                .withHeader(io.activej.http.HttpHeaders.CONTENT_TYPE, "application/json")
                                                                .build();
                                                    } catch (Exception e) {
                                                        throw new RuntimeException("Failed to serialize response", e);
                                                    }
                                                });
                                    } catch (Exception e) {
                                        logger.error("Invalid canvas generate request", e);
                                        return io.activej.promise.Promise.of(
                                                io.activej.http.HttpResponse.ofCode(400)
                                                        .withPlainText("{\"error\":\"Invalid request: " +
                                                                e.getMessage().replace("\"", "\\\"") + "\"}")
                                                        .build());
                                    }
                                })
                                .mapException(e -> {
                                    logger.error("Canvas generation failed", e);
                                    return io.activej.http.HttpError.ofCode(500);
                                }))
                .build();

        logger.info("Creating YAPPC AI HTTP server on port {}", port);

        return HttpServer.builder(eventloop, router)
                .withListenPort(port)
                .build();
    }

    @Override
    protected void onApplicationStarted() {
        logger.info("=== YAPPC AI Service v{} started on port {} ===",
                getServiceVersion(),
                System.getProperty("yappc.ai.port", String.valueOf(DEFAULT_PORT)));
    }

    public static void main(String[] args) throws Exception {
        new YappcAiService().launch(args);
    }
}
