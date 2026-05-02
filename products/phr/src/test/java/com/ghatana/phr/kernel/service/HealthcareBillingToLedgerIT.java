package com.ghatana.phr.kernel.service;

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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * End-to-end integration test for PHR billing close -> platform ledger posting.
 *
 * @doc.type class
 * @doc.purpose Verifies healthcare encounter closure emits correct BillingTransaction
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@DisplayName("HealthcareBillingToLedgerIT")
class HealthcareBillingToLedgerIT extends EventloopTestBase {

    @Test
    @DisplayName("closing encounter posts CHARGE transaction with patient/provider accounts")
    void encounterClosePostsCharge() {
        PhrTestInfrastructure.StubDataCloudAdapter dataCloud =
            new PhrTestInfrastructure.StubDataCloudAdapter();
        CapturingLedger ledger = new CapturingLedger();

        BillingService service = new BillingService(
            PhrTestInfrastructure.createTestContext(dataCloud), ledger);
        runPromise(() -> service.start());

        BillingService.BillingEncounter created = runPromise(() -> service.createEncounter(
            new BillingService.BillingEncounter(
                null,
                "patient-int-1",
                "provider-int-1",
                "facility-int-1",
                List.of(new BillingService.ServiceLine("99213", "Visit", 1, new BigDecimal("1500.00"), "NPR")),
                new BigDecimal("1500.00"),
                "NPR",
                BillingService.EncounterStatus.OPEN,
                null,
                null
            )
        ));

        BillingService.BillingEncounter closed = runPromise(() -> service.closeEncounter(created.id()));
        assertEquals(BillingService.EncounterStatus.CLOSED, closed.status());
        assertNotNull(closed.closedAt());

        assertEquals(1, ledger.posted.size());
        LedgerTransaction posted = ledger.posted.getFirst();
        assertEquals("enc:" + created.id(), posted.getTransactionId());
        assertEquals(LedgerTransaction.TransactionType.CHARGE, posted.getType());
        assertEquals("PHR:AR:patient-int-1", posted.getDebitAccount());
        assertEquals("PHR:REVENUE:provider-int-1", posted.getCreditAccount());
        assertEquals("NPR", posted.getCurrency());
    }

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
