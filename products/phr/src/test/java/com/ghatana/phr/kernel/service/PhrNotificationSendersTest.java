package com.ghatana.phr.kernel.service;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Tests kernel-context resolution for the PHR notification sender boundary
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PhrNotificationSenders")
class PhrNotificationSendersTest extends EventloopTestBase {

    @Test
    @DisplayName("wraps context-provided sender and delegates notifications")
    void wrapsContextProvidedSender() {
        PhrTestInfrastructure.StubDataCloudAdapter dataCloud = new PhrTestInfrastructure.StubDataCloudAdapter();
        PhrNotificationTestSupport.RecordingNotificationSender delegate =
            new PhrNotificationTestSupport.RecordingNotificationSender();
        KernelContext context = PhrTestInfrastructure.createTestContext(
            dataCloud,
            Map.of(PhrNotificationSender.class, delegate)
        );

        PhrNotificationSender sender = PhrNotificationSenders.fromContext(context);

        runPromise(() -> sender.notifyConsentChange(new PhrNotificationSender.ConsentChangeNotification(
            "patient-1",
            "doctor-1",
            "grant-1",
            PhrNotificationSender.ConsentChangeType.GRANT_CREATED,
            PhrNotificationSender.DEFAULT_CHANNELS,
            "corr-consent-1",
            "phr_consent_change"
        )));

        assertThat(sender).isInstanceOf(ResilientPhrNotificationSender.class);
        assertThat(delegate.consentChangeNotifications()).hasSize(1);
    }

    @Test
    @DisplayName("falls back to a no-op sender when no dependency is registered")
    void fallsBackToNoOpSender() {
        PhrTestInfrastructure.StubDataCloudAdapter dataCloud = new PhrTestInfrastructure.StubDataCloudAdapter();
        KernelContext context = PhrTestInfrastructure.createTestContext(dataCloud);

        PhrNotificationSender sender = PhrNotificationSenders.fromContext(context);

        runPromise(() -> sender.scheduleAppointmentReminder(new PhrNotificationSender.AppointmentReminderNotification(
            "appointment-1",
            "patient-1",
            "provider-1",
            Instant.now().plusSeconds(3600),
            PhrNotificationSender.DEFAULT_CHANNELS,
            "corr-appointment-1",
            "phr_appointment_reminder_schedule"
        )));

        assertThat(sender).isInstanceOf(ResilientPhrNotificationSender.class);
    }
}