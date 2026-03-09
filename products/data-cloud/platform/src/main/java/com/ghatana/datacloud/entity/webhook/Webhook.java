package com.ghatana.datacloud.entity.webhook;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain model for webhook subscription.
 *
 * <p>
 * <b>Purpose</b><br>
 * Represents a webhook subscription configured by a tenant to receive
 * HTTP POST callbacks when specified events occur. Includes event filtering,
 * URL configuration, retry policies, and enable/disable toggle.
 *
 * <p>
 * <b>Usage</b><br>
 * 
 * <pre>{@code
 * Webhook webhook = new Webhook(
 *         UUID.randomUUID(),
 *         "tenant-123",
 *         WebhookEventType.ENTITY_CREATED,
 *         "https://example.com/webhook",
 *         "prod",
 *         true,
 *         3,
 *         5000,
 *         120);
 *
 * // Disable webhook
 * Webhook disabled = webhook.withEnabled(false);
 * }</pre>
 *
 * <p>
 * <b>Retry Policy</b><br>
 * - maxRetries: Maximum delivery attempts (1-10)
 * - retryDelayMs: Delay between retries in milliseconds
 * - deliveryTimeoutSeconds: Timeout for each delivery attempt (5-300s)
 *
 * <p>
 * <b>Architecture Role</b><br>
 * - Domain model for webhook configuration
 * - Immutable value object pattern
 * - Multi-tenant support (tenantId field)
 * - Enables auditing and versioning via repository
 *
 * <p>
 * <b>Thread Safety</b><br>
 * Immutable (all fields final). Safe to share across threads.
 *
 * @see WebhookEventType
 * @see WebhookEvent
 * @doc.type class
 * @doc.purpose Webhook subscription domain model
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public class Webhook {

    private final UUID id;
    private final String tenantId;
    private final WebhookEventType eventType;
    private final String url;
    private final String environment;
    private final boolean enabled;
    private final int maxRetries;
    private final int retryDelayMs;
    private final int deliveryTimeoutSeconds;
    private final Instant createdAt;
    private final Instant updatedAt;

    /**
     * Create webhook subscription.
     *
     * @param id                     webhook ID (unique)
     * @param tenantId               tenant identifier
     * @param eventType              event type to subscribe to
     * @param url                    HTTPS endpoint URL for delivery
     * @param environment            environment label (e.g., "prod", "staging")
     * @param enabled                whether webhook is currently active
     * @param maxRetries             maximum number of delivery attempts (1-10)
     * @param retryDelayMs           delay between retries in milliseconds
     *                               (1000-60000)
     * @param deliveryTimeoutSeconds timeout per attempt in seconds (5-300)
     */
    public Webhook(
            UUID id,
            String tenantId,
            WebhookEventType eventType,
            String url,
            String environment,
            boolean enabled,
            int maxRetries,
            int retryDelayMs,
            int deliveryTimeoutSeconds) {
        this(
                id,
                tenantId,
                eventType,
                url,
                environment,
                enabled,
                maxRetries,
                retryDelayMs,
                deliveryTimeoutSeconds,
                Instant.now());
    }

    // Internal constructor that receives a single timestamp and applies it
    // to both createdAt and updatedAt to guarantee they are identical on creation.
    private Webhook(
            UUID id,
            String tenantId,
            WebhookEventType eventType,
            String url,
            String environment,
            boolean enabled,
            int maxRetries,
            int retryDelayMs,
            int deliveryTimeoutSeconds,
            Instant now) {
        this(
                id,
                tenantId,
                eventType,
                url,
                environment,
                enabled,
                maxRetries,
                retryDelayMs,
                deliveryTimeoutSeconds,
                now,
                now);
    }

    /**
     * Create webhook with timestamps.
     *
     * @param id                     webhook ID
     * @param tenantId               tenant identifier
     * @param eventType              event type to subscribe to
     * @param url                    HTTPS endpoint URL
     * @param environment            environment label
     * @param enabled                whether active
     * @param maxRetries             maximum delivery attempts
     * @param retryDelayMs           retry delay in milliseconds
     * @param deliveryTimeoutSeconds delivery timeout in seconds
     * @param createdAt              creation timestamp
     * @param updatedAt              last update timestamp
     */
    public Webhook(
            UUID id,
            String tenantId,
            WebhookEventType eventType,
            String url,
            String environment,
            boolean enabled,
            int maxRetries,
            int retryDelayMs,
            int deliveryTimeoutSeconds,
            Instant createdAt,
            Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId cannot be null");
        this.eventType = Objects.requireNonNull(eventType, "eventType cannot be null");
        this.url = Objects.requireNonNull(url, "url cannot be null");
        this.environment = Objects.requireNonNull(environment, "environment cannot be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt cannot be null");

        // Validation
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId cannot be blank");
        }
        if (url.isBlank()) {
            throw new IllegalArgumentException("url cannot be blank");
        }
        if (!url.startsWith("https://")) {
            throw new IllegalArgumentException("url must use HTTPS protocol (security requirement)");
        }
        if (environment.isBlank()) {
            throw new IllegalArgumentException("environment cannot be blank");
        }
        if (maxRetries < 1 || maxRetries > 10) {
            throw new IllegalArgumentException("maxRetries must be between 1 and 10");
        }
        if (retryDelayMs < 1000 || retryDelayMs > 60000) {
            throw new IllegalArgumentException("retryDelayMs must be between 1000 and 60000");
        }
        if (deliveryTimeoutSeconds < 5 || deliveryTimeoutSeconds > 300) {
            throw new IllegalArgumentException("deliveryTimeoutSeconds must be between 5 and 300");
        }

        this.enabled = enabled;
        this.maxRetries = maxRetries;
        this.retryDelayMs = retryDelayMs;
        this.deliveryTimeoutSeconds = deliveryTimeoutSeconds;
    }

    /**
     * Create webhook with enabled/disabled status change.
     *
     * @param enabled new enabled status
     * @return new Webhook with updated status
     */
    public Webhook withEnabled(boolean enabled) {
        return new Webhook(
                this.id,
                this.tenantId,
                this.eventType,
                this.url,
                this.environment,
                enabled,
                this.maxRetries,
                this.retryDelayMs,
                this.deliveryTimeoutSeconds,
                this.createdAt,
                Instant.now());
    }

    /**
     * Create webhook with updated URL.
     *
     * @param url new HTTPS URL
     * @return new Webhook with updated URL
     */
    public Webhook withUrl(String url) {
        Objects.requireNonNull(url, "url cannot be null");
        if (!url.startsWith("https://")) {
            throw new IllegalArgumentException("url must use HTTPS protocol");
        }
        return new Webhook(
                this.id,
                this.tenantId,
                this.eventType,
                url,
                this.environment,
                this.enabled,
                this.maxRetries,
                this.retryDelayMs,
                this.deliveryTimeoutSeconds,
                this.createdAt,
                Instant.now());
    }

    public UUID getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public WebhookEventType getEventType() {
        return eventType;
    }

    public String getUrl() {
        return url;
    }

    public String getEnvironment() {
        return environment;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public int getRetryDelayMs() {
        return retryDelayMs;
    }

    public int getDeliveryTimeoutSeconds() {
        return deliveryTimeoutSeconds;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String toString() {
        return "Webhook{" +
                "id=" + id +
                ", tenantId='" + tenantId + '\'' +
                ", eventType=" + eventType +
                ", url='" + url + '\'' +
                ", enabled=" + enabled +
                ", environment='" + environment + '\'' +
                '}';
    }
}
