package com.ghatana.datacloud.entity.webhook;

/**
 * Enumeration of webhook event types.
 *
 * <p><b>Purpose</b><br>
 * Defines supported webhook event types that trigger webhook delivery.
 * Used to filter subscriptions and route events to registered webhooks.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * WebhookEventType eventType = WebhookEventType.ENTITY_CREATED;
 * Webhook webhook = Webhook.builder()
 *     .eventType(eventType)
 *     .url("https://example.com/webhook")
 *     .build();
 * }</pre>
 *
 * <p><b>Supported Events</b><br>
 * - ENTITY_CREATED: Entity added to collection
 * - ENTITY_UPDATED: Entity modified in collection
 * - ENTITY_DELETED: Entity removed from collection
 * - COLLECTION_CREATED: Collection created
 * - COLLECTION_UPDATED: Collection metadata changed
 * - COLLECTION_DELETED: Collection removed
 *
 * @see Webhook
 * @see WebhookEvent
 * @doc.type enum
 * @doc.purpose Event type enumeration for webhook triggering
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public enum WebhookEventType {
    /**
     * Entity has been created in a collection.
     */
    ENTITY_CREATED("entity.created"),

    /**
     * Entity has been updated in a collection.
     */
    ENTITY_UPDATED("entity.updated"),

    /**
     * Entity has been deleted from a collection.
     */
    ENTITY_DELETED("entity.deleted"),

    /**
     * Collection has been created.
     */
    COLLECTION_CREATED("collection.created"),

    /**
     * Collection metadata has been updated.
     */
    COLLECTION_UPDATED("collection.updated"),

    /**
     * Collection has been deleted.
     */
    COLLECTION_DELETED("collection.deleted");

    private final String eventName;

    WebhookEventType(String eventName) {
        this.eventName = eventName;
    }

    /**
     * Get the event name used in webhook payloads.
     *
     * @return event name string (e.g., "entity.created")
     */
    public String getEventName() {
        return eventName;
    }

    /**
     * Parse event type from event name string.
     *
     * @param eventName event name (e.g., "entity.created")
     * @return WebhookEventType enum value
     * @throws IllegalArgumentException if event name not recognized
     */
    public static WebhookEventType fromEventName(String eventName) {
        for (WebhookEventType type : values()) {
            if (type.eventName.equals(eventName)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown event name: " + eventName);
    }
}
