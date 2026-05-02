package com.ghatana.phr.kernel.service;

import com.ghatana.phr.kernel.service.BillingService.*;
import com.ghatana.plugin.ledger.LedgerTransaction;
import com.ghatana.plugin.ledger.LedgerPlugin;
import com.ghatana.plugin.ledger.LedgerPlugin.AccountType;
import com.ghatana.plugin.ledger.LedgerPlugin.LedgerAccount;
import com.ghatana.plugin.ledger.LedgerPlugin.LedgerEntry;
import com.ghatana.plugin.ledger.LedgerPlugin.PostingStatus;
import com.ghatana.plugin.ledger.LedgerPlugin.TimeRange;
import com.ghatana.platform.plugin.PluginContext;
import com.ghatana.platform.plugin.PluginMetadata;
import com.ghatana.platform.plugin.PluginState;
import com.ghatana.platform.plugin.PluginType;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
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
    private BillingService serviceWithLedger;
    private CapturingLedger capturingLedger;

    @BeforeEach
    void setUp() {
        PhrTestInfrastructure.StubDataCloudAdapter dataCloud =
                new PhrTestInfrastructure.StubDataCloudAdapter();
        service = new BillingService(PhrTestInfrastructure.createTestContext(dataCloud));
        runPromise(service::start);
        capturingLedger = new CapturingLedger();
        serviceWithLedger = new BillingService(
            PhrTestInfrastructure.createTestContext(dataCloud), capturingLedger);
        runPromise(serviceWithLedger::start);
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

        @Test
        @DisplayName("sanitizes service line descriptions")
        void sanitizesServiceLineDescriptions() {
            BillingEncounter encounter = new BillingEncounter(
                null,
                "patient-1",
                "provider-1",
                "facility-1",
                List.of(new ServiceLine("99213", "<b>Office visit</b>", 1, new BigDecimal("1200.00"), "NPR")),
                new BigDecimal("1200.00"),
                "NPR",
                EncounterStatus.OPEN,
                null,
                null
            );

            BillingEncounter stored = runPromise(() -> service.createEncounter(encounter));

            assertThat(stored.serviceLines().get(0).description()).isEqualTo("&lt;b&gt;Office visit&lt;/b&gt;");
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
            LedgerTransaction tx = capturingLedger.posted.get(0);
            assertEquals("enc:" + enc.id(), tx.getTransactionId());
            assertEquals("phr", tx.getSourceId());
            assertEquals("PHR:AR:p-L1", tx.getDebitAccount());
            assertEquals("PHR:REVENUE:dr-L1", tx.getCreditAccount());
            assertEquals(0, new BigDecimal("1200.00").compareTo(tx.getAmount()));
            assertEquals("NPR", tx.getCurrency());
            assertEquals(LedgerTransaction.TransactionType.CHARGE, tx.getType());
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

            assertEquals(1, capturingLedger.posted.size());
            assertEquals("enc:" + enc.id(), capturingLedger.posted.get(0).getTransactionId(),
                "Transaction ID must contain encounter ID for ledger idempotency");
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
     * @doc.purpose Stub BillingLedgerPlugin for PHR billing integration tests
     * @doc.layer product
     * @doc.pattern TestDouble
     */
    static final class CapturingLedger implements LedgerPlugin {

        final List<LedgerTransaction> posted = new ArrayList<>();
        private int counter = 0;

        @Override
        public Promise<String> postTransaction(LedgerTransaction transaction) {
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

        @Override
        public Promise<LedgerAccount> createAccount(String accountId, AccountType type) {
            return Promise.of(new LedgerAccount(accountId, type, "NPR", BigDecimal.ZERO, Instant.now()));
        }

        @Override
        public Promise<Optional<LedgerEntry>> getEntry(String entryId) {
            return Promise.of(Optional.empty());
        }

        @Override
        public Promise<List<LedgerEntry>> queryEntries(String accountId, TimeRange range) {
            return Promise.of(List.of());
        }

        // Plugin interface methods
        @Override
        public PluginMetadata metadata() {
            return PluginMetadata.builder()
                .id("test-ledger")
                .name("Test Ledger")
                .version("1.0.0")
                .description("Test billing ledger plugin")
                .type(PluginType.CUSTOM)
                .build();
        }

        @Override
        public PluginState getState() {
            return PluginState.RUNNING;
        }

        @Override
        public Promise<Void> initialize(PluginContext context) {
            return Promise.complete();
        }

        @Override
        public Promise<Void> start() {
            return Promise.complete();
        }

        @Override
        public Promise<Void> stop() {
            return Promise.complete();
        }
    }
}
