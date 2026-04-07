package com.ghatana.phr.kernel.service;

import io.activej.promise.Promise;
import java.time.Instant;
import java.util.Objects;

/**
 * @doc.type interface
 * @doc.purpose Declares channel-specific delivery for PHR notification outbox processing
 * @doc.layer product
 * @doc.pattern Port
 */
public interface PhrNotificationDeliveryChannels {

    Promise<DeliveryReceipt> sendEmail(NotificationEnvelope notification);

    Promise<DeliveryReceipt> sendSms(NotificationEnvelope notification);

    Promise<DeliveryReceipt> sendPush(NotificationEnvelope notification);

    record NotificationEnvelope(
        String notificationId,
        String patientId,
        String recipientId,
        String providerId,
        String referenceId,
        String referenceType,
        String notificationType,
        PhrNotificationSender.NotificationChannel channel,
        Instant scheduledFor,
        Instant createdAt,
        String correlationId,
        String traceOperation
    ) {
        public NotificationEnvelope {
            Objects.requireNonNull(notificationId, "notificationId must not be null");
            Objects.requireNonNull(patientId, "patientId must not be null");
            Objects.requireNonNull(recipientId, "recipientId must not be null");
            Objects.requireNonNull(referenceId, "referenceId must not be null");
            Objects.requireNonNull(referenceType, "referenceType must not be null");
            Objects.requireNonNull(notificationType, "notificationType must not be null");
            Objects.requireNonNull(channel, "channel must not be null");
            Objects.requireNonNull(scheduledFor, "scheduledFor must not be null");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            Objects.requireNonNull(correlationId, "correlationId must not be null");
            Objects.requireNonNull(traceOperation, "traceOperation must not be null");
        }
    }

    record DeliveryReceipt(String providerMessageId, Instant deliveredAt) {
        public DeliveryReceipt {
            Objects.requireNonNull(providerMessageId, "providerMessageId must not be null");
            Objects.requireNonNull(deliveredAt, "deliveredAt must not be null");
        }
    }
}