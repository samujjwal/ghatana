package com.ghatana.yappc.ai.canvas;

import com.ghatana.ai.llm.*;
import com.ghatana.ai.prompts.PromptTemplateManager;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.SimpleMetricsCollector;
import io.activej.dns.DnsClient;
import io.activej.dns.IDnsClient;
import io.activej.eventloop.Eventloop;
import io.activej.http.HttpClient;
import io.activej.inject.Injector;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import io.activej.launcher.Launcher;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;

/**
 * Canvas AI Server
 *
 * ActiveJ/gRPC server for Canvas AI operations.
 * Hosts validation and code generation services.
 *
 * <p>Lifecycle:
 * <ul>
 *   <li>gRPC service on port {@code GRPC_PORT} (default 50051)</li>
 *   <li>HTTP health endpoint on port {@code HEALTH_PORT} (default 8080):
 *       {@code GET /health} returns {@code {"status":"ok"}}</li>
 *   <li>Graceful shutdown: health server closes first, then gRPC drains with a
 *       30-second timeout before hard-stopping</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Main server for Canvas AI backend
 * @doc.layer platform
 * @doc.pattern Server
 */
public class CanvasAIServer extends Launcher {

    private static final Logger logger = LoggerFactory.getLogger(CanvasAIServer.class);
    private static final int GRPC_PORT = 50051;
    private static final int HEALTH_PORT = 8080;

    private Server grpcServer;
    private com.sun.net.httpserver.HttpServer healthServer;

    @Override
    protected void run() throws Exception {
        logger.info("Starting Canvas AI Server — gRPC port {}, health port {}", GRPC_PORT, HEALTH_PORT);

        startHealthServer();

        CanvasAIServiceImpl canvasAIService =
            Injector.of(getModule()).getInstance(CanvasAIServiceImpl.class);

        // Build gRPC server with injected service
        grpcServer = ServerBuilder.forPort(GRPC_PORT)
            .addService(canvasAIService)
            .build()
            .start();

        logger.info("Canvas AI Server started — gRPC ready on port {}", GRPC_PORT);

        // Register JVM shutdown hook for graceful drain
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown signal received — stopping Canvas AI Server");
            try {
                stopServer();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Interrupted during shutdown", e);
            }
        }, "canvas-ai-shutdown"));

        // Block until gRPC server terminates
        grpcServer.awaitTermination();
    }

    /** Starts the lightweight HTTP health endpoint. */
    private void startHealthServer() throws java.io.IOException {
        healthServer = com.sun.net.httpserver.HttpServer.create(
            new InetSocketAddress(HEALTH_PORT), 0);
        healthServer.createContext("/health", exchange -> {
            byte[] body = ("{\"status\":\"ok\",\"grpcPort\":" + GRPC_PORT + "}")
                .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        healthServer.start();
        logger.info("Health endpoint available at http://localhost:{}/health", HEALTH_PORT);
    }

    private void stopServer() throws InterruptedException {
        // Stop health server first so load-balancers drain traffic immediately
        if (healthServer != null) {
            healthServer.stop(0);
            logger.info("Health server stopped");
        }
        if (grpcServer != null) {
            grpcServer.shutdown();
            boolean terminated = grpcServer.awaitTermination(30, TimeUnit.SECONDS);
            if (!terminated) {
                logger.warn("gRPC server did not drain within 30 s — forcing shutdown");
                grpcServer.shutdownNow();
            }
            logger.info("Canvas AI gRPC server stopped");
        }
    }

    @Override
    protected io.activej.inject.module.Module getModule() {
        return new AbstractModule() {
            @Provides
            MeterRegistry meterRegistry() {
                return new SimpleMeterRegistry();
            }

            @Provides
            MetricsCollector metricsCollector(MeterRegistry registry) {
                return new SimpleMetricsCollector(registry);
            }

            @Provides
            Eventloop eventloop() {
                return Eventloop.create();
            }

            @Provides
            IDnsClient dnsClient(Eventloop eventloop) {
                return DnsClient.builder(eventloop, InetAddress.getLoopbackAddress()).build();
            }

            @Provides
            HttpClient httpClient(Eventloop eventloop, IDnsClient dnsClient) {
                return HttpClient.create(eventloop, dnsClient);
            }

            @Provides
            LLMConfiguration openAIConfig() {
                String apiKey = System.getenv("OPENAI_API_KEY");
                if (apiKey == null || apiKey.isEmpty()) {
                    throw new IllegalStateException("OPENAI_API_KEY environment variable is not set");
                }
                return LLMConfiguration.builder()
                    .apiKey(apiKey)
                    .modelName(System.getenv().getOrDefault("OPENAI_MODEL", "gpt-4"))
                    .temperature(0.7)
                    .maxTokens(4096)
                    .timeoutSeconds(60)
                    .maxRetries(3)
                    .build();
            }

            @Provides
            LLMConfiguration anthropicConfig() {
                String apiKey = System.getenv("ANTHROPIC_API_KEY");
                if (apiKey == null || apiKey.isEmpty()) {
                    throw new IllegalStateException("ANTHROPIC_API_KEY environment variable is not set");
                }
                return LLMConfiguration.builder()
                    .apiKey(apiKey)
                    .modelName(System.getenv().getOrDefault("ANTHROPIC_MODEL", "claude-3-opus-20240229"))
                    .temperature(0.7)
                    .maxTokens(4096)
                    .timeoutSeconds(60)
                    .maxRetries(3)
                    .build();
            }

            @Provides
            ToolAwareCompletionService openAIProvider(
                    LLMConfiguration openAIConfig,
                    HttpClient httpClient,
                    MetricsCollector metricsCollector) {
                return new ToolAwareOpenAICompletionService(openAIConfig, httpClient, metricsCollector);
            }

            @Provides
            ToolAwareCompletionService anthropicProvider(
                    LLMConfiguration anthropicConfig,
                    HttpClient httpClient,
                    MetricsCollector metricsCollector) {
                return new ToolAwareAnthropicCompletionService(anthropicConfig, httpClient, metricsCollector);
            }

            @Provides
            LLMGateway llmGateway(
                    ToolAwareCompletionService openAIProvider,
                    ToolAwareCompletionService anthropicProvider,
                    MetricsCollector metricsCollector) {
                return DefaultLLMGateway.builder()
                    .addProvider("openai", openAIProvider)
                    .addProvider("anthropic", anthropicProvider)
                    .defaultProvider("openai")
                    .metrics(metricsCollector)
                    .build();
            }

            @Provides
            PromptTemplateManager promptTemplateManager() {
                return new PromptTemplateManager();
            }

            @Provides
            CanvasValidationService canvasValidationService(MetricsCollector metrics) {
                return new CanvasValidationService(metrics);
            }

            @Provides
            CanvasGenerationService canvasGenerationService(LLMGateway llmGateway,
                                                           PromptTemplateManager promptTemplates,
                                                           MetricsCollector metrics) {
                return new CanvasGenerationService(llmGateway, promptTemplates, metrics);
            }

            @Provides
            DataSource dataSource() {
                String url = System.getenv().getOrDefault("YAPPC_AI_DB_URL", "jdbc:postgresql://localhost:5432/yappc_ai");
                String user = System.getenv().getOrDefault("YAPPC_AI_DB_USER", "yappc");
                String pass = System.getenv().getOrDefault("YAPPC_AI_DB_PASS", "yappc");
                com.zaxxer.hikari.HikariConfig config = new com.zaxxer.hikari.HikariConfig();
                config.setJdbcUrl(url);
                config.setUsername(user);
                config.setPassword(pass);
                config.setMaximumPoolSize(10);
                return new com.zaxxer.hikari.HikariDataSource(config);
            }

            @Provides
            CanvasAIServiceImpl canvasAIService(CanvasValidationService validationService,
                                               CanvasGenerationService generationService,
                                               MetricsCollector metrics,
                                               DataSource dataSource) {
                return new CanvasAIServiceImpl(validationService, generationService, metrics, dataSource);
            }
        };
    }

    public static void main(String[] args) throws Exception {
        CanvasAIServer launcher = new CanvasAIServer();
        launcher.launch(args);
    }
}
