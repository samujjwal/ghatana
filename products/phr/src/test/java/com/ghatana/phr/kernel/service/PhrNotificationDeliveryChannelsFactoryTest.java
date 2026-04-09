package com.ghatana.phr.kernel.service;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Tests PHR notification delivery channel factory fallbacks when no concrete providers are configured
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PhrNotificationDeliveryChannelsFactory")
class PhrNotificationDeliveryChannelsFactoryTest extends EventloopTestBase {

    @Test
    @DisplayName("falls back to resilient no-op channels when provider endpoints are absent")
    void fallsBackToResilientNoOpChannels() {
        PhrTestInfrastructure.StubDataCloudAdapter dataCloud = new PhrTestInfrastructure.StubDataCloudAdapter();
        PhrNotificationDeliveryChannels channels = PhrNotificationDeliveryChannelsFactory.fromContext(
            PhrTestInfrastructure.createTestContext(dataCloud)
        );

        PhrNotificationDeliveryChannels.DeliveryReceipt receipt = runPromise(() -> channels.sendEmail(
            new PhrNotificationDeliveryChannels.NotificationEnvelope(
                "notif-1",
                "patient-1",
                "recipient-1",
                "provider-1",
                "ref-1",
                "appointment",
                "APPOINTMENT_REMINDER_SCHEDULED",
                PhrNotificationSender.NotificationChannel.EMAIL,
                Instant.parse("2026-04-07T10:00:00Z"),
                Instant.parse("2026-04-07T09:00:00Z"),
                "corr-factory-1",
                "phr_appointment_reminder_schedule"
            )
        ));

        assertThat(receipt.providerMessageId()).startsWith("noop-email-");
    }
}
