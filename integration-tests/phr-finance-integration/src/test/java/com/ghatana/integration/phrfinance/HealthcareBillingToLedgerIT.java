package com.ghatana.integration.phrfinance;

import com.ghatana.plugin.billing.BillingLedgerPlugin;
import com.ghatana.kernel.context.KernelContext;
import com.ghatana.plugin.billing.BillingTransaction;
import com.ghatana.phr.kernel.service.BillingService;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Cross-domain integration test for PHR encounter close to ledger posting contract
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@DisplayName("HealthcareBillingToLedgerIT")
class HealthcareBillingToLedgerIT extends EventloopTestBase {

    @Test
    @DisplayName("closing encounter emits CHARGE transaction with patient/provider accounts")
    void closingEncounterEmitsChargeTransaction() {
        PhrFinanceTestFixtures.StubDataCloudAdapter dataCloud = new PhrFinanceTestFixtures.StubDataCloudAdapter();
        CapturingLedgerAdapter ledger = new CapturingLedgerAdapter();

        BillingService billingService = new BillingService(
            PhrFinanceTestFixtures.createTestContext(dataCloud),
            ledger,
            Executors.newCachedThreadPool()
        );

        runPromise(() -> billingService.start());

        BillingService.BillingEncounter created = runPromise(() -> billingService.createEncounter(
            new BillingService.BillingEncounter(
                null,
                "patient-cross-1",
                "provider-cross-1",
                "facility-cross-1",
                List.of(new BillingService.ServiceLine("99213", "Consultation", 1, new BigDecimal("1800.00"), "NPR")),
                new BigDecimal("1800.00"),
                "NPR",
                BillingService.EncounterStatus.OPEN,
                null,
                null
            )
        ));

        BillingService.BillingEncounter closed = runPromise(() -> billingService.closeEncounter(created.id()));

        assertThat(closed.status()).isEqualTo(BillingService.EncounterStatus.CLOSED);
        assertThat(ledger.posted).hasSize(1);

        com.ghatana.plugin.billing.BillingTransaction posted = ledger.posted.getFirst();
        assertThat(posted.getTransactionId()).isEqualTo("enc:" + created.id());
        assertThat(posted.getType()).isEqualTo(com.ghatana.plugin.billing.BillingTransaction.TransactionType.CHARGE);
        assertThat(posted.getDebitAccount()).isEqualTo("PHR:AR:patient-cross-1");
        assertThat(posted.getCreditAccount()).isEqualTo("PHR:REVENUE:provider-cross-1");
    }

    private static final class CapturingLedgerAdapter implements BillingLedgerPlugin {

        private final List<BillingTransaction> posted = new ArrayList<>();

        @Override
        public Promise<String> postTransaction(BillingTransaction transaction) {
            posted.add(transaction);
            return Promise.of("entry-" + posted.size());
        }

        @Override
        public Promise<String> reverseTransaction(String originalTransactionId, String reversalReason) {
            return Promise.of("reversal-1");
        }

        @Override
        public Promise<BillingLedgerPlugin.PostingStatus> getPostingStatus(String transactionId) {
            boolean found = posted.stream().anyMatch(tx -> tx.getTransactionId().equals(transactionId));
            return Promise.of(found ? BillingLedgerPlugin.PostingStatus.POSTED : BillingLedgerPlugin.PostingStatus.NOT_FOUND);
        }

        @Override
        public Promise<BillingLedgerPlugin.LedgerAccount> createAccount(String accountId, BillingLedgerPlugin.AccountType type) {
            return Promise.ofException(new UnsupportedOperationException());
        }

        @Override
        public Promise<java.util.Optional<BillingLedgerPlugin.LedgerEntry>> getEntry(String entryId) {
            return Promise.ofException(new UnsupportedOperationException());
        }

        @Override
        public Promise<java.util.List<BillingLedgerPlugin.LedgerEntry>> queryEntries(String accountId, BillingLedgerPlugin.TimeRange range) {
            return Promise.ofException(new UnsupportedOperationException());
        }

        @Override
        public com.ghatana.platform.plugin.PluginMetadata metadata() {
            return com.ghatana.platform.plugin.PluginMetadata.builder().id("test").name("Test").version("1.0").build();
        }

        @Override
        public com.ghatana.platform.plugin.PluginState getState() {
            return com.ghatana.platform.plugin.PluginState.STARTED;
        }

        @Override
        public Promise<Void> initialize(com.ghatana.platform.plugin.PluginContext context) {
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

        @Override
        public Promise<Void> shutdown() {
            return Promise.complete();
        }
    }

    private static class TestableBillingService extends BillingService {
        public TestableBillingService(KernelContext context, BillingLedgerPlugin ledger, Executor executor) {
            super(context, ledger, executor);
        }

        @Override
        protected <T> byte[] serialize(T object, String typeName, int version) {
            // Use actual TypedDataSerializer but call it synchronously
            // The Promise.ofBlocking in AbstractDataService will handle the async wrapping
            return com.ghatana.kernel.util.TypedDataSerializer.toBytes(object, typeName, version);
        }
    }
}
