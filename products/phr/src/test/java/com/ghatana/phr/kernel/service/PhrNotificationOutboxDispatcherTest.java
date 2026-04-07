package com.ghatana.phr.kernel.service;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Tests durable outbox dispatch to real PHR notification delivery channels
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PhrNotificationOutboxDispatcher")
class PhrNotificationOutboxDispatcherTest extends EventloopTestBase {

    private KernelContext context;
    private DurablePhrNotificationSender durableSender;
    private PhrNotificationOutboxDispatcher dispatcher;
    private PhrNotificationTestSupport.RecordingDeliveryChannels deliveryChannels;

    @BeforeEach
    void setUp() {
        PhrTestInfrastructure.StubDataCloudAdapter dataCloud = new PhrTestInfrastructure.StubDataCloudAdapter();
        context = PhrTestInfrastructure.createTestContext(dataCloud);
        durableSender = new DurablePhrNotificationSender(context);
        deliveryChannels = new PhrNotificationTestSupport.RecordingDeliveryChannels();
        dispatcher = new PhrNotificationOutboxDispatcher(context, deliveryChannels);
        runPromise(durableSender::start);
        runPromise(dispatcher::start);
    }

    @Test
    @DisplayName("dispatches pending notifications to email, sms, and push providers")
    void dispatchesPendingNotifications() {
        runPromise(() -> durableSender.scheduleAppointmentReminder(new PhrNotificationSender.AppointmentReminderNotification(
            "appointment-1",
            "patient-1",
            "provider-1",
            Instant.parse("2026-04-06T12:00:00Z"),
            PhrNotificationSender.DEFAULT_CHANNELS,
            "corr-dispatch-1",
            "phr_appointment_reminder_schedule"
        )));

        PhrNotificationOutboxDispatcher.DispatchResult result = runPromise(() -> dispatcher.dispatchPendingNotifications(10));

        assertThat(result.processedCount()).isEqualTo(3);
        assertThat(result.deliveredCount()).isEqualTo(3);
        assertThat(deliveryChannels.emailNotifications()).hasSize(1);
        assertThat(deliveryChannels.smsNotifications()).hasSize(1);
        assertThat(deliveryChannels.pushNotifications()).hasSize(1);
        assertThat(deliveryChannels.emailNotifications().getFirst().correlationId()).isEqualTo("corr-dispatch-1");

        List<DurablePhrNotificationSender.NotificationOutboxEntry> entries = runPromise(() -> durableSender.getPendingNotifications(10));
        assertThat(entries).isEmpty();
        assertThat(result.outcomes())
            .extracting(PhrNotificationOutboxDispatcher.DispatchOutcome::status)
            .containsOnly(DurablePhrNotificationSender.NotificationStatus.DELIVERED);
    }

    @Test
    @DisplayName("marks notifications failed when a provider rejects delivery")
    void marksNotificationsFailed() {
        runPromise(() -> durableSender.notifyConsentChange(new PhrNotificationSender.ConsentChangeNotification(
            "patient-1",
            "doctor-1",
            "grant-1",
            PhrNotificationSender.ConsentChangeType.GRANT_CREATED,
            java.util.Set.of(PhrNotificationSender.NotificationChannel.EMAIL),
            "corr-dispatch-fail-1",
            "phr_consent_change"
        )));

        AtomicInteger attempts = new AtomicInteger();
        dispatcher = new PhrNotificationOutboxDispatcher(
            context,
            new PhrNotificationDeliveryChannels() {
                @Override
                public Promise<DeliveryReceipt> sendEmail(NotificationEnvelope notification) {
                    attempts.incrementAndGet();
                    return Promise.ofException(new IllegalStateException("email provider unavailable"));
                }

                @Override
                public Promise<DeliveryReceipt> sendSms(NotificationEnvelope notification) {
                    return Promise.of(new DeliveryReceipt("sms", Instant.now()));
                }

                @Override
                public Promise<DeliveryReceipt> sendPush(NotificationEnvelope notification) {
                    return Promise.of(new DeliveryReceipt("push", Instant.now()));
                }
            }
        );
        runPromise(dispatcher::start);

        PhrNotificationOutboxDispatcher.DispatchResult result = runPromise(() -> dispatcher.dispatchPendingNotifications(10));

        assertThat(attempts.get()).isEqualTo(1);
        assertThat(result.deliveredCount()).isEqualTo(0);
        assertThat(result.failedCount()).isEqualTo(1);

        DurablePhrNotificationSender.NotificationOutboxEntry failedEntry = runPromise(
            () -> dispatcher.getNotification(result.outcomes().getFirst().notificationId())
        );
        assertThat(failedEntry.status()).isEqualTo(DurablePhrNotificationSender.NotificationStatus.FAILED);
        assertThat(failedEntry.failureReason()).contains("email provider unavailable");
    }
}