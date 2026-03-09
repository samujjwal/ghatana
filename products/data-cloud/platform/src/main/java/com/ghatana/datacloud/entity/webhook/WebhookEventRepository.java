package com.ghatana.datacloud.entity.webhook;

import io.activej.promise.Promise;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port interface for webhook event repository.
 *
 * <p><b>Purpose</b><br>
 * Defines contract for persisting webhook events and delivery attempts.
 * Enables querying event history, delivery status, and retry tracking.
 * All operations are Promise-based for non-blocking execution.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * Promise<WebhookEvent> saved = repository.saveEvent(event);
 * Promise<List<WebhookDelivery>> deliveries = repository.getDeliveries(
 *     "tenant-123",
 *     webhookId,
 *     limit
 * );
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * - Port interface in hexagonal architecture
 * - Adapter implementations in infrastructure layer
 * - Multi-tenant scoped operations
 * - Supports audit and forensics
 *
 * <p><b>Thread Safety</b><br>
 * Implementations must be thread-safe. Callers use Promise composition.
 *
 * @see WebhookEvent
 * @see WebhookDelivery
 * @doc.type interface
 * @doc.purpose Webhook event repository port interface
 * @doc.layer domain
 * @doc.pattern Port (Hexagonal Architecture)
 */
public interface WebhookEventRepository {

    /**
     * Save webhook event (transaction point).
     *
     * @param event webhook event to save
     * @return Promise of saved event with ID
     */
    Promise<WebhookEvent> saveEvent(WebhookEvent event);

    /**
     * Find event by ID.
     *
     * @param id event ID
     * @param tenantId tenant identifier
     * @return Promise of Optional event
     */
    Promise<Optional<WebhookEvent>> findEventById(UUID id, String tenantId);

    /**
     * Save webhook delivery attempt.
     *
     * @param delivery delivery attempt record
     * @return Promise of saved delivery
     */
    Promise<WebhookDelivery> saveDelivery(WebhookDelivery delivery);

    /**
     * Get delivery history for webhook (most recent first).
     *
     * @param tenantId tenant identifier
     * @param webhookId webhook ID
     * @param limit maximum records to return
     * @return Promise of delivery list
     */
    Promise<List<WebhookDelivery>> getDeliveries(String tenantId, UUID webhookId, int limit);

    /**
     * Get pending events for a webhook (not yet delivered successfully).
     *
     * @param tenantId tenant identifier
     * @param webhookId webhook ID
     * @param limit maximum records
     * @return Promise of pending events
     */
    Promise<List<WebhookEvent>> getPendingEvents(String tenantId, UUID webhookId, int limit);

    /**
     * Find all failed deliveries (for retry processing).
     *
     * @param tenantId tenant identifier
     * @param maxAttempts filter by attempt number
     * @return Promise of failed delivery list
     */
    Promise<List<WebhookDelivery>> findFailedDeliveries(String tenantId, int maxAttempts);
}
