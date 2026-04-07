package com.ghatana.phr.kernel.service;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Tests durable PHR notification outbox persistence and channel fanout behavior
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DurablePhrNotificationSender")
class DurablePhrNotificationSenderTest extends EventloopTestBase {

    private DurablePhrNotificationSender sender;

    @BeforeEach
    void setUp() {
        PhrTestInfrastructure.StubDataCloudAdapter dataCloud = new PhrTestInfrastructure.StubDataCloudAdapter();
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
            PhrNotificationSender.DEFAULT_CHANNELS
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
    }

    @Test
    @DisplayName("persists consent notifications with the intended recipient")
    void persistsConsentNotificationsWithRecipientMetadata() {
        runPromise(() -> sender.notifyConsentChange(new PhrNotificationSender.ConsentChangeNotification(
            "patient-2",
            "provider-9",
            "grant-7",
            PhrNotificationSender.ConsentChangeType.EMERGENCY_ACCESS_GRANTED,
            PhrNotificationSender.DEFAULT_CHANNELS
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
}