package com.ghatana.phr.service;

import com.ghatana.phr.kernel.retention.LegalHoldService;
import com.ghatana.phr.kernel.retention.PatientDeletionWorkflow;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PhrRetentionScheduler}.
 *
 * @doc.type test
 * @doc.purpose Verifies tenant-scoped retention scheduler deletion requests
 * @doc.layer test
 * @doc.pattern Unit Test
 */
@DisplayName("PHR Retention Scheduler")
class PhrRetentionSchedulerTest {

    @Test
    @DisplayName("Retention scan submits deletion requests with each eligible patient's tenant")
    void retentionScanSubmitsDeletionRequestsWithPatientTenant() {
        RecordingDeletionWorkflow deletionWorkflow = new RecordingDeletionWorkflow();
        PhrRetentionScheduler scheduler = new PhrRetentionScheduler(
            deletionWorkflow,
            deletionWorkflow.legalHoldService,
            cutoff -> List.of(
                new PhrRetentionScheduler.RetentionEligibilityPort.RetentionEligiblePatient("tenant-a", "patient-1"),
                new PhrRetentionScheduler.RetentionEligibilityPort.RetentionEligiblePatient("tenant-b", "patient-2")
            ),
            new NoOpConsentExpiryPort(),
            () -> 0,
            Duration.ofHours(1),
            Duration.ofHours(1),
            Duration.ofHours(1),
            30
        );

        scheduler.runRetentionScan();

        assertThat(deletionWorkflow.requests)
            .extracting(PatientDeletionWorkflow.DeletionRequest::tenantId)
            .containsExactly("tenant-a", "tenant-b");
        assertThat(deletionWorkflow.requests)
            .extracting(PatientDeletionWorkflow.DeletionRequest::patientId)
            .containsExactly("patient-1", "patient-2");
    }

    private static final class RecordingDeletionWorkflow extends PatientDeletionWorkflow {
        private final RecordingLegalHoldService legalHoldService = new RecordingLegalHoldService();
        private final List<DeletionRequest> requests = new ArrayList<>();

        private RecordingDeletionWorkflow() {
            super(new RecordingLegalHoldService(), (request, decision) -> Promise.complete());
        }

        @Override
        public Promise<DeletionReport> execute(DeletionRequest request, List<ResourceCandidate> resources) {
            requests.add(request);
            return Promise.of(new DeletionReport(
                request.requestId(),
                request.patientId(),
                request.tenantId(),
                Instant.now(),
                List.of(),
                false
            ));
        }
    }

    private static final class NoOpConsentExpiryPort implements PhrRetentionScheduler.ConsentExpiryPort {
        @Override
        public int markExpiredConsents() {
            return 0;
        }

        @Override
        public int purgeExpiredConsents(int gracePeriodDays) {
            return 0;
        }
    }

    private static final class RecordingLegalHoldService implements LegalHoldService {
        @Override
        public Promise<LegalHold> raiseHold(
            String tenantId,
            HoldScope scope,
            String scopeValue,
            String raisedBy,
            String reason,
            Instant expiresAt
        ) {
            return Promise.of(new LegalHold(
                UUID.randomUUID(),
                tenantId,
                scope,
                scopeValue,
                raisedBy,
                Instant.now(),
                reason,
                HoldStatus.ACTIVE,
                null,
                expiresAt
            ));
        }

        @Override
        public Promise<Void> releaseHold(UUID holdId, String releasedBy) {
            return Promise.complete();
        }

        @Override
        public Promise<Boolean> isUnderHold(String tenantId, String patientId, String resourceType, String resourceId) {
            return Promise.of(false);
        }

        @Override
        public Promise<List<LegalHold>> listActiveHolds(String tenantId) {
            return Promise.of(List.of());
        }

        @Override
        public Promise<Optional<LegalHold>> getHold(UUID holdId) {
            return Promise.of(Optional.empty());
        }
    }
}
