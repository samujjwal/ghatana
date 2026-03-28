package com.ghatana.aiplatform.gateway;

import com.ghatana.ai.embedding.EmbeddingService;
import com.ghatana.ai.llm.CompletionService;
import com.ghatana.platform.observability.MetricsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * Routes LLM requests to the appropriate provider based on tenant configuration and
 * provider availability.
 *
 * <p>Maintains separate registries for embedding and completion providers.
 * Default providers are used when no tenant-specific configuration matches.
 * A fallback provider is selected at random from the remaining providers when the primary is unavailable.
 *
 * @doc.type class
 * @doc.purpose Routes embedding and completion requests to appropriate LLM providers
 * @doc.layer platform
 * @doc.pattern Strategy + Router
 */
public class ProviderRouter {

    private static final Logger logger = LoggerFactory.getLogger(ProviderRouter.class);

    private final Map<String, EmbeddingService> embeddingProviders = new HashMap<>();
    private final Map<String, CompletionService> completionProviders = new HashMap<>();
    private final Map<String, TenantProviderConfig> tenantConfigs = new HashMap<>();
    private final MetricsCollector metrics;
    private final Random random = new Random();

    private String defaultEmbeddingProvider;
    private String defaultCompletionProvider;

    /**
     * Creates a ProviderRouter with a metrics collector for observability.
     *
     * @param metrics metrics collector (must not be null)
     */
    public ProviderRouter(MetricsCollector metrics) {
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    }

    /**
     * Registers an embedding service under the given provider name.
     *
     * @param providerName unique provider identifier
     * @param service      embedding service implementation
     */
    public void registerEmbeddingService(String providerName, EmbeddingService service) {
        Objects.requireNonNull(providerName, "providerName must not be null");
        Objects.requireNonNull(service, "service must not be null");
        embeddingProviders.put(providerName, service);
        logger.info("Registered embedding provider: {}", providerName);
    }

    /**
     * Registers a completion service under the given provider name.
     *
     * @param providerName unique provider identifier
     * @param service      completion service implementation
     */
    public void registerCompletionService(String providerName, CompletionService service) {
        Objects.requireNonNull(providerName, "providerName must not be null");
        Objects.requireNonNull(service, "service must not be null");
        completionProviders.put(providerName, service);
        logger.info("Registered completion provider: {}", providerName);
    }

    /**
     * Registers tenant-specific provider overrides.
     *
     * @param tenantId tenant identifier
     * @param config   tenant provider configuration
     */
    public void registerTenantConfig(String tenantId, TenantProviderConfig config) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(config, "config must not be null");
        tenantConfigs.put(tenantId, config);
    }

    /**
     * Selects the embedding service for the given tenant, falling back to the default.
     *
     * @param tenantId tenant identifier
     * @return the selected embedding service
     * @throws ProviderRouterException when no provider is available
     */
    public EmbeddingService selectEmbeddingService(String tenantId) {
        TenantProviderConfig config = tenantConfigs.get(tenantId);
        String provider = (config != null && config.embeddingProvider() != null)
                ? config.embeddingProvider()
                : defaultEmbeddingProvider;

        EmbeddingService service = provider != null ? embeddingProviders.get(provider) : null;
        if (service == null) {
            metrics.incrementCounter("provider.router.miss", "type", "embedding", "tenant", tenantId);
            throw new ProviderRouterException("No embedding provider available for tenant: " + tenantId);
        }
        metrics.incrementCounter("provider.router.hit", "type", "embedding", "provider", provider);
        return service;
    }

    /**
     * Selects a fallback embedding service (any registered provider other than the primary).
     *
     * @param tenantId tenant identifier
     * @return a fallback embedding service, or {@code null} if none
     */
    public EmbeddingService selectFallbackEmbeddingService(String tenantId) {
        return embeddingProviders.values().stream()
                .skip(random.nextInt(Math.max(1, embeddingProviders.size())))
                .findFirst()
                .orElse(null);
    }

    /**
     * Selects the completion service for the given tenant, falling back to the default.
     *
     * @param tenantId tenant identifier
     * @return the selected completion service
     * @throws ProviderRouterException when no provider is available
     */
    public CompletionService selectCompletionService(String tenantId) {
        TenantProviderConfig config = tenantConfigs.get(tenantId);
        String provider = (config != null && config.completionProvider() != null)
                ? config.completionProvider()
                : defaultCompletionProvider;

        CompletionService service = provider != null ? completionProviders.get(provider) : null;
        if (service == null) {
            metrics.incrementCounter("provider.router.miss", "type", "completion", "tenant", tenantId);
            throw new ProviderRouterException("No completion provider available for tenant: " + tenantId);
        }
        metrics.incrementCounter("provider.router.hit", "type", "completion", "provider", provider);
        return service;
    }

    /**
     * Selects a fallback completion service (any registered provider other than the primary).
     *
     * @param tenantId tenant identifier
     * @return a fallback completion service, or {@code null} if none
     */
    public CompletionService selectFallbackCompletionService(String tenantId) {
        return completionProviders.values().stream()
                .skip(random.nextInt(Math.max(1, completionProviders.size())))
                .findFirst()
                .orElse(null);
    }

    /**
     * Sets the default embedding provider used when no tenant override is present.
     *
     * @param providerName registered provider name
     */
    public void setDefaultEmbeddingProvider(String providerName) {
        this.defaultEmbeddingProvider = providerName;
    }

    /**
     * Sets the default completion provider used when no tenant override is present.
     *
     * @param providerName registered provider name
     */
    public void setDefaultCompletionProvider(String providerName) {
        this.defaultCompletionProvider = providerName;
    }

    // ------------------------------------------------------------------ //
    //  Inner types                                                         //
    // ------------------------------------------------------------------ //

    /**
     * Per-tenant provider configuration.
     *
     * @param embeddingProvider  preferred embedding provider name (may be null to use default)
     * @param completionProvider preferred completion provider name (may be null to use default)
     */
    public record TenantProviderConfig(String embeddingProvider, String completionProvider) {
    }

    /**
     * Thrown when no suitable provider is registered for a routing request.
     */
    public static final class ProviderRouterException extends RuntimeException {
        public ProviderRouterException(String message) {
            super(message);
        }
    }
}
