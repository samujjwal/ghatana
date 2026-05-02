package com.ghatana.integration.phrfinance;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.plugin.ledger.LedgerPlugin;
import com.ghatana.plugin.ledger.LedgerTransaction;
import com.ghatana.phr.kernel.service.BillingService;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Verifies patient identity continuity from PHR encounters to finance posting accounts
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@DisplayName("PatientIdentitySyncIT")
class PatientIdentitySyncIT extends EventloopTestBase {

    // Test-specific subclass that overrides serialize to avoid blocking
    private static class TestableBillingService extends BillingService {
        public TestableBillingService(KernelContext context, LedgerPlugin ledger, Executor executor) {
            super(context, ledger, executor);
        }

        @Override
        protected <T> byte[] serialize(T object, String typeName, int version) {
            // Use actual TypedDataSerializer but call it synchronously
            // The Promise.ofBlocking in AbstractDataService will handle the async wrapping
            return com.ghatana.kernel.util.TypedDataSerializer.toBytes(object, typeName, version);
        }
    }

    @Test
    @DisplayName("encounter close keeps patient identity in ledger debit account")
    void patientIdentityIsPreservedAcrossBoundary() {
        PhrFinanceTestFixtures.StubDataCloudAdapter dataCloud = new PhrFinanceTestFixtures.StubDataCloudAdapter();
        IdentityAssertingLedgerAdapter ledger = new IdentityAssertingLedgerAdapter("patient-identity-77");

        BillingService billingService = new TestableBillingService(
            PhrFinanceTestFixtures.createTestContext(dataCloud),
            ledger,
            Executors.newCachedThreadPool()
        );

        runPromise(() -> billingService.start());

        BillingService.BillingEncounter encounter = runPromise(() -> billingService.createEncounter(
            new BillingService.BillingEncounter(
                null,
                "patient-identity-77",
                "provider-identity-22",
                "facility-identity-11",
                List.of(new BillingService.ServiceLine("80053", "Metabolic panel", 1, new BigDecimal("950.00"), "NPR")),
                new BigDecimal("950.00"),
                "NPR",
                BillingService.EncounterStatus.OPEN,
                null,
                null
            )
        ));

        runPromise(() -> billingService.closeEncounter(encounter.id()));

        assertThat(ledger.validated).isTrue();
    }

    private static final class IdentityAssertingLedgerAdapter implements LedgerPlugin {

        private final String expectedPatientId;
        private boolean validated;

        private IdentityAssertingLedgerAdapter(String expectedPatientId) {
            this.expectedPatientId = expectedPatientId;
        }

        @Override
        public Promise<String> postTransaction(LedgerTransaction transaction) {
            assertThat(transaction.getDebitAccount()).isEqualTo("PHR:AR:" + expectedPatientId);
            assertThat(transaction.getExternalReferenceId()).isNotBlank();
            validated = true;
            return Promise.of("entry-identity");
        }

        @Override
        public Promise<String> reverseTransaction(String originalTransactionId, String reversalReason) {
            return Promise.of("reversal-identity");
        }

        @Override
        public Promise<LedgerPlugin.PostingStatus> getPostingStatus(String transactionId) {
            return Promise.of(validated ? LedgerPlugin.PostingStatus.POSTED : LedgerPlugin.PostingStatus.NOT_FOUND);
        }

        @Override
        public Promise<LedgerPlugin.LedgerAccount> createAccount(String accountId, LedgerPlugin.AccountType type) {
            return Promise.ofException(new UnsupportedOperationException());
        }

        @Override
        public Promise<java.util.Optional<LedgerPlugin.LedgerEntry>> getEntry(String entryId) {
            return Promise.ofException(new UnsupportedOperationException());
        }

        @Override
        public Promise<java.util.List<LedgerPlugin.LedgerEntry>> queryEntries(String accountId, LedgerPlugin.TimeRange range) {
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
}
