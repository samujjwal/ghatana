package com.ghatana.yappc.canvas.ai;

import com.ghatana.ai.llm.*;
import com.ghatana.ai.prompts.PromptTemplateManager;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.SimpleMetricsCollector;
import io.activej.dns.DnsClient;
import io.activej.dns.IDnsClient;
import io.activej.eventloop.Eventloop;
import io.activej.http.HttpClient;
import io.activej.inject.annotation.Provides;
import io.activej.inject.annotation.Inject;
import io.activej.inject.module.AbstractModule;
import io.activej.launcher.Launcher;
import io.activej.promise.Promise;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Canvas AI Server
 * 
 * ActiveJ/gRPC server for Canvas AI operations.
 * Hosts validation and code generation services.
 * 
 * @doc.type class
 * @doc.purpose Main server for Canvas AI backend
 * @doc.layer platform
 * @doc.pattern Server
 */
public class CanvasAIServer extends Launcher {
    
    private static final Logger logger = LoggerFactory.getLogger(CanvasAIServer.class);
    private static final int PORT = 50051;  // Standard gRPC port
    
    private Server server;
    
    @Inject
    private CanvasAIServiceImpl canvasAIService;
    
    @Override
    protected void run() throws Exception {
        logger.info("Starting Canvas AI Server on port {}", PORT);
        
        // Build gRPC server with injected service
        server = ServerBuilder.forPort(PORT)
            .addService(canvasAIService)
            .build()
            .start();
        
        logger.info("Canvas AI Server started successfully on port {}", PORT);
        
        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down Canvas AI Server...");
            try {
                CanvasAIServer.this.stopServer();
            } catch (InterruptedException e) {
                logger.error("Error during shutdown", e);
            }
        }));
        
        // Wait for termination
        server.awaitTermination();
    }
    
    private void stopServer() throws InterruptedException {
        if (server != null) {
            server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
            logger.info("Canvas AI Server shut down successfully");
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
            CanvasAIServiceImpl canvasAIService(CanvasValidationService validationService,
                                               CanvasGenerationService generationService,
                                               MetricsCollector metrics) {
                return new CanvasAIServiceImpl(validationService, generationService, metrics);
            }
        };
    }
    
    public static void main(String[] args) throws Exception {
        CanvasAIServer launcher = new CanvasAIServer();
        launcher.launch(args);
    }
}
