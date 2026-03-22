package com.ghatana.tutorputor.contentstudio.config;

import com.ghatana.ai.llm.DefaultLLMGateway;
import com.ghatana.ai.llm.LLMGateway;
import com.ghatana.ai.llm.LLMConfiguration;
import com.ghatana.ai.llm.ToolAwareOpenAICompletionService;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.MetricsCollectorFactory;
import io.activej.dns.DnsClient;
import io.activej.dns.IDnsClient;
import io.activej.eventloop.Eventloop;
import io.activej.http.HttpClient;
import io.activej.reactor.nio.NioReactor;
import com.ghatana.tutorputor.contentstudio.grpc.ContentGenerationServiceImpl;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Configuration and bootstrap for the Content Generation gRPC server.
 * 
 * <p>This class handles:
 * <ul>
 *   <li>LLM provider configuration (OpenAI, Ollama)</li>
 *   <li>gRPC server setup and lifecycle</li>
 *   <li>Metrics registry configuration</li>
 *   <li>Graceful shutdown handling</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Server configuration and bootstrap
 * @doc.layer infrastructure
 * @doc.pattern Configuration
 */
public class ContentGenerationServerConfig {

    private static final Logger LOG = LoggerFactory.getLogger(ContentGenerationServerConfig.class);
    
    private final int port;
    private final LLMGateway llmGateway;
    private final MeterRegistry meterRegistry;
    private final Executor executor;
    private Server server;

    /**
     * Creates a new server configuration with default settings.
     *
     * @param port the port to listen on
     */
    public ContentGenerationServerConfig(int port) {
        this.port = port;
        this.meterRegistry = new SimpleMeterRegistry();
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.llmGateway = createLLMGateway();
    }

    /**
     * Creates a new server configuration with custom components.
     *
     * @param port the port to listen on
     * @param llmGateway the LLM gateway to use
     * @param meterRegistry the metrics registry
     * @param executor the executor for async operations
     */
    public ContentGenerationServerConfig(int port, LLMGateway llmGateway, 
                                          MeterRegistry meterRegistry, Executor executor) {
        this.port = port;
        this.llmGateway = llmGateway;
        this.meterRegistry = meterRegistry;
        this.executor = executor;
    }

    /**
     * Starts the gRPC server.
     *
     * @throws IOException if the server fails to start
     */
    public void start() throws IOException {
        ContentGenerationServiceImpl service = new ContentGenerationServiceImpl(
            llmGateway, executor, meterRegistry);
        
        server = ServerBuilder.forPort(port)
            .addService(service)
            .build()
            .start();
        
        LOG.info("Content Generation gRPC server started on port {}", port);
        
        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Shutting down gRPC server...");
            try {
                stop();
            } catch (InterruptedException e) {
                LOG.error("Error during shutdown", e);
                Thread.currentThread().interrupt();
            }
        }));
    }

    /**
     * Stops the gRPC server gracefully.
     *
     * @throws InterruptedException if the shutdown is interrupted
     */
    public void stop() throws InterruptedException {
        if (server != null) {
            server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
            LOG.info("gRPC server stopped");
        }
    }

    /**
     * Blocks until the server is terminated.
     *
     * @throws InterruptedException if the wait is interrupted
     */
    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    /**
     * Creates the LLM gateway based on environment configuration.
     */
    private LLMGateway createLLMGateway() {
        String primaryProvider = getEnv("LLM_PRIMARY_PROVIDER", "openai");

        MetricsCollector metricsCollector = MetricsCollectorFactory.create(meterRegistry);

        Eventloop eventloop = Eventloop.builder().withThreadName("tutorputor-grpc-llm").build();
        Thread eventloopThread = new Thread(eventloop::run, "tutorputor-grpc-llm-eventloop");
        eventloopThread.setDaemon(true);
        eventloopThread.start();

        NioReactor reactor = (NioReactor) eventloop;
        IDnsClient dnsClient = DnsClient.create(reactor, InetAddress.getLoopbackAddress());
        HttpClient httpClient = HttpClient.create(reactor, dnsClient);

        DefaultLLMGateway.Builder gatewayBuilder = DefaultLLMGateway.builder()
            .metrics(metricsCollector);

        List<String> providerOrder = new ArrayList<>();

        // Configure OpenAI if API key is available
        String openaiApiKey = getEnv("OPENAI_API_KEY", null);
        if (openaiApiKey != null && !openaiApiKey.isEmpty()) {
            String openaiModel = getEnv("OPENAI_MODEL", "gpt-4o");

            LLMConfiguration openaiConfig = LLMConfiguration.builder()
                .apiKey(openaiApiKey)
                .modelName(openaiModel)
                .build();

            gatewayBuilder.addProvider(
                "openai",
                new ToolAwareOpenAICompletionService(openaiConfig, httpClient, metricsCollector)
            );
            providerOrder.add("openai");
            LOG.info("OpenAI provider configured with model: {}", openaiModel);
        }

        // Configure Ollama (OpenAI-compatible) if URL is available
        String ollamaUrl = getEnv("OLLAMA_URL", "http://localhost:11434");
        String ollamaModel = getEnv("OLLAMA_MODEL", "llama3.2");
        if (ollamaUrl != null && !ollamaUrl.isEmpty()) {
            LLMConfiguration ollamaConfig = LLMConfiguration.builder()
                .apiKey("ollama")
                .baseUrl(ollamaUrl)
                .modelName(ollamaModel)
                .build();

            gatewayBuilder.addProvider(
                "ollama",
                new ToolAwareOpenAICompletionService(ollamaConfig, httpClient, metricsCollector)
            );
            providerOrder.add("ollama");
            LOG.info("Ollama provider configured at {} with model: {}", ollamaUrl, ollamaModel);
        }

        if (providerOrder.isEmpty()) {
            throw new IllegalStateException(
                "No LLM providers configured. Set OPENAI_API_KEY or OLLAMA_URL environment variables.");
        }

        // Default provider + fallback order
        if (primaryProvider != null && providerOrder.contains(primaryProvider)) {
            gatewayBuilder.defaultProvider(primaryProvider);
        }

        // Ensure fallback order starts with primary provider when possible
        if (primaryProvider != null && providerOrder.contains(primaryProvider)) {
            List<String> ordered = new ArrayList<>();
            ordered.add(primaryProvider);
            for (String p : providerOrder) {
                if (!p.equals(primaryProvider)) {
                    ordered.add(p);
                }
            }
            gatewayBuilder.fallbackOrder(ordered);
        } else {
            gatewayBuilder.fallbackOrder(providerOrder);
        }

        return gatewayBuilder.build();
    }

    private String getEnv(String name, String defaultValue) {
        String value = System.getenv(name);
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }

    /**
     * Main entry point for standalone server execution.
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        int port = Integer.parseInt(System.getenv().getOrDefault("GRPC_PORT", "50051"));
        
        ContentGenerationServerConfig config = new ContentGenerationServerConfig(port);
        config.start();
        config.blockUntilShutdown();
    }
}
