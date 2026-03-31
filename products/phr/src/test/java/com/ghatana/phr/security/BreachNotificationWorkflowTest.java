package com.ghatana.phr.security;

import com.ghatana.phr.security.BreachNotificationWorkflow.*;
import io.activej.promise.Promise;
import io.activej.test.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link BreachNotificationWorkflow}.
 *
 * @doc.type class
 * @doc.purpose Validates 72-hour breach notification pipeline logic
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("BreachNotificationWorkflow")
class BreachNotificationWorkflowTest extends EventloopTestBase {

    private RecordingNotificationSender notificationSender;
    private RecordingAuditLogger auditLogger;
    private RecordingContainmentService containmentService;
    private BreachNotificationWorkflow workflow;

    @BeforeEach
    void setUp() {
        notificationSender = new RecordingNotificationSender();
        auditLogger = new RecordingAuditLogger();
        containmentService = new RecordingContainmentService();
        workflow = new BreachNotificationWorkflow(notificationSender, auditLogger, containmentService);
    }

    @Nested
    @DisplayName("SEV-1 breach")
    class Sev1BreachTests {

        @Test
        @DisplayName("triggers containment, compliance notification, regulatory notification, and patient notification")
        void fullWorkflow() {
            BreachReport report = new BreachReport(
                    "tenant-hospital-1",
                    BreachSeverity.SEV_1,
                    "Cross-tenant PHI exposure in lab results",
                    250,
                    true,
                    List.of("lab-service", "data-store"),
                    "security-team"
            );

            BreachCase result = runPromise(() -> workflow.initiate(report));

            assertThat(result.caseId()).startsWith("BREACH-");
            assertThat(result.status()).isEqualTo(BreachStatus.NOTIFICATION_PENDING);
            assertThat(containmentService.containedTenants).contains("tenant-hospital-1");
            assertThat(notificationSender.complianceNotifications).hasSize(1);
            assertThat(notificationSender.regulatoryScheduled).hasSize(1);
            assertThat(notificationSender.patientNotifications).hasSize(1);
            assertThat(notificationSender.postIncidentReviews).hasSize(1);
            assertThat(auditLogger.loggedCases).hasSize(1);
        }

        @Test
        @DisplayName("sets regulatory deadline to 72 hours from initiation")
        void regulatoryDeadline() {
            BreachReport report = new BreachReport(
                    "tenant-1", BreachSeverity.SEV_1, "breach",
                    10, true, List.of(), "reporter"
            );

            BreachCase result = runPromise(() -> workflow.initiate(report));

            assertThat(result.regulatoryDeadline())
                    .isAfter(result.initiatedAt())
                    .isBefore(result.initiatedAt().plusSeconds(72 * 3600 + 60));
        }
    }

    @Nested
    @DisplayName("SEV-2 breach")
    class Sev2BreachTests {

        @Test
        @DisplayName("triggers regulatory notification when >50 patients affected")
        void regulatoryRequiredAboveThreshold() {
            BreachReport report = new BreachReport(
                    "tenant-2", BreachSeverity.SEV_2, "Partial compromise",
                    100, false, List.of("api-gateway"), "sec-ops"
            );

            BreachCase result = runPromise(() -> workflow.initiate(report));

            assertThat(notificationSender.regulatoryScheduled).hasSize(1);
            assertThat(result.status()).isEqualTo(BreachStatus.NOTIFICATION_PENDING);
        }

        @Test
        @DisplayName("skips regulatory notification when <50 patients and no PHI exposed")
        void noRegulatoryBelowThreshold() {
            BreachReport report = new BreachReport(
                    "tenant-2", BreachSeverity.SEV_2, "Limited breach",
                    30, false, List.of("audit-service"), "sec-ops"
            );

            BreachCase result = runPromise(() -> workflow.initiate(report));

            assertThat(notificationSender.regulatoryScheduled).isEmpty();
            assertThat(notificationSender.patientNotifications).isEmpty();
            // Compliance is always notified
            assertThat(notificationSender.complianceNotifications).hasSize(1);
        }
    }

    @Nested
    @DisplayName("SEV-3 breach")
    class Sev3BreachTests {

        @Test
        @DisplayName("triggers regulatory notification only if PHI exposed")
        void regulatoryOnlyIfPhiExposed() {
            BreachReport withPhi = new BreachReport(
                    "tenant-3", BreachSeverity.SEV_3, "Contained issue",
                    5, true, List.of(), "dev-team"
            );

            BreachCase result = runPromise(() -> workflow.initiate(withPhi));

            assertThat(notificationSender.regulatoryScheduled).hasSize(1);
        }

        @Test
        @DisplayName("skips regulatory notification when no PHI exposed")
        void noRegulatoryWithoutPhi() {
            BreachReport withoutPhi = new BreachReport(
                    "tenant-3", BreachSeverity.SEV_3, "Config exposure only",
                    0, false, List.of(), "dev-team"
            );

            BreachCase result = runPromise(() -> workflow.initiate(withoutPhi));

            assertThat(notificationSender.regulatoryScheduled).isEmpty();
            assertThat(containmentService.containedTenants).contains("tenant-3");
        }
    }

    // ──────────── Recording stubs ────────────

    private static class RecordingNotificationSender implements NotificationSender {
        final List<BreachCase> complianceNotifications = new ArrayList<>();
        final List<BreachCase> regulatoryScheduled = new ArrayList<>();
        final List<BreachCase> patientNotifications = new ArrayList<>();
        final List<BreachCase> postIncidentReviews = new ArrayList<>();

        @Override
        public Promise<Void> notifyComplianceLead(BreachCase breachCase) {
            complianceNotifications.add(breachCase);
            return Promise.complete();
        }

        @Override
        public Promise<Void> scheduleRegulatoryNotification(BreachCase breachCase) {
            regulatoryScheduled.add(breachCase);
            return Promise.complete();
        }

        @Override
        public Promise<Void> notifyAffectedPatients(BreachCase breachCase) {
            patientNotifications.add(breachCase);
            return Promise.complete();
        }

        @Override
        public Promise<Void> schedulePostIncidentReview(BreachCase breachCase) {
            postIncidentReviews.add(breachCase);
            return Promise.complete();
        }
    }

    private static class RecordingAuditLogger implements BreachAuditLogger {
        final List<BreachCase> loggedCases = new ArrayList<>();

        @Override
        public Promise<Void> logBreachInitiated(BreachCase breachCase) {
            loggedCases.add(breachCase);
            return Promise.complete();
        }
    }

    private static class RecordingContainmentService implements ContainmentService {
        final List<String> containedTenants = new ArrayList<>();

        @Override
        public Promise<Void> executeContainment(String tenantId, BreachSeverity severity) {
            containedTenants.add(tenantId);
            return Promise.complete();
        }
    }
}
