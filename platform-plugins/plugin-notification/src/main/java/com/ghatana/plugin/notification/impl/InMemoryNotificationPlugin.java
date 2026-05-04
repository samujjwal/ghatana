package com.ghatana.plugin.notification.impl;

import com.ghatana.plugin.notification.NotificationPlugin;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory implementation of {@link NotificationPlugin}.
 *
 * <p>This implementation stores notifications in memory and simulates delivery.
 * It is suitable for local development and testing but not for production use
 * as notifications are lost on restart.</p>
 *
 * <p>Variant: {@code in-memory}</p>
 * <p>Durability: {@code non-durable}</p>
 *
 * @doc.type class
 * @doc.purpose In-memory notification plugin for development and testing
 * @doc.layer platform
 * @doc.pattern Plugin
 */
public final class InMemoryNotificationPlugin implements NotificationPlugin {

    private static final Logger LOG = LoggerFactory.getLogger(InMemoryNotificationPlugin.class);
    private static final int MAX_RETRY_ATTEMPTS = 3;

    private final ConcurrentHashMap<String, DeliveryStatus> notifications = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<DeadLetterEntry> deadLetterQueue = new CopyOnWriteArrayList<>();
    private final AtomicInteger deliveryCounter = new AtomicInteger(0);

    @Override
    public void start() {
        LOG.info("[NotificationPlugin] In-memory notification plugin started");
    }

    @Override
    public void stop() {
        LOG.info("[NotificationPlugin] In-memory notification plugin stopped");
        notifications.clear();
        deadLetterQueue.clear();
    }

    @Override
    public Promise<String> dispatch(String recipientId, String template, Map<String, String> attributes) {
        String notificationId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        DeliveryStatus status = new DeliveryStatus(
            notificationId,
            recipientId,
            template,
            DeliveryState.PENDING,
            0,
            now,
            null,
            now
        );

        notifications.put(notificationId, status);

        // Simulate async delivery
        simulateDelivery(notificationId, status);

        LOG.info("[NotificationPlugin] Notification queued: id={}, recipient={}, template={}",
            notificationId, recipientId, template);

        return Promise.of(notificationId);
    }

    @Override
    public Promise<DeliveryStatus> getDeliveryStatus(String notificationId) {
        DeliveryStatus status = notifications.get(notificationId);
        if (status == null) {
            return Promise.ofException(new IllegalArgumentException("Notification not found: " + notificationId));
        }
        return Promise.of(status);
    }

    @Override
    public Promise<Void> retry(String notificationId) {
        DeliveryStatus status = notifications.get(notificationId);
        if (status == null) {
            return Promise.ofException(new IllegalArgumentException("Notification not found: " + notificationId));
        }

        if (status.state() == DeliveryState.DEAD_LETTERED) {
            // Remove from DLQ and retry
            deadLetterQueue.removeIf(entry -> entry.notificationId().equals(notificationId));
        }

        DeliveryStatus newStatus = new DeliveryStatus(
            notificationId,
            status.recipientId(),
            status.template(),
            DeliveryState.PENDING,
            0, // Reset attempt count
            Instant.now(),
            null,
            status.createdAt()
        );

        notifications.put(notificationId, newStatus);
        simulateDelivery(notificationId, newStatus);

        LOG.info("[NotificationPlugin] Notification retried: id={}", notificationId);
        return Promise.of(null);
    }

    @Override
    public Promise<List<DeadLetterEntry>> listDeadLetterQueue(int limit, int offset) {
        List<DeadLetterEntry> result = deadLetterQueue.stream()
            .skip(offset)
            .limit(limit)
            .toList();
        return Promise.of(result);
    }

    @Override
    public Promise<Void> reprocessDeadLetter(String notificationId) {
        return retry(notificationId);
    }

    private void simulateDelivery(String notificationId, DeliveryStatus status) {
        // In a real implementation, this would publish to an event bus
        // For now, we simulate delivery with a simple counter
        int deliveryNum = deliveryCounter.incrementAndGet();
        
        // Simulate 90% success rate
        if (deliveryNum % 10 != 0) {
            // Success
            DeliveryStatus delivered = new DeliveryStatus(
                notificationId,
                status.recipientId(),
                status.template(),
                DeliveryState.DELIVERED,
                1,
                Instant.now(),
                null,
                status.createdAt()
            );
            notifications.put(notificationId, delivered);
            LOG.debug("[NotificationPlugin] Notification delivered: id={}", notificationId);
        } else {
            // Failure - move to DLQ after max retries
            DeliveryStatus failed = new DeliveryStatus(
                notificationId,
                status.recipientId(),
                status.template(),
                DeliveryState.DEAD_LETTERED,
                MAX_RETRY_ATTEMPTS,
                Instant.now(),
                "Simulated delivery failure",
                status.createdAt()
            );
            notifications.put(notificationId, failed);

            DeadLetterEntry dlqEntry = new DeadLetterEntry(
                notificationId,
                status.recipientId(),
                status.template(),
                Map.of(), // attributes not stored in in-memory version
                MAX_RETRY_ATTEMPTS,
                "Simulated delivery failure",
                Instant.now()
            );
            deadLetterQueue.add(dlqEntry);
            LOG.warn("[NotificationPlugin] Notification dead-lettered: id={}", notificationId);
        }
    }
}
