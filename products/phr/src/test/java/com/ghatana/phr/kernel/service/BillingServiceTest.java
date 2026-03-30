package com.ghatana.phr.kernel.service;

import com.ghatana.phr.kernel.service.BillingService.*;
import com.ghatana.platform.billing.BillingTransaction;
import com.ghatana.platform.billing.BillingTransactionCoordinator;
import com.ghatana.platform.billing.LedgerPostingService;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link BillingService}.
 *
 * @doc.type class
 * @doc.purpose Tests for PHR billing service — encounters, claims, status updates
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("BillingService")
class BillingServiceTest extends EventloopTestBase {

    private BillingService service;

    /** Service wired with the capturing ledger stub. */
    private BillingService serviceWithLedger;
    private BillingService serviceWithCoordinator;
    private CapturingLedgerPostingService capturingLedger;
    private CapturingCoordinator capturingCoordinator;

    @BeforeEach
    void setUp() {
        PhrTestInfrastructure.StubDataCloudAdapter dataCloud =
                new PhrTestInfrastructure.StubDataCloudAdapter();
        service = new BillingService(PhrTestInfrastructure.createTestContext(dataCloud));
        runPromise(service::start);
        capturingLedger = new CapturingLedgerPostingService();
        serviceWithLedger = new BillingService(
            PhrTestInfrastructure.createTestContext(dataCloud), capturingLedger);
        runPromise(serviceWithLedger::start);

        capturingCoordinator = new CapturingCoordinator();
        serviceWithCoordinator = new BillingService(
            PhrTestInfrastructure.createTestContext(dataCloud),
            capturingLedger,
            capturingCoordinator
        );
        runPromise(serviceWithCoordinator::start);
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
            assertEquals("billing", service.getName());
        }
    }

    @Nested
    @DisplayName("createEncounter")
    class EncounterTests {

        @Test
        @DisplayName("creates encounter in OPEN status")
        void createsOpen() {
            BillingEncounter enc = buildEncounter("patient-1", "provider-1", null);

            BillingEncounter stored = runPromise(() -> service.createEncounter(enc));

            assertNotNull(stored.id());
            assertThat(stored.status()).isEqualTo(EncounterStatus.OPEN);
        }

        @Test
        @DisplayName("rejects null patientId")
        void rejectsNull() {
            assertThrows(Exception.class,
                    () -> runPromise(() -> service.createEncounter(buildEncounter(null, "dr", null))));
            clearFatalError();
        }
    }

    @Nested
    @DisplayName("closeEncounter")
    class CloseTests {

        @Test
        @DisplayName("transitions encounter to CLOSED")
        void closesEncounter() {
            BillingEncounter enc = runPromise(() ->
                    service.createEncounter(buildEncounter("p1", "dr", null)));

            BillingEncounter closed = runPromise(() -> service.closeEncounter(enc.id()));

            assertThat(closed.status()).isEqualTo(EncounterStatus.CLOSED);
            assertNotNull(closed.closedAt());
        }

        @Test
        @DisplayName("throws for unknown encounter")
        void throwsForUnknown() {
            assertThrows(Exception.class, () -> runPromise(() -> service.closeEncounter("ghost")));
            clearFatalError();
        }
    }

    @Nested
    @DisplayName("submitClaim")
    class ClaimTests {

        @Test
        @DisplayName("submits claim in SUBMITTED status")
        void submits() {
            BillingEncounter enc = runPromise(() ->
                    service.createEncounter(buildEncounter("p1", "dr1", null)));

            InsuranceClaim claim = buildClaim("p1", enc.id(), "NHSF");
            InsuranceClaim stored = runPromise(() -> service.submitClaim(claim));

            assertNotNull(stored.id());
            assertThat(stored.status()).isEqualTo(ClaimStatus.SUBMITTED);
            assertThat(stored.insurerId()).isEqualTo("NHSF");
        }
    }

    @Nested
    @DisplayName("updateClaimStatus")
    class StatusUpdateTests {

        @Test
        @DisplayName("transitions claim to APPROVED")
        void approveClaim() {
            InsuranceClaim claim = runPromise(() -> service.submitClaim(
                    buildClaim("p1", "enc-1", "NHSF")));

            InsuranceClaim approved = runPromise(() ->
                    service.updateClaimStatus(claim.id(), ClaimStatus.APPROVED, "All valid"));

            assertThat(approved.status()).isEqualTo(ClaimStatus.APPROVED);
            assertNotNull(approved.adjudicatedAt());
        }

        @Test
        @DisplayName("transitions claim to DENIED")
        void denyClaim() {
            InsuranceClaim claim = runPromise(() -> service.submitClaim(
                    buildClaim("p1", "enc-1", "NHSF")));

            InsuranceClaim denied = runPromise(() ->
                    service.updateClaimStatus(claim.id(), ClaimStatus.DENIED, "Not covered"));

            assertThat(denied.status()).isEqualTo(ClaimStatus.DENIED);
        }
    }

    @Nested
    @DisplayName("getPatientBillingHistory")
    class HistoryTests {

        @Test
        @DisplayName("returns all encounters for patient")
        void returnsHistory() {
            runPromise(() -> service.createEncounter(buildEncounter("patient-M", "dr1", null)));
            runPromise(() -> service.createEncounter(buildEncounter("patient-M", "dr2", null)));
            runPromise(() -> service.createEncounter(buildEncounter("patient-N", "dr1", null)));

            List<BillingEncounter> history = runPromise(() ->
                    service.getPatientBillingHistory("patient-M"));

            assertThat(history).hasSize(2);
            assertThat(history).allMatch(e -> "patient-M".equals(e.patientId()));
        }
    }

    // ─────────────────────── Helpers ──────────────────────────────────────────

    @Nested
    @DisplayName("Finance ledger integration")
    class LedgerIntegration {

        @Test
        @DisplayName("closing an encounter posts a CHARGE BillingTransaction to the ledger")
        void closingEncounterPostsToLedger() {
            BillingEncounter enc = runPromise(() ->
                serviceWithLedger.createEncounter(buildEncounter("p-L1", "dr-L1", null)));

            runPromise(() -> serviceWithLedger.closeEncounter(enc.id()));

            assertEquals(1, capturingLedger.posted.size());
            BillingTransaction tx = capturingLedger.posted.get(0);
            assertEquals("enc:" + enc.id(), tx.getTransactionId());
            assertEquals("phr", tx.getSourceProductId());
            assertEquals("PHR:AR:p-L1", tx.getDebitAccount());
            assertEquals("PHR:REVENUE:dr-L1", tx.getCreditAccount());
            assertEquals(0, new BigDecimal("1200.00").compareTo(tx.getAmount()));
            assertEquals("NPR", tx.getCurrency());
            assertEquals(BillingTransaction.TransactionType.CHARGE, tx.getType());
        }

        @Test
        @DisplayName("no ledger post when LedgerPostingService is not wired")
        void noLedgerPostWhenNotWired() {
            BillingEncounter enc = runPromise(() ->
                service.createEncounter(buildEncounter("p-L2", "dr-L2", null)));

            runPromise(() -> service.closeEncounter(enc.id()));

            assertTrue(capturingLedger.posted.isEmpty(),
                "Ledger must not be called when service has no LedgerPostingService");
        }

        @Test
        @DisplayName("the transaction ID uses the encounter ID for idempotency")
        void transactionIdContainsEncounterId() {
            BillingEncounter enc = runPromise(() ->
                serviceWithLedger.createEncounter(buildEncounter("p-L3", "dr-L3", null)));

            runPromise(() -> serviceWithLedger.closeEncounter(enc.id()));

            BillingTransaction tx = capturingLedger.posted.get(0);
            assertTrue(tx.getTransactionId().contains(enc.id()),
                "Transaction ID must contain encounter ID for ledger idempotency");
        }

        @Test
        @DisplayName("coordinator path is used when BillingTransactionCoordinator is wired")
        void coordinatorPathUsedWhenConfigured() {
            BillingEncounter enc = runPromise(() ->
                serviceWithCoordinator.createEncounter(buildEncounter("p-L4", "dr-L4", null)));

            runPromise(() -> serviceWithCoordinator.closeEncounter(enc.id()));

            assertEquals(1, capturingCoordinator.invocations.size());
            assertEquals("phr-encounter-close:" + enc.id(), capturingCoordinator.invocations.get(0));
        }
    }

    private static BillingEncounter buildEncounter(String patientId, String providerId, String id) {
        List<ServiceLine> lines = List.of(
                new ServiceLine("99213", "Office visit", 1, new BigDecimal("1200.00"), "NPR")
        );
        return new BillingEncounter(id, patientId, providerId, "facility-1",
                lines, new BigDecimal("1200.00"), "NPR",
                EncounterStatus.OPEN, null, null);
    }

    private static InsuranceClaim buildClaim(String patientId, String encounterId,
                                              String insurerId) {
        return new InsuranceClaim(null, patientId, encounterId, insurerId,
                "POLICY-12345", new BigDecimal("1200.00"), "NPR",
                ClaimStatus.SUBMITTED, null, null, null);
    }

    // ─────────────────────── Stubs ────────────────────────────────────────────

    /**
     * Captures every posted {@link BillingTransaction} for assertion in tests.
     *
     * @doc.type class
     * @doc.purpose Stub LedgerPostingService for PHR billing integration tests
     * @doc.layer product
     * @doc.pattern TestDouble
     */
    static final class CapturingLedgerPostingService implements LedgerPostingService {

        final List<BillingTransaction> posted = new ArrayList<>();
        private int counter = 0;

        @Override
        public Promise<String> postTransaction(BillingTransaction transaction) {
            posted.add(transaction);
            return Promise.of("entry-" + (++counter));
        }

        @Override
        public Promise<String> reverseTransaction(String originalTransactionId, String reversalReason) {
            return Promise.of("reversal-" + (++counter));
        }

        @Override
        public Promise<PostingStatus> getPostingStatus(String transactionId) {
            boolean found = posted.stream()
                .anyMatch(t -> t.getTransactionId().equals(transactionId));
            return Promise.of(found ? PostingStatus.POSTED : PostingStatus.NOT_FOUND);
        }
    }

    static final class CapturingCoordinator implements BillingTransactionCoordinator {
        final List<String> invocations = new ArrayList<>();

        @Override
        public Promise<CoordinationResult> coordinate(String workflowId, List<BillingTransaction> transactions) {
            invocations.add(workflowId);
            List<String> txIds = transactions.stream().map(BillingTransaction::getTransactionId).toList();
            return Promise.of(new CoordinationResult(workflowId, true, txIds, List.of(), null));
        }
    }
}
