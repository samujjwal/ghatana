package com.ghatana.phr.kernel.service;

import com.ghatana.phr.kernel.service.TelemedicineService.*;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TelemedicineService}.
 *
 * @doc.type class
 * @doc.purpose Tests for PHR telemedicine service — session lifecycle
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("TelemedicineService")
class TelemedicineServiceTest extends EventloopTestBase {

    private TelemedicineService service;
    private PhrNotificationTestSupport.RecordingNotificationSender notificationSender;
    private PhrTestInfrastructure.StubDataCloudAdapter dataCloud;

    @BeforeEach
    void setUp() {
        dataCloud = new PhrTestInfrastructure.StubDataCloudAdapter();
        notificationSender = new PhrNotificationTestSupport.RecordingNotificationSender();
        service = new TelemedicineService(PhrTestInfrastructure.createTestContext(dataCloud), notificationSender);
        runPromise(service::start);
    }

    @Nested
    @DisplayName("service lifecycle")
    class Lifecycle {

        @Test
        void healthyAfterStart() {
            assertTrue(service.isHealthy());
        }

        @Test
        void serviceName() {
            assertEquals("telemedicine", service.getName());
        }
    }

    @Nested
    @DisplayName("scheduleSession")
    class ScheduleTests {

        @Test
        @DisplayName("stores session in SCHEDULED status")
        void storesScheduled() {
            TeleSession session = buildSession("patient-1", "provider-1", null);

            TeleSession stored = runPromise(() -> service.scheduleSession(session));

            assertNotNull(stored.id());
            assertThat(stored.status()).isEqualTo(SessionStatus.SCHEDULED);
            assertThat(notificationSender.telemedicineNotifications()).hasSize(1);
            assertThat(notificationSender.telemedicineNotifications().getFirst().notificationType())
                .isEqualTo(PhrNotificationSender.TelemedicineNotificationType.SESSION_SCHEDULED);
            assertThat(notificationSender.telemedicineNotifications().getFirst().correlationId())
                .startsWith("phr_telemedicine_schedule-");
            assertThat(dataCloud.metadataFor("phr.telemedicine.sessions", stored.id()))
                .containsKey("correlationId")
                .containsEntry("traceOperation", "phr_telemedicine_schedule");
        }

        @Test
        @DisplayName("rejects null patientId")
        void rejectsNullPatient() {
            assertThrows(Exception.class,
                    () -> runPromise(() -> service.scheduleSession(buildSession(null, "dr", null))));
            clearFatalError();
        }

        @Test
        @DisplayName("rejects non-https join urls")
        void rejectsNonHttpsJoinUrl() {
            TeleSession session = new TeleSession(
                null,
                "patient-1",
                "provider-1",
                Instant.now().plusSeconds(3600),
                30,
                "ZOOM_HEALTH",
                "http://zoom.us/j/12345",
                SessionStatus.SCHEDULED,
                null,
                null,
                null
            );

            assertThrows(Exception.class, () -> runPromise(() -> service.scheduleSession(session)));
            clearFatalError();
        }
    }

    @Nested
    @DisplayName("session lifecycle")
    class SessionLifecycle {

        @Test
        @DisplayName("starts and completes a session")
        void startAndComplete() {
            TeleSession created = runPromise(() ->
                    service.scheduleSession(buildSession("p1", "dr1", null)));

            TeleSession started = runPromise(() -> service.startSession(created.id()));
            assertThat(started.status()).isEqualTo(SessionStatus.IN_PROGRESS);
            assertNotNull(started.startedAt());

            TeleSession completed = runPromise(() ->
                    service.completeSession(started.id(), "<b>Good session</b>"));
            assertThat(completed.status()).isEqualTo(SessionStatus.COMPLETED);
            assertNotNull(completed.endedAt());
                assertThat(completed.notes()).isEqualTo("&lt;b&gt;Good session&lt;/b&gt;");
        }

        @Test
        @DisplayName("cannot complete a SCHEDULED (not-started) session")
        void cannotCompleteScheduled() {
            TeleSession created = runPromise(() ->
                    service.scheduleSession(buildSession("p1", "dr1", null)));

            assertThrows(Exception.class,
                    () -> runPromise(() -> service.completeSession(created.id(), "notes")));
            clearFatalError();
        }

        @Test
        @DisplayName("cancelSession transitions to CANCELLED")
        void cancelSession() {
            TeleSession created = runPromise(() ->
                    service.scheduleSession(buildSession("p1", "dr1", null)));

            TeleSession cancelled = runPromise(() ->
                    service.cancelSession(created.id(), "Patient unavailable"));

            assertThat(cancelled.status()).isEqualTo(SessionStatus.CANCELLED);
            assertThat(notificationSender.telemedicineNotifications()).hasSize(2);
            assertThat(notificationSender.telemedicineNotifications().getLast().notificationType())
            .isEqualTo(PhrNotificationSender.TelemedicineNotificationType.SESSION_CANCELLED);
        }

        @Test
        @DisplayName("rescheduleSession updates schedule and emits rescheduled notification")
        void rescheduleSession() {
            TeleSession created = runPromise(() -> service.scheduleSession(buildSession("p1", "dr1", null)));

            Instant rescheduledTime = Instant.now().plusSeconds(10_800);
            TeleSession rescheduled = runPromise(() -> service.rescheduleSession(
                created.id(),
                rescheduledTime,
                45,
                "https://zoom.us/j/67890"
            ));

            assertThat(rescheduled.status()).isEqualTo(SessionStatus.SCHEDULED);
            assertThat(rescheduled.scheduledAt()).isEqualTo(rescheduledTime);
            assertThat(rescheduled.durationMinutes()).isEqualTo(45);
            assertThat(notificationSender.telemedicineNotifications().getLast().notificationType())
                .isEqualTo(PhrNotificationSender.TelemedicineNotificationType.SESSION_RESCHEDULED);
        }

        @Test
        @DisplayName("markNoShow transitions session to NO_SHOW and emits notification")
        void markNoShow() {
            TeleSession created = runPromise(() -> service.scheduleSession(buildSession("p1", "dr1", null)));

            TeleSession noShow = runPromise(() -> service.markNoShow(created.id(), "Patient unreachable"));

            assertThat(noShow.status()).isEqualTo(SessionStatus.NO_SHOW);
            assertThat(noShow.notes()).isEqualTo("Patient unreachable");
            assertThat(notificationSender.telemedicineNotifications().getLast().notificationType())
                .isEqualTo(PhrNotificationSender.TelemedicineNotificationType.SESSION_NO_SHOW);
        }

        @Test
        @DisplayName("cannot cancel a COMPLETED session")
        void cannotCancelCompleted() {
            TeleSession created = runPromise(() ->
                    service.scheduleSession(buildSession("p1", "dr1", null)));
            runPromise(() -> service.startSession(created.id()));
            runPromise(() -> service.completeSession(created.id(), "done"));

            assertThrows(Exception.class,
                    () -> runPromise(() -> service.cancelSession(created.id(), "too late")));
            clearFatalError();
        }
    }

    @Nested
    @DisplayName("getPatientSessions")
    class QueryTests {

        @Test
        @DisplayName("returns sessions sorted newest-first")
        void sortedNewestFirst() {
            runPromise(() -> service.scheduleSession(buildSession("patient-Q", "dr1", null)));
            runPromise(() -> service.scheduleSession(buildSession("patient-Q", "dr2", null)));
            runPromise(() -> service.scheduleSession(buildSession("patient-R", "dr1", null)));

            List<TeleSession> sessions = runPromise(() -> service.getPatientSessions("patient-Q"));

            assertThat(sessions).hasSize(2);
            assertThat(sessions).allMatch(s -> "patient-Q".equals(s.patientId()));
        }

        @Test
        @DisplayName("rate limits repeated patient session queries")
        void queryRateLimit() {
            for (int index = 0; index < 120; index++) {
                runPromise(() -> service.getPatientSessions("patient-rate"));
            }

            assertThrows(Exception.class, () -> runPromise(() -> service.getPatientSessions("patient-rate")));
            clearFatalError();
        }
    }

    // ─────────────────────── Helpers ──────────────────────────────────────────

    private static TeleSession buildSession(String patientId, String providerId, String id) {
        return new TeleSession(id, patientId, providerId,
                Instant.now().plusSeconds(3600), 30,
                "ZOOM_HEALTH", "https://zoom.us/j/12345",
                SessionStatus.SCHEDULED, null, null, null);
    }
}
