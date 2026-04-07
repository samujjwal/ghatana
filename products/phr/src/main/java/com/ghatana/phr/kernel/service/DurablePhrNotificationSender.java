package com.ghatana.phr.kernel.service;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.service.AbstractDataService;
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
public final class DurablePhrNotificationSender extends AbstractDataService implements PhrNotificationSender {

    private static final String OUTBOX_DATASET = "phr.notifications.outbox";

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
            Map.of(
                "id", "string",
                "patientId", "string",
                "recipientId", "string",
                "providerId", "string",
                "referenceId", "string",
                "referenceType", "string",
                "notificationType", "string",
                "channel", "string",
                "status", "string",
                "scheduledFor", "timestamp",
                "createdAt", "timestamp"
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
            notification.channels()
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
            notification.channels()
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
            notification.channels()
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
            notification.channels()
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

    private Promise<Void> persistNotifications(
            String patientId,
            String recipientId,
            String providerId,
            String referenceId,
            String referenceType,
            String notificationType,
            Instant scheduledFor,
            Set<NotificationChannel> channels) {
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
                    createdAt
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

    private static Map<String, String> metadataFor(NotificationOutboxEntry entry) {
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
        if (entry.providerId() != null) {
            metadata.put("providerId", entry.providerId());
        }
        return Map.copyOf(metadata);
    }

    public enum NotificationStatus {
        PENDING
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
        Instant createdAt
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
        }
    }
}