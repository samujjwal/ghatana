package com.ghatana.requirements.api.bootstrap;

import com.ghatana.ai.llm.CompletionService;
import com.ghatana.ai.llm.LLMConfiguration;
import com.ghatana.ai.llm.OpenAICompletionService;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.MetricsCollectorFactory;
import io.activej.dns.DnsClient;
import io.activej.eventloop.Eventloop;
import io.activej.http.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;

/**
 * Simple bootstrap helper to wire HttpClient into OpenAICompletionService for local development.
 * Not intended as production launcher; production should use platform DI/bootstrap.
 
 * @doc.type class
 * @doc.purpose Handles ai requirements bootstrap operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public final class AiRequirementsBootstrap {
    private static final Logger logger = LoggerFactory.getLogger(AiRequirementsBootstrap.class);

    private AiRequirementsBootstrap() {}

    public static CompletionService createOpenAIService(Eventloop eventloop) {
        // Create metrics collector (Noop for local dev if registry not available)
        MetricsCollector metrics = MetricsCollectorFactory.create(null);

        DnsClient dnsClient;
        try {
            dnsClient = DnsClient.create(eventloop, InetAddress.getByName("8.8.8.8"));
        } catch (java.net.UnknownHostException e) {
            throw new RuntimeException("Failed to create DNS client", e);
        }
        HttpClient httpClient = HttpClient.create(eventloop, dnsClient);

        // Build LLM configuration from environment
        LLMConfiguration config = LLMConfiguration.builder()
                .modelName(System.getenv().getOrDefault("OPENAI_MODEL", "gpt-4"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .timeoutSeconds((int) (Long.parseLong(System.getenv().getOrDefault("OPENAI_REQUEST_TIMEOUT_MS", "30000")) / 1000))
                .maxRetries(Integer.parseInt(System.getenv().getOrDefault("OPENAI_MAX_RETRIES", "3")))
                .build();

        OpenAICompletionService service = new OpenAICompletionService(config, httpClient, metrics);
        logger.info("Created OpenAICompletionService with model={}", config.getModelName());
        return service;
    }
}

