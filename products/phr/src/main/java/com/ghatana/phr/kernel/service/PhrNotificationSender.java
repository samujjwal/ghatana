package com.ghatana.phr.kernel.service;

import io.activej.promise.Promise;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * @doc.type interface
 * @doc.purpose Declares patient-facing notification delivery for PHR appointment, consent, and telemedicine flows
 * @doc.layer product
 * @doc.pattern Port
 */
public interface PhrNotificationSender {

    Set<NotificationChannel> DEFAULT_CHANNELS = Set.of(
        NotificationChannel.EMAIL,
        NotificationChannel.SMS,
        NotificationChannel.PUSH
    );

    Promise<Void> scheduleAppointmentReminder(AppointmentReminderNotification notification);

    Promise<Void> cancelAppointmentReminder(AppointmentReminderNotification notification);

    Promise<Void> notifyConsentChange(ConsentChangeNotification notification);

    Promise<Void> notifyTelemedicineSession(TelemedicineSessionNotification notification);

    enum NotificationChannel {
        EMAIL,
        SMS,
        PUSH
    }

    enum ConsentChangeType {
        GRANT_CREATED,
        GRANT_REVOKED,
        EMERGENCY_ACCESS_GRANTED
    }

    enum TelemedicineNotificationType {
        SESSION_SCHEDULED,
        SESSION_CANCELLED
    }

    record AppointmentReminderNotification(
            String appointmentId,
            String patientId,
            String providerId,
            Instant scheduledTime,
            Set<NotificationChannel> channels) {

        public AppointmentReminderNotification {
            Objects.requireNonNull(appointmentId, "appointmentId must not be null");
            Objects.requireNonNull(patientId, "patientId must not be null");
            Objects.requireNonNull(providerId, "providerId must not be null");
            Objects.requireNonNull(scheduledTime, "scheduledTime must not be null");
            channels = Set.copyOf(Objects.requireNonNull(channels, "channels must not be null"));
        }
    }

    record ConsentChangeNotification(
            String patientId,
            String recipientId,
            String referenceId,
            ConsentChangeType changeType,
            Set<NotificationChannel> channels) {

        public ConsentChangeNotification {
            Objects.requireNonNull(patientId, "patientId must not be null");
            Objects.requireNonNull(recipientId, "recipientId must not be null");
            Objects.requireNonNull(referenceId, "referenceId must not be null");
            Objects.requireNonNull(changeType, "changeType must not be null");
            channels = Set.copyOf(Objects.requireNonNull(channels, "channels must not be null"));
        }
    }

    record TelemedicineSessionNotification(
            String sessionId,
            String patientId,
            String providerId,
            Instant scheduledAt,
            TelemedicineNotificationType notificationType,
            Set<NotificationChannel> channels) {

        public TelemedicineSessionNotification {
            Objects.requireNonNull(sessionId, "sessionId must not be null");
            Objects.requireNonNull(patientId, "patientId must not be null");
            Objects.requireNonNull(providerId, "providerId must not be null");
            Objects.requireNonNull(scheduledAt, "scheduledAt must not be null");
            Objects.requireNonNull(notificationType, "notificationType must not be null");
            channels = Set.copyOf(Objects.requireNonNull(channels, "channels must not be null"));
        }
    }
}