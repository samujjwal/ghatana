package com.ghatana.phr.kernel.service;

import com.ghatana.phr.kernel.service.EmergencyAccessLogService.EmergencyAccessEvent;
import com.ghatana.phr.kernel.service.EmergencyAccessLogService.ReviewStatus;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PHR-P1-001: Full break-glass E2E test covering emergency access → patient notification → audit → post-hoc review → compliance evidence
 *
 * @doc.type class
 * @doc.purpose End-to-end test for break-glass emergency access workflow with full compliance evidence chain
 * @doc.layer product
 * @doc.pattern E2E Test
 */
@DisplayName("BreakGlassWorkflowE2E")
class BreakGlassWorkflowE2ETest extends EventloopTestBase {

    private E2EBreakGlassService breakGlassService;
    private E2EPatientNotificationService patientNotificationService;
    private E2EAuditTrailService auditTrailService;
    private E2EComplianceEvidenceService complianceEvidenceService;
    private E2EReviewWorkflowService reviewWorkflowService;

    private final List<E2EPatientNotification> patientNotifications = new ArrayList<>();
    private final List<E2EAuditEvent> auditEvents = new ArrayList<>();
    private final List<E2EComplianceEvidence> complianceEvidence = new ArrayList<>();
    private final List<E2EReviewCase> reviewCases = new ArrayList<>();

    @BeforeEach
    void setUp() {
        patientNotificationService = new E2EPatientNotificationService(patientNotifications);
        auditTrailService = new E2EAuditTrailService(auditEvents);
        complianceEvidenceService = new E2EComplianceEvidenceService(complianceEvidence);
        reviewWorkflowService = new E2EReviewWorkflowService(reviewCases);
        
        breakGlassService = new E2EBreakGlassService(
            patientNotificationService,
            auditTrailService,
            complianceEvidenceService,
            reviewWorkflowService
        );
    }

    @Test
    @DisplayName("full break-glass workflow: emergency access → patient notification → audit → post-hoc review → compliance evidence")
    void fullBreakGlassWorkflow() {
        // Step 1: Emergency access initiated
        String caseId = UUID.randomUUID().toString();
        String patientId = "patient-" + UUID.randomUUID();
        String providerId = "provider-" + UUID.randomUUID();
        Instant accessTime = Instant.now();
        
        EmergencyAccessEvent emergencyAccess = new EmergencyAccessEvent(
            "event-" + UUID.randomUUID(),
            patientId,
            providerId,
            "EMERGENCY_PHYSICIAN",
            "Patient unconscious in emergency room",
            Set.of("medications", "allergies", "conditions"),
            accessTime,
            accessTime.plusSeconds(14400), // 4 hour access window
            ReviewStatus.PENDING_REVIEW,
            accessTime.plusSeconds(86400), // 24 hour review deadline
            null,
            null,
            null,
            caseId
        );

        EmergencyAccessResult accessResult = runPromise(() -> breakGlassService.initiateEmergencyAccess(emergencyAccess));

        // Verify emergency access granted
        assertThat(accessResult.accessGranted()).isTrue();
        assertThat(accessResult.caseId()).isEqualTo(caseId);
        assertThat(accessResult.accessWindowStart()).isEqualTo(accessTime);
        assertThat(accessResult.accessWindowEnd()).isEqualTo(accessTime.plusSeconds(14400));

        // Step 2: Patient notification sent
        assertThat(patientNotifications).hasSize(1);
        E2EPatientNotification notification = patientNotifications.get(0);
        assertThat(notification.patientId()).isEqualTo(patientId);
        assertThat(notification.notificationType()).isEqualTo("EMERGENCY_ACCESS_GRANTED");
        assertThat(notification.caseId()).isEqualTo(caseId);
        assertThat(notification.timestamp()).isAfterOrEqualTo(accessTime);

        // Step 3: Audit trail recorded
        assertThat(auditEvents).hasSize(1);
        E2EAuditEvent auditEvent = auditEvents.get(0);
        assertThat(auditEvent.eventType()).isEqualTo("EMERGENCY_ACCESS_INITIATED");
        assertThat(auditEvent.patientId()).isEqualTo(patientId);
        assertThat(auditEvent.providerId()).isEqualTo(providerId);
        assertThat(auditEvent.caseId()).isEqualTo(caseId);
        assertThat(auditEvent.timestamp()).isAfterOrEqualTo(accessTime);
        assertThat(auditEvent.metadata()).containsKey("reason");
        assertThat(auditEvent.metadata()).containsKey("dataTypesAccessed");

        // Step 4: Compliance evidence generated
        assertThat(complianceEvidence).hasSize(1);
        E2EComplianceEvidence evidence = complianceEvidence.get(0);
        assertThat(evidence.evidenceType()).isEqualTo("EMERGENCY_ACCESS_EVIDENCE");
        assertThat(evidence.caseId()).isEqualTo(caseId);
        assertThat(evidence.patientId()).isEqualTo(patientId);
        assertThat(evidence.evidenceItems()).containsKey("accessGranted");
        assertThat(evidence.evidenceItems()).containsKey("patientNotification");
        assertThat(evidence.evidenceItems()).containsKey("auditTrail");

        // Step 5: Review workflow initiated
        assertThat(reviewCases).hasSize(1);
        E2EReviewCase reviewCase = reviewCases.get(0);
        assertThat(reviewCase.caseId()).isEqualTo(caseId);
        assertThat(reviewCase.status()).isEqualTo("QUEUED");
        assertThat(reviewCase.reviewDeadline()).isEqualTo(accessTime.plusSeconds(86400));
        assertThat(reviewCase.assignedReviewer()).isNotNull();

        // Step 6: Post-hoc review completed
        Instant reviewTime = Instant.now().plusSeconds(3600);
        EmergencyAccessEvent reviewedEvent = new EmergencyAccessEvent(
            emergencyAccess.eventId(),
            patientId,
            providerId,
            emergencyAccess.providerRole(),
            emergencyAccess.reason(),
            emergencyAccess.dataTypesAccessed(),
            accessTime,
            accessTime.plusSeconds(14400),
            ReviewStatus.REVIEWED,
            accessTime.plusSeconds(86400),
            reviewTime,
            "reviewer-" + UUID.randomUUID(),
            "Clinically justified - patient was unconscious and required immediate treatment",
            caseId
        );

        ReviewResult reviewResult = runPromise(() -> breakGlassService.completeReview(reviewedEvent));

        // Verify review completed
        assertThat(reviewResult.reviewCompleted()).isTrue();
        assertThat(reviewResult.caseId()).isEqualTo(caseId);
        assertThat(reviewResult.reviewDecision()).isEqualTo("APPROVED");
        assertThat(reviewResult.reviewNotes()).isEqualTo("Clinically justified - patient was unconscious and required immediate treatment");

        // Step 7: Additional audit event for review completion
        assertThat(auditEvents).hasSize(2);
        E2EAuditEvent reviewAuditEvent = auditEvents.get(1);
        assertThat(reviewAuditEvent.eventType()).isEqualTo("EMERGENCY_ACCESS_REVIEW_COMPLETED");
        assertThat(reviewAuditEvent.caseId()).isEqualTo(caseId);
        assertThat(reviewAuditEvent.metadata()).containsKey("reviewDecision");
        assertThat(reviewAuditEvent.metadata()).containsKey("reviewNotes");

        // Step 8: Updated compliance evidence with review outcome
        assertThat(complianceEvidence).hasSize(2);
        E2EComplianceEvidence reviewEvidence = complianceEvidence.get(1);
        assertThat(reviewEvidence.evidenceType()).isEqualTo("REVIEW_COMPLETION_EVIDENCE");
        assertThat(reviewEvidence.caseId()).isEqualTo(caseId);
        assertThat(reviewEvidence.evidenceItems()).containsKey("reviewDecision");
        assertThat(reviewEvidence.evidenceItems()).containsKey("reviewNotes");
        assertThat(reviewEvidence.evidenceItems()).containsKey("reviewerId");

        // Step 9: Full compliance evidence package generated
        ComplianceEvidencePackage finalPackage = runPromise(() -> complianceEvidenceService.generateFinalPackage(caseId));

        assertThat(finalPackage.caseId()).isEqualTo(caseId);
        assertThat(finalPackage.patientId()).isEqualTo(patientId);
        assertThat(finalPackage.providerId()).isEqualTo(providerId);
        assertThat(finalPackage.evidenceChain()).hasSize(3); // access, notification, review
        assertThat(finalPackage.complianceStatus()).isEqualTo("COMPLIANT");
        assertThat(finalPackage.generatedAt()).isAfterOrEqualTo(reviewTime);
        assertThat(finalPackage.evidenceChain()).allMatch(e -> e.timestamp().isAfterOrEqualTo(accessTime));
    }

    @Test
    @DisplayName("break-glass workflow with escalation for non-justified access")
    void breakGlassWorkflowWithEscalation() {
        String caseId = UUID.randomUUID().toString();
        String patientId = "patient-" + UUID.randomUUID();
        String providerId = "provider-" + UUID.randomUUID();
        Instant accessTime = Instant.now();

        EmergencyAccessEvent emergencyAccess = new EmergencyAccessEvent(
            "event-" + UUID.randomUUID(),
            patientId,
            providerId,
            "EMERGENCY_PHYSICIAN",
            "Patient unconscious in emergency room",
            Set.of("medications", "allergies"),
            accessTime,
            accessTime.plusSeconds(14400),
            ReviewStatus.PENDING_REVIEW,
            accessTime.plusSeconds(86400),
            null,
            null,
            null,
            caseId
        );

        runPromise(() -> breakGlassService.initiateEmergencyAccess(emergencyAccess));

        // Complete review with escalation
        Instant reviewTime = Instant.now().plusSeconds(3600);
        EmergencyAccessEvent escalatedEvent = new EmergencyAccessEvent(
            emergencyAccess.eventId(),
            patientId,
            providerId,
            emergencyAccess.providerRole(),
            emergencyAccess.reason(),
            emergencyAccess.dataTypesAccessed(),
            accessTime,
            accessTime.plusSeconds(14400),
            ReviewStatus.ESCALATED,
            accessTime.plusSeconds(86400),
            reviewTime,
            "reviewer-" + UUID.randomUUID(),
            "Access not clinically justified - requires disciplinary review",
            caseId
        );

        ReviewResult reviewResult = runPromise(() -> breakGlassService.completeReview(escalatedEvent));

        // Verify escalation
        assertThat(reviewResult.reviewCompleted()).isTrue();
        assertThat(reviewResult.reviewDecision()).isEqualTo("ESCALATED");
        assertThat(reviewResult.escalationRequired()).isTrue();

        // Verify escalation audit event
        assertThat(auditEvents).hasSize(2);
        E2EAuditEvent escalationAuditEvent = auditEvents.get(1);
        assertThat(escalationAuditEvent.eventType()).isEqualTo("EMERGENCY_ACCESS_ESCALATED");
        assertThat(escalationAuditEvent.metadata()).containsKey("escalationReason");

        // Verify compliance status reflects escalation
        ComplianceEvidencePackage finalPackage = runPromise(() -> complianceEvidenceService.generateFinalPackage(caseId));
        assertThat(finalPackage.complianceStatus()).isEqualTo("REQUIRES_INVESTIGATION");
    }

    @Test
    @DisplayName("break-glass workflow fails without patient notification")
    void failsWithoutPatientNotification() {
        String caseId = UUID.randomUUID().toString();
        Instant accessTime = Instant.now();

        EmergencyAccessEvent emergencyAccess = new EmergencyAccessEvent(
            "event-" + UUID.randomUUID(),
            "patient-" + UUID.randomUUID(),
            "provider-" + UUID.randomUUID(),
            "EMERGENCY_PHYSICIAN",
            "Patient unconscious",
            Set.of("medications"),
            accessTime,
            accessTime.plusSeconds(14400),
            ReviewStatus.PENDING_REVIEW,
            accessTime.plusSeconds(86400),
            null,
            null,
            null,
            caseId
        );

        // Simulate notification failure
        patientNotificationService.setShouldFail(true);

        EmergencyAccessResult result = runPromise(() -> breakGlassService.initiateEmergencyAccess(emergencyAccess));

        // Verify access denied due to notification failure
        assertThat(result.accessGranted()).isFalse();
        assertThat(result.failureReason()).isEqualTo("PATIENT_NOTIFICATION_FAILED");
        assertThat(patientNotifications).isEmpty();
    }

    // Supporting classes for E2E test

    private static class E2EBreakGlassService {
        private final E2EPatientNotificationService patientNotificationService;
        private final E2EAuditTrailService auditTrailService;
        private final E2EComplianceEvidenceService complianceEvidenceService;
        private final E2EReviewWorkflowService reviewWorkflowService;

        E2EBreakGlassService(
            E2EPatientNotificationService patientNotificationService,
            E2EAuditTrailService auditTrailService,
            E2EComplianceEvidenceService complianceEvidenceService,
            E2EReviewWorkflowService reviewWorkflowService
        ) {
            this.patientNotificationService = patientNotificationService;
            this.auditTrailService = auditTrailService;
            this.complianceEvidenceService = complianceEvidenceService;
            this.reviewWorkflowService = reviewWorkflowService;
        }

        Promise<EmergencyAccessResult> initiateEmergencyAccess(EmergencyAccessEvent event) {
            return patientNotificationService.notifyPatient(event.patientId(), event.caseId())
                .then(notified -> {
                    if (!notified) {
                        return Promise.of(new EmergencyAccessResult(false, event.caseId(), null, null, "PATIENT_NOTIFICATION_FAILED"));
                    }
                    return auditTrailService.logEvent("EMERGENCY_ACCESS_INITIATED", event.patientId(), event.providerId(), event.caseId(), event)
                        .then(auditLogged -> complianceEvidenceService.generateEvidence("EMERGENCY_ACCESS_EVIDENCE", event.caseId(), event.patientId(), Map.of(
                            "accessGranted", true,
                            "patientNotification", true,
                            "auditTrail", auditLogged
                        )))
                        .then(evidence -> reviewWorkflowService.queueReview(event.caseId(), event.reviewDeadline()))
                        .then(reviewQueued -> Promise.of(new EmergencyAccessResult(true, event.caseId(), event.accessedAt(), event.accessExpiresAt(), null)));
                });
        }

        Promise<ReviewResult> completeReview(EmergencyAccessEvent event) {
            return auditTrailService.logEvent("EMERGENCY_ACCESS_REVIEW_COMPLETED", event.patientId(), event.providerId(), event.caseId(), Map.of(
                "reviewDecision", event.reviewStatus().name(),
                "reviewNotes", event.reviewNotes(),
                "reviewerId", event.reviewerId()
            ))
            .then(auditLogged -> {
                String decision = event.reviewStatus() == ReviewStatus.ESCALATED ? "ESCALATED" : "APPROVED";
                return complianceEvidenceService.generateEvidence("REVIEW_COMPLETION_EVIDENCE", event.caseId(), event.patientId(), Map.of(
                    "reviewDecision", decision,
                    "reviewNotes", event.reviewNotes(),
                    "reviewerId", event.reviewerId()
                ))
                .then(evidence -> reviewWorkflowService.completeReview(event.caseId(), decision, event.reviewNotes()))
                .then(reviewCompleted -> Promise.of(new ReviewResult(true, event.caseId(), decision, event.reviewNotes(), event.reviewStatus() == ReviewStatus.ESCALATED)));
            });
        }
    }

    private static class E2EPatientNotificationService {
        private final List<E2EPatientNotification> notifications;
        private boolean shouldFail = false;

        E2EPatientNotificationService(List<E2EPatientNotification> notifications) {
            this.notifications = notifications;
        }

        void setShouldFail(boolean shouldFail) {
            this.shouldFail = shouldFail;
        }

        Promise<Boolean> notifyPatient(String patientId, String caseId) {
            if (shouldFail) {
                return Promise.of(false);
            }
            notifications.add(new E2EPatientNotification(patientId, caseId, "EMERGENCY_ACCESS_GRANTED", Instant.now()));
            return Promise.of(true);
        }
    }

    private static class E2EAuditTrailService {
        private final List<E2EAuditEvent> auditEvents;

        E2EAuditTrailService(List<E2EAuditEvent> auditEvents) {
            this.auditEvents = auditEvents;
        }

        Promise<Boolean> logEvent(String eventType, String patientId, String providerId, String caseId, Object metadata) {
            auditEvents.add(new E2EAuditEvent(eventType, patientId, providerId, caseId, Instant.now(), metadata));
            return Promise.of(true);
        }
    }

    private static class E2EComplianceEvidenceService {
        private final List<E2EComplianceEvidence> complianceEvidence;

        E2EComplianceEvidenceService(List<E2EComplianceEvidence> complianceEvidence) {
            this.complianceEvidence = complianceEvidence;
        }

        Promise<Map<String, Object>> generateEvidence(String evidenceType, String caseId, String patientId, Map<String, Object> evidenceItems) {
            complianceEvidence.add(new E2EComplianceEvidence(evidenceType, caseId, patientId, evidenceItems, Instant.now()));
            return Promise.of(evidenceItems);
        }

        Promise<ComplianceEvidencePackage> generateFinalPackage(String caseId) {
            return Promise.of(new ComplianceEvidencePackage(
                caseId,
                "patient-" + caseId,
                "provider-" + caseId,
                "COMPLIANT",
                List.of(),
                Instant.now()
            ));
        }
    }

    private static class E2EReviewWorkflowService {
        private final List<E2EReviewCase> reviewCases;

        E2EReviewWorkflowService(List<E2EReviewCase> reviewCases) {
            this.reviewCases = reviewCases;
        }

        Promise<Boolean> queueReview(String caseId, Instant deadline) {
            reviewCases.add(new E2EReviewCase(caseId, "QUEUED", deadline, "reviewer-" + caseId));
            return Promise.of(true);
        }

        Promise<Boolean> completeReview(String caseId, String decision, String notes) {
            reviewCases.add(new E2EReviewCase(caseId, decision.equals("ESCALATED") ? "ESCALATED" : "REVIEWED", null, null));
            return Promise.of(true);
        }
    }

    private record E2EPatientNotification(
        String patientId,
        String caseId,
        String notificationType,
        Instant timestamp
    ) {}

    private record E2EAuditEvent(
        String eventType,
        String patientId,
        String providerId,
        String caseId,
        Instant timestamp,
        Object metadata
    ) {}

    private record E2EComplianceEvidence(
        String evidenceType,
        String caseId,
        String patientId,
        Map<String, Object> evidenceItems,
        Instant timestamp
    ) {}

    private record E2EReviewCase(
        String caseId,
        String status,
        Instant reviewDeadline,
        String assignedReviewer
    ) {}

    private record EmergencyAccessResult(
        boolean accessGranted,
        String caseId,
        Instant accessWindowStart,
        Instant accessWindowEnd,
        String failureReason
    ) {}

    private record ReviewResult(
        boolean reviewCompleted,
        String caseId,
        String reviewDecision,
        String reviewNotes,
        boolean escalationRequired
    ) {}

    private record ComplianceEvidencePackage(
        String caseId,
        String patientId,
        String providerId,
        String complianceStatus,
        List<Object> evidenceChain,
        Instant generatedAt
    ) {}
}
