package com.ghatana.tutorputor.contentstudio.server;

import com.ghatana.ai.llm.DefaultLLMGateway;
import com.ghatana.ai.llm.LLMConfiguration;
import com.ghatana.ai.llm.LLMGateway;
import com.ghatana.ai.llm.ToolAwareOpenAICompletionService;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.MetricsCollectorFactory;
import com.ghatana.tutorputor.contentstudio.grpc.ContentGenerationServiceImpl;
import io.activej.dns.DnsClient;
import io.activej.eventloop.Eventloop;
import io.activej.http.HttpClient;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @doc.type class
 * @doc.purpose Deployable gRPC server for TutorPutor Content Studio generation APIs.
 * @doc.layer product
 * @doc.pattern ServiceLauncher
 */
public final class ContentStudioGrpcServerMain {
    private static final Logger LOG = LoggerFactory.getLogger(ContentStudioGrpcServerMain.class);

    private ContentStudioGrpcServerMain() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        int port = Integer.parseInt(Optional.ofNullable(System.getenv("CONTENT_STUDIO_GRPC_PORT")).orElse("50051"));

        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        MetricsCollector metricsCollector = MetricsCollectorFactory.create(meterRegistry);

        Eventloop eventloop = Eventloop.builder()
            .withThreadName("content-studio-llm")
            .build();

        Thread eventloopThread = new Thread(eventloop::run, "content-studio-llm-eventloop");
        eventloopThread.start();

        DnsClient dnsClient = DnsClient.create(eventloop, new java.net.InetSocketAddress("8.8.8.8", 53));
        HttpClient httpClient = HttpClient.create(eventloop, dnsClient);
        LLMGateway llmGateway = buildGateway(httpClient, metricsCollector);

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        ContentGenerationServiceImpl contentService = new ContentGenerationServiceImpl(llmGateway, executor, meterRegistry);

        Server server = ServerBuilder.forPort(port)
            .addService(contentService)
            .build()
            .start();

        LOG.info("Content Studio gRPC server started on port {}", port);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                LOG.info("Shutting down Content Studio gRPC server...");
                server.shutdown();
                if (!server.awaitTermination(10, TimeUnit.SECONDS)) {
                    server.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                executor.shutdown();
                eventloop.breakEventloop();
            }
        }));

        server.awaitTermination();
        eventloopThread.join(TimeUnit.SECONDS.toMillis(5));
    }

    private static LLMGateway buildGateway(HttpClient httpClient, MetricsCollector metricsCollector) {
        String openAiApiKey = System.getenv("OPENAI_API_KEY");
        if (openAiApiKey == null || openAiApiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY is required");
        }

        String baseUrl = Optional.ofNullable(System.getenv("OPENAI_BASE_URL")).orElse("https://api.openai.com");
        String modelName = Optional.ofNullable(System.getenv("OPENAI_MODEL")).orElse("gpt-4o-mini");

        LLMConfiguration openAiConfig = LLMConfiguration.builder()
            .apiKey(openAiApiKey)
            .baseUrl(baseUrl)
            .modelName(modelName)
            .build();

        ToolAwareOpenAICompletionService openAiService = new ToolAwareOpenAICompletionService(
            openAiConfig,
            httpClient,
            metricsCollector
        );

        return DefaultLLMGateway.builder()
            .metrics(metricsCollector)
            .defaultProvider("openai")
            .fallbackOrder(List.of("openai"))
            .addProvider("openai", openAiService)
            .build();
    }
}
