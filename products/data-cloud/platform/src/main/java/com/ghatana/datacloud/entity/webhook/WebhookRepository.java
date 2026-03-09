package com.ghatana.datacloud.entity.webhook;

import io.activej.promise.Promise;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port interface for webhook subscription repository.
 *
 * <p><b>Purpose</b><br>
 * Defines contract for persisting and querying webhook subscriptions.
 * Implementations may use relational DB, document stores, or other backends.
 * All operations are Promise-based for non-blocking execution.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * Promise<Webhook> created = repository.save(webhook);
 * Promise<List<Webhook>> subscriptions = repository.findByTenantAndEventType(
 *     "tenant-123",
 *     WebhookEventType.ENTITY_CREATED
 * );
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * - Port interface in hexagonal architecture
 * - Adapter implementations in infrastructure layer
 * - Enables multi-tenancy (all operations scoped by tenant)
 * - Supports audit trail via repository
 *
 * <p><b>Thread Safety</b><br>
 * Implementations must be thread-safe. Callers use Promise composition
 * for async operations.
 *
 * @see Webhook
 * @doc.type interface
 * @doc.purpose Webhook repository port interface
 * @doc.layer domain
 * @doc.pattern Port (Hexagonal Architecture)
 */
public interface WebhookRepository {

    /**
     * Save webhook subscription (create or update).
     *
     * @param webhook webhook to save
     * @return Promise of saved webhook with ID
     */
    Promise<Webhook> save(Webhook webhook);

    /**
     * Find webhook by ID.
     *
     * @param id webhook ID
     * @param tenantId tenant identifier (for isolation)
     * @return Promise of Optional webhook
     */
    Promise<Optional<Webhook>> findById(UUID id, String tenantId);

    /**
     * Find all webhooks for tenant and event type.
     *
     * @param tenantId tenant identifier
     * @param eventType event type filter
     * @return Promise of webhook list
     */
    Promise<List<Webhook>> findByTenantAndEventType(String tenantId, WebhookEventType eventType);

    /**
     * Find all enabled webhooks for tenant.
     *
     * @param tenantId tenant identifier
     * @return Promise of list of enabled webhooks
     */
    Promise<List<Webhook>> findEnabledByTenant(String tenantId);

    /**
     * Delete webhook subscription.
     *
     * @param id webhook ID
     * @param tenantId tenant identifier
     * @return Promise of void
     */
    Promise<Void> deleteById(UUID id, String tenantId);

    /**
     * Find all webhooks for tenant (no filtering).
     *
     * @param tenantId tenant identifier
     * @return Promise of all webhooks for tenant
     */
    Promise<List<Webhook>> findByTenant(String tenantId);
}
