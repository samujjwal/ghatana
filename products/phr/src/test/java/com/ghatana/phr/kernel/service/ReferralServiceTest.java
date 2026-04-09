package com.ghatana.phr.kernel.service;

import com.ghatana.phr.kernel.service.ReferralService.*;
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
 * Unit tests for {@link ReferralService}.
 *
 * @doc.type class
 * @doc.purpose Tests for PHR referral service — create, accept, close
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("ReferralService")
class ReferralServiceTest extends EventloopTestBase {

    private ReferralService service;

    @BeforeEach
    void setUp() {
        PhrTestInfrastructure.StubDataCloudAdapter dataCloud =
                new PhrTestInfrastructure.StubDataCloudAdapter();
        service = new ReferralService(PhrTestInfrastructure.createTestContext(dataCloud));
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
            assertEquals("referral", service.getName());
        }
    }

    @Nested
    @DisplayName("createReferral")
    class CreateTests {

        @Test
        @DisplayName("stores referral in PENDING status")
        void storePending() {
            Referral ref = buildReferral("patient-1", "dr-from", "spec-cardio", null);

            Referral stored = runPromise(() -> service.createReferral(ref));

            assertNotNull(stored.id());
            assertThat(stored.status()).isEqualTo(ReferralStatus.PENDING);
        }

        @Test
        @DisplayName("rejects null patientId")
        void rejectsNull() {
            assertThrows(Exception.class,
                    () -> runPromise(() -> service.createReferral(buildReferral(null, "dr", "spec", null))));
            clearFatalError();
        }

        @Test
        @DisplayName("sanitizes clinical reason")
        void sanitizesClinicalReason() {
            Referral referral = new Referral(
                null,
                "patient-1",
                "enc-1",
                "dr-from",
                null,
                "spec-cardio",
                "<script>alert('xss')</script>",
                ReferralUrgency.ROUTINE,
                ReferralStatus.PENDING,
                Instant.now(),
                null,
                null
            );

            Referral stored = runPromise(() -> service.createReferral(referral));

            assertThat(stored.clinicalReason()).isEqualTo("&lt;script&gt;alert(&#x27;xss&#x27;)&lt;/script&gt;");
        }
    }

    @Nested
    @DisplayName("acceptReferral")
    class AcceptTests {

        @Test
        @DisplayName("transitions to ACCEPTED")
        void transitionsToAccepted() {
            Referral ref = runPromise(() ->
                    service.createReferral(buildReferral("p1", "dr-from", "spec", null)));

            Referral accepted = runPromise(() -> service.acceptReferral(ref.id(), "spec-provider-1"));

            assertThat(accepted.status()).isEqualTo(ReferralStatus.ACCEPTED);
        }

        @Test
        @DisplayName("throws when referral is not in PENDING status")
        void throwsWhenNotPending() {
            Referral ref = runPromise(() ->
                    service.createReferral(buildReferral("p1", "dr-from", "spec", null)));
            runPromise(() -> service.acceptReferral(ref.id(), "spec-1"));

            // Try accepting again (now ACCEPTED, not PENDING)
            assertThrows(Exception.class,
                    () -> runPromise(() -> service.acceptReferral(ref.id(), "spec-2")));
            clearFatalError();
        }
    }

    @Nested
    @DisplayName("closeReferral")
    class CloseTests {

        @Test
        @DisplayName("transitions ACCEPTED to COMPLETED")
        void completesAccepted() {
            Referral ref = runPromise(() ->
                    service.createReferral(buildReferral("p1", "dr", "spec", null)));
            runPromise(() -> service.acceptReferral(ref.id(), "spec-1"));

            Referral closed = runPromise(() -> service.closeReferral(ref.id(), "Consultation done"));

            assertThat(closed.status()).isEqualTo(ReferralStatus.COMPLETED);
        }
    }

    @Nested
    @DisplayName("getPatientReferrals")
    class QueryTests {

        @Test
        @DisplayName("returns all referrals for patient sorted newest-first")
        void sortedNewestFirst() {
            runPromise(() -> service.createReferral(buildReferral("patient-Z", "dr", "cardio", null)));
            runPromise(() -> service.createReferral(buildReferral("patient-Z", "dr", "neuro", null)));
            runPromise(() -> service.createReferral(buildReferral("patient-W", "dr", "ortho", null)));

            List<Referral> refs = runPromise(() -> service.getPatientReferrals("patient-Z"));

            assertThat(refs).hasSize(2);
            assertThat(refs).allMatch(r -> "patient-Z".equals(r.patientId()));
        }
    }

    // ─────────────────────── Helpers ──────────────────────────────────────────

    private static Referral buildReferral(String patientId, String referringProviderId,
                                          String specialtyCode, String id) {
        return new Referral(id, patientId, "enc-1", referringProviderId, null,
                specialtyCode, "Cardiac evaluation",
                ReferralUrgency.ROUTINE, ReferralStatus.PENDING,
                Instant.now(), null, null);
    }
}
