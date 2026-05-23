package com.ghatana.digitalmarketing.domain.notification;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Notification domain entity.
 *
 * @doc.type class
 * @doc.purpose Represents a notification for delivery with retry and DLQ support
 * @doc.layer product
 * @doc.pattern Domain Entity
 */
public class Notification {

    private final String notificationId;
    private final String tenantId;
    private final String recipientId;
    private final String recipientType;
    private final String channel;
    private final String templateId;
    private final Map<String, String> templateData;
    private final DeliveryStatus status;
    private final int attemptCount;
    private final Instant queuedAt;
    private final Instant lastAttemptAt;
    private final Instant nextRetryAt;
    private final String errorMessage;
    private final boolean inDlq;
    private final String createdBy;

    private Notification(Builder builder) {
        this.notificationId = Objects.requireNonNull(builder.notificationId, "notificationId must not be null");
        this.tenantId = Objects.requireNonNull(builder.tenantId, "tenantId must not be null");
        this.recipientId = Objects.requireNonNull(builder.recipientId, "recipientId must not be null");
        this.recipientType = Objects.requireNonNull(builder.recipientType, "recipientType must not be null");
        this.channel = Objects.requireNonNull(builder.channel, "channel must not be null");
        this.templateId = Objects.requireNonNull(builder.templateId, "templateId must not be null");
        this.templateData = Objects.requireNonNull(builder.templateData, "templateData must not be null");
        this.status = Objects.requireNonNull(builder.status, "status must not be null");
        this.attemptCount = builder.attemptCount;
        this.queuedAt = Objects.requireNonNull(builder.queuedAt, "queuedAt must not be null");
        this.lastAttemptAt = builder.lastAttemptAt;
        this.nextRetryAt = builder.nextRetryAt;
        this.errorMessage = builder.errorMessage;
        this.inDlq = builder.inDlq;
        this.createdBy = Objects.requireNonNull(builder.createdBy, "createdBy must not be null");
    }

    public String notificationId() {
        return notificationId;
    }

    public String tenantId() {
        return tenantId;
    }

    public String recipientId() {
        return recipientId;
    }

    public String recipientType() {
        return recipientType;
    }

    public String channel() {
        return channel;
    }

    public String templateId() {
        return templateId;
    }

    public Map<String, String> templateData() {
        return templateData;
    }

    public DeliveryStatus status() {
        return status;
    }

    public int attemptCount() {
        return attemptCount;
    }

    public Instant queuedAt() {
        return queuedAt;
    }

    public Instant lastAttemptAt() {
        return lastAttemptAt;
    }

    public Instant nextRetryAt() {
        return nextRetryAt;
    }

    public String errorMessage() {
        return errorMessage;
    }

    public boolean inDlq() {
        return inDlq;
    }

    public String createdBy() {
        return createdBy;
    }

    /**
     * Mark notification as delivered.
     */
    public void markDelivered() {
        // In a real implementation, this would update the status
    }

    /**
     * Mark notification as failed and schedule retry.
     *
     * @param reason failure reason
     * @param maxRetries maximum retry attempts
     * @throws IllegalStateException if max retries exceeded
     */
    public void markFailed(String reason, int maxRetries) {
        if (attemptCount >= maxRetries) {
            throw new IllegalStateException("Max retries exceeded for notification: " + notificationId);
        }
        // In a real implementation, this would update the status and schedule retry
    }

    /**
     * Move notification to DLQ.
     */
    public void moveToDlq() {
        // In a real implementation, this would mark the notification as in DLQ
    }

    /**
     * Replay notification from DLQ.
     */
    public void replay() {
        if (!inDlq) {
            throw new IllegalStateException("Cannot replay notification not in DLQ");
        }
        // In a real implementation, this would reset the notification for retry
    }

    public static Builder builder() {
        return new Builder();
    }

    public static String generateNotificationId() {
        return "NOTIF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    public static final class Builder {
        private String notificationId;
        private String tenantId;
        private String recipientId;
        private String recipientType;
        private String channel;
        private String templateId;
        private Map<String, String> templateData;
        private DeliveryStatus status = DeliveryStatus.QUEUED;
        private int attemptCount = 0;
        private Instant queuedAt = Instant.now();
        private Instant lastAttemptAt;
        private Instant nextRetryAt;
        private String errorMessage;
        private boolean inDlq = false;
        private String createdBy;

        public Builder notificationId(String notificationId) {
            this.notificationId = notificationId;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder recipientId(String recipientId) {
            this.recipientId = recipientId;
            return this;
        }

        public Builder recipientType(String recipientType) {
            this.recipientType = recipientType;
            return this;
        }

        public Builder channel(String channel) {
            this.channel = channel;
            return this;
        }

        public Builder templateId(String templateId) {
            this.templateId = templateId;
            return this;
        }

        public Builder templateData(Map<String, String> templateData) {
            this.templateData = templateData;
            return this;
        }

        public Builder status(DeliveryStatus status) {
            this.status = status;
            return this;
        }

        public Builder attemptCount(int attemptCount) {
            this.attemptCount = attemptCount;
            return this;
        }

        public Builder queuedAt(Instant queuedAt) {
            this.queuedAt = queuedAt;
            return this;
        }

        public Builder lastAttemptAt(Instant lastAttemptAt) {
            this.lastAttemptAt = lastAttemptAt;
            return this;
        }

        public Builder nextRetryAt(Instant nextRetryAt) {
            this.nextRetryAt = nextRetryAt;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder inDlq(boolean inDlq) {
            this.inDlq = inDlq;
            return this;
        }

        public Builder createdBy(String createdBy) {
            this.createdBy = createdBy;
            return this;
        }

        public Notification build() {
            return new Notification(this);
        }
    }

    public enum DeliveryStatus {
        QUEUED,
        PENDING,
        DELIVERED,
        FAILED,
        DLQ
    }
}
