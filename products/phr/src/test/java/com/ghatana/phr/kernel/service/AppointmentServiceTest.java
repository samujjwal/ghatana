package com.ghatana.phr.kernel.service;

import com.ghatana.kernel.adapter.datacloud.DataWriteRequest;
import com.ghatana.kernel.context.KernelContext;
import com.ghatana.phr.kernel.service.AppointmentService.Appointment;
import com.ghatana.phr.kernel.service.AppointmentService.AppointmentRequest;
import com.ghatana.phr.kernel.service.AppointmentService.TimeSlot;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link AppointmentService}.
 *
 * @doc.type class
 * @doc.purpose Tests for PHR appointment boundary validation and sanitization
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AppointmentService")
class AppointmentServiceTest extends EventloopTestBase {

    private TestableAppointmentService service;
    private PhrNotificationTestSupport.RecordingNotificationSender notificationSender;

    @BeforeEach
    void setUp() {
        PhrTestInfrastructure.StubDataCloudAdapter dataCloud = new PhrTestInfrastructure.StubDataCloudAdapter();
        KernelContext context = PhrTestInfrastructure.createTestContext(dataCloud);
        notificationSender = new PhrNotificationTestSupport.RecordingNotificationSender();
        service = new TestableAppointmentService(context, dataCloud, notificationSender);
        runPromise(service::start);
        runPromise(() -> service.seedAvailableSlot("slot-1", "provider-1", Instant.now().plusSeconds(3600)));
    }

    @Test
    @DisplayName("escapes appointment reason before storing")
    void escapesAppointmentReason() {
        Appointment stored = runPromise(() -> service.createAppointment(new AppointmentRequest(
            "patient-1",
            "provider-1",
            "slot-1",
            Instant.now().plusSeconds(3600),
            30,
            "<script>alert('xss')</script>",
            "IN_PERSON"
        )));

        assertThat(stored.getReason()).isEqualTo("&lt;script&gt;alert(&#x27;xss&#x27;)&lt;/script&gt;");
        assertThat(notificationSender.scheduledAppointmentReminders()).hasSize(1);
        assertThat(notificationSender.scheduledAppointmentReminders().getFirst().channels())
            .containsExactlyInAnyOrder(
                PhrNotificationSender.NotificationChannel.EMAIL,
                PhrNotificationSender.NotificationChannel.SMS,
                PhrNotificationSender.NotificationChannel.PUSH
            );
    }

    @Test
    @DisplayName("rejects unsupported appointment type")
    void rejectsUnsupportedAppointmentType() {
        assertThrows(Exception.class, () -> runPromise(() -> service.createAppointment(new AppointmentRequest(
            "patient-1",
            "provider-1",
            "slot-1",
            Instant.now().plusSeconds(3600),
            30,
            "Consultation",
            "DROP_TABLE"
        ))));
        clearFatalError();
    }

    @Test
    @DisplayName("cancels scheduled reminders when appointment is cancelled")
    void cancelsScheduledReminders() {
        Appointment stored = runPromise(() -> service.createAppointment(new AppointmentRequest(
            "patient-1",
            "provider-1",
            "slot-1",
            Instant.now().plusSeconds(3600),
            30,
            "Consultation",
            "IN_PERSON"
        )));

        runPromise(() -> service.cancelAppointment(stored.getId(), "Rescheduled"));

        assertThat(notificationSender.cancelledAppointmentReminders()).hasSize(1);
        assertThat(notificationSender.cancelledAppointmentReminders().getFirst().appointmentId())
            .isEqualTo(stored.getId());
    }

    private static final class TestableAppointmentService extends AppointmentService {
        private final PhrTestInfrastructure.StubDataCloudAdapter dataCloud;

        private TestableAppointmentService(
                KernelContext context,
                PhrTestInfrastructure.StubDataCloudAdapter dataCloud,
                PhrNotificationSender notificationSender) {
            super(context, notificationSender);
            this.dataCloud = dataCloud;
        }

        private Promise<Void> seedAvailableSlot(String slotId, String providerId, Instant startTime) {
            TimeSlot slot = new TimeSlot(
                slotId,
                providerId,
                "2026-04-07",
                startTime,
                startTime.plusSeconds(1800),
                "AVAILABLE"
            );
            return dataCloud.writeData(new DataWriteRequest(
                "phr.appointment.slots",
                slotId,
                serialize(slot, "TimeSlot", 1),
                Map.of(
                    "slotId", slotId,
                    "providerId", providerId,
                    "startTime", startTime.toString(),
                    "available", "true"
                )
            ));
        }
    }
}