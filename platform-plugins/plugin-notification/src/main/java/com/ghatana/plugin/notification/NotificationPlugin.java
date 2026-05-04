package com.ghatana.plugin.notification;

import com.ghatana.platform.plugin.Plugin;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.Map;

/**
 * Notification Plugin - Durable notification delivery with retry and dead-letter queue.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Asynchronous notification delivery via event bus</li>
 *   <li>Delivery state tracking (pending, delivered, failed)</li>
 *   <li>Configurable retry policy with exponential backoff</li>
 *   <li>Dead-letter queue for permanently failed notifications</li>
 *   <li>Template-based notification rendering</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose Notification plugin interface for reliable delivery
 * @doc.layer platform
 * @doc.pattern Plugin
 * @since 1.0.0
 */
public interface NotificationPlugin extends Plugin {

    /**
     * Dispatches a notification to the specified recipient.
     *
     * <p>The notification is queued for asynchronous delivery. The returned promise
     * resolves with the notification ID once the notification is queued, not when
     * it is delivered. Use {@link #getDeliveryStatus} to track delivery state.</p>
     *
     * @param recipientId the recipient user or contact identifier
     * @param template the notification template key (e.g., "dmos.campaign.launched")
     * @param attributes template merge attributes
     * @return Promise resolving to the notification ID
     */
    Promise<String> dispatch(String recipientId, String template, Map<String, String> attributes);

    /**
     * Gets the delivery status of a notification.
     *
     * @param notificationId the notification ID
     * @return Promise resolving to the delivery status
     */
    Promise<DeliveryStatus> getDeliveryStatus(String notificationId);

    /**
     * Retries a failed notification.
     *
     * <p>This method resets the retry count and re-queues the notification for delivery.
     * If the notification has exceeded the maximum retry limit, it will be moved to
     * the dead-letter queue.</p>
     *
     * @param notificationId the notification ID
     * @return Promise resolving when retry is scheduled
     */
    Promise<Void> retry(String notificationId);

    /**
     * Lists notifications in the dead-letter queue.
     *
     * @param limit maximum number of notifications to return
     * @param offset offset for pagination
     * @return Promise resolving to the list of dead-lettered notifications
     */
    Promise<java.util.List<DeadLetterEntry>> listDeadLetterQueue(int limit, int offset);

    /**
     * Reprocesses a dead-lettered notification.
     *
     * <p>This moves the notification back to the pending queue for retry.
     * Use with caution for notifications that may have already been delivered.</p>
     *
     * @param notificationId the notification ID
     * @return Promise resolving when reprocessing is scheduled
     */
    Promise<Void> reprocessDeadLetter(String notificationId);

    /**
     * Delivery status.
     */
    enum DeliveryState {
        PENDING,
        DELIVERED,
        FAILED,
        DEAD_LETTERED
    }

    /**
     * Delivery status record.
     */
    record DeliveryStatus(
        String notificationId,
        String recipientId,
        String template,
        DeliveryState state,
        int attemptCount,
        Instant lastAttemptAt,
        String lastError,
        Instant createdAt
    ) {}

    /**
     * Dead-letter queue entry.
     */
    record DeadLetterEntry(
        String notificationId,
        String recipientId,
        String template,
        Map<String, String> attributes,
        int attemptCount,
        String failureReason,
        Instant deadLetteredAt
    ) {}
}
