package com.ghatana.digitalmarketing.application.notification;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.notification.Notification;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Implementation of NotificationService with in-memory storage.
 *
 * @doc.type class
 * @doc.purpose Provides notification delivery operations with retry and DLQ
 * @doc.layer product
 * @doc.pattern Service Implementation
 */
public class NotificationServiceImpl implements NotificationService {

    private static final int MAX_RETRIES = 3;
    private final ConcurrentMap<String, Notification> notifications = new ConcurrentHashMap<>();

    @Override
    public Promise<Notification> queueNotification(DmOperationContext ctx, QueueNotificationRequest request) {
        String notificationId = Notification.generateNotificationId();
        Notification notification = Notification.builder()
            .notificationId(notificationId)
            .tenantId(ctx.tenantId())
            .recipientId(request.recipientId())
            .recipientType(request.recipientType())
            .channel(request.channel())
            .templateId(request.templateId())
            .templateData(request.templateData())
            .status(Notification.DeliveryStatus.QUEUED)
            .attemptCount(0)
            .queuedAt(Instant.now())
            .createdBy(ctx.userId())
            .build();

        notifications.put(notificationId, notification);
        return Promise.of(notification);
    }

    @Override
    public Promise<NotificationDeliveryStatus> checkDeliveryStatus(DmOperationContext ctx, String notificationId) {
        Notification notification = notifications.get(notificationId);
        if (notification == null) {
            return Promise.ofException(new IllegalArgumentException("Notification not found: " + notificationId));
        }

        NotificationDeliveryStatus status = new NotificationDeliveryStatus(
            notification.notificationId(),
            notification.status(),
            notification.attemptCount(),
            notification.lastAttemptAt() != null ? notification.lastAttemptAt().toString() : null,
            notification.nextRetryAt() != null ? notification.nextRetryAt().toString() : null,
            notification.errorMessage()
        );

        return Promise.of(status);
    }

    @Override
    public Promise<Notification> replayDlqMessage(DmOperationContext ctx, String notificationId) {
        Notification notification = notifications.get(notificationId);
        if (notification == null) {
            return Promise.ofException(new IllegalArgumentException("Notification not found: " + notificationId));
        }

        notification.replay();

        Notification replayed = Notification.builder()
            .notificationId(notification.notificationId())
            .tenantId(notification.tenantId())
            .recipientId(notification.recipientId())
            .recipientType(notification.recipientType())
            .channel(notification.channel())
            .templateId(notification.templateId())
            .templateData(notification.templateData())
            .status(Notification.DeliveryStatus.QUEUED)
            .attemptCount(0)
            .queuedAt(Instant.now())
            .inDlq(false)
            .createdBy(notification.createdBy())
            .build();

        notifications.put(notificationId, replayed);
        return Promise.of(replayed);
    }

    @Override
    public Promise<List<Notification>> listDlqMessages(DmOperationContext ctx, int limit) {
        return Promise.of(
            notifications.values().stream()
                .filter(n -> n.inDlq())
                .limit(limit)
                .toList()
        );
    }

    @Override
    public Promise<Notification> markDelivered(DmOperationContext ctx, String notificationId) {
        Notification notification = notifications.get(notificationId);
        if (notification == null) {
            return Promise.ofException(new IllegalArgumentException("Notification not found: " + notificationId));
        }

        notification.markDelivered();

        Notification delivered = Notification.builder()
            .notificationId(notification.notificationId())
            .tenantId(notification.tenantId())
            .recipientId(notification.recipientId())
            .recipientType(notification.recipientType())
            .channel(notification.channel())
            .templateId(notification.templateId())
            .templateData(notification.templateData())
            .status(Notification.DeliveryStatus.DELIVERED)
            .attemptCount(notification.attemptCount())
            .queuedAt(notification.queuedAt())
            .lastAttemptAt(Instant.now())
            .createdBy(notification.createdBy())
            .build();

        notifications.put(notificationId, delivered);
        return Promise.of(delivered);
    }

    @Override
    public Promise<Notification> markFailed(DmOperationContext ctx, String notificationId, String reason) {
        Notification notification = notifications.get(notificationId);
        if (notification == null) {
            return Promise.ofException(new IllegalArgumentException("Notification not found: " + notificationId));
        }

        try {
            notification.markFailed(reason, MAX_RETRIES);

            Notification failed = Notification.builder()
                .notificationId(notification.notificationId())
                .tenantId(notification.tenantId())
                .recipientId(notification.recipientId())
                .recipientType(notification.recipientType())
                .channel(notification.channel())
                .templateId(notification.templateId())
                .templateData(notification.templateData())
                .status(Notification.DeliveryStatus.FAILED)
                .attemptCount(notification.attemptCount() + 1)
                .queuedAt(notification.queuedAt())
                .lastAttemptAt(Instant.now())
                .nextRetryAt(Instant.now().plusSeconds(60))
                .errorMessage(reason)
                .createdBy(notification.createdBy())
                .build();

            notifications.put(notificationId, failed);
            return Promise.of(failed);
        } catch (IllegalStateException e) {
            // Max retries exceeded, move to DLQ
            notification.moveToDlq();

            Notification dlq = Notification.builder()
                .notificationId(notification.notificationId())
                .tenantId(notification.tenantId())
                .recipientId(notification.recipientId())
                .recipientType(notification.recipientType())
                .channel(notification.channel())
                .templateId(notification.templateId())
                .templateData(notification.templateData())
                .status(Notification.DeliveryStatus.DLQ)
                .attemptCount(notification.attemptCount() + 1)
                .queuedAt(notification.queuedAt())
                .lastAttemptAt(Instant.now())
                .errorMessage(reason)
                .inDlq(true)
                .createdBy(notification.createdBy())
                .build();

            notifications.put(notificationId, dlq);
            return Promise.of(dlq);
        }
    }
}
