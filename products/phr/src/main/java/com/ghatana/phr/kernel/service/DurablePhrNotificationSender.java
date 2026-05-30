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
    static final String PREFERENCES_DATASET = "phr.notifications.preferences";

    public DurablePhrNotificationSender(KernelContext context) {
        super(context);
    }

    @Override
    public String getName() {
        return "phr-notification-outbox";
    }

    @Override
    protected Promise<Void> initializeDatasets() {
        return Promises.all(
            createSchema(
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
                    Map.entry("providerMessageId", "string"),
                    Map.entry("readAt", "timestamp")
                ),
                Map.of("retention", "2years")
            ),
            createSchema(
                PREFERENCES_DATASET,
                Map.ofEntries(
                    Map.entry("principalId", "string"),
                    Map.entry("emailEnabled", "boolean"),
                    Map.entry("smsEnabled", "boolean"),
                    Map.entry("inAppEnabled", "boolean"),
                    Map.entry("updatedAt", "timestamp")
                ),
                Map.of("retention", "5years")
            )
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

    public Promise<Void> markAsRead(String notificationId, String principalId) {
        ensureRunning();

        String sanitizedNotificationId = PhrInputSanitizationUtils.requireSafeIdentifier(notificationId, "notificationId");
        String sanitizedPrincipalId = PhrInputSanitizationUtils.requireSafeIdentifier(principalId, "principalId");

        return queryRecords(OUTBOX_DATASET, "id = :id", Map.of("id", sanitizedNotificationId), 1, 0, NotificationOutboxEntry.class)
            .then(entries -> {
                if (entries.isEmpty()) {
                    return Promise.ofException(new IllegalStateException("Notification not found"));
                }
                NotificationOutboxEntry entry = entries.get(0);
                if (!entry.patientId().equals(sanitizedPrincipalId)) {
                    return Promise.ofException(new IllegalStateException("Cannot mark another user's notification as read"));
                }

                NotificationOutboxEntry updated = new NotificationOutboxEntry(
                    entry.id(),
                    entry.patientId(),
                    entry.recipientId(),
                    entry.providerId(),
                    entry.referenceId(),
                    entry.referenceType(),
                    entry.notificationType(),
                    entry.channel(),
                    entry.status(),
                    entry.scheduledFor(),
                    entry.createdAt(),
                    entry.correlationId(),
                    entry.traceOperation(),
                    entry.deliveryAttemptedAt(),
                    entry.deliveredAt(),
                    entry.failureReason(),
                    entry.providerMessageId(),
                    Instant.now()
                );

                return updateRecord(
                    OUTBOX_DATASET,
                    sanitizedNotificationId,
                    updated,
                    mutationMetadata(metadataFor(updated), updated.recipientId()),
                    "PhrNotificationOutboxEntry",
                    1
                ).map(__ -> (Void) null);
            });
    }

    public Promise<String> handleNotificationAction(String notificationId, String principalId, String action) {
        ensureRunning();

        String sanitizedNotificationId = PhrInputSanitizationUtils.requireSafeIdentifier(notificationId, "notificationId");
        String sanitizedPrincipalId = PhrInputSanitizationUtils.requireSafeIdentifier(principalId, "principalId");
        String sanitizedAction = PhrInputSanitizationUtils.requireSafeCode(action, "action");

        return queryRecords(OUTBOX_DATASET, "id = :id", Map.of("id", sanitizedNotificationId), 1, 0, NotificationOutboxEntry.class)
            .then(entries -> {
                if (entries.isEmpty()) {
                    return Promise.ofException(new IllegalStateException("Notification not found"));
                }
                NotificationOutboxEntry entry = entries.get(0);
                if (!entry.patientId().equals(sanitizedPrincipalId)) {
                    return Promise.ofException(new IllegalStateException("Cannot handle action for another user's notification"));
                }

                // Handle action based on notification type
                String result = switch (sanitizedAction) {
                    case "view" -> "viewed";
                    case "dismiss" -> "dismissed";
                    case "accept" -> "accepted";
                    case "decline" -> "declined";
                    default -> "unknown_action";
                };

                return Promise.of(result);
            });
    }

    public Promise<Map<String, Object>> getNotificationPreferences(String principalId) {
        ensureRunning();
        String sanitizedPrincipalId = PhrInputSanitizationUtils.requireSafeIdentifier(principalId, "principalId");

        return queryRecords(PREFERENCES_DATASET, "id = :id", Map.of("id", sanitizedPrincipalId), 1, 0, NotificationPreferences.class)
            .then(entries -> {
                if (entries.isEmpty()) {
                    return Promise.of(Map.of(
                        "emailEnabled", true,
                        "smsEnabled", false,
                        "inAppEnabled", true,
                        "updatedAt", Instant.now().toString()
                    ));
                }
                NotificationPreferences pref = entries.get(0);
                return Promise.of(Map.of(
                    "emailEnabled", pref.emailEnabled(),
                    "smsEnabled", pref.smsEnabled(),
                    "inAppEnabled", pref.inAppEnabled(),
                    "updatedAt", pref.updatedAt().toString()
                ));
            });
    }

    public Promise<Void> updateNotificationPreferences(String principalId, Map<String, Object> preferences) {
        ensureRunning();
        String sanitizedPrincipalId = PhrInputSanitizationUtils.requireSafeIdentifier(principalId, "principalId");

        boolean emailEnabled = preferences.get("emailEnabled") instanceof Boolean ? (Boolean) preferences.get("emailEnabled") : true;
        boolean smsEnabled = preferences.get("smsEnabled") instanceof Boolean ? (Boolean) preferences.get("smsEnabled") : false;
        boolean inAppEnabled = preferences.get("inAppEnabled") instanceof Boolean ? (Boolean) preferences.get("inAppEnabled") : true;

        NotificationPreferences updated = new NotificationPreferences(
            sanitizedPrincipalId,
            emailEnabled,
            smsEnabled,
            inAppEnabled,
            Instant.now()
        );

        return createRecord(
            PREFERENCES_DATASET,
            sanitizedPrincipalId,
            updated,
            mutationMetadata(Map.of("principalId", sanitizedPrincipalId), sanitizedPrincipalId),
            "NotificationPreferences",
            1
        ).toVoid();
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
                    null,
                    null
                );
                return createRecord(
                    OUTBOX_DATASET,
                    deliveryId,
                    entry,
                    mutationMetadata(metadataFor(entry), entry.recipientId()),
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

    public record NotificationPreferences(
        String principalId,
        boolean emailEnabled,
        boolean smsEnabled,
        boolean inAppEnabled,
        Instant updatedAt
    ) {
        public NotificationPreferences {
            Objects.requireNonNull(principalId, "principalId must not be null");
            Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        }
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
        String providerMessageId,
        Instant readAt
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
                messageId,
                null
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
                null,
                null
            );
        }
    }
}
