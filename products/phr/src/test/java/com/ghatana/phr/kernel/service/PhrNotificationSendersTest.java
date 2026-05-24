package com.ghatana.phr.kernel.service;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    @DisplayName("fails closed when no sender dependency is registered")
    void failsClosedWhenSenderMissing() {
        PhrTestInfrastructure.StubDataCloudAdapter dataCloud = new PhrTestInfrastructure.StubDataCloudAdapter();
        KernelContext context = PhrTestInfrastructure.createTestContext(dataCloud);

        assertThatThrownBy(() -> PhrNotificationSenders.fromContext(context))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("PhrNotificationSender dependency is required");
    }
}
