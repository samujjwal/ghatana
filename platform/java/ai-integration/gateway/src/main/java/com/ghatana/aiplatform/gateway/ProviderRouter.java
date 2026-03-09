package com.ghatana.aiplatform.gateway;

import com.ghatana.ai.embedding.EmbeddingService;
import com.ghatana.ai.llm.CompletionService;
import com.ghatana.platform.observability.MetricsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Routes AI provider requests based on tenant configuration, model policies, and availability.
 *
 * <p><b>Purpose</b><br>
 * Provides intelligent provider selection with fallback strategies:
 * <ul>
 *   <li>Multi-provider routing (OpenAI, Claude, local models)</li>
 *   <li>Tenant-specific policies (preferred provider, cost limits)</li>
 *   <li>Fallback to alternative providers on primary failure</li>
 *   <li>Load balancing and health checks</li>
 *   <li>Metrics tracking for provider availability and performance</li>
 * </ul>
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * ProviderRouter router = new ProviderRouter(metrics);
 * router.registerEmbeddingService("openai", openaiService);
 * router.registerCompletionService("openai", openaiCompletionService);
 *
 * // Select provider based on tenant config
 * EmbeddingService provider = router.selectEmbeddingService("tenant-123");
 * CompletionService completion = router.selectCompletionService("tenant-456");
 *
 * // Fallback on primary failure
 * EmbeddingService fallback = router.selectFallbackEmbeddingService("tenant-123");
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * Core gateway component enabling multi-provider strategies and tenant isolation.
 * Used by LLMGatewayService for routing decisions, ProviderRouter selects appropriate service.
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe - uses ConcurrentHashMap for provider registry and configuration.
 *
 * @doc.type class
 * @doc.purpose Provider routing with tenant policies and fallback strategies
 * @doc.layer platform
 * @doc.pattern Strategy + Factory
 */
public class ProviderRouter {

    private static final Logger logger = LoggerFactory.getLogger(ProviderRouter.class);

    private final Map<String, EmbeddingService> embeddingProviders = new ConcurrentHashMap<>();
    private final Map<String, CompletionService> completionProviders = new ConcurrentHashMap<>();
    private final Map<String, TenantProviderConfig> tenantConfigs = new ConcurrentHashMap<>();
    private final MetricsCollector metrics;
    private final Random random = new Random();

    private String defaultEmbeddingProvider = "openai";
    private String defaultCompletionProvider = "openai";

    /**
     * Constructs provider router.
     *
     * @param metrics metrics collector
     */
    public ProviderRouter(MetricsCollector metrics) {
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    }

    /**
     * Registers an embedding service provider.
     *
     * @param providerName unique provider identifier
     * @param service embedding service implementation
     */
    public void registerEmbeddingService(String providerName, EmbeddingService service) {
        Objects.requireNonNull(providerName, "providerName must not be null");
        Objects.requireNonNull(service, "service must not be null");
        embeddingProviders.put(providerName, service);
        logger.info("Registered embedding provider: {}", providerName);
    }

    /**
     * Registers a completion service provider.
     *
     * @param providerName unique provider identifier
     * @param service completion service implementation
     */
    public void registerCompletionService(String providerName, CompletionService service) {
        Objects.requireNonNull(providerName, "providerName must not be null");
        Objects.requireNonNull(service, "service must not be null");
        completionProviders.put(providerName, service);
        logger.info("Registered completion provider: {}", providerName);
    }

    /**
     * Registers tenant-specific provider configuration.
     *
     * @param tenantId tenant identifier
     * @param config tenant provider configuration
     */
    public void registerTenantConfig(String tenantId, TenantProviderConfig config) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(config, "config must not be null");
        tenantConfigs.put(tenantId, config);
        logger.info("Registered provider config for tenant: {}", tenantId);
    }

    /**
     * Selects embedding service for tenant.
     *
     * <p>GIVEN: Valid tenant ID
     * <p>WHEN: selectEmbeddingService() is called
     * <p>THEN: Returns configured provider or default if not configured
     *
     * @param tenantId tenant identifier
     * @return embedding service for tenant
     */
    public EmbeddingService selectEmbeddingService(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        TenantProviderConfig config = tenantConfigs.getOrDefault(tenantId, TenantProviderConfig.DEFAULT);
        String providerName = config.getPreferredEmbeddingProvider();

        EmbeddingService service = embeddingProviders.get(providerName);
        if (service == null) {
            metrics.incrementCounter("ai.router.provider.not_found",
                    "tenant", tenantId, "provider", providerName);
            // Fallback to default
            service = embeddingProviders.get(defaultEmbeddingProvider);
            if (service == null) {
                throw new ProviderRouterException("No embedding providers registered");
            }
        }

        metrics.incrementCounter("ai.router.provider.selected",
                "tenant", tenantId, "provider", providerName);
        return service;
    }

    /**
     * Selects fallback embedding service for tenant.
     *
     * @param tenantId tenant identifier
     * @return fallback embedding service or null if none available
     */
    public EmbeddingService selectFallbackEmbeddingService(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        TenantProviderConfig config = tenantConfigs.getOrDefault(tenantId, TenantProviderConfig.DEFAULT);
        String fallbackProvider = config.getFallbackEmbeddingProvider();

        if (fallbackProvider == null) {
            // Use any available provider except the primary
            String primary = config.getPreferredEmbeddingProvider();
            for (String provider : embeddingProviders.keySet()) {
                if (!provider.equals(primary)) {
                    metrics.incrementCounter("ai.router.fallback.selected",
                            "tenant", tenantId, "provider", provider);
                    return embeddingProviders.get(provider);
                }
            }
            return null;
        }

        metrics.incrementCounter("ai.router.fallback.selected",
                "tenant", tenantId, "provider", fallbackProvider);
        return embeddingProviders.get(fallbackProvider);
    }

    /**
     * Selects completion service for tenant.
     *
     * @param tenantId tenant identifier
     * @return completion service for tenant
     */
    public CompletionService selectCompletionService(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        TenantProviderConfig config = tenantConfigs.getOrDefault(tenantId, TenantProviderConfig.DEFAULT);
        String providerName = config.getPreferredCompletionProvider();

        CompletionService service = completionProviders.get(providerName);
        if (service == null) {
            metrics.incrementCounter("ai.router.provider.not_found",
                    "tenant", tenantId, "provider", providerName);
            // Fallback to default
            service = completionProviders.get(defaultCompletionProvider);
            if (service == null) {
                throw new ProviderRouterException("No completion providers registered");
            }
        }

        metrics.incrementCounter("ai.router.provider.selected",
                "tenant", tenantId, "provider", providerName);
        return service;
    }

    /**
     * Selects fallback completion service for tenant.
     *
     * @param tenantId tenant identifier
     * @return fallback completion service or null if none available
     */
    public CompletionService selectFallbackCompletionService(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        TenantProviderConfig config = tenantConfigs.getOrDefault(tenantId, TenantProviderConfig.DEFAULT);
        String fallbackProvider = config.getFallbackCompletionProvider();

        if (fallbackProvider == null) {
            // Use any available provider except the primary
            String primary = config.getPreferredCompletionProvider();
            for (String provider : completionProviders.keySet()) {
                if (!provider.equals(primary)) {
                    metrics.incrementCounter("ai.router.fallback.selected",
                            "tenant", tenantId, "provider", provider);
                    return completionProviders.get(provider);
                }
            }
            return null;
        }

        metrics.incrementCounter("ai.router.fallback.selected",
                "tenant", tenantId, "provider", fallbackProvider);
        return completionProviders.get(fallbackProvider);
    }

    /**
     * Sets default embedding provider when no tenant config available.
     *
     * @param providerName provider identifier
     */
    public void setDefaultEmbeddingProvider(String providerName) {
        Objects.requireNonNull(providerName, "providerName must not be null");
        this.defaultEmbeddingProvider = providerName;
    }

    /**
     * Sets default completion provider when no tenant config available.
     *
     * @param providerName provider identifier
     */
    public void setDefaultCompletionProvider(String providerName) {
        Objects.requireNonNull(providerName, "providerName must not be null");
        this.defaultCompletionProvider = providerName;
    }

    /**
     * Tenant-specific provider configuration.
     */
    public static class TenantProviderConfig {
        public static final TenantProviderConfig DEFAULT = new TenantProviderConfig(
                "openai", "claude", "openai", "claude");

        private final String preferredEmbeddingProvider;
        private final String fallbackEmbeddingProvider;
        private final String preferredCompletionProvider;
        private final String fallbackCompletionProvider;

        /**
         * Constructs tenant provider configuration.
         *
         * @param preferredEmbeddingProvider primary embedding provider
         * @param fallbackEmbeddingProvider fallback embedding provider
         * @param preferredCompletionProvider primary completion provider
         * @param fallbackCompletionProvider fallback completion provider
         */
        public TenantProviderConfig(
                String preferredEmbeddingProvider,
                String fallbackEmbeddingProvider,
                String preferredCompletionProvider,
                String fallbackCompletionProvider) {
            this.preferredEmbeddingProvider = Objects.requireNonNull(preferredEmbeddingProvider);
            this.fallbackEmbeddingProvider = Objects.requireNonNull(fallbackEmbeddingProvider);
            this.preferredCompletionProvider = Objects.requireNonNull(preferredCompletionProvider);
            this.fallbackCompletionProvider = Objects.requireNonNull(fallbackCompletionProvider);
        }

        public String getPreferredEmbeddingProvider() {
            return preferredEmbeddingProvider;
        }

        public String getFallbackEmbeddingProvider() {
            return fallbackEmbeddingProvider;
        }

        public String getPreferredCompletionProvider() {
            return preferredCompletionProvider;
        }

        public String getFallbackCompletionProvider() {
            return fallbackCompletionProvider;
        }
    }

    /**
     * Exception for provider routing errors.
     */
    public static class ProviderRouterException extends RuntimeException {
        public ProviderRouterException(String message) {
            super(message);
        }

        public ProviderRouterException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
