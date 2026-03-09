package com.ghatana.datacloud.application.webhook;

import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.datacloud.entity.webhook.Webhook;
import com.ghatana.datacloud.entity.webhook.WebhookEventType;
import com.ghatana.datacloud.entity.webhook.WebhookRepository;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service for webhook subscription management.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides business logic for creating, updating, querying, and deleting
 * webhook subscriptions. Enforces tenant isolation, validates webhook
 * configuration, and coordinates with infrastructure for persistence.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * WebhookService service = new WebhookService(repository, metrics);
 *
 * // Register webhook
 * Promise<Webhook> registered = service.registerWebhook(
 *     "tenant-123",
 *     WebhookEventType.ENTITY_CREATED,
 *     "https://api.example.com/webhook",
 *     "prod",
 *     3,      // max retries
 *     5000,   // retry delay ms
 *     120     // timeout seconds
 * );
 *
 * // List subscriptions
 * Promise<List<Webhook>> webhooks = service.listWebhooks("tenant-123");
 *
 * // Disable webhook
 * Promise<Webhook> disabled = service.updateEnabled(webhookId, "tenant-123", false);
 * }</pre>
 *
 * <p>
 * <b>Features</b><br>
 * - Register webhooks with configurable retry policies - Query subscriptions by
 * tenant, event type, or status - Enable/disable webhooks without deletion -
 * Update webhook configuration (URL, timeouts) - Track metrics for subscription
 * operations - Enforce HTTPS URLs (security requirement)
 *
 * <p>
 * <b>Multi-Tenancy</b><br>
 * All operations scoped by tenant ID. Webhooks from different tenants are
 * isolated.
 *
 * <p>
 * <b>Thread Safety</b><br>
 * Stateless service - thread-safe. All state delegated to repository.
 *
 * <p>
 * <b>Architecture Role</b><br>
 * - Application service in hexagonal architecture - Uses WebhookRepository port
 * (infrastructure agnostic) - Uses MetricsCollector (core/observability) -
 * Returns Promise-based async operations
 *
 * @see Webhook
 * @see WebhookRepository
 * @doc.type class
 * @doc.purpose Webhook subscription management service
 * @doc.layer application
 * @doc.pattern Service (Application Layer)
 */
public class WebhookService {

    private static final Logger logger = LoggerFactory.getLogger(WebhookService.class);

    private final WebhookRepository webhookRepository;
    private final MetricsCollector metrics;

    /**
     * Create webhook service.
     *
     * @param webhookRepository repository for webhook persistence
     * @param metrics metrics collector for observability
     */
    public WebhookService(WebhookRepository webhookRepository, MetricsCollector metrics) {
        this.webhookRepository = Objects.requireNonNull(webhookRepository, "WebhookRepository cannot be null");
        this.metrics = Objects.requireNonNull(metrics, "MetricsCollector cannot be null");
        logger.info("WebhookService initialized");
    }

    /**
     * Register new webhook subscription.
     *
     * @param tenantId tenant identifier
     * @param eventType type of events to subscribe to
     * @param url HTTPS endpoint URL for delivery
     * @param environment environment label (e.g., "prod", "staging")
     * @param maxRetries maximum delivery attempts (1-10)
     * @param retryDelayMs delay between retries in milliseconds (1000-60000)
     * @param deliveryTimeoutSeconds timeout per attempt in seconds (5-300)
     * @return Promise of created webhook
     */
    public Promise<Webhook> registerWebhook(
            String tenantId,
            WebhookEventType eventType,
            String url,
            String environment,
            int maxRetries,
            int retryDelayMs,
            int deliveryTimeoutSeconds) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(eventType, "eventType cannot be null");

        long startTime = System.currentTimeMillis();

        try {
            Webhook webhook = new Webhook(
                    UUID.randomUUID(),
                    tenantId,
                    eventType,
                    url,
                    environment,
                    true, // enabled by default
                    maxRetries,
                    retryDelayMs,
                    deliveryTimeoutSeconds);

            return webhookRepository.save(webhook)
                    .map(saved -> {
                        long duration = System.currentTimeMillis() - startTime;
                        metrics.incrementCounter("webhook.register.success",
                                "tenant", tenantId,
                                "event_type", eventType.name());
                        metrics.recordTimer("webhook.register.duration", duration);
                        logger.info("Webhook registered: {} for tenant {}", saved.getId(), tenantId);
                        return saved;
                    })
                    .whenException(error -> {
                        long duration = System.currentTimeMillis() - startTime;
                        metrics.incrementCounter("webhook.register.error",
                                "tenant", tenantId,
                                "error", error.getClass().getSimpleName());
                        logger.error("Failed to register webhook for tenant {}", tenantId, error);
                    });
        } catch (IllegalArgumentException e) {
            metrics.incrementCounter("webhook.register.error",
                    "tenant", tenantId,
                    "error", "VALIDATION");
            logger.warn("Invalid webhook configuration for tenant {}: {}", tenantId, e.getMessage());
            return Promise.ofException(e);
        }
    }

    /**
     * List all webhooks for tenant.
     *
     * @param tenantId tenant identifier
     * @return Promise of webhook list
     */
    public Promise<List<Webhook>> listWebhooks(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");

        return webhookRepository.findByTenant(tenantId)
                .map(webhooks -> {
                    metrics.incrementCounter("webhook.list",
                            "tenant", tenantId,
                            "count", String.valueOf(webhooks.size()));
                    logger.debug("Listed {} webhooks for tenant {}", webhooks.size(), tenantId);
                    return webhooks;
                });
    }

    /**
     * List enabled webhooks for tenant and event type.
     *
     * @param tenantId tenant identifier
     * @param eventType event type filter
     * @return Promise of webhook list
     */
    public Promise<List<Webhook>> listWebhooksByEventType(String tenantId, WebhookEventType eventType) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(eventType, "eventType cannot be null");

        return webhookRepository.findByTenantAndEventType(tenantId, eventType)
                .map(webhooks -> {
                    List<Webhook> enabled = webhooks.stream()
                            .filter(Webhook::isEnabled)
                            .toList();
                    logger.debug("Found {} enabled webhooks for tenant {} and event type {}",
                            enabled.size(), tenantId, eventType);
                    return enabled;
                });
    }

    /**
     * Get webhook by ID.
     *
     * @param id webhook ID
     * @param tenantId tenant identifier (for isolation)
     * @return Promise of Optional webhook
     */
    public Promise<Optional<Webhook>> getWebhook(UUID id, String tenantId) {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(tenantId, "tenantId cannot be null");

        return webhookRepository.findById(id, tenantId)
                .map(webhook -> {
                    if (webhook.isPresent()) {
                        logger.debug("Retrieved webhook {} for tenant {}", id, tenantId);
                    } else {
                        logger.debug("Webhook {} not found for tenant {}", id, tenantId);
                    }
                    return webhook;
                });
    }

    /**
     * Update webhook enabled/disabled status.
     *
     * @param id webhook ID
     * @param tenantId tenant identifier
     * @param enabled new enabled status
     * @return Promise of updated webhook
     */
    public Promise<Webhook> updateEnabled(UUID id, String tenantId, boolean enabled) {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(tenantId, "tenantId cannot be null");

        return webhookRepository.findById(id, tenantId)
                .then(optionalWebhook -> {
                    if (optionalWebhook.isEmpty()) {
                        return Promise.ofException(
                                new IllegalArgumentException("Webhook not found: " + id));
                    }

                    Webhook webhook = optionalWebhook.get();
                    Webhook updated = webhook.withEnabled(enabled);
                    return webhookRepository.save(updated);
                })
                .map(updated -> {
                    metrics.incrementCounter("webhook.update",
                            "tenant", tenantId,
                            "enabled", String.valueOf(enabled));
                    logger.info("Updated webhook {} enabled status to {} for tenant {}",
                            id, enabled, tenantId);
                    return updated;
                })
                .whenException(error -> {
                    metrics.incrementCounter("webhook.update.error",
                            "tenant", tenantId,
                            "error", error.getClass().getSimpleName());
                    logger.error("Failed to update webhook {} for tenant {}", id, tenantId, error);

                });
    }

    /**
     * Update webhook URL.
     *
     * @param id webhook ID
     * @param tenantId tenant identifier
     * @param newUrl new HTTPS URL
     * @return Promise of updated webhook
     */
    public Promise<Webhook> updateUrl(UUID id, String tenantId, String newUrl) {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(newUrl, "newUrl cannot be null");

        return webhookRepository.findById(id, tenantId)
                .then(optionalWebhook -> {
                    if (optionalWebhook.isEmpty()) {
                        return Promise.ofException(
                                new IllegalArgumentException("Webhook not found: " + id));
                    }

                    Webhook webhook = optionalWebhook.get();
                    Webhook updated = webhook.withUrl(newUrl);
                    return webhookRepository.save(updated);
                })
                .map(updated -> {
                    metrics.incrementCounter("webhook.url_update",
                            "tenant", tenantId);
                    logger.info("Updated webhook {} URL for tenant {}", id, tenantId);
                    return updated;
                })
                .whenException(error -> {
                    metrics.incrementCounter("webhook.url_update.error",
                            "tenant", tenantId,
                            "error", error.getClass().getSimpleName());
                    logger.error("Failed to update webhook URL {} for tenant {}", id, tenantId, error);

                });
    }

    /**
     * Delete webhook subscription.
     *
     * @param id webhook ID
     * @param tenantId tenant identifier
     * @return Promise of void
     */
    public Promise<Void> deleteWebhook(UUID id, String tenantId) {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(tenantId, "tenantId cannot be null");

        return webhookRepository.deleteById(id, tenantId)
                .whenResult(() -> {
                    metrics.incrementCounter("webhook.delete",
                            "tenant", tenantId);
                    logger.info("Deleted webhook {} for tenant {}", id, tenantId);
                })
                .whenException(error -> {
                    metrics.incrementCounter("webhook.delete.error",
                            "tenant", tenantId,
                            "error", error.getClass().getSimpleName());
                    logger.error("Failed to delete webhook {} for tenant {}", id, tenantId, error);
                });
    }
}
