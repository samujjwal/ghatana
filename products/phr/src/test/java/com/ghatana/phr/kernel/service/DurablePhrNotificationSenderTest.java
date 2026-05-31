package com.ghatana.phr.kernel.service;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @doc.type class
 * @doc.purpose Tests durable PHR notification outbox persistence and channel fanout behavior
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DurablePhrNotificationSender")
class DurablePhrNotificationSenderTest extends EventloopTestBase {

    private DurablePhrNotificationSender sender;
    private PhrTestInfrastructure.StubDataCloudAdapter dataCloud;

    @BeforeEach
    void setUp() {
        dataCloud = new PhrTestInfrastructure.StubDataCloudAdapter();
        sender = new DurablePhrNotificationSender(PhrTestInfrastructure.createTestContext(dataCloud));
        runPromise(sender::start);
    }

    @Test
    @DisplayName("fans out appointment reminders into channel-specific pending outbox entries")
    void fansOutAppointmentRemindersIntoOutboxEntries() {
        Instant scheduledTime = Instant.parse("2026-04-06T12:00:00Z");

        runPromise(() -> sender.scheduleAppointmentReminder(new PhrNotificationSender.AppointmentReminderNotification(
            "appointment-1",
            "patient-1",
            "provider-1",
            scheduledTime,
            PhrNotificationSender.DEFAULT_CHANNELS,
            "corr-appointment-1",
            "phr_appointment_reminder_schedule"
        )));

        List<DurablePhrNotificationSender.NotificationOutboxEntry> entries = runPromise(
            () -> sender.getPendingNotifications("patient-1", 10)
        );

        assertThat(entries).hasSize(3);
        assertThat(entries)
            .extracting(DurablePhrNotificationSender.NotificationOutboxEntry::referenceId)
            .containsOnly("appointment-1");
        assertThat(entries)
            .extracting(DurablePhrNotificationSender.NotificationOutboxEntry::notificationType)
            .containsOnly("APPOINTMENT_REMINDER_SCHEDULED");
        assertThat(entries)
            .extracting(DurablePhrNotificationSender.NotificationOutboxEntry::channel)
            .containsExactlyInAnyOrder(
                PhrNotificationSender.NotificationChannel.EMAIL,
                PhrNotificationSender.NotificationChannel.SMS,
                PhrNotificationSender.NotificationChannel.PUSH
            );
        assertThat(entries)
            .extracting(DurablePhrNotificationSender.NotificationOutboxEntry::scheduledFor)
            .containsOnly(scheduledTime);
        assertThat(entries)
            .extracting(DurablePhrNotificationSender.NotificationOutboxEntry::status)
            .containsOnly(DurablePhrNotificationSender.NotificationStatus.PENDING);
        assertThat(entries)
            .extracting(DurablePhrNotificationSender.NotificationOutboxEntry::correlationId)
            .containsOnly("corr-appointment-1");
        assertThat(entries)
            .extracting(DurablePhrNotificationSender.NotificationOutboxEntry::traceOperation)
            .containsOnly("phr_appointment_reminder_schedule");
        assertThat(entries)
            .extracting(DurablePhrNotificationSender.NotificationOutboxEntry::safeReasonCode)
            .containsOnly("APPOINTMENT_REMINDER_SCHEDULED");
        assertThat(entries)
            .extracting(DurablePhrNotificationSender.NotificationOutboxEntry::deepLinkId)
            .containsOnly("appointment:appointment-1");
    }

    @Test
    @DisplayName("persists consent notifications with the intended recipient")
    void persistsConsentNotificationsWithRecipientMetadata() {
        runPromise(() -> sender.notifyConsentChange(new PhrNotificationSender.ConsentChangeNotification(
            "patient-2",
            "provider-9",
            "grant-7",
            PhrNotificationSender.ConsentChangeType.EMERGENCY_ACCESS_GRANTED,
            PhrNotificationSender.DEFAULT_CHANNELS,
            "corr-consent-7",
            "phr_emergency_access_granted"
        )));

        List<DurablePhrNotificationSender.NotificationOutboxEntry> entries = runPromise(
            () -> sender.getPendingNotifications("patient-2", 10)
        );

        assertThat(entries).hasSize(3);
        assertThat(entries)
            .extracting(DurablePhrNotificationSender.NotificationOutboxEntry::recipientId)
            .containsOnly("provider-9");
        assertThat(entries)
            .extracting(DurablePhrNotificationSender.NotificationOutboxEntry::notificationType)
            .containsOnly(PhrNotificationSender.ConsentChangeType.EMERGENCY_ACCESS_GRANTED.name());
        assertThat(entries)
            .extracting(DurablePhrNotificationSender.NotificationOutboxEntry::referenceType)
            .containsOnly("consent-grant");
    }

    @Test
    @DisplayName("returns global pending notifications in created order")
    void returnsGlobalPendingNotifications() {
        runPromise(() -> sender.notifyTelemedicineSession(new PhrNotificationSender.TelemedicineSessionNotification(
            "tele-1",
            "patient-3",
            "provider-3",
            Instant.parse("2026-04-06T13:00:00Z"),
            PhrNotificationSender.TelemedicineNotificationType.SESSION_SCHEDULED,
            PhrNotificationSender.DEFAULT_CHANNELS,
            "corr-tele-1",
            "phr_telemedicine_schedule"
        )));

        List<DurablePhrNotificationSender.NotificationOutboxEntry> entries = runPromise(() -> sender.getPendingNotifications(10));

        assertThat(entries).hasSize(3);
        assertThat(entries)
            .extracting(DurablePhrNotificationSender.NotificationOutboxEntry::referenceId)
            .containsOnly("tele-1");
        assertThat(dataCloud.metadataFor(DurablePhrNotificationSender.OUTBOX_DATASET, entries.getFirst().id()))
            .containsEntry("correlationId", "corr-tele-1")
            .containsEntry("traceOperation", "phr_phr-notification-outbox-entry_create");
    }

    @Test
    @DisplayName("notification actions validate action names and persist read state")
    void notificationActionsValidateAndPersistState() {
        runPromise(() -> sender.scheduleAppointmentReminder(new PhrNotificationSender.AppointmentReminderNotification(
            "appointment-2",
            "patient-4",
            "provider-4",
            Instant.parse("2026-04-08T12:00:00Z"),
            Set.of(PhrNotificationSender.NotificationChannel.PUSH),
            "corr-appointment-2",
            "phr_appointment_reminder_schedule"
        )));
        DurablePhrNotificationSender.NotificationOutboxEntry entry = runPromise(
            () -> sender.getPendingNotifications("patient-4", 10)
        ).getFirst();

        String result = runPromise(() -> sender.handleNotificationAction(entry.id(), "patient-4", "dismiss"));

        assertThat(result).isEqualTo("dismissed");
        DurablePhrNotificationSender.NotificationOutboxEntry updated = runPromise(
            () -> sender.getPendingNotifications("patient-4", 10)
        ).getFirst();
        assertThat(updated.readAt()).isNotNull();

        assertThatThrownBy(() -> runPromise(() -> sender.handleNotificationAction(entry.id(), "patient-4", "export-record")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported notification action");
    }
}
