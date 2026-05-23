package com.ghatana.digitalmarketing.application.notification;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.notification.Notification;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for notification delivery with retry and dead-letter queue.
 *
 * @doc.type class
 * @doc.purpose Defines operations for managing notification delivery (DMOS-F2-007)
 * @doc.layer product
 * @doc.pattern Service
 */
public interface NotificationService {

    /**
     * Queue a notification for delivery.
     *
     * @param ctx     operation context
     * @param request notification request
     * @return the queued notification
     */
    Promise<Notification> queueNotification(DmOperationContext ctx, QueueNotificationRequest request);

    /**
     * Check delivery status of a notification.
     *
     * @param ctx             operation context
     * @param notificationId notification ID
     * @return delivery status
     */
    Promise<NotificationDeliveryStatus> checkDeliveryStatus(DmOperationContext ctx, String notificationId);

    /**
     * Replay a DLQ message.
     *
     * @param ctx             operation context
     * @param notificationId notification ID
     * @return the replayed notification
     */
    Promise<Notification> replayDlqMessage(DmOperationContext ctx, String notificationId);

    /**
     * List DLQ messages.
     *
     * @param ctx   operation context
     * @param limit max results
     * @return list of DLQ messages
     */
    Promise<List<Notification>> listDlqMessages(DmOperationContext ctx, int limit);

    /**
     * Mark notification as delivered.
     *
     * @param ctx             operation context
     * @param notificationId notification ID
     * @return updated notification
     */
    Promise<Notification> markDelivered(DmOperationContext ctx, String notificationId);

    /**
     * Mark notification as failed.
     *
     * @param ctx             operation context
     * @param notificationId notification ID
     * @param reason          failure reason
     * @return updated notification
     */
    Promise<Notification> markFailed(DmOperationContext ctx, String notificationId, String reason);

    // ── Request types ─────────────────────────────────────────────────────────

    record QueueNotificationRequest(
        String recipientId,
        String recipientType,
        String channel,
        String templateId,
        java.util.Map<String, String> templateData,
        int priority
    ) {
        public QueueNotificationRequest {
            // Validation logic
        }
    }

    record NotificationDeliveryStatus(
        String notificationId,
        Notification.DeliveryStatus status,
        int attemptCount,
        String lastAttemptAt,
        String nextRetryAt,
        String errorMessage
    ) {}
}
