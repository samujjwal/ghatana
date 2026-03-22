package com.ghatana.tutorputor.agent;

import com.ghatana.ai.llm.DefaultLLMGateway;
import com.ghatana.ai.llm.LLMConfiguration;
import com.ghatana.ai.llm.LLMGateway;
import com.ghatana.ai.llm.ToolAwareOpenAICompletionService;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.MetricsCollectorFactory;
import com.ghatana.tutorputor.contentstudio.knowledge.KnowledgeBaseService;
import io.activej.dns.DnsClient;
import io.activej.dns.IDnsClient;
import io.activej.http.HttpClient;
import io.activej.eventloop.Eventloop;
import io.activej.reactor.nio.NioReactor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Factory for creating TutorPutor agents with all dependencies wired.
 * 
 * <p>This factory:
 * <ul>
 *   <li>Creates and configures all agent dependencies</li>
 *   <li>Supports multiple LLM providers (OpenAI, Ollama)</li>
 *   <li>Provides pre-configured agents for different use cases</li>
 *   <li>Manages shared resources (executors, HTTP clients)</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Factory for TutorPutor agent creation
 * @doc.layer product
 * @doc.pattern Factory
 */
public class TutorPutorAgentFactory {

    private static final Logger LOG = LoggerFactory.getLogger(TutorPutorAgentFactory.class);
    
    private final MeterRegistry meterRegistry;
    private final Executor executor;
    private final LLMGateway llmGateway;
    private final KnowledgeBaseService knowledgeBaseService;
    private final ContentQualityValidator qualityValidator;

    private TutorPutorAgentFactory(Builder builder) {
        this.meterRegistry = builder.meterRegistry;
        this.executor = builder.executor;
        this.llmGateway = builder.llmGateway;
        this.knowledgeBaseService = builder.knowledgeBaseService;
        this.qualityValidator = new ContentQualityValidator(meterRegistry);
        
        LOG.info("TutorPutorAgentFactory initialized with LLM provider: {}", 
            builder.llmProvider);
    }

    /**
     * Creates a ContentGenerationAgent.
     *
     * @return a fully configured content generation agent
     */
    @NotNull
    public ContentGenerationAgent createContentGenerationAgent() {
        ContentGenerationOutputGenerator generator = new ContentGenerationOutputGenerator(
            llmGateway,
            knowledgeBaseService,
            executor,
            meterRegistry
        );
        
        return new ContentGenerationAgent(generator, knowledgeBaseService, qualityValidator);
    }

    /**
     * Creates a builder for TutorPutorAgentFactory.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a factory with default configuration for development.
     *
     * @return a factory configured for local development
     */
    public static TutorPutorAgentFactory createForDevelopment() {
        return builder()
            .withOllamaProvider("http://localhost:11434", "llama3.2")
            .build();
    }

    /**
     * Creates a factory with default configuration for production.
     *
     * @param openAIApiKey the OpenAI API key
     * @return a factory configured for production
     */
    public static TutorPutorAgentFactory createForProduction(String openAIApiKey) {
        return builder()
            .withOpenAIProvider(openAIApiKey, "gpt-4o")
            .build();
    }

    /**
     * Builder for TutorPutorAgentFactory.
     */
    public static class Builder {
        private MeterRegistry meterRegistry;
        private Executor executor;
        private LLMGateway llmGateway;
        private KnowledgeBaseService knowledgeBaseService;
        private HttpClient httpClient;
        private String llmProvider = "openai";

        /**
         * Sets the metrics registry.
         *
         * @param meterRegistry the registry
         * @return this builder
         */
        public Builder withMeterRegistry(@NotNull MeterRegistry meterRegistry) {
            this.meterRegistry = meterRegistry;
            return this;
        }

        /**
         * Sets the executor for async operations.
         *
         * @param executor the executor
         * @return this builder
         */
        public Builder withExecutor(@NotNull Executor executor) {
            this.executor = executor;
            return this;
        }

        /**
         * Sets the HTTP client.
         *
         * @param httpClient the HTTP client
         * @return this builder
         */
        public Builder withHttpClient(@NotNull HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        /**
         * Configures OpenAI as the LLM provider.
         *
         * @param apiKey the OpenAI API key
         * @param model the model to use (e.g., "gpt-4o", "gpt-3.5-turbo")
         * @return this builder
         */
        public Builder withOpenAIProvider(@NotNull String apiKey, @NotNull String model) {
            Objects.requireNonNull(apiKey, "apiKey cannot be null");
            Objects.requireNonNull(model, "model cannot be null");
            
            this.llmProvider = "openai";
            MetricsCollector metricsCollector = MetricsCollectorFactory.create(meterRegistry);

            if (httpClient == null) {
                Eventloop eventloop = Eventloop.builder().build();
                Thread eventloopThread = new Thread(eventloop::run, "tutorputor-openai-eventloop");
                eventloopThread.setDaemon(true);
                eventloopThread.start();

                NioReactor reactor = (NioReactor) eventloop;
                IDnsClient dnsClient = DnsClient.create(reactor, InetAddress.getLoopbackAddress());
                httpClient = HttpClient.create(reactor, dnsClient);
            }

            LLMConfiguration config = LLMConfiguration.builder()
                .apiKey(apiKey)
                .modelName(model)
                .build();

            ToolAwareOpenAICompletionService service = new ToolAwareOpenAICompletionService(
                config,
                httpClient,
                metricsCollector
            );

            this.llmGateway = DefaultLLMGateway.builder()
                .metrics(metricsCollector)
                .defaultProvider("openai")
                .fallbackOrder(java.util.List.of("openai"))
                .addProvider("openai", service)
                .build();
            return this;
        }

        /**
         * Configures Ollama as the LLM provider.
         *
         * @param baseUrl the Ollama server URL
         * @param model the model to use (e.g., "llama3.2", "mistral")
         * @return this builder
         */
        public Builder withOllamaProvider(@NotNull String baseUrl, @NotNull String model) {
            Objects.requireNonNull(baseUrl, "baseUrl cannot be null");
            Objects.requireNonNull(model, "model cannot be null");
            
            this.llmProvider = "ollama";
            MetricsCollector metricsCollector = MetricsCollectorFactory.create(meterRegistry);

            if (httpClient == null) {
                Eventloop eventloop = Eventloop.builder().build();
                Thread eventloopThread = new Thread(eventloop::run, "tutorputor-ollama-eventloop");
                eventloopThread.setDaemon(true);
                eventloopThread.start();

                NioReactor reactor = (NioReactor) eventloop;
                IDnsClient dnsClient = DnsClient.create(reactor, InetAddress.getLoopbackAddress());
                httpClient = HttpClient.create(reactor, dnsClient);
            }

            // Ollama exposes an OpenAI-compatible API. Use the tool-aware OpenAI client with baseUrl pointed at Ollama.
            LLMConfiguration config = LLMConfiguration.builder()
                .apiKey("ollama")
                .baseUrl(baseUrl)
                .modelName(model)
                .build();

            ToolAwareOpenAICompletionService service = new ToolAwareOpenAICompletionService(
                config,
                httpClient,
                metricsCollector
            );

            this.llmGateway = DefaultLLMGateway.builder()
                .metrics(metricsCollector)
                .defaultProvider("ollama")
                .fallbackOrder(java.util.List.of("ollama"))
                .addProvider("ollama", service)
                .build();
            return this;
        }

        /**
         * Sets a custom LLM gateway.
         *
         * @param llmGateway the LLM gateway
         * @return this builder
         */
        public Builder withLLMGateway(@NotNull LLMGateway llmGateway) {
            this.llmGateway = llmGateway;
            this.llmProvider = "custom";
            return this;
        }

        /**
         * Builds the factory.
         *
         * @return the configured factory
         */
        public TutorPutorAgentFactory build() {
            // Set defaults for unset values
            if (meterRegistry == null) {
                meterRegistry = new SimpleMeterRegistry();
                LOG.warn("No MeterRegistry provided, using SimpleMeterRegistry");
            }
            
            if (executor == null) {
                executor = Executors.newVirtualThreadPerTaskExecutor();
                LOG.info("Using virtual thread executor");
            }
            
            if (httpClient == null) {
                Eventloop eventloop = Eventloop.builder().build();
                Thread eventloopThread = new Thread(eventloop::run, "tutorputor-agent-factory-eventloop");
                eventloopThread.setDaemon(true);
                eventloopThread.start();

                NioReactor reactor = (NioReactor) eventloop;
                IDnsClient dnsClient = DnsClient.create(reactor, InetAddress.getLoopbackAddress());
                httpClient = HttpClient.create(reactor, dnsClient);
                LOG.info("Created default HTTP client");
            }
            
            if (llmGateway == null) {
                // Default to Ollama for local development
                String ollamaUrl = System.getenv().getOrDefault("OLLAMA_URL", "http://localhost:11434");
                String ollamaModel = System.getenv().getOrDefault("OLLAMA_MODEL", "llama3.2");

                MetricsCollector metricsCollector = MetricsCollectorFactory.create(meterRegistry);
                LLMConfiguration config = LLMConfiguration.builder()
                    .apiKey("ollama")
                    .baseUrl(ollamaUrl)
                    .modelName(ollamaModel)
                    .build();

                ToolAwareOpenAICompletionService service = new ToolAwareOpenAICompletionService(
                    config,
                    httpClient,
                    metricsCollector
                );

                llmGateway = DefaultLLMGateway.builder()
                    .metrics(metricsCollector)
                    .defaultProvider("ollama")
                    .fallbackOrder(java.util.List.of("ollama"))
                    .addProvider("ollama", service)
                    .build();
                llmProvider = "ollama";
                LOG.info("Using default Ollama provider at {}", ollamaUrl);
            }
            
            if (knowledgeBaseService == null) {
                knowledgeBaseService = new KnowledgeBaseService(httpClient, meterRegistry);
            }
            
            return new TutorPutorAgentFactory(this);
        }
    }
}
