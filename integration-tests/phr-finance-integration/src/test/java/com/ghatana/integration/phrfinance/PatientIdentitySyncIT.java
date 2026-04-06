package com.ghatana.integration.phrfinance;

import com.ghatana.plugin.billing.BillingLedgerPlugin;
import com.ghatana.plugin.billing.BillingTransaction;
import com.ghatana.phr.kernel.service.BillingService;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Verifies patient identity continuity from PHR encounters to finance posting accounts
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@DisplayName("PatientIdentitySyncIT")
class PatientIdentitySyncIT extends EventloopTestBase {

    @Test
    @DisplayName("encounter close keeps patient identity in ledger debit account")
    void patientIdentityIsPreservedAcrossBoundary() {
        PhrFinanceTestFixtures.StubDataCloudAdapter dataCloud = new PhrFinanceTestFixtures.StubDataCloudAdapter();
        IdentityAssertingLedgerAdapter ledger = new IdentityAssertingLedgerAdapter("patient-identity-77");

        BillingService billingService = new BillingService(
            PhrFinanceTestFixtures.createTestContext(dataCloud),
            ledger
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

    private static final class IdentityAssertingLedgerAdapter implements BillingLedgerPlugin {

        private final String expectedPatientId;
        private boolean validated;

        private IdentityAssertingLedgerAdapter(String expectedPatientId) {
            this.expectedPatientId = expectedPatientId;
        }

        @Override
        public Promise<String> postTransaction(BillingTransaction transaction) {
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
        public Promise<BillingLedgerPlugin.PostingStatus> getPostingStatus(String transactionId) {
            return Promise.of(validated ? BillingLedgerPlugin.PostingStatus.POSTED : BillingLedgerPlugin.PostingStatus.NOT_FOUND);
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
}
