package com.ghatana.tutorputor.contentgeneration;

import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Main launcher for the tutorputor content generation service.
 * Provides a full service lifecycle with health checks, graceful shutdown,
 * configuration validation, and signal handling.
 *
 * @doc.type class
 * @doc.purpose Bootstrap the content generation gRPC runtime with health, config validation, and graceful shutdown
 * @doc.layer application
 * @doc.pattern Bootstrap
 */
public final class ContentGenerationLauncher {

    private static final Logger LOG = LoggerFactory.getLogger(ContentGenerationLauncher.class);
    private static final int DEFAULT_GRPC_PORT = 50051;
    private static final int DEFAULT_HEALTH_PORT = 8081;
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 30;

    private static volatile HttpServer healthServer;
    private static volatile ContentGenerationServer serverConfig;
    private static final AtomicBoolean ready = new AtomicBoolean(false);
    private static final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private static final AtomicReference<Instant> startTime = new AtomicReference<>();

    private ContentGenerationLauncher() {
    }

    static int resolvePort(String value, int defaultPort, String envName) {
        if (value == null || value.isBlank()) {
            return defaultPort;
        }

        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0 || parsed > 65535) {
                throw new IllegalArgumentException(
                    String.format("%s must be between 1 and 65535 but was %s", envName, value));
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                String.format("%s must be a valid integer port but was %s", envName, value), ex);
        }
    }

    static void validateConfiguration() {
        LOG.info("Validating configuration...");

        String openAiApiKey = System.getenv("OPENAI_API_KEY");
        String ollamaUrl = System.getenv("OLLAMA_URL");

        if ((openAiApiKey == null || openAiApiKey.isBlank())
            && (ollamaUrl == null || ollamaUrl.isBlank())) {
            LOG.warn("No explicit LLM provider configured. Falling back to the default Ollama URL at http://localhost:11434.");
        }

        if (openAiApiKey != null && !openAiApiKey.isBlank()) {
            LOG.info("OpenAI API key configured (masked: {})", maskApiKey(openAiApiKey));
        }

        if (ollamaUrl != null && !ollamaUrl.isBlank()) {
            LOG.info("Ollama URL configured: {}", ollamaUrl);
        }

        int grpcPort = resolvePort(System.getenv("GRPC_PORT"), DEFAULT_GRPC_PORT, "GRPC_PORT");
        int healthPort = resolvePort(System.getenv("HEALTH_PORT"), DEFAULT_HEALTH_PORT, "HEALTH_PORT");

        LOG.info("Configuration validated: gRPC port {}, health port {}", grpcPort, healthPort);
    }

    private static String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() <= 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "..." + apiKey.substring(apiKey.length() - 4);
    }

    private static HttpServer startHealthServer(int healthPort, int grpcPort) throws IOException {
        LOG.info("Starting health server on port {}", healthPort);
        HttpServer server = HttpServer.create(new InetSocketAddress(healthPort), 0);

        server.createContext("/health", exchange -> {
            String status = ready.get() ? "ready" : "starting";
            int statusCode = ready.get() ? 200 : 503;
            Instant started = startTime.get();
            long uptimeSeconds = started != null ? java.time.Duration.between(started, Instant.now()).getSeconds() : 0;

            String body = String.format(
                "{\"status\":\"%s\",\"grpcPort\":%d,\"healthPort\":%d,\"uptimeSeconds\":%d,\"timestamp\":\"%s\"}",
                status,
                grpcPort,
                healthPort,
                uptimeSeconds,
                Instant.now().toString()
            );
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(bytes);
            }
        });

        server.createContext("/ready", exchange -> {
            String status = ready.get() ? "ready" : "starting";
            int statusCode = ready.get() ? 200 : 503;
            byte[] bytes = status.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(bytes);
            }
        });

        server.createContext("/metrics", exchange -> {
            String metrics = "# HELP content_generation_ready Whether the service is ready\n" +
                "# TYPE content_generation_ready gauge\n" +
                "content_generation_ready " + (ready.get() ? "1" : "0") + "\n" +
                "# HELP content_generation_uptime_seconds Service uptime in seconds\n" +
                "# TYPE content_generation_uptime_seconds gauge\n";
            Instant started = startTime.get();
            if (started != null) {
                metrics += "content_generation_uptime_seconds " +
                    java.time.Duration.between(started, Instant.now()).getSeconds() + "\n";
            }
            byte[] bytes = metrics.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(bytes);
            }
        });

        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
        return server;
    }

    /**
     * F-026: Loads the {@link ContentGenerationServer} implementation via the
     * service-loader pattern (typed, not reflection).
     *
     * <p>The concrete class
     * {@code com.ghatana.tutorputor.contentstudio.config.ContentGenerationServerConfig}
     * in the {@code content-studio-agents} runtime module must implement this
     * interface.  We still use {@code Class.forName} for classpath isolation
     * (the impl is an optional runtime dep), but we cast to the typed interface
     * rather than using raw reflection to invoke individual methods.
     */
    private static ContentGenerationServer createServerConfig(int grpcPort) {
        try {
            LOG.info("Loading ContentGenerationServerConfig for port {}", grpcPort);
            Class<?> configClass = Class.forName(
                "com.ghatana.tutorputor.contentstudio.config.ContentGenerationServerConfig");
            Object instance = configClass.getConstructor(int.class).newInstance(grpcPort);
            if (!(instance instanceof ContentGenerationServer server)) {
                throw new IllegalStateException(
                    "ContentGenerationServerConfig must implement ContentGenerationServer");
            }
            return server;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(
                "Unable to load ContentGenerationServerConfig. Ensure content-studio-agents is on the runtime classpath.",
                ex);
        }
    }

    private static void setupShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (shuttingDown.compareAndSet(false, true)) {
                LOG.info("Shutdown hook triggered, initiating graceful shutdown...");
                gracefulShutdown();
            }
        }));
    }

    private static void gracefulShutdown() {
        LOG.info("Starting graceful shutdown...");

        ready.set(false);

        if (serverConfig != null) {
            try {
                serverConfig.shutdown();
                LOG.info("Content generation runtime shutdown completed");
            } catch (Exception e) {
                LOG.error("Error during content generation runtime shutdown", e);
            }
        }

        if (healthServer != null) {
            try {
                healthServer.stop(0);
                LOG.info("Health server stopped");
            } catch (Exception e) {
                LOG.error("Error stopping health server", e);
            }
        }

        LOG.info("Graceful shutdown completed");
    }

    // Package-private setters for testing
    static void setReady(boolean value) {
        ready.set(value);
    }

    static void setStartTime() {
        startTime.set(Instant.now());
    }

    // Package-private reset method for testing
    static void resetState() {
        ready.set(false);
        startTime.set(null);
    }

    public static void main(String[] args) {
        try {
            LOG.info("========================================");
            LOG.info("Tutorputor Content Generation Service");
            LOG.info("========================================");

            validateConfiguration();

            int grpcPort = resolvePort(System.getenv("GRPC_PORT"), DEFAULT_GRPC_PORT, "GRPC_PORT");
            int healthPort = resolvePort(System.getenv("HEALTH_PORT"), DEFAULT_HEALTH_PORT, "HEALTH_PORT");

            LOG.info("Starting tutorputor content generation service on gRPC port {} with health port {}", grpcPort, healthPort);

            setupShutdownHook();

            serverConfig = createServerConfig(grpcPort);
            healthServer = startHealthServer(healthPort, grpcPort);

            startTime.set(Instant.now());

            try {
                serverConfig.start();
                ready.set(true);
                LOG.info("Content generation runtime started successfully on port {}", grpcPort);
                LOG.info("Service is ready to accept requests");
                LOG.info("Health endpoints available at http://localhost:{}/health", healthPort);
                LOG.info("Metrics available at http://localhost:{}/metrics", healthPort);

                serverConfig.blockUntilShutdown();
            } catch (Exception e) {
                LOG.error("Fatal error during service startup or execution", e);
                ready.set(false);
                System.exit(1);
            }
        } catch (Throwable t) {
            LOG.error("Fatal error during service initialization", t);
            System.exit(1);
        } finally {
            if (!shuttingDown.get()) {
                gracefulShutdown();
            }
        }
    }
}
