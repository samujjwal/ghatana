package com.ghatana.phr.kernel.service;

import com.ghatana.kernel.context.KernelContext;

import io.activej.promise.Promise;
import io.activej.promise.Promises;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * @doc.type class
 * @doc.purpose Persists patient-facing PHR notifications into a durable outbox for downstream channel delivery
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class DurablePhrNotificationSender extends PhrServiceBase implements PhrNotificationSender {

    static final String OUTBOX_DATASET = "phr.notifications.outbox";

    public DurablePhrNotificationSender(KernelContext context) {
        super(context);
    }

    @Override
    public String getName() {
        return "phr-notification-outbox";
    }

    @Override
    protected Promise<Void> initializeDatasets() {
        return createSchema(
            OUTBOX_DATASET,
            Map.ofEntries(
                Map.entry("id", "string"),
                Map.entry("patientId", "string"),
                Map.entry("recipientId", "string"),
                Map.entry("providerId", "string"),
                Map.entry("referenceId", "string"),
                Map.entry("referenceType", "string"),
                Map.entry("notificationType", "string"),
                Map.entry("channel", "string"),
                Map.entry("status", "string"),
                Map.entry("scheduledFor", "timestamp"),
                Map.entry("createdAt", "timestamp"),
                Map.entry("correlationId", "string"),
                Map.entry("traceOperation", "string"),
                Map.entry("deliveryAttemptedAt", "timestamp"),
                Map.entry("deliveredAt", "timestamp"),
                Map.entry("failureReason", "string"),
                Map.entry("providerMessageId", "string")
            ),
            Map.of("retention", "2years")
        );
    }

    @Override
    public Promise<Void> scheduleAppointmentReminder(AppointmentReminderNotification notification) {
        Objects.requireNonNull(notification, "notification must not be null");
        return persistNotifications(
            notification.patientId(),
            notification.patientId(),
            notification.providerId(),
            notification.appointmentId(),
            "appointment",
            "APPOINTMENT_REMINDER_SCHEDULED",
            notification.scheduledTime(),
            notification.channels(),
            notification.correlationId(),
            notification.traceOperation()
        );
    }

    @Override
    public Promise<Void> cancelAppointmentReminder(AppointmentReminderNotification notification) {
        Objects.requireNonNull(notification, "notification must not be null");
        return persistNotifications(
            notification.patientId(),
            notification.patientId(),
            notification.providerId(),
            notification.appointmentId(),
            "appointment",
            "APPOINTMENT_REMINDER_CANCELLED",
            notification.scheduledTime(),
            notification.channels(),
            notification.correlationId(),
            notification.traceOperation()
        );
    }

    @Override
    public Promise<Void> notifyConsentChange(ConsentChangeNotification notification) {
        Objects.requireNonNull(notification, "notification must not be null");
        return persistNotifications(
            notification.patientId(),
            notification.recipientId(),
            null,
            notification.referenceId(),
            "consent-grant",
            notification.changeType().name(),
            Instant.now(),
            notification.channels(),
            notification.correlationId(),
            notification.traceOperation()
        );
    }

    @Override
    public Promise<Void> notifyTelemedicineSession(TelemedicineSessionNotification notification) {
        Objects.requireNonNull(notification, "notification must not be null");
        return persistNotifications(
            notification.patientId(),
            notification.patientId(),
            notification.providerId(),
            notification.sessionId(),
            "telemedicine-session",
            notification.notificationType().name(),
            notification.scheduledAt(),
            notification.channels(),
            notification.correlationId(),
            notification.traceOperation()
        );
    }

    public Promise<List<NotificationOutboxEntry>> getPendingNotifications(String patientId, int limit) {
        ensureRunning();

        String sanitizedPatientId = PhrInputSanitizationUtils.requireSafeIdentifier(patientId, "patientId");
        return queryRecords(
            OUTBOX_DATASET,
            "patientId = :patientId",
            Map.of("patientId", sanitizedPatientId),
            limit,
            0,
            NotificationOutboxEntry.class
        ).map(entries -> entries.stream()
            .filter(entry -> entry.status() == NotificationStatus.PENDING)
            .sorted((left, right) -> left.createdAt().compareTo(right.createdAt()))
            .toList());
    }

    public Promise<List<NotificationOutboxEntry>> getPendingNotifications(int limit) {
        ensureRunning();

        return queryRecords(
            OUTBOX_DATASET,
            "status = :status",
            Map.of("status", NotificationStatus.PENDING.name()),
            limit,
            0,
            NotificationOutboxEntry.class
        ).map(entries -> entries.stream()
            .sorted((left, right) -> left.createdAt().compareTo(right.createdAt()))
            .toList());
    }

    private Promise<Void> persistNotifications(
            String patientId,
            String recipientId,
            String providerId,
            String referenceId,
            String referenceType,
            String notificationType,
            Instant scheduledFor,
            Set<NotificationChannel> channels,
            String correlationId,
            String traceOperation) {
        ensureRunning();

        String sanitizedPatientId = PhrInputSanitizationUtils.requireSafeIdentifier(patientId, "patientId");
        String sanitizedRecipientId = PhrInputSanitizationUtils.requireSafeIdentifier(recipientId, "recipientId");
        String sanitizedProviderId = providerId == null
            ? null
            : PhrInputSanitizationUtils.requireSafeIdentifier(providerId, "providerId");
        String sanitizedReferenceId = PhrInputSanitizationUtils.requireSafeIdentifier(referenceId, "referenceId");
        String sanitizedReferenceType = PhrInputSanitizationUtils.requireSafeCode(referenceType, "referenceType");
        String sanitizedNotificationType = PhrInputSanitizationUtils.requireSafeCode(
            notificationType,
            "notificationType"
        );
        String sanitizedCorrelationId = PhrInputSanitizationUtils.requireSafeIdentifier(correlationId, "correlationId");
        String sanitizedTraceOperation = PhrInputSanitizationUtils.requireSafeCode(traceOperation, "traceOperation");
        Instant sanitizedScheduledFor = Objects.requireNonNull(scheduledFor, "scheduledFor must not be null");
        Set<NotificationChannel> sanitizedChannels = Set.copyOf(Objects.requireNonNull(channels, "channels must not be null"));
        Instant createdAt = Instant.now();

        return Promises.all(sanitizedChannels.stream()
            .map(channel -> {
                String deliveryId = generateId("phrn");
                NotificationOutboxEntry entry = new NotificationOutboxEntry(
                    deliveryId,
                    sanitizedPatientId,
                    sanitizedRecipientId,
                    sanitizedProviderId,
                    sanitizedReferenceId,
                    sanitizedReferenceType,
                    sanitizedNotificationType,
                    channel,
                    NotificationStatus.PENDING,
                    sanitizedScheduledFor,
                    createdAt,
                    sanitizedCorrelationId,
                    sanitizedTraceOperation,
                    null,
                    null,
                    null,
                    null
                );
                return createRecord(
                    OUTBOX_DATASET,
                    deliveryId,
                    entry,
                    metadataFor(entry),
                    "PhrNotificationOutboxEntry",
                    1
                ).toVoid();
            })
            .toList());
    }

    static Map<String, String> metadataFor(NotificationOutboxEntry entry) {
        HashMap<String, String> metadata = new HashMap<>();
        metadata.put("patientId", entry.patientId());
        metadata.put("recipientId", entry.recipientId());
        metadata.put("referenceId", entry.referenceId());
        metadata.put("referenceType", entry.referenceType());
        metadata.put("notificationType", entry.notificationType());
        metadata.put("channel", entry.channel().name());
        metadata.put("status", entry.status().name());
        metadata.put("scheduledFor", entry.scheduledFor().toString());
        metadata.put("createdAt", entry.createdAt().toString());
        metadata.put("correlationId", entry.correlationId());
        metadata.put("traceOperation", entry.traceOperation());
        if (entry.deliveryAttemptedAt() != null) {
            metadata.put("deliveryAttemptedAt", entry.deliveryAttemptedAt().toString());
        }
        if (entry.deliveredAt() != null) {
            metadata.put("deliveredAt", entry.deliveredAt().toString());
        }
        if (entry.failureReason() != null) {
            metadata.put("failureReason", entry.failureReason());
        }
        if (entry.providerMessageId() != null) {
            metadata.put("providerMessageId", entry.providerMessageId());
        }
        if (entry.providerId() != null) {
            metadata.put("providerId", entry.providerId());
        }
        return Map.copyOf(metadata);
    }

    public enum NotificationStatus {
        PENDING,
        DELIVERED,
        FAILED
    }

    public record NotificationOutboxEntry(
        String id,
        String patientId,
        String recipientId,
        String providerId,
        String referenceId,
        String referenceType,
        String notificationType,
        NotificationChannel channel,
        NotificationStatus status,
        Instant scheduledFor,
        Instant createdAt,
        String correlationId,
        String traceOperation,
        Instant deliveryAttemptedAt,
        Instant deliveredAt,
        String failureReason,
        String providerMessageId
    ) {
        public NotificationOutboxEntry {
            Objects.requireNonNull(id, "id must not be null");
            Objects.requireNonNull(patientId, "patientId must not be null");
            Objects.requireNonNull(recipientId, "recipientId must not be null");
            Objects.requireNonNull(referenceId, "referenceId must not be null");
            Objects.requireNonNull(referenceType, "referenceType must not be null");
            Objects.requireNonNull(notificationType, "notificationType must not be null");
            Objects.requireNonNull(channel, "channel must not be null");
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(scheduledFor, "scheduledFor must not be null");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            Objects.requireNonNull(correlationId, "correlationId must not be null");
            Objects.requireNonNull(traceOperation, "traceOperation must not be null");
        }

        NotificationOutboxEntry markDelivered(String messageId, Instant deliveredAt) {
            return new NotificationOutboxEntry(
                id,
                patientId,
                recipientId,
                providerId,
                referenceId,
                referenceType,
                notificationType,
                channel,
                NotificationStatus.DELIVERED,
                scheduledFor,
                createdAt,
                correlationId,
                traceOperation,
                deliveredAt,
                deliveredAt,
                null,
                messageId
            );
        }

        NotificationOutboxEntry markFailed(String failureReason, Instant attemptedAt) {
            return new NotificationOutboxEntry(
                id,
                patientId,
                recipientId,
                providerId,
                referenceId,
                referenceType,
                notificationType,
                channel,
                NotificationStatus.FAILED,
                scheduledFor,
                createdAt,
                correlationId,
                traceOperation,
                attemptedAt,
                null,
                failureReason,
                null
            );
        }
    }
}
