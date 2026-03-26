package com.ghatana.datacloud.entity.webhook;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain model for webhook event.
 *
 * <p><b>Purpose</b><br>
 * Represents an entity or collection event that has occurred and should be
 * delivered to webhooks. Contains event metadata (type, resource ID, timestamp)
 * and serialized event payload for delivery to subscribers.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * WebhookEvent event = new WebhookEvent(
 *     UUID.randomUUID(),
 *     "tenant-123",
 *     WebhookEventType.ENTITY_CREATED,
 *     "entity-456",
 *     "{\"id\":\"entity-456\",\"name\":\"Product\"}",
 *     Instant.now()
 * );
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * - Domain model representing business event
 * - Immutable value object
 * - Used to queue and persist events for webhook delivery
 * - Supports audit trail of what occurred
 *
 * <p><b>Thread Safety</b><br>
 * Immutable (all fields final). Safe to share across threads.
 *
 * @see WebhookEventType
 * @see Webhook
 * @doc.type class
 * @doc.purpose Webhook event domain model
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public class WebhookEvent {

    private final UUID id;
    private final String tenantId;
    private final WebhookEventType eventType;
    private final String resourceId;
    private final String payload;
    private final Instant createdAt;

    /**
     * Create webhook event.
     *
     * @param id event ID (unique)
     * @param tenantId tenant identifier
     * @param eventType type of event
     * @param resourceId ID of resource (entity or collection)
     * @param payload JSON payload with event data
     * @param createdAt event creation timestamp
     */
    public WebhookEvent(
            UUID id,
            String tenantId,
            WebhookEventType eventType,
            String resourceId,
            String payload,
            Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId cannot be null");
        this.eventType = Objects.requireNonNull(eventType, "eventType cannot be null");
        this.resourceId = Objects.requireNonNull(resourceId, "resourceId cannot be null");
        this.payload = Objects.requireNonNull(payload, "payload cannot be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");

        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId cannot be blank");
        }
        if (resourceId.isBlank()) {
            throw new IllegalArgumentException("resourceId cannot be blank");
        }
        if (payload.isBlank()) {
            throw new IllegalArgumentException("payload cannot be blank");
        }
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

    public String getResourceId() {
        return resourceId;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return "WebhookEvent{" +
                "id=" + id +
                ", tenantId='" + tenantId + '\'' +
                ", eventType=" + eventType +
                ", resourceId='" + resourceId + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
