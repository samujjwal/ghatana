package com.ghatana.tutorputor.contentgeneration;

import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Main launcher for the tutorputor content generation service.
 *
 * @doc.type class
 * @doc.purpose Bootstrap the content generation gRPC runtime with health and config validation
 * @doc.layer application
 * @doc.pattern Bootstrap
 */
public final class ContentGenerationLauncher {

    private static final Logger LOG = LoggerFactory.getLogger(ContentGenerationLauncher.class);
    private static final int DEFAULT_GRPC_PORT = 50051;
    private static final int DEFAULT_HEALTH_PORT = 8081;

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
        String openAiApiKey = System.getenv("OPENAI_API_KEY");
        String ollamaUrl = System.getenv("OLLAMA_URL");

        if ((openAiApiKey == null || openAiApiKey.isBlank())
            && (ollamaUrl == null || ollamaUrl.isBlank())) {
            LOG.warn("No explicit LLM provider configured. Falling back to the default Ollama URL at http://localhost:11434.");
        }

        resolvePort(System.getenv("GRPC_PORT"), DEFAULT_GRPC_PORT, "GRPC_PORT");
        resolvePort(System.getenv("HEALTH_PORT"), DEFAULT_HEALTH_PORT, "HEALTH_PORT");
    }

    private static HttpServer startHealthServer(int healthPort, int grpcPort, AtomicBoolean ready) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(healthPort), 0);
        server.createContext("/health", exchange -> {
            String body = String.format(
                "{\"status\":\"%s\",\"grpcPort\":%d,\"healthPort\":%d}",
                ready.get() ? "ready" : "starting",
                grpcPort,
                healthPort
            );
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(ready.get() ? 200 : 503, bytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(bytes);
            }
        });
        server.createContext("/ready", exchange -> {
            byte[] bytes = (ready.get() ? "ready" : "starting").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(ready.get() ? 200 : 503, bytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(bytes);
            }
        });
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
        return server;
    }

    private static Object createServerConfig(int grpcPort) {
        try {
            Class<?> configClass = Class.forName(
                "com.ghatana.tutorputor.contentstudio.config.ContentGenerationServerConfig");
            return configClass.getConstructor(int.class).newInstance(grpcPort);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(
                "Unable to load ContentGenerationServerConfig. Ensure content-studio-agents is on the runtime classpath.",
                ex);
        }
    }

    private static void invokeLifecycleMethod(Object target, String methodName)
        throws IOException, InterruptedException {
        try {
            Method method = target.getClass().getMethod(methodName);
            method.invoke(target);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            if (cause instanceof InterruptedException interruptedException) {
                throw interruptedException;
            }
            throw new IllegalStateException("Failed to invoke " + methodName + " on content generation runtime", cause);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to invoke " + methodName + " on content generation runtime", ex);
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        validateConfiguration();

        int grpcPort = resolvePort(System.getenv("GRPC_PORT"), DEFAULT_GRPC_PORT, "GRPC_PORT");
        int healthPort = resolvePort(System.getenv("HEALTH_PORT"), DEFAULT_HEALTH_PORT, "HEALTH_PORT");
        AtomicBoolean ready = new AtomicBoolean(false);

        LOG.info("Starting tutorputor content generation service on gRPC port {} with health port {}", grpcPort, healthPort);

        Object serverConfig = createServerConfig(grpcPort);
        HttpServer healthServer = startHealthServer(healthPort, grpcPort, ready);

        try {
            invokeLifecycleMethod(serverConfig, "start");
            ready.set(true);
            LOG.info("Content generation runtime started successfully");
            invokeLifecycleMethod(serverConfig, "blockUntilShutdown");
        } finally {
            ready.set(false);
            healthServer.stop(0);
            LOG.info("Content generation health server stopped");
        }
    }
}
